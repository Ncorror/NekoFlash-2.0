package ru.forum.adbfastboottool

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import java.util.zip.CRC32
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FastbootFlashImageTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun input(bytes: ByteArray) = temporary.newFile().also { it.writeBytes(bytes) }
    private fun source(file: File) = FirmwareSource.WorkspaceFile(file).open()
    private fun read(bytes: ByteArray, at: Int) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(at).toLong() and 0xffffffffL
    private fun put(bytes: ByteArray, at: Int, value: Long) { ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(at, value.toInt()) }
    private fun sparse(blocks: Long, body: AndroidSparseImage.Writer.() -> Unit): File = temporary.newFile().also { file ->
        AndroidSparseImage.Writer(file, 4096, blocks).use { it.body(); it.finish() }
    }
    private fun reconstruct(file: File, limit: Long, expected: ByteArray, requested: Long = 0): Long = source(file).use { opened ->
        val plan = FastbootFlashImage.prepare(opened, limit, requested)
        assertTrue(plan.needsResparse)
        val work = temporary.newFolder()
        val target = temporary.newFile().also { it.writeBytes(ByteArray(expected.size) { 0x5a }) }
        var count = 0L
        var wire = 0L
        assertTrue(plan.forEachPart(work) { part, index, total ->
            assertEquals(++count, index); assertEquals(plan.partCount, total)
            assertEquals(1, work.listFiles()!!.size)
            assertTrue(part.length() <= minOf(limit, requested.takeIf { it > 0 } ?: limit))
            assertEquals(expected.size.toLong(), AndroidSparseImage.inspect(part).expandedBytes)
            HostImageFixtures.expand(part, target)
            wire += part.length()
            true
        })
        assertEquals(plan.partCount, count); assertEquals(plan.downloadBytes, wire)
        assertArrayEquals(expected, target.readBytes())
        assertTrue(work.listFiles()!!.isEmpty())
        count
    }

    @Test fun rawDataSurvivesMultipleDownloadsWithZeroPaddedFinalBlock() {
        val raw = ByteArray(4096 * 5 + 73).also { Random(1).nextBytes(it) }
        val expected = raw.copyOf(4096 * 6)
        assertTrue(reconstruct(input(raw), 2 * 4096L, expected) > 1)
    }
    @Test fun existingSparseFillAndSkipPreservePriorPartitionBytes() {
        val data = ByteArray(4096 * 4).also { Random(2).nextBytes(it) }
        val image = sparse(10) { skip(1); raw(data); fill(0x12345678, 2); skip(2); fill(0, 1) }
        val expectedFile = temporary.newFile().also { it.writeBytes(ByteArray(4096 * 10) { 0x5a }) }
        HostImageFixtures.expand(image, expectedFile)
        assertTrue(reconstruct(image, 8300, expectedFile.readBytes()) > 1)
    }
    @Test fun exactDeviceLimitKeepsTheOriginalSingleDownload() = source(input(ByteArray(8192))).use {
        val image = FastbootFlashImage.prepare(it, 8192)
        assertFalse(image.needsResparse); assertEquals(1, image.partCount); assertEquals(8192, image.downloadBytes)
    }
    @Test fun userLimitCannotOverrideSmallerDeviceLimit() = source(input(ByteArray(20_000))).use {
        assertEquals(8192, FastbootFlashImage.prepare(it, 8192, 65536).limitBytes)
        assertEquals(8192, FastbootFlashImage.prepare(it, 65536, 8192).limitBytes)
    }
    @Test fun zeroAndUnknownLimitUseAutomaticWireCapacity() = source(input(ByteArray(8192))).use {
        assertEquals(0xffffffffL, FastbootFlashImage.prepare(it, null, 0).limitBytes)
        assertEquals(0xffffffffL, FastbootFlashImage.prepare(it, 0, Long.MAX_VALUE).limitBytes)
    }
    @Test fun rawOverFourGiBPlansWithoutReadingOrAllocatingItsContents() {
        var readBytes = 0
        val source = object : FirmwareSource.Opened {
            override val sizeBytes = (1L shl 32) + 19
            override fun readFullyAt(offset: Long, target: ByteArray) {
                assertEquals(0L, offset); assertEquals(4, target.size); readBytes += target.size
                target.fill(0x35)
            }
            override fun close() {}
        }
        val plan = FastbootFlashImage.prepare(source, null)
        assertEquals(2, plan.partCount)
        assertTrue(plan.largestPartBytes <= 0xffffffffL)
        assertEquals((1L shl 32) + 4096, plan.expandedBytes)
        assertEquals(4, readBytes)
    }
    @Test fun sparseExpandedBeyondFourGiBKeepsLargeOffsetsWithoutExpansion() {
        val image = sparse(0x100001L) { fill(0x11223344, 0x100000L); raw(ByteArray(4096) { 7 }) }
        source(image).use { opened ->
            val plan = FastbootFlashImage.prepare(opened, 4148)
            assertEquals(2, plan.partCount); assertEquals((1L shl 32) + 4096, plan.expandedBytes)
            plan.forEachPart(temporary.newFolder()) { part, index, _ ->
                assertEquals(0x100001L, AndroidSparseImage.inspect(part).blocks)
                val bytes = part.readBytes()
                if (index == 2L) {
                    assertEquals(0xcac3, ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getShort(28).toInt() and 0xffff)
                    assertEquals(0x100000L, read(bytes, 32))
                    assertTrue(bytes.takeLast(4096).all { it == 7.toByte() })
                }
                true
            }
        }
    }
    @Test fun tooSmallLimitFailsDuringPreparation() = source(input(ByteArray(5000))).use {
        assertThrows(IllegalArgumentException::class.java) { FastbootFlashImage.prepare(it, 4096) }
    }
    @Test fun sourceMagicWithInvalidSparseGeometryIsNeverTreatedAsRaw() {
        val bytes = ByteArray(28); put(bytes, 0, 0xed26ff3aL)
        source(input(bytes)).use { assertThrows(IllegalArgumentException::class.java) { FastbootFlashImage.prepare(it, 8192) } }
    }
    @Test fun truncatedRawChunkAndTrailingJunkAreRejectedEvenBelowDownloadLimit() {
        val good = sparse(1) { raw(ByteArray(4096)) }.readBytes()
        for (bytes in listOf(good.copyOf(good.size - 1), good + byteArrayOf(1))) source(input(bytes)).use {
            assertThrows(IllegalArgumentException::class.java) { FastbootFlashImage.prepare(it, 65536) }
        }
    }
    private fun withCrc(image: File, expanded: ByteArray): File {
        val bytes = image.readBytes()
        val crc = CRC32().also { it.update(expanded) }.value
        put(bytes, 20, read(bytes, 20) + 1)
        put(bytes, 24, crc)
        val chunk = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(0xcac4.toShort()).putShort(0).putInt(0).putInt(16).putInt(crc.toInt()).array()
        return input(bytes + chunk)
    }
    @Test fun crcChunksAndHeaderAreVerifiedBeforeResparseAndNotCopiedToParts() {
        val raw = ByteArray(8192).also { Random(3).nextBytes(it) }
        val image = withCrc(sparse(2) { raw(raw) }, raw)
        assertTrue(reconstruct(image, 4160, raw) > 1)
        val bytes = image.readBytes(); bytes[50] = (bytes[50].toInt() xor 1).toByte()
        source(input(bytes)).use { assertThrows(IllegalArgumentException::class.java) { FastbootFlashImage.prepare(it, 4160) } }
    }
    @Test fun crcCountsFillAndDontCareAsSpecified() {
        val image = sparse(3) { fill(0x12121212, 1); skip(1); fill(0x34343434, 1) }
        val expanded = ByteArray(4096) { 0x12 } + ByteArray(4096) + ByteArray(4096) { 0x34 }
        source(withCrc(image, expanded)).use { assertFalse(FastbootFlashImage.prepare(it, 4096).needsResparse) }
    }
    @Test fun invalidHeaderChecksumIsRejectedWithoutCrcChunk() {
        val bytes = sparse(1) { fill(7, 1) }.readBytes(); put(bytes, 24, 123)
        source(input(bytes)).use { assertThrows(IllegalArgumentException::class.java) { FastbootFlashImage.prepare(it, 8192) } }
    }
    @Test fun crcChunkCannotAdvanceBlocks() {
        val bytes = withCrc(sparse(1) { fill(0, 1) }, ByteArray(4096)).readBytes()
        put(bytes, bytes.size - 12, 1)
        source(input(bytes)).use { assertThrows(IllegalArgumentException::class.java) { FastbootFlashImage.prepare(it, 8192) } }
    }
    @Test fun extendedHeadersCanBeReserializedWithoutCopyingTheirPadding() {
        val original = sparse(1) { fill(0x21212121, 1) }.readBytes()
        val header = original.copyOfRange(0, 28).copyOf(80)
        ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).putShort(8, 80)
        val extended = input(header + original.copyOfRange(28, original.size))
        assertEquals(1, reconstruct(extended, 44, ByteArray(4096) { 0x21 }))
    }
    @Test fun allSkipImageSurvivesReserializationWithoutZeroingExistingData() {
        val original = sparse(2) { skip(2) }.readBytes()
        val header = original.copyOfRange(0, 28).copyOf(80)
        ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).putShort(8, 80)
        assertEquals(1, reconstruct(input(header + original.copyOfRange(28, original.size)), 40, ByteArray(8192) { 0x5a }))
    }
    @Test fun failureStopsAfterCurrentPacketAndRemovesIt() = source(input(ByteArray(30000))).use {
        val plan = FastbootFlashImage.prepare(it, 8192)
        val work = temporary.newFolder(); var calls = 0
        assertFalse(plan.forEachPart(work) { _, _, _ -> ++calls < 2 })
        assertEquals(2, calls); assertTrue(work.listFiles()!!.isEmpty())
    }
    @Test fun cancellationBetweenPacketsDoesNotReplayOrSendNextPacket() = source(input(ByteArray(30000))).use {
        var cancelled = false; var calls = 0
        val plan = FastbootFlashImage.prepare(it, 8192) { if (cancelled) throw InterruptedException() }
        val work = temporary.newFolder()
        assertThrows(InterruptedException::class.java) { plan.forEachPart(work) { _, _, _ -> calls++; cancelled = true; true } }
        assertEquals(1, calls); assertTrue(work.listFiles()!!.isEmpty())
    }
    @Test fun readFailureWhileMaterializingPacketCleansPartialFileBeforeSend() {
        var fail = false
        val reader = object : FirmwareSource.Opened {
            override val sizeBytes = 20_000L
            override fun readFullyAt(offset: Long, target: ByteArray) { if (fail) error("Provider disconnected"); target.fill(5) }
            override fun close() {}
        }
        val plan = FastbootFlashImage.prepare(reader, 8192); fail = true
        val work = temporary.newFolder(); var sent = false
        assertThrows(IllegalStateException::class.java) { plan.forEachPart(work) { _, _, _ -> sent = true; true } }
        assertFalse(sent); assertTrue(work.listFiles()!!.isEmpty())
    }
    @Test fun randomizedSparseOverlaysMatchIndependentWholeImageDecoder() {
        val random = Random(230)
        repeat(20) {
            val image = sparse(24) {
                repeat(24) {
                    when (random.nextInt(4)) {
                        0 -> skip(1)
                        1 -> fill(random.nextInt(), 1)
                        else -> raw(ByteArray(4096).also { random.nextBytes(it) })
                    }
                }
            }
            val expected = temporary.newFile().also { it.writeBytes(ByteArray(24 * 4096) { 0x5a }) }
            HostImageFixtures.expand(image, expected)
            reconstruct(image, 4200L + random.nextInt(12000), expected.readBytes())
        }
    }
    @Test fun sparseLimitSyntaxAndOverflowAreExplicit() {
        assertEquals(0, FastbootFlashImage.parseLimit("0")); assertEquals(1024, FastbootFlashImage.parseLimit("1K"))
        assertEquals(2L shl 20, FastbootFlashImage.parseLimit("2m")); assertEquals(3L shl 30, FastbootFlashImage.parseLimit("3G"))
        for (bad in listOf("", "-1", "1.5G", "1GB", "0x1000", "9223372036854775808", "999999999999G")) {
            assertThrows(bad, IllegalArgumentException::class.java) { FastbootFlashImage.parseLimit(bad) }
        }
    }
}
