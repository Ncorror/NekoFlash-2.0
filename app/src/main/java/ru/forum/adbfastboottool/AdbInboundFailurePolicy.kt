package ru.forum.adbfastboottool

/**
 * Preserve the real reason when a USB ADB payload read fails.
 *
 * The single-reader dispatcher must not turn USB-IN failures or checksum
 * mismatches into a generic INVALID_PAYLOAD. This is diagnostic only:
 * every failure is still terminal for the current transport session.
 */
internal object AdbInboundFailurePolicy {
    fun payloadReadFailed(
        cause: AdbPacketDispatcher.FailureCode?,
        detail: String?,
        command: Long,
        payloadBytes: Int
    ): AdbPacketDispatcher.ReadResult.Failed {
        val code = cause?.takeUnless { it == AdbPacketDispatcher.FailureCode.NONE }
            ?: AdbPacketDispatcher.FailureCode.INVALID_PAYLOAD
        val context = "ADB payload read/validation failed for cmd=0x${command.toString(16)}, bytes=${payloadBytes}"
        val message = detail?.takeIf { it.isNotBlank() }?.let { "${context}: ${it}" } ?: context
        return AdbPacketDispatcher.ReadResult.Failed(code, message)
    }
}
