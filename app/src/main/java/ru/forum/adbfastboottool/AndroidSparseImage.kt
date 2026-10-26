package ru.forum.adbfastboottool

import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Streaming Android sparse v1 encoding. No USB, filesystem creation or whole-image allocation. */
internal object AndroidSparseImage {
    private const val MAGIC = 0xed26ff3aL
    private const val RAW = 0xcac1
    private const val FILL = 0xcac2
    private const val SKIP = 0xcac3
    private const val CRC = 0xcac4
    private const val U32 = 0xffff_ffffL
    data class Info(val blockSize: Int, val blocks: Long, val chunks: Long) {
        val expandedBytes: Long get() = Math.multiplyExact(blockSize.toLong(), blocks)
    }

    class Writer(file: File, val blockSize: Int, private val totalBlocks: Long) : Closeable {
        private val output = run {
            require(blockSize >= 512 && blockSize and (blockSize - 1) == 0 && totalBlocks in 1..U32)
            RandomAccessFile(file, "rw")
        }
        private var blocks = 0L
        private var chunks = 0L
        private var finished = false
        init {
            require(blockSize >= 512 && blockSize and (blockSize - 1) == 0 && totalBlocks in 1..U32)
            output.setLength(0)
            val header = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(MAGIC.toInt()).putShort(1).putShort(0).putShort(28).putShort(12)
                .putInt(blockSize).putInt(totalBlocks.toInt()).putInt(0).putInt(0).array()
            output.write(header)
        }
        private fun chunk(type: Int, count: Long, payload: Long) {
            check(!finished)
            require(count >= 0 && count <= totalBlocks - blocks && payload <= U32 - 12)
            require(chunks < U32)
            output.write(ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
                .putShort(type.toShort()).putShort(0).putInt(count.toInt()).putInt((12 + payload).toInt()).array())
            blocks += count
            chunks++
        }
        fun skip(count: Long) { if (count > 0) chunk(SKIP, count, 0) }
        fun fill(value: Int, count: Long) {
            if (count == 0L) return
            chunk(FILL, count, 4)
            output.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array())
        }
        fun raw(bytes: ByteArray, length: Int = bytes.size) {
            require(length > 0 && length <= bytes.size && length % blockSize == 0)
            chunk(RAW, (length / blockSize).toLong(), length.toLong())
            output.write(bytes, 0, length)
        }
        fun raw(input: InputStream, byteCount: Long, checkCancelled: () -> Unit) {
            require(byteCount % blockSize == 0L)
            val buffer = ByteArray(maxOf(blockSize, 1024 * 1024 / blockSize * blockSize))
            var remaining = byteCount
            while (remaining > 0) {
                checkCancelled()
                val count = minOf(remaining, buffer.size.toLong()).toInt()
                var offset = 0
                while (offset < count) {
                    val n = input.read(buffer, offset, count - offset)
                    check(n > 0) { "Truncated sparse RAW source" }
                    offset += n
                }
                raw(buffer, count)
                remaining -= count
            }
        }
        fun finish() {
            check(blocks == totalBlocks) { "Sparse image covers $blocks/$totalBlocks blocks" }
            output.seek(20)
            output.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(chunks.toInt()).array())
            output.fd.sync()
            finished = true
        }
        override fun close() = output.close()
    }

    fun inspect(file: File, checkCancelled: () -> Unit = {}): Info = RandomAccessFile(file, "r").use { input ->
        fun u32(): Long = Integer.reverseBytes(input.readInt()).toLong() and U32
        fun u16(): Int = java.lang.Short.reverseBytes(input.readShort()).toInt() and 0xffff
        require(input.length() >= 28 && u32() == MAGIC && u16() == 1) { "Invalid Android sparse header" }
        u16()
        val header = u16(); val chunkHeader = u16(); val blockSize = u32()
        val total = u32(); val count = u32(); u32()
        require(header >= 28 && chunkHeader >= 12 && header <= input.length())
        require(blockSize in 512..Int.MAX_VALUE.toLong() && blockSize and (blockSize - 1) == 0L && total > 0)
        require(count <= (input.length() - header) / chunkHeader)
        input.seek(header.toLong())
        var blocks = 0L
        var chunkIndex = 0L
        while (chunkIndex < count) {
            checkCancelled()
            val start = input.filePointer
            require(input.length() - start >= chunkHeader)
            val type = u16(); u16(); val n = u32(); val bytes = u32()
            val payload = when (type) {
                RAW -> Math.multiplyExact(n, blockSize)
                FILL -> 4L
                SKIP -> 0L
                CRC -> 4L.also { require(n == 0L) }
                else -> error("Unknown sparse chunk: $type")
            }
            require(bytes == chunkHeader + payload && bytes <= input.length() - start) { "Invalid sparse chunk length" }
            require(n <= total - blocks) { "Sparse block count overflow" }
            blocks += n
            input.seek(start + bytes)
            chunkIndex++
        }
        require(blocks == total && input.filePointer == input.length()) { "Sparse image is incomplete or has trailing data" }
        Info(blockSize.toInt(), total, count)
    }

    /** Holes mean zero bytes, not DONT_CARE: old on-device inode tables must not survive formatting. */
    fun fromRaw(source: File, target: File, nextData: (RandomAccessFile, Long) -> Long? = { _, offset -> offset },
        checkCancelled: () -> Unit = {}) {
        val blockSize = 4096
        RandomAccessFile(source, "r").use { input ->
            val length = input.length()
            require(length > 0 && length % blockSize == 0L)
            Writer(target, blockSize, length / blockSize).use { output ->
                val buffer = ByteArray(blockSize)
                var offset = 0L
                var fillValue = 0
                var fillCount = 0L
                fun flushFill() { if (fillCount > 0) { output.fill(fillValue, fillCount); fillCount = 0 } }
                while (offset < length) {
                    checkCancelled()
                    val data = nextData(input, offset)?.coerceAtMost(length) ?: length
                    require(data >= offset)
                    val aligned = data / blockSize * blockSize
                    if (aligned > offset) {
                        flushFill(); output.fill(0, (aligned - offset) / blockSize); offset = aligned
                        if (offset == length) break
                    }
                    input.seek(offset); input.readFully(buffer)
                    val value = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).int
                    var same = true
                    for (i in 4 until blockSize) if (buffer[i] != buffer[i % 4]) { same = false; break }
                    if (same) {
                        if (fillCount > 0 && fillValue != value) flushFill()
                        fillValue = value; fillCount++
                    } else { flushFill(); output.raw(buffer) }
                    offset += blockSize
                }
                flushFill(); output.finish()
            }
            check(input.length() == length) { "Filesystem image changed while encoding" }
        }
    }
}
