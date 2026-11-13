package ru.forum.adbfastboottool

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportSanitizerTest {
    @Test
    fun explicitFastbootSerialSelectorIsRedactedInShareCopy() {
        val raw = "fastboot -s cc16218c getvar all"
        val sanitized = ReportSanitizer.sanitizeText(raw)
        assertTrue(sanitized.contains("fastboot -s ${ReportSanitizer.REDACTED_SERIAL} getvar all"))
        assertFalse(sanitized.contains("cc16218c"))
    }

    @Test
    fun quotedAdbSerialSelectorIsRedactedInShareCopy() {
        val raw = "adb -s \"device with spaces\" shell getprop ro.product.device"
        val sanitized = ReportSanitizer.sanitizeText(raw)
        assertTrue(sanitized.contains("adb -s ${ReportSanitizer.REDACTED_SERIAL} shell"))
        assertFalse(sanitized.contains("device with spaces"))
    }

    @Test
    fun ordinarySerialFieldsRemainRedacted() {
        val sanitized = ReportSanitizer.sanitizeText("serial=ABC123 serialno:XYZ987")
        assertFalse(sanitized.contains("ABC123"))
        assertFalse(sanitized.contains("XYZ987"))
        assertTrue(sanitized.contains(ReportSanitizer.REDACTED_SERIAL))
    }
}
