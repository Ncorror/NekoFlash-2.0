package ru.forum.adbfastboottool

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import org.junit.Assert.*

/** Independent test decoder: does not call the production inspector or sparse writer. */
internal object HostImageFixtures {
    fun expand(source: File, target: File, poison: Boolean = false) {
        RandomAccessFile(source, "r").use { input ->
            fun int() = Integer.reverseBytes(input.readInt()).toLong() and 0xffffffffL
            fun short() = java.lang.Short.reverseBytes(input.readShort()).toInt() and 0xffff
            assertEquals(0xed26ff3aL, int()); assertEquals(1, short()); short()
            val header = short(); val chunkHeader = short(); val block = int(); val blocks = int(); val chunks = int(); int()
            RandomAccessFile(target, "rw").use { out ->
                out.setLength(block * blocks)
                if (poison) {
                    require(block * blocks < 1024 * 1024)
                    out.write(ByteArray((block * blocks).toInt()) { 0x5a })
                }
                out.seek(0); input.seek(header.toLong())
                val buffer = ByteArray(1024 * 1024)
                repeat(chunks.toInt()) {
                    val start = input.filePointer
                    val type = short(); short(); val size = int() * block; val bytes = int()
                    input.seek(start + chunkHeader)
                    when (type) {
                        0xcac1 -> {
                            var left = size
                            while (left > 0) {
                                val n = minOf(left, buffer.size.toLong()).toInt()
                                input.readFully(buffer, 0, n); out.write(buffer, 0, n); left -= n
                            }
                        }
                        0xcac2 -> {
                            val pattern = ByteArray(4); input.readFully(pattern)
                            for (i in buffer.indices) buffer[i] = pattern[i % 4]
                            var left = size
                            while (left > 0) {
                                val n = minOf(left, buffer.size.toLong()).toInt()
                                out.write(buffer, 0, n); left -= n
                            }
                        }
                        0xcac3 -> out.seek(out.filePointer + size)
                        0xcac4 -> assertEquals(0L, size)
                        else -> error("Unknown test-decoder chunk $type")
                    }
                    input.seek(start + bytes)
                }
                assertEquals(block * blocks, out.filePointer)
                assertEquals(input.length(), input.filePointer)
            }
        }
    }

    fun emptySuper(file: File, minor: Int = 2, names: List<String> = listOf("super"),
        suffixed: Boolean = false, extents: Boolean = false): ByteArray {
        val headerSize = if (minor < 2) 128 else 256
        val geometry = ByteArray(4096)
        val g = ByteBuffer.wrap(geometry).order(ByteOrder.LITTLE_ENDIAN)
        g.putInt(0, 0x616c4467); g.putInt(4, 52); g.putInt(40, 4096); g.putInt(44, 2); g.putInt(48, 4096)
        rehash(geometry, 0, 52, 8)
        val tables = ByteArray(52 + (if (extents) 24 else 0) + 48 + names.size * 64)
        val t = ByteBuffer.wrap(tables).order(ByteOrder.LITTLE_ENDIAN)
        "system".toByteArray().copyInto(tables)
        if (extents) { t.putInt(44, 1); t.putLong(52, 8) }
        val group = 52 + if (extents) 24 else 0
        "default".toByteArray().copyInto(tables, group)
        names.forEachIndexed { index, name ->
            val start = group + 48 + index * 64
            t.putLong(start, if (index == 0) 2048 else 0)
            t.putInt(start + 8, 1024 * 1024)
            t.putLong(start + 16, 4 * 1024 * 1024)
            name.toByteArray().copyInto(tables, start + 24)
            t.putInt(start + 60, if (suffixed) 1 else 0)
        }
        val header = ByteArray(headerSize)
        val h = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        h.putInt(0, 0x414c5030); h.putShort(4, 10); h.putShort(6, minor.toShort()); h.putInt(8, headerSize)
        h.putInt(44, tables.size)
        h.putInt(80, 0); h.putInt(84, 1); h.putInt(88, 52)
        h.putInt(92, 52); h.putInt(96, if (extents) 1 else 0); h.putInt(100, 24)
        h.putInt(104, group); h.putInt(108, 1); h.putInt(112, 48)
        h.putInt(116, group + 48); h.putInt(120, names.size); h.putInt(124, 64)
        MessageDigest.getInstance("SHA-256").digest(tables).copyInto(header, 48)
        rehash(header, 0, headerSize, 12)
        return (geometry + header + tables).also { file.writeBytes(it) }
    }

    fun rehash(bytes: ByteArray, offset: Int, length: Int, checksum: Int) {
        bytes.fill(0, offset + checksum, offset + checksum + 32)
        MessageDigest.getInstance("SHA-256").digest(bytes.copyOfRange(offset, offset + length)).copyInto(bytes, offset + checksum)
    }
}
