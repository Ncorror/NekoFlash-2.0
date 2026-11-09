package ru.forum.adbfastboottool

/**
 * Plans a bounded USBFS-compatible read window for one already validated ADB
 * payload. Only a complete collection of correctly sized chunks may become a
 * protocol packet. A short/failed USB completion never produces a partial ACK.
 */
internal object AdbUsbInboundPipeline {
    const val CHUNK_BYTES = 16 * 1024
    const val MAX_IN_FLIGHT = 4
    private const val MAX_PAYLOAD_BYTES = 1_048_576

    data class Chunk(val index: Int, val offset: Int, val size: Int)

    fun plan(length: Int): List<Chunk> {
        require(length in 0..MAX_PAYLOAD_BYTES)
        return buildList {
            var offset = 0
            while (offset < length) {
                val size = minOf(CHUNK_BYTES, length - offset)
                add(Chunk(index = this.size, offset = offset, size = size))
                offset += size
            }
        }
    }

    /**
     * Completion is associated with the original queued request, never with
     * the order in which requestWait() happens to return it.
     */
    class Tracker(private val chunks: List<Chunk>) {
        private val completed = BooleanArray(chunks.size)
        var acceptedBytes: Int = 0
            private set
        var acceptedChunks: Int = 0
            private set

        fun accept(index: Int, actualBytes: Int): Boolean {
            val chunk = chunks.getOrNull(index) ?: return false
            if (completed[index] || actualBytes != chunk.size) return false
            completed[index] = true
            acceptedChunks++
            acceptedBytes += actualBytes
            return true
        }

        val complete: Boolean
            get() = acceptedChunks == chunks.size
    }
}
