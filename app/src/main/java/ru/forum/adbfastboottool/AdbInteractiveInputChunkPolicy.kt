package ru.forum.adbfastboottool

/**
 * ADB A_WRTE payload sizes must respect the device's CNXN maxdata.
 *
 * shell,v2,pty adds a five-byte framing header *inside* each ADB WRTE.
 * Slice only the wire packets: never truncate, modify or interpret the
 * user's UTF-8 bytes. The remote PTY reassembles an ordinary byte stream.
 */
internal object AdbInteractiveInputChunkPolicy {
    const val FALLBACK_MAX_PAYLOAD = 4096

    fun maxWirePayload(advertisedPeerMaxdata: Int, hostMaximum: Int): Int {
        require(hostMaximum > 0) { "Invalid host ADB max payload" }
        val peerMax = if (advertisedPeerMaxdata > 0) advertisedPeerMaxdata else FALLBACK_MAX_PAYLOAD
        return minOf(peerMax, hostMaximum)
    }

    fun fragments(payloadBytes: Int, maxWirePayload: Int, framingBytes: Int): Sequence<IntRange> {
        require(payloadBytes >= 0) { "Negative PTY input length" }
        require(framingBytes >= 0 && maxWirePayload > framingBytes) {
            "ADB peer maxdata is too small for shell framing"
        }
        val maxChunk = maxWirePayload - framingBytes
        return sequence {
            var offset = 0
            while (offset < payloadBytes) {
                val count = minOf(maxChunk, payloadBytes - offset)
                yield(offset until offset + count)
                offset += count
            }
        }
    }
}
