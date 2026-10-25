package ru.forum.adbfastboottool

import org.junit.Assert.*
import org.junit.Test
import ru.forum.adbfastboottool.RecoveryInstallVerifier.Verdict.*

class RecoveryInstallVerifierTest {
    private fun verdict(log: String) = RecoveryInstallVerifier.evaluateCurrentAttempt(log.trimIndent()).verdict
    private val boot = "12345678-1234-1234-1234-123456789abc"
    private val hash = "a".repeat(64)

    @Test fun archivedSuccessCannotConfirmANewAttempt() {
        val logs = listOf(
            RecoveryInstallVerifier.LogSource("/cache/recovery/last_log", "Install from ADB complete (status: 0)."),
            RecoveryInstallVerifier.LogSource("/tmp/recovery.log", "Starting ADB sideload\nInstall from ADB complete (status: 0).")
        )
        assertEquals(UNKNOWN, RecoveryInstallVerifier.evaluate(logs).verdict)
    }
    @Test fun missingSessionCannotConfirmAnAttempt() {
        assertEquals(UNKNOWN, verdict("Install from ADB complete (status: 0)."))
    }
    @Test fun aospCurrentSessionSuccess() {
        assertEquals(SUCCESS, verdict("Starting ADB sideload\nInstalling update\nInstall from ADB complete (status: 0)."))
    }
    @Test fun aospCurrentSessionFailure() {
        assertEquals(FAILED, verdict("Starting ADB sideload\nInstall from ADB complete (status: 1)."))
    }
    @Test fun twrpCurrentSessionSuccess() {
        assertEquals(SUCCESS, verdict("I:operation_start: 'Sideload'\nStarting ADB sideload\nInstalling zip file\nI:operation_end - status=0"))
    }
    @Test fun orangeFoxFinalStatusWinsOverIntermediateSuccess() {
        assertEquals(FAILED, verdict("Starting sideload\nFinished installing OrangeFox!\nI:operation_end - status=1"))
    }
    @Test fun intermediateScriptSuccessIsNotInstallationSuccess() {
        assertEquals(UNKNOWN, verdict("Starting sideload\nscript succeeded\nFinished installing OrangeFox!"))
    }
    @Test fun transferCompletionIsNotInstallationSuccess() {
        assertEquals(UNKNOWN, verdict("Starting ADB sideload\nDONEDONE"))
    }
    @Test fun repeatedAttemptOfTheSamePackageIsAmbiguous() {
        val one = "Starting ADB sideload\nInstalling zip file '/sideload/package.zip'\nInstall from ADB complete (status: 0).\n"
        assertEquals(UNKNOWN, verdict(one + one))
    }
    @Test fun unrelatedLaterRecoveryActionInvalidatesVerdict() {
        assertEquals(UNKNOWN, verdict("I:operation_start: 'Sideload'\nI:operation_end - status=0\nI:operation_start: 'Install'"))
    }
    @Test fun unrelatedSecondPackageInvalidatesVerdict() {
        assertEquals(UNKNOWN, verdict("Starting sideload\nInstalling zip file 'one.zip'\nI:operation_end - status=0\nInstalling zip file 'two.zip'"))
    }
    @Test fun oldStatusBeforeTheCurrentSessionIsIgnored() {
        assertEquals(UNKNOWN, verdict("Install from ADB complete (status: 0).\nStarting ADB sideload"))
    }
    @Test fun finalFailureIsNotOverwrittenByScriptOutput() {
        assertEquals(FAILED, verdict("Starting sideload\nI:operation_end - status=1\nscript succeeded"))
    }
    @Test fun errorEvidenceIsKeptForTheCurrentAttempt() {
        val result = RecoveryInstallVerifier.evaluateCurrentAttempt("Starting ADB sideload\nUpdater process ended with ERROR: 7")
        assertEquals(FAILED, result.verdict)
        assertTrue(result.evidence.orEmpty().contains("ERROR: 7"))
    }
    @Test fun sameBootAndUnchangedPrefixRequired() {
        val boundary = RecoveryLogBoundary.parse(boot, "4294967296", "$hash  /tmp/recovery.log")!!
        assertEquals(4294967296L, boundary.byteCount)
        assertTrue(boundary.matches(boot, hash))
        assertFalse(boundary.matches("22345678-1234-1234-1234-123456789abc", hash))
        assertFalse(boundary.matches(boot, "b".repeat(64)))
    }
    @Test fun malformedOrOverflowingBoundaryIsNotProof() {
        assertNull(RecoveryLogBoundary.parse("", "12", hash))
        assertNull(RecoveryLogBoundary.parse(boot, "-1", hash))
        assertNull(RecoveryLogBoundary.parse(boot, Long.MAX_VALUE.toString(), hash))
        assertNull(RecoveryLogBoundary.parse(boot, "9999999999999999999999", hash))
        assertNull(RecoveryLogBoundary.parse(boot, "12", "bad"))
    }
    @Test fun emptyLogCanBeACheckpointButNotAVerdict() {
        assertNotNull(RecoveryLogBoundary.parse(boot, "0", hash))
        assertEquals(UNKNOWN, verdict(""))
    }
}
