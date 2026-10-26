package ru.forum.adbfastboottool

import java.io.File

/** A narrow adapter to the existing protocol, also usable by deterministic offline tests. */
internal interface FastbootPlanPort {
    fun getVar(name: String): String?
    fun downloadLimitBytes(): Long? = FastbootGetVarAllParser.parseSizeValue(getVar("max-download-size"))
    fun targets(partition: String, slot: String?): List<String>
    fun command(command: String): Boolean
    fun flash(partition: String, file: File)
    fun download(file: File, command: String)
    fun fetch(partition: String, file: File)
    fun inspect(partition: String)
}

/** Prepare host images before the first mutation; execute once, in order, without replay. */
internal class FastbootPlanExecutor(
    private val port: FastbootPlanPort,
    private val formatter: FilesystemFormatter,
    private val directory: File,
    private val checkCancelled: () -> Unit = {},
    private val log: (String) -> Unit = {},
    private val sparseLimit: Long = 0,
    private val onProgress: (String) -> Unit = {}
) {
    private data class Prepared(val label: String, val payload: File? = null, val flash: Boolean = false, val execute: () -> Unit)
    private var plannedActive: String? = null

    fun run(steps: List<FastbootExecutionStep>) {
        require(steps.isNotEmpty()) { "Empty Fastboot execution plan" }
        require(directory.isDirectory)
        plannedActive = null
        val prepared = steps.flatMap { step -> checkCancelled(); prepare(step) }
        val payloads = prepared.filter { it.payload != null }.distinctBy { it.payload to it.flash }
        if (payloads.isNotEmpty()) {
            val max = port.downloadLimitBytes()
            for (action in payloads) {
                val file = checkNotNull(action.payload)
                require(file.isFile && file.canRead()) { "Payload is unavailable: ${file.name}" }
                if (action.flash) {
                    FirmwareSource.WorkspaceFile(file).open().use {
                        FastbootFlashImage.prepare(it, max, sparseLimit, checkCancelled)
                    }
                } else {
                    // boot/update-super/download are one command payload, not partition overlays.
                    val rejection = FastbootDownloadSizePolicy.validate(file.length(), max)
                    require(rejection == null) {
                        "Payload ${file.name}: ${file.length()} bytes exceeds single-download capacity (${rejection?.reason}, ${rejection?.maxBytes}). No erase was sent."
                    }
                }
            }
        }
        prepared.forEachIndexed { index, action ->
            checkCancelled()
            log("Fastboot ${index + 1}/${prepared.size}: ${action.label}")
            onProgress("${index + 1}/${prepared.size}: ${action.label}")
            action.execute()
        }
    }

    private fun command(wire: String) = Prepared(wire) {
        check(port.command(wire)) { "Fastboot command failed: $wire. Execution stopped; completed steps were not rolled back." }
    }
    private fun flash(target: String, file: File) = Prepared("flash $target (${file.length()} bytes)", file, flash = true) {
        port.flash(target, file)
    }
    private fun targets(partition: String, slot: String?): List<String> = port.targets(partition, slot ?: plannedActive).also {
        require(it.isNotEmpty()) { "No target for $partition" }
    }

    private fun prepare(step: FastbootExecutionStep): List<Prepared> = when (step) {
        is FastbootExecutionStep.Raw -> listOf(command(step.command))
        is FastbootExecutionStep.SetActive -> {
            require(step.slot.matches(Regex("[a-z]")))
            plannedActive = step.slot
            listOf(command("set_active:${step.slot}"))
        }
        is FastbootExecutionStep.Flash -> targets(step.partition, step.slot).map { flash(it, step.file) }
        is FastbootExecutionStep.PartitionCommand -> targets(step.partition, step.slot).map { command("${step.prefix}:$it") }
        is FastbootExecutionStep.Download -> listOf(Prepared(step.command, step.file) { port.download(step.file, step.command) })
        is FastbootExecutionStep.Fetch -> {
            val target = targets(step.partition, step.slot).singleOrNull()
                ?: error("Fastboot fetch requires one partition per output file")
            listOf(Prepared("fetch $target") { port.fetch(target, step.file) })
        }
        is FastbootExecutionStep.Inspect -> listOf(Prepared("inspect ${step.partition}") { port.inspect(step.partition) })
        is FastbootExecutionStep.Format -> targets(step.partition, step.slot).map { target ->
            flash(target, filesystem(target, step.type, step.size, step.options))
        }
        is FastbootExecutionStep.Wipe -> listOf("userdata", "cache", "metadata").flatMap { partition ->
            val type = port.getVar("partition-type:$partition")?.trim()?.takeIf { it.isNotEmpty() }
            if (type == null) {
                check(partition != "userdata") { "Cannot determine userdata filesystem; wipe was not started" }
                log("Wipe: $partition is not reported by the device; skipped")
                emptyList()
            } else if (type == "ext4" || type == "f2fs") {
                val image = filesystem(partition, null, null, step.options, type)
                listOf(command("erase:$partition"), flash(partition, image))
            } else {
                log("Wipe: $partition type=$type; erase only, no filesystem generator for this type")
                listOf(command("erase:$partition"))
            }
        }
        is FastbootExecutionStep.WipeSuper -> {
            onProgress("Preparing wipe-super metadata")
            val metadata = SuperEmptyImage.load(step.image, directory, checkCancelled)
            val images = metadata.build(directory, checkCancelled)
            val flashes = images.flatMap { image ->
                val device = image.device
                val names = if (device.slotSuffixed) forcedSlotTargets(device.name, step.slot)
                    else targets(device.name, step.slot)
                names.map { name ->
                    port.getVar("partition-size:$name")?.takeIf { it.isNotBlank() }?.let { reported ->
                        require(FastbootHostSyntax.hexSize(reported) >= device.bytes) {
                            "Template block device $name (${device.bytes} bytes) exceeds the reported partition size"
                        }
                    }
                    flash(name, image.file)
                }
            }
            if (metadata.devices.first().name == "super") flashes else listOf(Prepared("oem allow-flash-super") {
                if (!port.command("oem allow-flash-super")) log("Optional OEM allow-flash-super failed; attempting the requested flash")
            }) + flashes
        }
    }

    private fun filesystem(target: String, overrideType: String?, overrideSize: String?, options: Set<String>,
        knownType: String? = null): File {
        val reportedType = knownType ?: port.getVar("partition-type:$target")?.trim()?.takeIf { it.isNotEmpty() }
            ?: error("Cannot determine filesystem type: $target")
        val reportedSize = port.getVar("partition-size:$target") ?: error("Cannot determine partition size: $target")
        val type = overrideType ?: reportedType
        val size = FastbootHostSyntax.hexSize(overrideSize ?: reportedSize)
        if (overrideType != null && type != reportedType) log("Format $target: requested $type, reported $reportedType")
        if (overrideSize != null) log("Format $target: requested size $overrideSize, reported $reportedSize")
        fun block(name: String): Long {
            val raw = port.getVar(name) ?: return 0
            val value = runCatching { FastbootHostSyntax.hexSize(raw) }.getOrDefault(0)
            return value.takeIf { it > 0 && it and (it - 1) == 0L } ?: 0
        }
        checkCancelled()
        log("Preparing $type filesystem for $target ($size bytes)")
        onProgress("Preparing $type filesystem for $target ($size bytes)")
        return formatter.generate(FilesystemSpec(type, size, block("erase-block-size"), block("logical-block-size"), options), directory, checkCancelled)
    }

    /** liblp SLOT_SUFFIXED must work even when the bootloader has-slot query says no. */
    private fun forcedSlotTargets(partition: String, requested: String?): List<String> {
        fun current(): String = plannedActive ?: port.getVar("current-slot")?.trim()?.removePrefix("_")
            ?.takeIf { it.matches(Regex("[a-z]")) } ?: error("Cannot determine current slot for $partition")
        val slot = requested ?: current()
        val slots = when (slot) {
            "all", "other" -> {
                val count = port.getVar("slot-count")?.trim()?.toIntOrNull()
                    ?.takeIf { it in 1..26 } ?: error("Cannot determine slot count for $partition")
                if (slot == "all") (0 until count).map { ('a' + it).toString() }
                else {
                    val index = current()[0] - 'a'
                    require(index < count && count > 1) { "No other slot for $partition" }
                    listOf(('a' + (index + 1) % count).toString())
                }
            }
            else -> listOf(slot.also { require(it.matches(Regex("[a-z]"))) { "Invalid slot: $it" } })
        }
        return slots.map { "${partition}_$it" }
    }
}
