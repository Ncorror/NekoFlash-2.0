package ru.forum.adbfastboottool

/**
 * A completed checkpoint is retained for reports, not reopened as a perpetual
 * on-screen status after a cold Activity launch.
 *
 * The decision is UI-only. It must never clear recovery evidence, change an
 * operation verdict, or suppress progress for a newly running operation.
 */
internal object OperationResultRestorePolicy {
    fun hideOldFinishedResult(
        coldLaunch: Boolean,
        activeOperation: Boolean,
        finishedResult: Boolean
    ): Boolean = coldLaunch && !activeOperation && finishedResult

    // The UI may release a "verification needed" notice after a readable hold.
    // The persisted verdict/evidence are never auto-acknowledged or rewritten.
    fun shouldAutoDismissStrip(outcome: String?): Boolean = outcome in
        setOf("SUCCESS", "CANCELLED", "VERIFY_PENDING")
}
