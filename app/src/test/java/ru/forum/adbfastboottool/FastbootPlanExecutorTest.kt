package ru.forum.adbfastboottool

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FastbootPlanExecutorTest {
    @get:Rule val temporary = TemporaryFolder()
    private val events = mutableListOf<String>()
    private val variables = mutableMapOf("partition-type:userdata" to "ext4", "partition-size:userdata" to "1000000",
        "max-download-size" to "1000000", "current-slot" to "a", "slot-count" to "2")
    private var fail: String? = null
    private val port = object : FastbootPlanPort {
        override fun getVar(name: String): String? { events += "get:$name"; return variables[name] }
        override fun targets(partition: String, slot: String?): List<String> {
            events += "targets:$partition:$slot"
            return when (slot) { "all" -> listOf("${partition}_a", "${partition}_b"); null -> listOf(partition); else -> listOf("${partition}_$slot") }
        }
        override fun command(command: String): Boolean { events += command; return command != fail }
        override fun flash(partition: String, file: File) { events += "flash:$partition"; check("flash:$partition" != fail) }
        override fun download(file: File, command: String) { events += "download:$command" }
        override fun fetch(partition: String, file: File) { events += "fetch:$partition" }
        override fun inspect(partition: String) { events += "inspect:$partition" }
    }
    private val formatter = FilesystemFormatter { spec, dir, _ ->
        events += "generate:${spec.type}:${spec.sizeBytes}:${spec.options.sorted()}"
        File.createTempFile("fixture-", ".img", dir).also { it.writeBytes(ByteArray(100)) }
    }
    private fun executor(generator: FilesystemFormatter = formatter, cancel: () -> Unit = {}) =
        FastbootPlanExecutor(port, generator, temporary.root, cancel)
    private fun mutations() = events.filter { it.startsWith("erase:") || it.startsWith("flash:") || it.startsWith("reboot") || it.startsWith("set_active:") || it.startsWith("oem ") }

    @Test fun formatBuildsFilesystemThenFlashesWithoutInventedWireFormatOrErase() {
        executor().run(listOf(FastbootExecutionStep.Format("userdata")))
        assertEquals(listOf("flash:userdata"), mutations())
        assertTrue(events.indexOfFirst { it.startsWith("generate:") } < events.indexOf("flash:userdata"))
        assertTrue(events.none { it.startsWith("format:") })
    }
    @Test fun formatOverrideAndFsOptionsReachTheGenerator() {
        executor().run(listOf(FastbootExecutionStep.Format("userdata", "f2fs", "0x2000000", options = setOf("casefold", "projid"))))
        assertTrue(events.contains("generate:f2fs:33554432:[casefold, projid]"))
    }
    @Test fun failedGeneratorSendsNoMutationEvenForWipePlusReboot() {
        assertThrows(IllegalStateException::class.java) {
            executor(FilesystemFormatter { _, _, _ -> error("mkfs unavailable") })
                .run(listOf(FastbootExecutionStep.Wipe(), FastbootExecutionStep.Raw("reboot")))
        }
        assertTrue(mutations().isEmpty())
    }
    @Test fun wipePreparesEveryFilesystemBeforeErasingAndPreservesTheFollowingCommand() {
        variables["partition-type:cache"] = "ext4"; variables["partition-size:cache"] = "800000"
        variables["partition-type:metadata"] = "raw"
        executor().run(listOf(FastbootExecutionStep.Wipe(), FastbootExecutionStep.Raw("reboot")))
        assertEquals(listOf("erase:userdata", "flash:userdata", "erase:cache", "flash:cache", "erase:metadata", "reboot"), mutations())
        assertTrue(events.indexOfLast { it.startsWith("generate:") } < events.indexOf("erase:userdata"))
    }
    @Test fun missingOptionalPartitionsAreSkippedWithoutInventingEraseTargets() {
        executor().run(listOf(FastbootExecutionStep.Wipe()))
        assertEquals(listOf("erase:userdata", "flash:userdata"), mutations())
    }
    @Test fun unknownUserdataNeverClaimsSuccessfulWipe() {
        variables.remove("partition-type:userdata")
        assertThrows(IllegalStateException::class.java) { executor().run(listOf(FastbootExecutionStep.Wipe())) }
        assertTrue(mutations().isEmpty())
    }
    @Test fun laterGeneratorFailureStillPreventsTheFirstErase() {
        variables["partition-type:cache"] = "ext4"; variables["partition-size:cache"] = "800000"
        assertThrows(IllegalStateException::class.java) {
            executor(FilesystemFormatter { spec, dir, cancel ->
                check(spec.sizeBytes != 0x800000L) { "cache mkfs failed" }; formatter.generate(spec, dir, cancel)
            }).run(listOf(FastbootExecutionStep.Wipe()))
        }
        assertTrue(mutations().isEmpty())
    }
    @Test fun deviceDownloadLimitIsCheckedBeforeAnyErase() {
        variables["max-download-size"] = "10"
        assertThrows(IllegalArgumentException::class.java) { executor().run(listOf(FastbootExecutionStep.Wipe())) }
        assertTrue(mutations().isEmpty())
    }
    @Test fun decimalDownloadLimitIsNotMisreadAsHexadecimalPartitionSize() {
        // 100-byte fixture exceeds 75 decimal, but would fit if incorrectly read as 0x75.
        variables["max-download-size"] = "75"
        assertThrows(IllegalArgumentException::class.java) { executor().run(listOf(FastbootExecutionStep.Wipe())) }
        assertTrue(mutations().isEmpty())
    }
    @Test fun validLargeFlashCanBePlannedAfterWipeUnderDeviceDownloadLimit() {
        variables["max-download-size"] = "8192"
        val input = temporary.newFile().also { it.writeBytes(ByteArray(30000)) }
        executor().run(listOf(FastbootExecutionStep.Wipe(), FastbootExecutionStep.Flash("system", input, null)))
        assertEquals(listOf("erase:userdata", "flash:userdata", "flash:system"), mutations())
    }
    @Test fun malformedLaterSparseIsRejectedBeforeFirstWipeErase() {
        val input = temporary.newFile()
        AndroidSparseImage.Writer(input, 4096, 3).use { it.raw(ByteArray(4096 * 3)); it.finish() }
        input.writeBytes(input.readBytes().dropLast(1).toByteArray())
        assertThrows(IllegalArgumentException::class.java) {
            executor().run(listOf(FastbootExecutionStep.Wipe(), FastbootExecutionStep.Flash("system", input, null)))
        }
        assertTrue(mutations().isEmpty())
    }
    @Test fun indivisibleBootStillFailsPreflightBeforeWipe() {
        variables["max-download-size"] = "8192"
        val input = temporary.newFile().also { it.writeBytes(ByteArray(30000)) }
        assertThrows(IllegalArgumentException::class.java) {
            executor().run(listOf(FastbootExecutionStep.Wipe(), FastbootExecutionStep.Download(input, "boot")))
        }
        assertTrue(events.none { it.startsWith("download:") }); assertTrue(mutations().isEmpty())
    }
    @Test fun impossibleUserSparseLimitFailsBeforeErase() {
        val input = temporary.newFile().also { it.writeBytes(ByteArray(30000)) }
        assertThrows(IllegalArgumentException::class.java) {
            FastbootPlanExecutor(port, formatter, temporary.root, sparseLimit = 4096)
                .run(listOf(FastbootExecutionStep.Wipe(), FastbootExecutionStep.Flash("system", input, null)))
        }
        assertTrue(mutations().isEmpty())
    }
    @Test fun stopOnFailureDoesNotFlashOrRunTheTailOfThePlan() {
        fail = "erase:userdata"
        assertThrows(IllegalStateException::class.java) { executor().run(listOf(FastbootExecutionStep.Wipe(), FastbootExecutionStep.Raw("reboot"))) }
        assertEquals(listOf("erase:userdata"), mutations())
    }
    @Test fun flashFailureStopsBeforeTheNextPartitionAndReboot() {
        variables["partition-type:cache"] = "raw"; fail = "flash:userdata"
        assertThrows(IllegalStateException::class.java) { executor().run(listOf(FastbootExecutionStep.Wipe(), FastbootExecutionStep.Raw("reboot"))) }
        assertEquals(listOf("erase:userdata", "flash:userdata"), mutations())
    }
    @Test fun cancellationBeforeExecutionPreventsDeviceWrites() {
        assertThrows(InterruptedException::class.java) {
            executor(cancel = { if (events.any { it.startsWith("generate:") }) throw InterruptedException() })
                .run(listOf(FastbootExecutionStep.Wipe(), FastbootExecutionStep.Raw("reboot")))
        }
        assertTrue(mutations().isEmpty())
    }
    @Test fun formatAllResolvesBothSlotsAndPreparesBothBeforeFlash() {
        for (slot in listOf("a", "b")) {
            variables["partition-type:system_$slot"] = "ext4"; variables["partition-size:system_$slot"] = "1000000"
        }
        executor().run(listOf(FastbootExecutionStep.Format("system", slot = "all")))
        assertEquals(listOf("flash:system_a", "flash:system_b"), mutations())
        assertTrue(events.indexOfLast { it.startsWith("generate:") } < events.indexOf("flash:system_a"))
    }
    @Test fun wipeSuperUsesGeneratedMetadataImageInsteadOfSendingHostCommandOrSource() {
        val input = File(temporary.root, "super_empty.img"); HostImageFixtures.emptySuper(input)
        executor().run(listOf(FastbootExecutionStep.WipeSuper(input, null)))
        assertEquals(listOf("flash:super"), mutations())
        assertTrue(events.none { it.startsWith("wipe-super") || it.startsWith("download:") })
    }
    @Test fun corruptWipeSuperInCombinedPlanStopsBeforeUserdataErase() {
        val input = File(temporary.root, "bad.img").also { it.writeBytes(ByteArray(8192)) }
        assertThrows(IllegalArgumentException::class.java) {
            executor().run(listOf(FastbootExecutionStep.Wipe(), FastbootExecutionStep.WipeSuper(input, null)))
        }
        assertTrue(mutations().isEmpty())
    }
    @Test fun retrofitSlotAllIsForcedByMetadataAndOptionalOemFailureDoesNotDropRequestedFlash() {
        val input = File(temporary.root, "retrofit.img")
        HostImageFixtures.emptySuper(input, names = listOf("system", "vendor"), suffixed = true)
        fail = "oem allow-flash-super"
        executor().run(listOf(FastbootExecutionStep.WipeSuper(input, "all")))
        assertEquals(listOf("oem allow-flash-super", "flash:system_a", "flash:system_b", "flash:vendor_a", "flash:vendor_b"), mutations())
        assertTrue(events.none { it.startsWith("targets:") })
    }
    @Test fun retrofitSlotOtherRequiresKnownCurrentSlot() {
        val input = File(temporary.root, "other.img"); HostImageFixtures.emptySuper(input, names = listOf("system"), suffixed = true)
        executor().run(listOf(FastbootExecutionStep.WipeSuper(input, "other")))
        assertEquals(listOf("oem allow-flash-super", "flash:system_b"), mutations())
        events.clear(); variables.remove("current-slot")
        assertThrows(IllegalStateException::class.java) { executor().run(listOf(FastbootExecutionStep.WipeSuper(input, "other"))) }
        assertTrue(mutations().isEmpty())
    }
    @Test fun activeSlotSelectionIsExecutedAndUsedByFollowingHostTargets() {
        val input = File(temporary.root, "boot.img").also { it.writeBytes(ByteArray(100)) }
        executor().run(FastbootHostSyntax.compose(listOf(FastbootExecutionStep.Flash("boot", input, null)), false, emptySet(), "b"))
        assertEquals(listOf("set_active:b", "flash:boot_b"), mutations())
        assertTrue(events.contains("targets:boot:b"))
    }
    @Test fun explicitSlotIsNotOverriddenByActiveSlotSelection() {
        val input = File(temporary.root, "boot.img").also { it.writeBytes(ByteArray(100)) }
        executor().run(FastbootHostSyntax.compose(listOf(FastbootExecutionStep.Flash("boot", input, "a")), false, emptySet(), "b"))
        assertEquals(listOf("set_active:b", "flash:boot_a"), mutations())
    }
}
