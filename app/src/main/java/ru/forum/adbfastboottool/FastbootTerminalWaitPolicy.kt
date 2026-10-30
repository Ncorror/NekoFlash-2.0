package ru.forum.adbfastboottool

/**
 * Prevent a USB driver that immediately returns -1/0 from hot-spinning while
 * waiting for a Fastboot terminal response.
 *
 * The wait does not discard, alter, or coalesce responses or trace entries.
 * Every attempted read still passes through the normal protocol logger.
 * The overall terminal deadline remains unchanged.
 */
internal object FastbootTerminalWaitPolicy {
    const val MIN_EMPTY_READ_INTERVAL_MS = 100L
    const val COMMAND_DEADLINE_MS = 120_000L

    /** Elapsed values derive from a monotonic clock, not device wall time. */
    fun elapsedMs(startNs: Long, nowNs: Long): Long =
        ((nowNs - startNs).coerceAtLeast(0L) / 1_000_000L)

    /**
     * Add only enough delay to avoid a tight loop. A slow timeout already
     * provides the delay; a fast failure gets a small cancellable pause.
     */
    fun retryDelayMs(readElapsedNs: Long, remainingMs: Long): Long {
        if (remainingMs <= 0L) return 0L
        val readElapsedMs = (readElapsedNs.coerceAtLeast(0L) / 1_000_000L)
        return (MIN_EMPTY_READ_INTERVAL_MS - readElapsedMs)
            .coerceAtLeast(0L)
            .coerceAtMost(remainingMs)
    }
}
