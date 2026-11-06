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
}
