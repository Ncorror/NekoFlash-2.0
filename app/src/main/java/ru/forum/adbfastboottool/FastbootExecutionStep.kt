package ru.forum.adbfastboottool

import java.io.File

/** Host work stays in one admitted operation; it is never sent as invented USB commands. */
internal sealed class FastbootExecutionStep {
    data class Raw(val command: String) : FastbootExecutionStep()
    data class SetActive(val slot: String) : FastbootExecutionStep()
    data class Flash(val partition: String, val file: File, val slot: String?) : FastbootExecutionStep()
    data class PartitionCommand(val prefix: String, val partition: String, val slot: String?) : FastbootExecutionStep()
    data class Download(val file: File, val command: String) : FastbootExecutionStep()
    data class Fetch(val partition: String, val file: File, val slot: String?) : FastbootExecutionStep()
    data class Inspect(val partition: String) : FastbootExecutionStep()
    data class Format(val partition: String, val type: String? = null, val size: String? = null,
        val slot: String? = null, val options: Set<String> = emptySet()) : FastbootExecutionStep()
    data class Wipe(val options: Set<String> = emptySet()) : FastbootExecutionStep()
    data class WipeSuper(val image: File, val slot: String?) : FastbootExecutionStep()
}

internal object FastbootHostSyntax {
    fun compose(primary: List<FastbootExecutionStep>, wipe: Boolean, options: Set<String>, active: String?): List<FastbootExecutionStep> = buildList {
        if (active != null) add(FastbootExecutionStep.SetActive(active))
        if (wipe) add(FastbootExecutionStep.Wipe(options))
        addAll(primary)
    }

    fun format(command: String, partition: String, slot: String?, options: Set<String>): FastbootExecutionStep.Format {
        val pieces = command.split(':')
        require(pieces.first() == "format" && pieces.size <= 3) { "Expected format[:FS_TYPE[:SIZE]] PARTITION" }
        require(partition.matches(Regex("[A-Za-z0-9._-]+"))) { "Invalid format partition: $partition" }
        val type = pieces.getOrNull(1)?.takeIf { it.isNotEmpty() }
        val size = pieces.getOrNull(2)?.takeIf { it.isNotEmpty() }
        if (size != null) hexSize(size)
        return FastbootExecutionStep.Format(partition, type, size, slot, options)
    }

    /** Fastboot partition sizes are hexadecimal, also when the 0x prefix is absent. */
    fun hexSize(raw: String): Long {
        val value = raw.trim().removePrefix("0x").removePrefix("0X")
        require(value.isNotEmpty() && value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            "Invalid hexadecimal Fastboot size: $raw"
        }
        return requireNotNull(value.toLongOrNull(16)?.takeIf { it > 0 }) { "Fastboot size is out of range: $raw" }
    }

    fun filesystemOptions(raw: String): Set<String> = raw.split(',').toSet().also { options ->
        require(options.all { it in setOf("projid", "casefold", "compress") }) {
            "Unknown filesystem options: $raw (projid,casefold,compress)"
        }
    }
}
