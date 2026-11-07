package ru.forum.adbfastboottool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Pure contract for real-device race: A_CLSE arrives before CLOSE_STDIN A_OKAY. */
class ShellV2EarlyClosePolicyTest {
    private fun frame(id: Int, body: ByteArray) =
        ByteBuffer.allocate(5 + body.size).order(ByteOrder.LITTLE_ENDIAN)
            .put(id.toByte()).putInt(body.size).put(body).array()

    @Test fun shortEchoMayBeBufferedBeforePeerClosesButThatAloneIsNotSuccess() {
        for ((text, expectedBytes) in listOf(
            "REV52_SHELL_OK\n" to 26,
            "REV52_REPEAT_OK\n" to 27
        )) {
            val buffered = frame(1, text.toByteArray()) + frame(3, byteArrayOf(0))
            assertEquals(expectedBytes, buffered.size)
            assertTrue(ShellV2EarlyClosePolicy.mayDrainAfterCloseStdin(
                writeAcknowledged = false, remoteClosed = true,
                bufferedResponseBytes = buffered.size, cancelled = false
            ))
            // A close + buffered bytes is not a successful command. The explicit
            // decoded EXIT frame is mandatory; a completed echo uses EXIT=0.
            assertFalse(ShellV2EarlyClosePolicy.succeeded(null, cancelled = false))
            assertEquals(0, ShellV2EarlyClosePolicy.completeExitCode(1, byteArrayOf(0)))
            assertTrue(ShellV2EarlyClosePolicy.succeeded(0, cancelled = false))
        }
    }

    @Test fun noPendingEvidenceOrNoCloseDoesNotBypassWriteFailure() {
        assertFalse(ShellV2EarlyClosePolicy.mayDrainAfterCloseStdin(false, true, 0, false))
        assertFalse(ShellV2EarlyClosePolicy.mayDrainAfterCloseStdin(false, false, 27, false))
        assertFalse(ShellV2EarlyClosePolicy.mayDrainAfterCloseStdin(false, true, 27, true))
        assertTrue(ShellV2EarlyClosePolicy.mayDrainAfterCloseStdin(true, false, 0, false))
    }

    @Test fun malformedOrMissingExitNeverSucceeds() {
        assertNull(ShellV2EarlyClosePolicy.completeExitCode(0, byteArrayOf()))
        assertNull(ShellV2EarlyClosePolicy.completeExitCode(1, null))
        assertNull(ShellV2EarlyClosePolicy.completeExitCode(1, byteArrayOf()))
        assertNull(ShellV2EarlyClosePolicy.completeExitCode(2, byteArrayOf(0, 0)))
        assertNull(ShellV2EarlyClosePolicy.completeExitCode(1, byteArrayOf(0, 0)))
        assertEquals(1, ShellV2EarlyClosePolicy.completeExitCode(1, byteArrayOf(1)))
        assertEquals(255, ShellV2EarlyClosePolicy.completeExitCode(1, byteArrayOf(-1)))
        assertFalse(ShellV2EarlyClosePolicy.succeeded(1, false))
        assertFalse(ShellV2EarlyClosePolicy.succeeded(null, false))
        assertFalse(ShellV2EarlyClosePolicy.succeeded(0, true))
    }
}
