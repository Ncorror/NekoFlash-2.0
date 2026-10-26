package ru.forum.adbfastboottool

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/** Streaming resparse: constant-size buffers, no expanded image or in-memory chunk table. */
internal class FastbootFlashImage private constructor(
    private val source: FirmwareSource.Opened,
    val limitBytes: Long,
    val blockSize: Int,
    val blocks: Long,
    private val sparseHeader: Header?,
    private val checkCancelled: () -> Unit
) {
    private data class Header(val bytes: Int, val chunkBytes: Int, val chunks: Long, val checksum: Long)
    private data class Run(val kind: Int, val block: Long, val blocks: Long,
        val offset: Long = 0, val bytes: Long = 0, val fill: Int = 0)
    private data class Stats(val parts: Long, val wireBytes: Long, val largest: Long)
    private interface Sink {
        fun begin(index: Long) {}
        fun chunk(run: Run) {}
        fun end(index: Long, bytes: Long, chunks: Long) {}
    }
    private class Stop : RuntimeException()
    val expandedBytes: Long get() = Math.multiplyExact(blocks, blockSize.toLong())
    val needsResparse: Boolean get() = source.sizeBytes > limitBytes
    private var stats = Stats(1, source.sizeBytes, source.sizeBytes)
    val partCount: Long get() = stats.parts
    val downloadBytes: Long get() = stats.wireBytes
    val largestPartBytes: Long get() = stats.largest

    /** Visits metadata only unless CRC validation is requested. DONT_CARE retains its original meaning. */
    private fun scan(crcEnabled: Boolean = false, visit: (Run) -> Unit = {}): Boolean {
        if (sparseHeader == null) {
            visit(Run(RAW, 0, blocks, bytes = source.sizeBytes))
            return false
        }
        val crc = if (crcEnabled) CRC32() else null
        var hasCrc = false
        var offset = sparseHeader.bytes.toLong()
        var block = 0L
        var index = 0L
        while (index++ < sparseHeader.chunks) {
            checkCancelled()
            require(offset <= source.sizeBytes - sparseHeader.chunkBytes) { "Truncated sparse chunk header" }
            val header = read(offset, 12)
            val kind = header.getShort(0).toInt() and 0xffff
            val count = header.u32(4)
            val size = header.u32(8)
            require(count <= blocks - block) { "Sparse block count overflow" }
            val bytes = when (kind) {
                RAW -> Math.multiplyExact(count, blockSize.toLong())
                FILL, CRC -> 4L
                SKIP -> 0L
                else -> error("Unknown sparse chunk: $kind")
            }
            require(size == sparseHeader.chunkBytes + bytes && size <= source.sizeBytes - offset) {
                "Invalid sparse chunk length"
            }
            val data = offset + sparseHeader.chunkBytes
            if (kind == CRC) {
                require(count == 0L) { "CRC chunk must not advance the image" }
                hasCrc = true
                if (crc != null) require(read(data, 4).u32(0) == crc.value) { "Sparse CRC32 mismatch" }
            } else {
                val run = Run(kind, block, count, data, bytes,
                    if (kind == FILL) read(data, 4).getInt(0) else 0)
                if (crc != null) checksum(crc, run)
                visit(run)
                block += count
            }
            offset += size
        }
        require(block == blocks && offset == source.sizeBytes) { "Incomplete sparse image or trailing bytes" }
        if (crc != null && sparseHeader.checksum != 0L) {
            require(crc.value == sparseHeader.checksum) { "Sparse header checksum mismatch" }
        }
        return hasCrc
    }

    private fun checksum(crc: CRC32, run: Run) {
        var left = run.blocks * blockSize
        var offset = run.offset
        val buffer = ByteArray(minOf(COPY_BYTES.toLong(), left).toInt())
        if (run.kind == FILL) for (i in buffer.indices) buffer[i] = (run.fill ushr (i % 4 * 8)).toByte()
        while (left > 0) {
            checkCancelled()
            val count = minOf(left, buffer.size.toLong()).toInt()
            if (run.kind == RAW) {
                val data = if (count == buffer.size) buffer else ByteArray(count)
                source.readFullyAt(offset, data); crc.update(data)
            } else crc.update(buffer, 0, count) // SKIP contributes zeroes to the sparse checksum.
            offset += count; left -= count
        }
    }

    private fun read(offset: Long, bytes: Int): ByteBuffer = ByteBuffer.wrap(ByteArray(bytes).also {
        source.readFullyAt(offset, it)
    }).order(ByteOrder.LITTLE_ENDIAN)

    /** Same packing pass is used for preflight and writing; it stores no per-part/chunk lists. */
    private fun pack(sink: Sink): Stats {
        var active = false
        var hasData = false
        var used = 0L
        var cursor = 0L
        var chunks = 0L
        var parts = 0L
        var wireBytes = 0L
        var largest = 0L
        fun emit(run: Run) {
            if (run.blocks == 0L) return
            sink.chunk(run)
            used += 12 + when (run.kind) { RAW -> run.blocks * blockSize; FILL -> 4L; else -> 0L }
            cursor += run.blocks; chunks++
            require(chunks <= U32) { "Too many sparse chunks in one download" }
        }
        fun begin(start: Long) {
            active = true; hasData = false; used = 28; cursor = 0; chunks = 0; parts++
            sink.begin(parts)
            if (start > 0) emit(Run(SKIP, 0, start))
        }
        fun end() {
            if (cursor < blocks) emit(Run(SKIP, cursor, blocks - cursor))
            require(used <= limitBytes) { "Download limit $limitBytes cannot hold sparse framing" }
            wireBytes = Math.addExact(wireBytes, used); largest = maxOf(largest, used)
            sink.end(parts, used, chunks)
            active = false
        }
        scan { run ->
            if (run.kind != SKIP) {
                var block = run.block
                var left = run.blocks
                var offset = run.offset
                var availableData = run.bytes
                while (left > 0) {
                    checkCancelled()
                    if (!active) begin(block)
                    val gap = block - cursor
                    val available = limitBytes - used - (if (gap > 0) 12 else 0)
                    val count = if (run.kind == FILL) {
                        if (available >= 16 + (if (block + left < blocks) 12 else 0)) left else 0L
                    } else {
                        // A final RAW chunk needs no trailing DONT_CARE header.
                        val tail = if (block + left == blocks && 12 + left * blockSize <= available) 0 else 12
                        minOf(left, maxOf(0L, (available - 12 - tail) / blockSize))
                    }
                    if (count == 0L) {
                        require(hasData) { "Download limit $limitBytes cannot fit one $blockSize-byte sparse block and headers" }
                        end()
                        continue
                    }
                    if (gap > 0) emit(Run(SKIP, cursor, gap))
                    val data = if (run.kind == RAW) minOf(availableData, count * blockSize) else 4L
                    emit(Run(run.kind, block, count, offset, data, run.fill))
                    hasData = true
                    block += count; left -= count; offset += data; availableData -= data
                    if (left > 0) end()
                }
            }
        }
        if (!active && parts == 0L) begin(0) // An all-DONT_CARE image is still a valid no-data sparse packet.
        if (active) end()
        return Stats(parts, wireBytes, largest)
    }

    /** Materialize at most one bounded packet. A false callback stops before creating/sending another. */
    fun forEachPart(directory: File, send: (File, Long, Long) -> Boolean): Boolean {
        check(needsResparse)
        require(directory.isDirectory)
        var file: File? = null
        var output: RandomAccessFile? = null
        fun cleanup() {
            try { output?.close() } finally {
                output = null
                file?.let { check(!it.exists() || it.delete()) { "Could not remove sparse packet ${it.name}" } }
                file = null
            }
        }
        try {
            val written = pack(object : Sink {
                override fun begin(index: Long) {
                    checkCancelled()
                    file = File.createTempFile("sparse-part-", ".img", directory)
                    output = RandomAccessFile(file, "rw")
                    output!!.write(ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN)
                        .putInt(MAGIC.toInt()).putShort(1).putShort(0).putShort(28).putShort(12)
                        .putInt(blockSize).putInt(blocks.toInt()).putInt(0).putInt(0).array())
                }
                override fun chunk(run: Run) {
                    checkCancelled()
                    val out = checkNotNull(output)
                    val bytes = when (run.kind) { RAW -> run.blocks * blockSize; FILL -> 4L; else -> 0L }
                    out.write(ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
                        .putShort(run.kind.toShort()).putShort(0).putInt(run.blocks.toInt()).putInt((12 + bytes).toInt()).array())
                    if (run.kind == FILL) out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(run.fill).array())
                    if (run.kind == RAW) {
                        var left = run.bytes
                        var offset = run.offset
                        val buffer = ByteArray(minOf(COPY_BYTES.toLong(), left).toInt())
                        while (left > 0) {
                            checkCancelled()
                            val data = if (left >= buffer.size) buffer else ByteArray(left.toInt())
                            source.readFullyAt(offset, data); out.write(data)
                            offset += data.size; left -= data.size
                        }
                        var padding = bytes - run.bytes
                        val zeroes = ByteArray(minOf(COPY_BYTES.toLong(), padding).toInt())
                        while (padding > 0) {
                            checkCancelled()
                            val count = minOf(padding, zeroes.size.toLong()).toInt()
                            out.write(zeroes, 0, count); padding -= count
                        }
                    }
                }
                override fun end(index: Long, bytes: Long, chunks: Long) {
                    val out = checkNotNull(output)
                    require(out.length() == bytes)
                    out.seek(20); out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(chunks.toInt()).array())
                    out.fd.sync(); out.close(); output = null
                    checkCancelled()
                    try { if (!send(checkNotNull(file), index, partCount)) throw Stop() } finally { cleanup() }
                }
            })
            check(written == stats) { "Image structure changed after preflight" }
            return true
        } catch (_: Stop) {
            return false
        } finally { cleanup() }
    }

    companion object {
        private const val MAGIC = 0xed26ff3aL
        private const val RAW = 0xcac1
        private const val FILL = 0xcac2
        private const val SKIP = 0xcac3
        private const val CRC = 0xcac4
        private const val U32 = 0xffff_ffffL
        private const val COPY_BYTES = 1024 * 1024
        private fun ByteBuffer.u32(offset: Int) = getInt(offset).toLong() and U32

        /** Zero follows AOSP's automatic device limit; an explicit positive limit cannot exceed the device/wire limit. */
        fun prepare(source: FirmwareSource.Opened, deviceMax: Long?, requestedMax: Long = 0,
            checkCancelled: () -> Unit = {}): FastbootFlashImage {
            checkCancelled()
            require(source.sizeBytes > 0 && requestedMax >= 0) { "Empty image or invalid sparse limit" }
            val limit = minOf(U32, deviceMax?.takeIf { it > 0 } ?: U32, requestedMax.takeIf { it > 0 } ?: U32)
            var blockSize = 4096
            var blocks = (source.sizeBytes - 1) / blockSize + 1
            var header: Header? = null
            val magic = if (source.sizeBytes >= 4) ByteArray(4).also { source.readFullyAt(0, it) } else ByteArray(4)
            if (ByteBuffer.wrap(magic).order(ByteOrder.LITTLE_ENDIAN).u32(0) == MAGIC) {
                require(source.sizeBytes >= 28) { "Truncated sparse header" }
                val b = ByteBuffer.wrap(ByteArray(28).also { source.readFullyAt(0, it) }).order(ByteOrder.LITTLE_ENDIAN)
                require(b.getShort(4).toInt() == 1) { "Unsupported sparse major version" }
                val size = b.u32(12)
                require(size in 4..Int.MAX_VALUE.toLong() && size % 4 == 0L) { "Invalid sparse block size" }
                blockSize = size.toInt(); blocks = b.u32(16)
                header = Header(b.getShort(8).toInt() and 0xffff, b.getShort(10).toInt() and 0xffff, b.u32(20), b.u32(24))
                require(header.bytes >= 28 && header.chunkBytes >= 12 && header.bytes <= source.sizeBytes)
                require(header.chunks <= (source.sizeBytes - header.bytes) / header.chunkBytes)
            }
            require(blocks in 1..U32) { "Image geometry exceeds Android sparse block count" }
            val image = FastbootFlashImage(source, limit, blockSize, blocks, header, checkCancelled)
            val hasCrc = image.scan()
            if (hasCrc || (header?.checksum ?: 0L) != 0L) image.scan(crcEnabled = true)
            if (image.needsResparse) image.stats = image.pack(object : Sink {})
            return image
        }

        fun parseLimit(value: String): Long {
            val match = Regex("([0-9]+)([KMGkmg]?)").matchEntire(value)
                ?: throw IllegalArgumentException("Invalid sparse size: $value; use bytes or K/M/G")
            val count = requireNotNull(match.groupValues[1].toLongOrNull()) { "Sparse size overflows: $value" }
            val multiplier = when (match.groupValues[2].uppercase()) { "K" -> 1024L; "M" -> 1024L * 1024; "G" -> 1024L * 1024 * 1024; else -> 1L }
            require(count <= Long.MAX_VALUE / multiplier) { "Sparse size overflows: $value" }
            return count * multiplier
        }
    }
}
