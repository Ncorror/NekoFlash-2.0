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

    /**
     * A terminal command reaches the timed-out state only after the entire USB OUT
     * command was accepted by bulkTransfer. A timeout here means the expected
     * Fastboot OKAY/FAIL never arrived on USB IN, not an OUT/write failure.
     * The device might already have acted on a reboot command.
     */
    fun isUnansweredTerminalCommand(reason: String): Boolean =
        reason.startsWith("Terminal Fastboot command timed out:", ignoreCase = true)

    /** Elapsed values derive from a monotonic clock, not device wall time. */
    fun elapsedMs(startNs: Long, nowNs: Long): Long =
        ((nowNs - startNs).coerceAtLeast(0L) / 1_000_000L)

    /**
     * Long bootloader erase/flash operations can stay silent for tens of seconds.
     * Make that state explicit without changing deadlines, retries or wire behavior.
     */
    fun waitMessage(command: String?, elapsedSec: Long): String {
        val label = command?.trim()?.takeIf { it.isNotEmpty() } ?: "Fastboot command"
        return if (elapsedSec <= 10L) {
            "⏳ Device is still processing $label. Command was sent; waiting for final OKAY/FAIL... " +
                "$elapsedSec sec. Do not resend or disconnect."
        } else {
            "⏳ Still processing $label... $elapsedSec sec; waiting for final OKAY/FAIL."
        }
    }

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
