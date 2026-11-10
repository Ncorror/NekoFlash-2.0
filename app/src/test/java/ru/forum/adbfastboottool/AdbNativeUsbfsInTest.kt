package ru.forum.adbfastboottool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbNativeUsbfsInTest {
    @Test fun successfulSyncBulkResultKeepsExactByteCount() {
        val raw = LongArray(14)
        raw[0] = 1
        raw[1] = 16_384
        raw[4] = 16_384
        raw[5] = 4
        raw[6] = NativeUsbfsBackend.STAGE_DONE.toLong()
        raw[7] = 1
        val result = AdbNativeUsbfsIn.decodeForTest(raw, 16_384)
        assertTrue(result.success)
        assertEquals(16_384, result.bytesTransferred)
        assertEquals(16_384, result.actualLength)
        assertEquals(1, result.requestCount)
        assertTrue(result.message.contains("requests=1"))
        assertEquals(0, result.errnoCode)
        assertFalse(result.disconnected)
    }

    @Test fun synchronousIoctlErrnoIsPreserved() {
        val raw = LongArray(14)
        raw[2] = 71 // EPROTO
        raw[5] = 5
        raw[6] = NativeUsbfsBackend.STAGE_READ.toLong()
        raw[7] = 1
        raw[13] = 71
        val result = AdbNativeUsbfsIn.decodeForTest(raw, 16_384)
        assertFalse(result.success)
        assertEquals(71, result.errnoCode)
        assertEquals(71, result.kernelIoctlErrno)
        assertEquals(1, result.requestCount)
        assertTrue(result.message.contains("kernelErrno=71"))
    }

    @Test fun positiveShortReadCanBeAccumulatedByNativeLoop() {
        val raw = LongArray(14)
        raw[0] = 1
        raw[1] = 16_384
        raw[4] = 8_192
        raw[6] = NativeUsbfsBackend.STAGE_DONE.toLong()
        raw[7] = 2
        val result = AdbNativeUsbfsIn.decodeForTest(raw, 16_384)
        assertTrue(result.success)
        assertEquals(16_384, result.bytesTransferred)
        assertEquals(2, result.requestCount)
    }

    @Test fun deviceRemovalIsClassifiedAsDisconnect() {
        for (errno in listOf(19L, 108L)) {
            val raw = LongArray(14)
            raw[2] = errno
            raw[6] = NativeUsbfsBackend.STAGE_READ.toLong()
            raw[13] = errno
            val result = AdbNativeUsbfsIn.decodeForTest(raw, 16_384)
            assertTrue(result.disconnected)
        }
    }

    @Test fun timeoutRemainsTerminal() {
        val raw = LongArray(14)
        raw[2] = 110
        raw[5] = 5_000
        raw[6] = NativeUsbfsBackend.STAGE_READ.toLong()
        raw[7] = 1
        raw[13] = 110
        val result = AdbNativeUsbfsIn.decodeForTest(raw, 16_384)
        assertFalse(result.success)
        assertTrue(result.timedOut)
        assertEquals(5_000L, result.elapsedMs)
    }
}
