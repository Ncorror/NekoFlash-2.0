package ru.forum.adbfastboottool

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/** liblp metadata-only image -> flashable sparse images, including retrofit block devices.
 * The input is copied once into a private snapshot and checksummed before any USB mutation.
 */
internal class SuperEmptyImage private constructor(
    private val snapshot: File,
    private val geometry: ByteArray,
    private val metadataBytes: Long,
    private val metadataMax: Long,
    private val slots: Long,
    val blockSize: Int,
    val devices: List<BlockDevice>
) {
    data class BlockDevice(val name: String, val bytes: Long, val firstLogicalSector: Long, val slotSuffixed: Boolean)
    data class Image(val device: BlockDevice, val file: File)

    fun build(directory: File, checkCancelled: () -> Unit): List<Image> = devices.mapIndexed { index, device ->
        checkCancelled()
        val output = File.createTempFile("super-$index-", ".sparse", directory)
        AndroidSparseImage.Writer(output, blockSize, device.bytes / blockSize).use { writer ->
            var used = 0L
            if (index == 0) {
                writer.fill(0, 4096L / blockSize)
                writer.raw(geometry); writer.raw(geometry)
                val padded = (metadataBytes + blockSize - 1) / blockSize * blockSize
                var copy = 0L
                while (copy < slots * 2) {
                    RandomAccessFile(snapshot, "r").use { input ->
                        input.seek(4096)
                        val buffer = ByteArray(blockSize)
                        var remaining = metadataBytes
                        while (remaining > 0) {
                            checkCancelled(); buffer.fill(0)
                            val n = minOf(remaining, blockSize.toLong()).toInt()
                            input.readFully(buffer, 0, n); writer.raw(buffer)
                            remaining -= n
                        }
                    }
                    writer.fill(0, (metadataMax - padded) / blockSize)
                    copy++
                }
                used = 12288L + metadataMax * slots * 2
            }
            writer.skip((device.bytes - used) / blockSize)
            writer.finish()
        }
        require(AndroidSparseImage.inspect(output, checkCancelled).expandedBytes == device.bytes)
        Image(device, output)
    }

    companion object {
        private data class Table(val offset: Long, val count: Long, val size: Int) {
            val end: Long get() = offset + count * size
        }
        private fun ByteBuffer.u32(at: Int) = getInt(at).toLong() and 0xffff_ffffL
        private fun buffer(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        private fun checkedHash(bytes: ByteArray, start: Int, length: Int, checksumAt: Int) {
            val expected = bytes.copyOfRange(checksumAt, checksumAt + 32)
            val copy = bytes.copyOfRange(start, start + length)
            copy.fill(0, checksumAt - start, checksumAt - start + 32)
            require(MessageDigest.isEqual(expected, MessageDigest.getInstance("SHA-256").digest(copy))) {
                "Invalid logical-partition metadata checksum"
            }
        }
        private fun name(bytes: ByteArray, offset: Int): String {
            val end = (offset until offset + 36).firstOrNull { bytes[it] == 0.toByte() } ?: offset + 36
            require((end until offset + 36).all { bytes[it] == 0.toByte() }) { "Invalid LP name padding" }
            return bytes.copyOfRange(offset, end).toString(Charsets.US_ASCII).also {
                require(it.matches(Regex("[A-Za-z0-9._-]+"))) { "Invalid LP name: $it" }
            }
        }

        fun load(source: File, directory: File, checkCancelled: () -> Unit): SuperEmptyImage {
            val snapshot = File.createTempFile("super-metadata-", ".img", directory)
            var keep = false
            try {
                val result = RandomAccessFile(source, "r").use { input ->
                    require(input.length() >= 4096 + 128) { "super_empty.img is truncated" }
                    val geometry = ByteArray(4096); input.readFully(geometry)
                    val g = buffer(geometry)
                    require(g.getInt(0) == 0x616c4467 && g.u32(4) == 52L) {
                        "wipe-super requires a liblp metadata-only super_empty.img, not a full/raw/sparse super image"
                    }
                    checkedHash(geometry, 0, 52, 8)
                    geometry.fill(0, 52, geometry.size)
                    val maximum = g.u32(40); val slots = g.u32(44); val block = g.u32(48)
                    require(slots > 0 && block in 512..4096 && block and (block - 1) == 0L)
                    require(maximum > 0 && maximum % block == 0L)
                    val header = ByteArray(128); input.readFully(header)
                    val h = buffer(header)
                    val minor = h.getShort(6).toInt() and 0xffff
                    require(h.getInt(0) == 0x414c5030 && h.getShort(4).toInt() == 10 && minor <= 2) {
                        "Unsupported LP metadata version"
                    }
                    val headerSize = if (minor < 2) 128 else 256
                    require(h.u32(8) == headerSize.toLong()) { "Invalid LP header size" }
                    val fullHeader = header.copyOf(headerSize)
                    if (headerSize > 128) input.readFully(fullHeader, 128, headerSize - 128)
                    checkedHash(fullHeader, 0, headerSize, 12)
                    val tablesSize = h.u32(44)
                    require(headerSize + tablesSize <= maximum && tablesSize <= input.length() - 4096 - headerSize) {
                        "LP tables exceed metadata bounds"
                    }
                    val sizes = listOf(52, 24, 48, 64)
                    val tables = sizes.mapIndexed { index, size ->
                        val offset = 80 + index * 12
                        require(h.u32(offset + 8) == size.toLong()) { "Unknown LP table entry size" }
                        Table(h.u32(offset), h.u32(offset + 4), size).also {
                            require(it.end <= tablesSize) { "LP table is out of bounds" }
                        }
                    }
                    val present = tables.filter { it.count > 0 }.sortedBy { it.offset }
                    present.zipWithNext().forEach { (a, b) -> require(a.end <= b.offset) { "Overlapping LP tables" } }
                    require(tables[1].count == 0L) { "wipe-super template has allocated extents; use super_empty.img" }
                    require(tables[2].count > 0 && tables[3].count > 0) { "LP groups or block devices are missing" }
                    RandomAccessFile(snapshot, "rw").use { output ->
                        output.write(geometry); output.write(fullHeader)
                        val digest = MessageDigest.getInstance("SHA-256")
                        val bytes = ByteArray(64 * 1024)
                        var remaining = tablesSize
                        while (remaining > 0) {
                            checkCancelled()
                            val count = minOf(remaining, bytes.size.toLong()).toInt()
                            input.readFully(bytes, 0, count); output.write(bytes, 0, count); digest.update(bytes, 0, count)
                            remaining -= count
                        }
                        require(MessageDigest.isEqual(fullHeader.copyOfRange(48, 80), digest.digest())) {
                            "Invalid LP tables checksum"
                        }
                        output.fd.sync()
                    }
                    val devices = mutableListOf<BlockDevice>()
                    RandomAccessFile(snapshot, "r").use { copied ->
                        fun each(table: Table, action: (ByteArray) -> Unit) {
                            var index = 0L
                            while (index < table.count) {
                                checkCancelled(); copied.seek(4096L + headerSize + table.offset + index * table.size)
                                val bytes = ByteArray(table.size); copied.readFully(bytes); action(bytes); index++
                            }
                        }
                        val partitionNames = HashSet<String>()
                        each(tables[0]) { bytes ->
                            val p = buffer(bytes)
                            require(partitionNames.add(name(bytes, 0))) { "Duplicate LP partition" }
                            require(p.u32(36) and (if (minor == 0) 3L else 15L).inv() == 0L) { "Unknown LP partition flags" }
                            require(p.u32(40) == 0L && p.u32(44) == 0L && p.u32(48) < tables[2].count) {
                                "Invalid empty partition or group reference"
                            }
                        }
                        val groupNames = HashSet<String>()
                        each(tables[2]) { bytes ->
                            require(groupNames.add(name(bytes, 0))) { "Duplicate LP group" }
                            require(buffer(bytes).u32(36) and 1L.inv() == 0L && buffer(bytes).getLong(40) >= 0)
                        }
                        val deviceNames = HashSet<String>()
                        each(tables[3]) { bytes ->
                            val b = buffer(bytes); val name = name(bytes, 24)
                            val size = b.getLong(16); val first = b.getLong(0); val flags = b.u32(60)
                            require(deviceNames.add(name) && flags and 1L.inv() == 0L)
                            require(size > 0 && size % block == 0L && size / block <= 0xffff_ffffL)
                            require(first >= 0 && first <= size / 512) { "LP first sector exceeds device" }
                            require(b.u32(8) % 512 == 0L && b.u32(12) % 512 == 0L)
                            devices += BlockDevice(name, size, first, flags == 1L)
                        }
                    }
                    val end = Math.addExact(12288L, Math.multiplyExact(maximum, Math.multiplyExact(slots, 2L)))
                    require(end <= devices.first().bytes && end / 512 <= devices.first().firstLogicalSector) {
                        "LP metadata overlaps logical data or exceeds super device"
                    }
                    SuperEmptyImage(snapshot, geometry, headerSize + tablesSize, maximum, slots, block.toInt(), devices)
                }
                keep = true
                return result
            } finally { if (!keep) snapshot.delete() }
        }
    }
}
