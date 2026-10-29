package ru.forum.adbfastboottool

import java.io.*
import java.nio.file.Files
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class WorkspaceFileImporterTest {
    private fun directory(test: (File) -> Unit) {
        val dir = Files.createTempDirectory("workspace-import").toFile()
        try { test(dir) } finally { dir.deleteRecursively() }
    }
    private fun pending(dir: File) = File(dir, ".nekoflash-import-test/pending.part")
    private fun copy(dir: File, bytes: ByteArray, control: ImportCancellation = ImportCancellation(),
                     expected: Long? = bytes.size.toLong(), progress: (Long) -> Unit = {},
                     space: (File) -> Long = { Long.MAX_VALUE }): File =
        WorkspaceFileImporter.importFile(dir, "test", "image.img", expected, control,
            { bytes.inputStream() }, progress, space)
    @Test fun preservesExistingImageAndPublishesCompleteNewFile() = directory { dir ->
        File(dir, "image.img").writeText("original")
        val bytes = ByteArray(8192) { it.toByte() }
        val result = copy(dir, bytes)
        assertEquals("image-1.img", result.name)
        assertEquals("original", File(dir, "image.img").readText())
        assertArrayEquals(bytes, result.readBytes()); assertFalse(pending(dir).exists())
    }
    @Test fun cancellationMidCopyRemovesPartialFile() = directory { dir ->
        val c = ImportCancellation()
        assertThrows(CancellationException::class.java) { copy(dir, ByteArray(2 * 1024 * 1024), c, progress = { c.cancel() }) }
        assertFalse(File(dir, "image.img").exists()); assertFalse(pending(dir).exists())
    }
    @Test fun cancellationAtLastByteStillPreventsPublication() = directory { dir ->
        val c = ImportCancellation()
        assertThrows(CancellationException::class.java) { copy(dir, byteArrayOf(1), c, progress = { c.cancel() }) }
        assertFalse(File(dir, "image.img").exists()); assertFalse(pending(dir).exists())
    }
    @Test fun aLateCancelCannotRetractPublishedFile() = directory { dir ->
        val c = ImportCancellation(); val result = copy(dir, byteArrayOf(1), c)
        assertFalse(c.cancel()); c.check(); assertArrayEquals(byteArrayOf(1), result.readBytes())
    }
    @Test fun capacityIsCheckedBeforeOpeningProvider() = directory { dir ->
        var opened = false
        assertThrows(LocalFileCopy.NotEnoughSpace::class.java) {
            WorkspaceFileImporter.importFile(dir, "test", "image.img", 10, ImportCancellation(),
                { opened = true; byteArrayOf(1).inputStream() }, availableSpace = { 9 })
        }
        assertFalse(opened); assertFalse(pending(dir).exists())
    }
    @Test fun capacityDisappearingDuringCopyRollsBack() = directory { dir ->
        var available = Long.MAX_VALUE
        assertThrows(LocalFileCopy.NotEnoughSpace::class.java) {
            copy(dir, ByteArray(2 * 1024 * 1024), progress = { available = 0 }, space = { available })
        }
        assertFalse(File(dir, "image.img").exists()); assertFalse(pending(dir).exists())
    }
    @Test fun shortOrGrowingSourceCannotBecomeACompleteImage() = directory { dir ->
        for (size in listOf(1L, 3L)) {
            assertThrows(IOException::class.java) { copy(dir, byteArrayOf(1, 2), expected = size) }
            assertFalse(File(dir, "image.img").exists()); assertFalse(pending(dir).exists())
        }
    }
    @Test fun unknownLengthAndZeroReadStillCopyAllBytes() = directory { dir ->
        val input = object : ByteArrayInputStream(byteArrayOf(1, 2, 3)) {
            var first = true
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (first) { first = false; return 0 }
                return super.read(b, off, len)
            }
        }
        val target = WorkspaceFileImporter.importFile(dir, "test", "image.img", null, ImportCancellation(),
            { input }, availableSpace = { Long.MAX_VALUE })
        assertArrayEquals(byteArrayOf(1, 2, 3), target.readBytes())
    }
    @Test fun providerFailureClosesInputAndCleansPartial() = directory { dir ->
        var closed = false
        val input = object : InputStream() {
            override fun read(): Int = throw IOException("provider gone")
            override fun close() { closed = true }
        }
        assertThrows(IOException::class.java) {
            WorkspaceFileImporter.importFile(dir, "test", "image.img", null, ImportCancellation(),
                { input }, availableSpace = { Long.MAX_VALUE })
        }
        assertTrue(closed); assertFalse(pending(dir).exists()); assertFalse(File(dir, "image.img").exists())
    }
    @Test fun restartOnlyReclaimsItsReservedPendingFile() = directory { dir ->
        pending(dir).parentFile!!.mkdirs(); pending(dir).writeText("abandoned")
        File(dir, "user.part").writeText("keep")
        val other = File(dir, ".nekoflash-import-other/pending.part").apply { parentFile!!.mkdir(); writeText("other") }
        copy(dir, byteArrayOf(1))
        assertFalse(pending(dir).exists()); assertEquals("keep", File(dir, "user.part").readText()); assertEquals("other", other.readText())
    }
    @Test fun secondImportCannotCleanAnActiveStagingFile() = directory { dir ->
        copy(dir, byteArrayOf(1, 2), progress = {
            assertTrue(pending(dir).exists())
            assertThrows(IOException::class.java) { copy(dir, byteArrayOf(3)) }
            assertTrue(pending(dir).exists())
        })
        assertArrayEquals(byteArrayOf(1, 2), File(dir, "image.img").readBytes())
    }
    @Test fun emptySourceIsRejected() = directory { dir ->
        assertThrows(IOException::class.java) { copy(dir, byteArrayOf()) }
        assertFalse(File(dir, "image.img").exists()); assertFalse(pending(dir).exists())
    }
}
