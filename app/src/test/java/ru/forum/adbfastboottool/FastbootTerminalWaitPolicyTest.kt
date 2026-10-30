package ru.forum.adbfastboottool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FastbootTerminalWaitPolicyTest {
    @Test fun immediateUsbErrorsCannotCauseTightRetryLoop() {
        assertEquals(100L, FastbootTerminalWaitPolicy.retryDelayMs(0L, 120_000L))
        assertEquals(100L, FastbootTerminalWaitPolicy.retryDelayMs(800_000L, 120_000L))
        assertEquals(70L, FastbootTerminalWaitPolicy.retryDelayMs(30_000_000L, 120_000L))
    }

    @Test fun realTimeoutOrSlowBootloaderIsNotDelayedAgain() {
        assertEquals(0L, FastbootTerminalWaitPolicy.retryDelayMs(2_000_000_000L, 100_000L))
        assertEquals(0L, FastbootTerminalWaitPolicy.retryDelayMs(100_000_000L, 100_000L))
    }

    @Test fun waitNeverExceedsRemainingDeadlineOrSleepsAfterExpiry() {
        assertEquals(15L, FastbootTerminalWaitPolicy.retryDelayMs(0L, 15L))
        assertEquals(0L, FastbootTerminalWaitPolicy.retryDelayMs(0L, 0L))
        assertEquals(0L, FastbootTerminalWaitPolicy.retryDelayMs(0L, -3L))
        assertEquals(2L, FastbootTerminalWaitPolicy.retryDelayMs(30_000_000L, 2L))
    }

    @Test fun clockIsMonotonicAndTerminalBudgetUnchanged() {
        val start = 77_000_000_000L
        assertEquals(0L, FastbootTerminalWaitPolicy.elapsedMs(start, start))
        assertEquals(399L, FastbootTerminalWaitPolicy.elapsedMs(start, start + 399_999_999L))
        assertEquals(0L, FastbootTerminalWaitPolicy.elapsedMs(start, start - 1_000L))
        assertEquals(120_000L, FastbootTerminalWaitPolicy.COMMAND_DEADLINE_MS)
        assertTrue(FastbootTerminalWaitPolicy.retryDelayMs(0L, 120_000L) > 0L)
    }
}
