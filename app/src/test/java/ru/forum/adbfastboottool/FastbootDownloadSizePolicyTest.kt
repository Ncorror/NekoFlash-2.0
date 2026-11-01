package ru.forum.adbfastboottool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class FastbootDownloadSizePolicyTest {
    @Test fun acceptsPayloadWithinWireAndDeviceLimits() {
        assertNull(FastbootDownloadSizePolicy.validate(128L * 1024L * 1024L, 256L * 1024L * 1024L))
    }

    @Test fun rejectsDeviceReportedLimitBeforeDataPhase() {
        val result = assertNotNullResult(
            FastbootDownloadSizePolicy.validate(300L * 1024L * 1024L, 256L * 1024L * 1024L)
        )
        assertEquals(FastbootDownloadSizePolicy.Reason.DEVICE_LIMIT, result.reason)
        assertEquals(256L * 1024L * 1024L, result.maxBytes)
    }

    @Test fun rejectsUnrepresentableSinglePayload() {
        val result = assertNotNullResult(
            FastbootDownloadSizePolicy.validate(0x1_0000_0000L, null)
        )
        assertEquals(FastbootDownloadSizePolicy.Reason.WIRE_LIMIT, result.reason)
        assertEquals(FastbootDownloadSizePolicy.WIRE_MAX_BYTES, result.maxBytes)
    }

    @Test fun missingDeviceLimitDoesNotInventOne() {
        assertNull(FastbootDownloadSizePolicy.validate(1024L, null))
    }

    private fun <T : Any> assertNotNullResult(value: T?): T {
        assertNotNull(value)
        return value!!
    }
}
