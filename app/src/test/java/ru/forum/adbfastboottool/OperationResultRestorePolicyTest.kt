package ru.forum.adbfastboottool

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationResultRestorePolicyTest {
    @Test fun oldPendingResultIsHiddenAfterColdLaunch() {
        assertTrue(OperationResultRestorePolicy.hideOldFinishedResult(
            coldLaunch = true, activeOperation = false, finishedResult = true))
    }

    @Test fun oldSuccessAndFailureUseTheSameTerminalRule() {
        // No UI special-casing of the verdict: the log/checkpoint retains the truth.
        assertTrue(OperationResultRestorePolicy.hideOldFinishedResult(true, false, true))
    }

    @Test fun activeFlashingIsNeverSuppressed() {
        assertFalse(OperationResultRestorePolicy.hideOldFinishedResult(true, true, true))
        assertFalse(OperationResultRestorePolicy.hideOldFinishedResult(true, true, false))
    }

    @Test fun inProcessCompletedOperationsRemainVisibleForReview() {
        assertFalse(OperationResultRestorePolicy.hideOldFinishedResult(false, false, true))
    }

    @Test fun inProgressRecoveryIsNotHidden() {
        assertFalse(OperationResultRestorePolicy.hideOldFinishedResult(true, false, false))
    }
}
