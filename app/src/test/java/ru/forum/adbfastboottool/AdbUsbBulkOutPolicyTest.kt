package ru.forum.adbfastboottool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbUsbBulkOutPolicyTest {
    @Test fun hardwareBoundaryBytes1023_1024_1025() {
        var zlpCalls = 0
        fun finish(size: Int): Boolean =
            AdbUsbBulkOutPolicy.finishTransfer(size, 512) {
                zlpCalls++
                0
            }
        assertTrue(finish(1023))
        assertEquals(0, zlpCalls)
        assertTrue(finish(1024))
        assertEquals(1, zlpCalls)
        assertTrue(finish(1025))
        assertEquals(1, zlpCalls)
        assertTrue(finish(0))
        assertEquals(1, zlpCalls)
    }

    @Test fun fastAndFullSpeedEndpointsUseActualMaxPacketSize() {
        for (maxPacket in listOf(8, 16, 32, 64, 512, 1024)) {
            assertTrue(AdbUsbBulkOutPolicy.requiresZlp(maxPacket, maxPacket))
            assertTrue(AdbUsbBulkOutPolicy.requiresZlp(2 * maxPacket, maxPacket))
            assertFalse(AdbUsbBulkOutPolicy.requiresZlp(maxPacket + 1, maxPacket))
            assertFalse(AdbUsbBulkOutPolicy.requiresZlp(0, maxPacket))
        }
        // ADB's 24-byte header must also end with ZLP on an 8-byte endpoint.
        assertTrue(AdbUsbBulkOutPolicy.requiresZlp(24, 8))
        assertFalse(AdbUsbBulkOutPolicy.requiresZlp(24, 512))
        // 16 KiB chunks are still a single logical ADB payload transfer.
        assertTrue(AdbUsbBulkOutPolicy.requiresZlp(16 * 1024, 512))
    }

    @Test fun missingOrFailedZlpIsNeverTreatedAsSuccess() {
        assertFalse(AdbUsbBulkOutPolicy.finishTransfer(1024, 512) { -1 })
        assertFalse(AdbUsbBulkOutPolicy.finishTransfer(1024, 512) { 1 })
        assertTrue(AdbUsbBulkOutPolicy.finishTransfer(1024, 512) { 0 })
        assertTrue(AdbUsbBulkOutPolicy.finishTransfer(1023, 512) {
            throw AssertionError("Unexpected USB ZLP for a nonmultiple packet")
        })
    }

    @Test fun badEndpointParametersAreRejected() {
        try {
            AdbUsbBulkOutPolicy.requiresZlp(1024, 0)
            throw AssertionError("Zero endpoint maxPacketSize accepted")
        } catch (_: IllegalArgumentException) {
        }
        try {
            AdbUsbBulkOutPolicy.requiresZlp(-1, 512)
            throw AssertionError("Negative transfer length accepted")
        } catch (_: IllegalArgumentException) {
        }
    }
}
