package ru.forum.adbfastboottool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbInboundFailurePolicyTest {
    private val wrte = 0x45545257L

    @Test fun oneMiBUsbInFailureKeepsUnderlyingCodeAndByteOffset() {
        val failure = AdbInboundFailurePolicy.payloadReadFailed(
            AdbPacketDispatcher.FailureCode.USB_IN_FAILED,
            "ADB payload USB IN failed after 16384/1048576 bytes (result=-1)",
            wrte,
            1_048_576
        )
        assertEquals(AdbPacketDispatcher.FailureCode.USB_IN_FAILED, failure.code)
        assertTrue(failure.message.contains("cmd=0x45545257"))
        assertTrue(failure.message.contains("bytes=1048576"))
        assertTrue(failure.message.contains("16384/1048576"))
        assertTrue(failure.message.contains("result=-1"))
    }

    @Test fun partialRecoveryPayloadStillKeepsChecksumMismatch() {
        val failure = AdbInboundFailurePolicy.payloadReadFailed(
            AdbPacketDispatcher.FailureCode.CHECKSUM_MISMATCH,
            "ADB payload checksum mismatch: expected=414, actual=413, bytes=422256",
            wrte,
            422_256
        )
        assertEquals(AdbPacketDispatcher.FailureCode.CHECKSUM_MISMATCH, failure.code)
        assertTrue(failure.message.contains("expected=414, actual=413"))
        assertTrue(failure.message.contains("bytes=422256"))
    }

    @Test fun disconnectIsNotMaskedAsInvalidPayload() {
        val failure = AdbInboundFailurePolicy.payloadReadFailed(
            AdbPacketDispatcher.FailureCode.DEVICE_DISCONNECTED,
            "USB device detached during payload read",
            wrte,
            1_048_576
        )
        assertEquals(AdbPacketDispatcher.FailureCode.DEVICE_DISCONNECTED, failure.code)
        assertTrue(failure.message.contains("USB device detached"))
    }

    @Test fun unknownOrNoneCauseStillFailsClosed() {
        for (cause in listOf(null, AdbPacketDispatcher.FailureCode.NONE)) {
            val failure = AdbInboundFailurePolicy.payloadReadFailed(cause, " ", wrte, 1_048_576)
            assertEquals(AdbPacketDispatcher.FailureCode.INVALID_PAYLOAD, failure.code)
            assertTrue(failure.message.contains("bytes=1048576"))
        }
    }

    @Test fun existingInvalidPayloadCauseIsPreserved() {
        val failure = AdbInboundFailurePolicy.payloadReadFailed(
            AdbPacketDispatcher.FailureCode.INVALID_PAYLOAD,
            "ADB payload length mismatch: header=2048, requested=1024",
            wrte,
            2048
        )
        assertEquals(AdbPacketDispatcher.FailureCode.INVALID_PAYLOAD, failure.code)
        assertTrue(failure.message.contains("header=2048, requested=1024"))
    }
}
