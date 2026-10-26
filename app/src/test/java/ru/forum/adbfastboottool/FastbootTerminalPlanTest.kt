package ru.forum.adbfastboottool

import java.io.File
import android.app.Application
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Exercise the actual Activity parser, including host-option stripping and normalization. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FastbootTerminalPlanTest {
    private lateinit var activity: MainActivity
    @Before fun attachParser() {
        activity = Robolectric.buildActivity(MainActivity::class.java).get()
        val app: Application = RuntimeEnvironment.getApplication()
        MainActivity::class.java.getDeclaredField("viewModel").apply { isAccessible = true }.set(activity, DeviceViewModel(app))
    }
    private fun field(instance: Any, name: String): Any? = instance.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(instance)
    private fun parse(command: String): Any? = MainActivity::class.java.getDeclaredMethod("parseFastbootCommand", String::class.java)
        .apply { isAccessible = true }.invoke(activity, command)
    @Suppress("UNCHECKED_CAST")
    private fun steps(request: Any): List<FastbootExecutionStep> = field(field(request, "action")!!, "steps") as List<FastbootExecutionStep>
    private fun image(): File = File(RuntimeEnvironment.getApplication().cacheDir, "input image.img").also { it.writeBytes(ByteArray(4096)) }

    @Test fun wipeIsAHostStepAndRetainsRebootWithSerial() {
        val request = parse("-s DeviceABC -w reboot")!!
        assertEquals("DeviceABC", field(request, "serial"))
        assertEquals(listOf(FastbootExecutionStep.Wipe(), FastbootExecutionStep.Raw("reboot")), steps(request))
    }
    @Test fun trailingWipeFlagWorksWithQuotedFlashFile() {
        val file = image(); val request = parse("flash boot '${file.path}' --slot=b -w")!!
        assertEquals(listOf(FastbootExecutionStep.Wipe(), FastbootExecutionStep.Flash("boot", file, "b")), steps(request))
    }
    @Test fun standaloneWipeWorksWithoutAPrimaryCommand() {
        assertEquals(listOf(FastbootExecutionStep.Wipe()), steps(parse("--wipe")!!))
    }
    @Test fun formatOverridesAndGlobalOptionsAreNormalizedTogether() {
        val request = parse("--fs-options=projid --slot=all format:f2fs:8000000 userdata --fs-options casefold")!!
        assertEquals(listOf(FastbootExecutionStep.Format("userdata", "f2fs", "8000000", "all", setOf("projid", "casefold"))), steps(request))
    }
    @Test fun wipeSuperResolvesItsInputAsAHostFile() {
        val file = image()
        assertEquals(listOf(FastbootExecutionStep.WipeSuper(file, "other")), steps(parse("wipe-super '${file.path}' --slot=other")!!))
    }
    @Test fun rawEscapeKeepsWireCommandAndOptionLikeOemArguments() {
        assertEquals(listOf(FastbootExecutionStep.Raw("format:userdata")), steps(parse("raw format:userdata")!!))
        val request = parse("-s AbC raw oem test '-w' --slot=a")!!
        assertEquals("AbC", field(request, "serial"))
        assertEquals(listOf(FastbootExecutionStep.Raw("oem test '-w' --slot=a")), steps(request))
    }
    @Test fun endOfHostOptionsPreservesOemArguments() {
        val request = parse("-s AbC -- oem test '-w' --slot=a")!!
        assertEquals(listOf(FastbootExecutionStep.Raw("oem test '-w' --slot=a")), steps(request))
    }
    @Test fun invalidFormatArgumentsCannotFallThroughToRawWireCommand() {
        for (command in listOf("format userdata ignored", "format:ext4:1G userdata", "format:ext4:1000:extra userdata", "--fs-options=wrong format userdata")) {
            assertNull(command, parse(command))
        }
    }
    @Test fun conflictingSerialsAreExplicitlyRejected() {
        assertNull(parse("-s one -s two -w"))
    }
    @Test fun sparseLimitsReachFlashAndFormatPlansWithSerialAndSlotIntact() {
        val file = image()
        for (option in listOf("-S 1G", "-S1G", "--sparse-limit 1G", "--sparse-limit=1G")) {
            val request = parse("-s AbC $option flash boot '${file.path}' --slot=b")!!
            assertEquals("AbC", field(request, "serial"))
            assertEquals(1L shl 30, field(field(request, "action")!!, "sparseLimit"))
            assertEquals(listOf(FastbootExecutionStep.Flash("boot", file, "b")), steps(request))
        }
        val request = parse("-S0 format userdata")!!
        assertEquals(0L, field(field(request, "action")!!, "sparseLimit"))
        assertEquals(listOf(FastbootExecutionStep.Format("userdata")), steps(request))
    }
    @Test fun invalidOrInapplicableSparseLimitsCannotBeSilentlyIgnored() {
        val file = image()
        for (command in listOf("-S -1 format userdata", "-S 1GB format userdata", "-S", "-S 999999999999G -w",
            "-S 1M reboot", "-S 1M boot '${file.path}'", "-S 1M update-super '${file.path}'", "-S 1M status")) {
            assertNull(command, parse(command))
        }
    }
    @Test fun rawAndEndOfOptionsPreserveOpaqueSparseLookingArguments() {
        for (prefix in listOf("raw", "--")) {
            assertEquals(listOf(FastbootExecutionStep.Raw("oem test -S 1M")), steps(parse("$prefix oem test -S 1M")!!))
        }
    }
    @Test fun updateSuperWipeArgumentDoesNotBecomeGlobalUserdataWipe() {
        val file = image()
        assertEquals(listOf(FastbootExecutionStep.Download(file, "update-super:super:wipe")), steps(parse("update-super '${file.path}' --wipe")!!))
    }
    @Test fun activeSlotOptionNoLongerDisappearsWhenPairedWithFlash() {
        val file = image()
        assertEquals(listOf(FastbootExecutionStep.SetActive("b"), FastbootExecutionStep.Flash("boot", file, null)), steps(parse("--set-active=b flash boot '${file.path}'")!!))
    }
    @Test fun trailingCommandsCannotBeSilentlyDroppedFromAWipePlan() {
        val file = image()
        for (command in listOf("-w flash boot '${file.path}' reboot", "-w erase userdata reboot", "-w boot '${file.path}' reboot", "update-super '${file.path}' super ignored")) {
            assertNull(command, parse(command))
        }
    }
}
