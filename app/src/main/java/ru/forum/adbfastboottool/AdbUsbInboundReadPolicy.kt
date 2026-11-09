package ru.forum.adbfastboottool

/**
 * Read one ADB USB IN payload using its length from the already validated
 * ADB header. AOSP host receivers request the complete payload-sized USB
 * transfer, rather than artificially slicing it into 16 KiB requests.
 *
 * Android 9+ supports bulkTransfer lengths above 16 KiB. Keep the existing
 * 16 KiB approach on API 26/27 where Android's USB API truncates larger
 * requests. A short *positive* result is not a failed packet: read the
 * remainder at its exact offset. A zero/negative result is terminal; never
 * retry a failed USB transaction or silently re-assemble corrupted data.
 */
internal object AdbUsbInboundReadPolicy {
    private const val MAX_ADB_PAYLOAD_BYTES = 1_048_576
    private const val LEGACY_BULK_LIMIT_BYTES = 16 * 1024
    private const val LEGACY_TIMEOUT_MS = 5_000
    private const val FULL_PAYLOAD_TIMEOUT_MS = 15_000

    sealed class Result {
        data class Complete(val bytesRead: Int, val requests: Int) : Result()
        data class Failed(
            val bytesRead: Int,
            val requestedBytes: Int,
            val transferResult: Int,
            val requests: Int
        ) : Result()
    }

    fun readExact(
        totalBytes: Int,
        apiLevel: Int,
        read: (offset: Int, requestBytes: Int, timeoutMs: Int) -> Int
    ): Result {
        require(totalBytes in 0..MAX_ADB_PAYLOAD_BYTES) { "ADB payload length out of range" }
        var offset = 0
        var requests = 0
        while (offset < totalBytes) {
            val remainder = totalBytes - offset
            val requested = if (apiLevel >= 28) remainder else minOf(remainder, LEGACY_BULK_LIMIT_BYTES)
            val timeout = if (requested > LEGACY_BULK_LIMIT_BYTES) FULL_PAYLOAD_TIMEOUT_MS else LEGACY_TIMEOUT_MS
            val result = read(offset, requested, timeout)
            requests++
            if (result <= 0 || result > requested) {
                return Result.Failed(offset, requested, result, requests)
            }
            offset += result
        }
        return Result.Complete(offset, requests)
    }
}
