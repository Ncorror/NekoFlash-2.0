package ru.forum.adbfastboottool

import org.junit.Assert.*
import org.junit.Test

class FastbootHostSyntaxTest {
    @Test fun formatDefaultsComeFromTheDevice() {
        assertEquals(FastbootExecutionStep.Format("userdata"), FastbootHostSyntax.format("format", "userdata", null, emptySet()))
    }
    @Test fun emptyTypeAllowsSizeOverride() {
        val step = FastbootHostSyntax.format("format::1000000", "userdata", "b", setOf("projid"))
        assertNull(step.type); assertEquals("1000000", step.size); assertEquals("b", step.slot)
        assertEquals(setOf("projid"), step.options)
    }
    @Test fun fastbootHexSizeNeverBecomesDecimal() {
        for (size in listOf("1000", "0x1000", "0X1000")) assertEquals(4096L, FastbootHostSyntax.hexSize(size))
    }
    @Test fun invalidOrOverflowingSizesAreRejected() {
        for (size in listOf("", "0", "-1", "+1", "1G", "ffffffffffffffff", "8000000000000000")) {
            assertThrows(IllegalArgumentException::class.java) { FastbootHostSyntax.hexSize(size) }
        }
    }
    @Test fun extraFormatFieldsAndInvalidPartitionNamesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { FastbootHostSyntax.format("format:ext4:1000:ignored", "userdata", null, emptySet()) }
        assertThrows(IllegalArgumentException::class.java) { FastbootHostSyntax.format("format", "userdata:extra", null, emptySet()) }
    }
    @Test fun fsOptionsUseDesktopNamesAndRejectUnknownOrEmptyOptions() {
        assertEquals(setOf("projid", "casefold", "compress"), FastbootHostSyntax.filesystemOptions("projid,casefold,compress"))
        for (options in listOf("", "project_quota", "casefold,")) {
            assertThrows(IllegalArgumentException::class.java) { FastbootHostSyntax.filesystemOptions(options) }
        }
    }
    @Test fun combinedPlanContainsActiveWipeAndPrimaryExactlyOnce() {
        val plan = FastbootHostSyntax.compose(listOf(FastbootExecutionStep.Raw("reboot")), true, setOf("projid"), "b")
        assertEquals(listOf(FastbootExecutionStep.SetActive("b"), FastbootExecutionStep.Wipe(setOf("projid")), FastbootExecutionStep.Raw("reboot")), plan)
    }
}
