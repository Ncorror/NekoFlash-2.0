package ru.forum.adbfastboottool

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CancellationException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class PackageArchiveExtractorTest {
    private fun directory(test: (File, File) -> Unit) {
        val root = Files.createTempDirectory("package-extract").toFile()
        val out = File(root, "out").apply { mkdir() }
        try { test(root, out) } finally { root.deleteRecursively() }
    }
    private fun archive(root: File, vararg entries: Pair<String, ByteArray>): File = File(root, "fixture.xapk").apply {
        ZipOutputStream(outputStream()).use { zip ->
            for ((name, bytes) in entries) { zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
        }
    }
    @Test fun plansAllUncompressedApkAndObbBytesBeforeWriting() = directory { root, out ->
        val file = archive(root, "base.apk" to ByteArray(5000), "main.obb" to ByteArray(7000))
        assertTrue(file.length() < 12000)
        var notified = false
        val error = assertThrows(LocalFileCopy.NotEnoughSpace::class.java) {
            PackageArchiveExtractor.extract(file, out, {}, onProgress = { _, _ -> notified = true }, availableSpace = { 11999 })
        }
        assertEquals(12000L, error.required); assertFalse(notified); assertTrue(out.listFiles()!!.isEmpty())
    }
    @Test fun preservesOrderAndDuplicateBaseNames() = directory { root, out ->
        val file = archive(root, "one/base.apk" to byteArrayOf(1), "two/base.apk" to byteArrayOf(2), "Android/obb/com.test/main.obb" to byteArrayOf(3))
        val entries = PackageArchiveExtractor.extract(file, out, {}, availableSpace = { Long.MAX_VALUE })
        assertEquals(listOf("000-base.apk", "001-base.apk", "obb-000-main.obb"), entries.map { it.file.name })
        assertEquals(listOf(false, false, true), entries.map { it.obb })
        assertEquals(listOf(1, 2, 3), entries.map { it.file.readBytes().single().toInt() })
    }
    @Test fun cancellationDuringObbAlsoRemovesCompletedApk() = directory { root, out ->
        val file = archive(root, "base.apk" to ByteArray(32), "main.obb" to ByteArray(2 * 1024 * 1024))
        File(out, "unrelated").writeText("keep"); val control = ImportCancellation()
        assertThrows(CancellationException::class.java) {
            PackageArchiveExtractor.extract(file, out, control::check,
                onProgress = { copied, _ -> if (copied > 32) control.cancel() }, availableSpace = { Long.MAX_VALUE })
        }
        assertEquals(listOf("unrelated"), out.listFiles()!!.map { it.name })
    }
    @Test fun lastChunkCancellationCannotReturnAnInstallSet() = directory { root, out ->
        val file = archive(root, "base.apk" to byteArrayOf(1, 2)); val control = ImportCancellation()
        assertThrows(CancellationException::class.java) {
            PackageArchiveExtractor.extract(file, out, control::check,
                onProgress = { copied, total -> if (copied == total) control.cancel() }, availableSpace = { Long.MAX_VALUE })
        }
        assertTrue(out.listFiles()!!.isEmpty())
    }
    @Test fun disappearingCapacityRollsBackAllEntries() = directory { root, out ->
        val file = archive(root, "base.apk" to byteArrayOf(1), "split.apk" to byteArrayOf(2)); var available = Long.MAX_VALUE
        assertThrows(LocalFileCopy.NotEnoughSpace::class.java) {
            PackageArchiveExtractor.extract(file, out, {}, onProgress = { copied, _ -> if (copied > 0) available = 0 }, availableSpace = { available })
        }
        assertTrue(out.listFiles()!!.isEmpty())
    }
    @Test fun unsafePathsCannotEscapeWorkspace() = directory { root, out ->
        val file = archive(root, "../escape.apk" to byteArrayOf(1), "/absolute.apk" to byteArrayOf(2), "nested\\..\\escape.obb" to byteArrayOf(3), "base.apk" to byteArrayOf(4))
        val skipped = mutableListOf<String>()
        val result = PackageArchiveExtractor.extract(file, out, {}, onSkipped = { skipped.add(it) }, availableSpace = { Long.MAX_VALUE })
        assertEquals(3, skipped.size); assertEquals(1, result.size); assertFalse(File(root, "escape.apk").exists())
    }
    @Test fun corruptStoredEntryIsRejectedByCrcAndCleaned() = directory { root, out ->
        val payload = "unique stored fixture payload".toByteArray(); val file = File(root, "broken.xapk")
        ZipOutputStream(file.outputStream()).use { zip ->
            val entry = ZipEntry("base.apk").apply {
                method = ZipEntry.STORED; size = payload.size.toLong(); compressedSize = size
                crc = java.util.zip.CRC32().apply { update(payload) }.value
            }
            zip.putNextEntry(entry); zip.write(payload); zip.closeEntry()
        }
        val bytes = file.readBytes()
        val offset = (0..bytes.size - payload.size).first { i -> payload.indices.all { bytes[i + it] == payload[it] } }
        bytes[offset] = (bytes[offset].toInt() xor 1).toByte(); file.writeBytes(bytes)
        assertThrows(IOException::class.java) { PackageArchiveExtractor.extract(file, out, {}, availableSpace = { Long.MAX_VALUE }) }
        assertTrue(out.listFiles()!!.isEmpty())
    }
    @Test fun restartReclaimsPreparationAndSuccessCleansWorkspace() = directory { root, _ ->
        val cache = File(root, "private-cache").apply { mkdir() }
        File(cache, "pending").apply { mkdir(); File(this, "partial.apk").writeText("interrupted") }
        File(cache, "pkg-previous").apply { mkdir(); File(this, "partial.obb").writeText("interrupted") }
        File(cache, "unrelated").writeText("keep")
        val result = PackageArchiveExtractor.withWorkspace(cache) { pending ->
            assertTrue(pending.listFiles()!!.isEmpty()); assertFalse(File(cache, "pkg-previous").exists())
            File(pending, "base.apk").writeText("prepared"); "installed"
        }
        assertEquals("installed", result); assertFalse(File(cache, "pending").exists()); assertEquals("keep", File(cache, "unrelated").readText())
    }
    @Test fun lockPreventsConcurrentCleanupAndReleasesAfterFailure() = directory { root, _ ->
        val cache = File(root, "private-cache")
        assertThrows(CancellationException::class.java) {
            PackageArchiveExtractor.withWorkspace(cache) { pending ->
                val prepared = File(pending, "base.apk").apply { writeText("still installing") }
                assertThrows(IOException::class.java) { PackageArchiveExtractor.withWorkspace(cache) { fail("Concurrent preparation entered") } }
                assertEquals("still installing", prepared.readText()); throw CancellationException("cancelled")
            }
        }
        assertFalse(File(cache, "pending").exists())
        PackageArchiveExtractor.withWorkspace(cache) { assertTrue(it.isDirectory) }
        assertFalse(File(cache, "pending").exists())
    }
}
