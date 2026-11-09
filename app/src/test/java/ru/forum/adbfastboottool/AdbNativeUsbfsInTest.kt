package ru.forum.adbfastboottool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbNativeUsbfsInTest {
    @Test fun aospTransferGeometryUsesEndpointPacketBoundary() {
        assertEquals(512, AdbNativeUsbfsIn.aospRequestLength(24, 512))
        assertEquals(16_384, AdbNativeUsbfsIn.aospRequestLength(16_384, 512))
        assertEquals(1_024, AdbNativeUsbfsIn.aospRequestLength(1_000, 512))
        assertEquals(64, AdbNativeUsbfsIn.aospRequestLength(24, 64))
        assertEquals(-1, AdbNativeUsbfsIn.aospRequestLength(24, 0))
        assertEquals(-1, AdbNativeUsbfsIn.aospRequestLength(0, 512))
    }

    @Test fun successfulTransferKeepsProtocolAndUsbRequestLengthsDistinct() {
        val raw = LongArray(14)
        raw[0] = 1
        raw[1] = 24
        raw[4] = 24
        raw[5] = 4
        raw[6] = NativeUsbfsBackend.STAGE_DONE.toLong()
        raw[7] = 1
        val result = AdbNativeUsbfsIn.decodeForTest(raw, 24, 512)
        assertTrue(result.success)
        assertEquals(24, result.bytesTransferred)
        assertEquals(24, result.actualLength)
        assertEquals(1, result.urbSubmissions)
        assertTrue(result.message.contains("bytes=24/24"))
        assertTrue(result.message.contains("request=512"))
        assertEquals(0, result.errnoCode)
        assertFalse(result.disconnected)
    }

    @Test fun urbProtocolErrorIsTerminalAndNotRetried() {
        val raw = LongArray(14)
        raw[0] = 0
        raw[1] = 0
        raw[2] = 71 // EPROTO
        raw[3] = -71
        raw[4] = 0
        raw[5] = 3
        raw[6] = NativeUsbfsBackend.STAGE_STATUS.toLong()
        raw[7] = 1
        val result = AdbNativeUsbfsIn.decodeForTest(raw, 16_384, 16_384)
        assertFalse(result.success)
        assertEquals(71, result.errnoCode)
        assertEquals(-71, result.urbStatus)
        assertEquals(1, result.urbSubmissions)
        assertTrue(result.message.contains("urbSubmissions=1"))
    }

    @Test fun unexpectedShortTransferStaysAProtocolFailure() {
        val raw = LongArray(14)
        raw[0] = 0
        raw[1] = 8_192
        raw[4] = 8_192
        raw[6] = NativeUsbfsBackend.STAGE_LENGTH.toLong()
        raw[7] = 1
        val result = AdbNativeUsbfsIn.decodeForTest(raw, 16_384, 16_384)
        assertFalse(result.success)
        assertEquals(8_192, result.bytesTransferred)
        assertEquals(8_192, result.actualLength)
    }

    @Test fun kernelSubmitErrnoIsPreservedSeparately() {
        val raw = LongArray(14)
        raw[2] = 32 // EPIPE
        raw[5] = 1
        raw[6] = NativeUsbfsBackend.STAGE_SUBMIT.toLong()
        raw[13] = 32
        val result = AdbNativeUsbfsIn.decodeForTest(raw, 16_384, 16_384)
        assertFalse(result.success)
        assertEquals(32, result.errnoCode)
        assertEquals(32, result.kernelIoctlErrno)
        assertTrue(result.message.contains("kernelErrno=32"))
    }

    @Test fun deviceRemovalIsClassifiedAsDisconnect() {
        for (errno in listOf(19L, 108L)) {
            val raw = LongArray(14)
            raw[2] = errno
            raw[6] = NativeUsbfsBackend.STAGE_REAP.toLong()
            val result = AdbNativeUsbfsIn.decodeForTest(raw, 16_384, 16_384)
            assertTrue(result.disconnected)
        }
    }

    @Test fun timeoutRemainsTerminalWithoutSyntheticSuccess() {
        val raw = LongArray(14)
        raw[2] = 110 // ETIMEDOUT
        raw[5] = 15_000
        raw[6] = NativeUsbfsBackend.STAGE_TIMEOUT.toLong()
        val result = AdbNativeUsbfsIn.decodeForTest(raw, 16_384, 16_384)
        assertFalse(result.success)
        assertEquals(110, result.errnoCode)
        assertEquals(15_000L, result.elapsedMs)
        assertFalse(result.disconnected)
        assertTrue(result.timedOut)
    }
}
