package ru.forum.adbfastboottool

import org.junit.Assert.*
import org.junit.Test

class AdbSyncUsbInTest {
    @Test fun failedBulkDoesNotClaimZeroActualBytes() {
        val result = AdbSyncUsbIn.decode(longArrayOf(-1, 71, 1234, 1), 16384)
        assertEquals(71, result.errno)
        assertTrue(result.detail(16384).contains("actual=unknown"))
        assertTrue(result.detail(16384).contains("kernelErrno=71"))
    }

    @Test fun preflightFailureIsNotKernelError() {
        val result = AdbSyncUsbIn.decode(longArrayOf(-1, 12, 0, 0), 16384)
        assertTrue(result.detail(16384).contains("kernelErrno=unknown"))
    }

    @Test fun positivePartialCompletionPreservesExactCount() {
        val result = AdbSyncUsbIn.decode(longArrayOf(511, 0, 500, 1), 16384)
        assertEquals(511, result.result)
        assertTrue(result.detail(16384).contains("actual=511"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun malformedNativeLengthIsRejected() {
        AdbSyncUsbIn.decode(longArrayOf(16385, 0, 0, 1), 16384)
    }
}
