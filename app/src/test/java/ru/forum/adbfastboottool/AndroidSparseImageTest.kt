package ru.forum.adbfastboottool

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AndroidSparseImageTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun file(name: String) = File(temporary.root, name)
    private fun sparse(): File = file("image.sparse").also { f ->
        AndroidSparseImage.Writer(f, 4096, 3).use {
            it.fill(0x12345678, 1); it.raw(ByteArray(4096) { n -> (n % 251).toByte() }); it.skip(1); it.finish()
        }
    }

    @Test fun independentlyDecodedChunksPreserveFillRawAndDontCare() {
        val source = sparse(); val out = file("expanded.raw")
        HostImageFixtures.expand(source, out, poison = true)
        val bytes = out.readBytes()
        assertArrayEquals(byteArrayOf(0x78, 0x56, 0x34, 0x12), bytes.copyOfRange(0, 4))
        assertArrayEquals(ByteArray(4096) { (it % 251).toByte() }, bytes.copyOfRange(4096, 8192))
        assertTrue(bytes.drop(8192).all { it == 0x5a.toByte() })
        assertEquals(12288L, AndroidSparseImage.inspect(source).expandedBytes)
    }
    @Test fun rawConversionDoesNotLeaveOldBytesInsideFilesystemHoles() {
        val source = file("source.raw")
        val bytes = ByteArray(16384)
        for (i in 4096 until 8192) bytes[i] = (i % 251).toByte()
        source.writeBytes(bytes)
        val encoded = file("converted.sparse")
        AndroidSparseImage.fromRaw(source, encoded, { _, offset -> when {
            offset < 4096 -> 4096L
            offset < 8192 -> offset
            else -> null
        } })
        val out = file("converted.raw")
        HostImageFixtures.expand(encoded, out, poison = true)
        assertArrayEquals(bytes, out.readBytes())
    }
    @Test fun denseRoundTripPreservesChangingRepeatedPatterns() {
        val bytes = ByteArray(4096 * 5) { n -> when (n / 4096) { 0 -> 0; 1, 2 -> (n % 4).toByte(); else -> (n % 251).toByte() } }
        val raw = file("dense.raw").also { it.writeBytes(bytes) }; val encoded = file("dense.sparse")
        AndroidSparseImage.fromRaw(raw, encoded)
        val out = file("dense-out.raw"); HostImageFixtures.expand(encoded, out)
        assertArrayEquals(bytes, out.readBytes())
    }
    @Test fun expandedImagesLargerThanFourGiBAreNotTruncated() {
        val f = file("large.sparse")
        AndroidSparseImage.Writer(f, 4096, 0x100000L).use { it.skip(0x100000L); it.finish() }
        assertEquals(0x100000000L, AndroidSparseImage.inspect(f).expandedBytes)
        assertEquals(40L, f.length())
    }
    @Test fun truncatedPayloadAndTrailingDataAreRejected() {
        val f = sparse(); RandomAccessFile(f, "rw").use { it.setLength(it.length() - 1) }
        assertThrows(IllegalArgumentException::class.java) { AndroidSparseImage.inspect(f) }
        val g = sparse(); g.appendBytes(byteArrayOf(0))
        assertThrows(IllegalArgumentException::class.java) { AndroidSparseImage.inspect(g) }
    }
    @Test fun chunkBlockOverflowIsRejected() {
        val f = sparse(); val b = f.readBytes()
        ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).putInt(32, 4); f.writeBytes(b)
        assertThrows(IllegalArgumentException::class.java) { AndroidSparseImage.inspect(f) }
    }
    @Test fun incompleteWriterCannotFinish() {
        AndroidSparseImage.Writer(file("short.sparse"), 4096, 2).use { w ->
            w.skip(1); assertThrows(IllegalStateException::class.java) { w.finish() }
        }
    }
    @Test fun conversionCancellationStopsBeforeImageIsComplete() {
        val raw = file("cancel.raw").also { it.writeBytes(ByteArray(8192)) }
        assertThrows(InterruptedException::class.java) {
            AndroidSparseImage.fromRaw(raw, file("cancel.sparse"), checkCancelled = { throw InterruptedException() })
        }
    }
}
