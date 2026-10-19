package ru.forum.adbfastboottool

/**
 * Protocol-size policy for a single Fastboot download transaction.
 *
 * This is not an artificial host cap: the wire command is download:%08x, so a
 * single payload cannot encode 2^32 bytes. A device may report an even smaller
 * max-download-size. Larger images require a real sparse/chunking implementation.
 */
internal object FastbootDownloadSizePolicy {
    const val WIRE_MAX_BYTES: Long = 0xFFFF_FFFFL

    enum class Reason { EMPTY, WIRE_LIMIT, DEVICE_LIMIT }

    data class Rejection(
        val reason: Reason,
        val payloadBytes: Long,
        val maxBytes: Long?
    )

    fun validate(payloadBytes: Long, deviceMaxBytes: Long?): Rejection? {
        if (payloadBytes <= 0L) {
            return Rejection(Reason.EMPTY, payloadBytes, null)
        }
        if (payloadBytes > WIRE_MAX_BYTES) {
            return Rejection(Reason.WIRE_LIMIT, payloadBytes, WIRE_MAX_BYTES)
        }
        val deviceLimit = deviceMaxBytes?.takeIf { it > 0L }
        if (deviceLimit != null && payloadBytes > deviceLimit) {
            return Rejection(Reason.DEVICE_LIMIT, payloadBytes, deviceLimit)
        }
        return null
    }
}
