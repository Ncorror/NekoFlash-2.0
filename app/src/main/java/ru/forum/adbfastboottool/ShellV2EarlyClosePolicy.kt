package ru.forum.adbfastboottool

/**
 * A short, one-shot shell/v2 command can finish (including its EXIT packet)
 * while the host waits for A_OKAY on the five-byte CLOSE_STDIN frame.
 *
 * Reading an already buffered response is not executing the command again.
 * This policy never infers successful execution from A_CLSE, bytes pending,
 * or missing/malformed EXIT. The command must report explicit exit=0.
 */
internal object ShellV2EarlyClosePolicy {
    fun mayDrainAfterCloseStdin(
        writeAcknowledged: Boolean,
        remoteClosed: Boolean,
        bufferedResponseBytes: Int,
        cancelled: Boolean
    ): Boolean = !cancelled && (
        writeAcknowledged || (remoteClosed && bufferedResponseBytes > 0)
    )

    fun completeExitCode(declaredLength: Int, payload: ByteArray?): Int? =
        if (declaredLength == 1 && payload?.size == 1) payload[0].toInt() and 0xFF else null

    fun succeeded(exitCode: Int?, cancelled: Boolean): Boolean =
        exitCode == 0 && !cancelled
}
