package ru.forum.adbfastboottool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FastbootPostDownloadWaitPolicyTest {
    @Test fun postDataBudgetIsUnchangedFromLegacyWait() {
        assertEquals(30_000L, FastbootPostDownloadWaitPolicy.FINAL_TIMEOUT_MS)
    }

    @Test fun aReadNeverWaitsForTheWholeThirtySecondBudget() {
        val interval = FastbootPostDownloadWaitPolicy.READ_SLICE_MS
        assertTrue(interval > 0)
        assertTrue(interval.toLong() < FastbootPostDownloadWaitPolicy.FINAL_TIMEOUT_MS)
        assertEquals(2_000, interval)
    }
}
