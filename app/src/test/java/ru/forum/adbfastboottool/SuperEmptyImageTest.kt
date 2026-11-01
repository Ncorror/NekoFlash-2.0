package ru.forum.adbfastboottool

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SuperEmptyImageTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun file(name: String) = File(temporary.root, name)
    private fun load(source: File) = SuperEmptyImage.load(source, temporary.root) {}

    @Test fun versionsTenZeroThroughTenTwoProduceGeometryAndEveryMetadataCopy() {
        for (minor in 0..2) {
            val source = file("super-$minor.img"); val original = HostImageFixtures.emptySuper(source, minor)
            val images = load(source).build(temporary.root) {}
            assertEquals(1, images.size); assertEquals("super", images.single().device.name)
            val expanded = file("expanded-$minor.raw"); HostImageFixtures.expand(images.single().file, expanded)
            val bytes = expanded.readBytes()
            assertTrue(bytes.take(4096).all { it == 0.toByte() })
            val geometry = original.copyOfRange(0, 4096); val metadata = original.copyOfRange(4096, original.size)
            assertArrayEquals(geometry, bytes.copyOfRange(4096, 8192))
            assertArrayEquals(geometry, bytes.copyOfRange(8192, 12288))
            for (copy in 0..3) {
                val start = 12288 + copy * 4096
                assertArrayEquals(metadata, bytes.copyOfRange(start, start + metadata.size))
                assertTrue(bytes.copyOfRange(start + metadata.size, start + 4096).all { it == 0.toByte() })
            }
        }
    }
    @Test fun retrofitProducesOneImagePerBlockDeviceAndPreservesSlotFlags() {
        val source = file("retrofit.img"); HostImageFixtures.emptySuper(source, names = listOf("system", "vendor"), suffixed = true)
        val parsed = load(source); val images = parsed.build(temporary.root) {}
        assertEquals(listOf("system", "vendor"), parsed.devices.map { it.name })
        assertTrue(parsed.devices.all { it.slotSuffixed })
        assertEquals(listOf(4194304L, 4194304L), images.map { AndroidSparseImage.inspect(it.file).expandedBytes })
        assertEquals(40L, images[1].file.length())
    }
    @Test fun inputMutationAfterLoadCannotChangeGeneratedMetadata() {
        val source = file("snapshot.img"); val original = HostImageFixtures.emptySuper(source)
        val parsed = load(source); source.writeBytes(ByteArray(original.size))
        val expanded = file("snapshot.raw"); HostImageFixtures.expand(parsed.build(temporary.root) {}.single().file, expanded)
        assertArrayEquals(original.copyOfRange(4096, original.size), expanded.readBytes().copyOfRange(12288, 12288 + original.size - 4096))
    }
    @Test fun geometryHeaderAndTableChecksumFailuresAreRejected() {
        for (offset in listOf(40, 4096 + 80, 4096 + 256)) {
            val source = file("bad-$offset.img"); val bytes = HostImageFixtures.emptySuper(source)
            bytes[offset] = (bytes[offset].toInt() xor 1).toByte(); source.writeBytes(bytes)
            assertThrows(IllegalArgumentException::class.java) { load(source) }
        }
    }
    @Test fun allocatedExtentsAreRejectedBeforeAnyFlashImageIsBuilt() {
        val source = file("allocated.img"); HostImageFixtures.emptySuper(source, extents = true)
        assertThrows(IllegalArgumentException::class.java) { load(source) }
        assertTrue(temporary.root.listFiles()!!.none { it.extension == "sparse" })
    }
    @Test fun overlappingTablesAreRejectedEvenWithValidChecksums() {
        val source = file("overlap.img"); val bytes = HostImageFixtures.emptySuper(source)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(4096 + 104, 0)
        HostImageFixtures.rehash(bytes, 4096, 256, 12); source.writeBytes(bytes)
        assertThrows(IllegalArgumentException::class.java) { load(source) }
    }
    @Test fun metadataCannotOverlapLogicalData() {
        val source = file("oversized.img"); val bytes = HostImageFixtures.emptySuper(source)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(44, 200)
        HostImageFixtures.rehash(bytes, 0, 52, 8); source.writeBytes(bytes)
        assertThrows(IllegalArgumentException::class.java) { load(source) }
    }
    @Test fun truncationAndNonLplibImagesAreRejected() {
        val f = file("truncated.img").also { it.writeBytes(ByteArray(100)) }
        assertThrows(IllegalArgumentException::class.java) { load(f) }
        f.writeBytes(ByteArray(8192))
        assertThrows(IllegalArgumentException::class.java) { load(f) }
    }
    @Test fun cancelledMetadataReadRemovesItsSnapshot() {
        val f = file("cancel.img"); HostImageFixtures.emptySuper(f)
        assertThrows(InterruptedException::class.java) { SuperEmptyImage.load(f, temporary.root) { throw InterruptedException() } }
        assertEquals(listOf(f.name), temporary.root.listFiles()!!.map { it.name })
    }
}
