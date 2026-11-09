package ru.forum.adbfastboottool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbNativeUsbfsInTest {
    @Test fun successfulUrbResultKeepsExactByteCount() {
        val raw = LongArray(14)
        raw[0] = 1
        raw[1] = 16_384
        raw[4] = 16_384
        raw[5] = 4
        raw[6] = NativeUsbfsBackend.STAGE_DONE.toLong()
        val result = AdbNativeUsbfsIn.decodeForTest(raw, 16_384)
        assertTrue(result.success)
        assertEquals(16_384, result.bytesTransferred)
        assertEquals(16_384, result.actualLength)
        assertEquals(0, result.errnoCode)
        assertFalse(result.disconnected)
    }

    @Test fun urbProtocolErrorIsNotCollapsedIntoGenericMinusOne() {
        val raw = LongArray(14)
        raw[0] = 0
        raw[1] = 0
        raw[2] = 71 // EPROTO
        raw[3] = -71
        raw[4] = 0
        raw[5] = 3
        raw[6] = NativeUsbfsBackend.STAGE_STATUS.toLong()
        val result = AdbNativeUsbfsIn.decodeForTest(raw, 16_384)
        assertFalse(result.success)
        assertEquals(71, result.errnoCode)
        assertEquals(-71, result.urbStatus)
        assertTrue(result.message.contains("errno=71"))
        assertTrue(result.message.contains("urbStatus=-71"))
    }

    @Test fun kernelSubmitErrnoIsPreservedSeparately() {
        val raw = LongArray(14)
        raw[2] = 32 // EPIPE
        raw[5] = 1
        raw[6] = NativeUsbfsBackend.STAGE_SUBMIT.toLong()
        raw[13] = 32
        val result = AdbNativeUsbfsIn.decodeForTest(raw, 16_384)
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
            val result = AdbNativeUsbfsIn.decodeForTest(raw, 16_384)
            assertTrue(result.disconnected)
        }
    }

    @Test fun timeoutRemainsTerminalWithoutSyntheticSuccess() {
        val raw = LongArray(14)
        raw[2] = 110 // ETIMEDOUT
        raw[5] = 15_000
        raw[6] = NativeUsbfsBackend.STAGE_TIMEOUT.toLong()
        val result = AdbNativeUsbfsIn.decodeForTest(raw, 16_384)
        assertFalse(result.success)
        assertEquals(110, result.errnoCode)
        assertEquals(15_000L, result.elapsedMs)
        assertFalse(result.disconnected)
        assertTrue(result.timedOut)
    }
}
