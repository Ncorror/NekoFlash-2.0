package ru.forum.adbfastboottool

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.CRC32

/** Bounded memory, not a file-size cap. Callers own publication and rollback. */
internal object LocalFileCopy {
    class NotEnoughSpace(val required: Long, val available: Long) :
        IOException("Not enough free space: need $required bytes, available $available")
    fun requireSpace(directory: File, bytes: Long, availableSpace: (File) -> Long) {
        require(bytes >= 0)
        val available = availableSpace(directory).coerceAtLeast(0)
        if (available < bytes) throw NotEnoughSpace(bytes, available)
    }
    fun copy(input: InputStream, output: OutputStream, directory: File,
             expectedSize: Long?, checkCancelled: () -> Unit,
             onProgress: (Long) -> Unit = {}, availableSpace: (File) -> Long,
             expectedCrc: Long? = null): Long {
        require(expectedSize == null || expectedSize >= 0)
        checkCancelled()
        expectedSize?.let { requireSpace(directory, it, availableSpace) }
        val buffer = ByteArray(1024 * 1024)
        val crc = expectedCrc?.let { CRC32() }
        var copied = 0L
        while (true) {
            checkCancelled()
            var count = input.read(buffer)
            if (count == 0) {
                // A provider's zero-byte read is not EOF.
                val byte = input.read()
                if (byte >= 0) { buffer[0] = byte.toByte(); count = 1 } else count = -1
            }
            checkCancelled()
            if (count < 0) break
            val next = try { Math.addExact(copied, count.toLong()) }
                catch (error: ArithmeticException) { throw IOException("File size overflows a 64-bit counter", error) }
            if (expectedSize != null && next > expectedSize) throw IOException("Source is larger than its declared size")
            requireSpace(directory, maxOf(count.toLong(), expectedSize?.minus(copied) ?: 0L), availableSpace)
            output.write(buffer, 0, count)
            crc?.update(buffer, 0, count)
            copied = next
            onProgress(copied)
        }
        checkCancelled()
        if (expectedSize != null && copied != expectedSize) throw IOException("Source size changed: expected $expectedSize, copied $copied")
        if (crc != null && crc.value != expectedCrc) throw IOException("Archive entry CRC mismatch")
        return copied
    }
}
