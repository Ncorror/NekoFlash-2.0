package ru.forum.adbfastboottool

import java.io.File
import java.io.Reader
import java.io.StringReader
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class AdbPullPathPolicyTest {
    @Test fun listingPreservesLegalUnusualNames() {
        val names = listOf("./line\nbreak", "./back\\slash", "./trailing ")
        val actualNames = names.map { it.replace("\\n", "\n") }
        val listing = (listOf("AFT_DIRS_BEGIN", ".", "AFT_FILES_BEGIN") + actualNames + "AFT_DONE" + "").joinToString("\u0000")
        assertEquals(actualNames, AdbPullPathPolicy.parseListing(listing).second)
    }
    @Test fun traversalAndAbsoluteEntriesAreRejected() {
        listOf("../bad", "./../bad", "./a/../../bad", "/absolute", "./a//b", "./a/./b").forEach {
            assertThrows(IllegalArgumentException::class.java) { AdbPullPathPolicy.normalize(it) }
        }
    }
    @Test fun incompleteListingCannotBecomeSuccess() {
        listOf("AFT_DIRS_BEGIN\u0000.\u0000AFT_FILES_BEGIN\u0000./file\u0000", "AFT_DIRS_BEGIN\n.\nAFT_FILES_BEGIN\n./file\n").forEach {
            assertThrows(IllegalArgumentException::class.java) { AdbPullPathPolicy.parseListing(it) }
        }
    }
    @Test fun symlinkEscapeIsRejected() {
        val dir = Files.createTempDirectory("pull-path").toFile()
        try {
            val root = File(dir, "root").apply { mkdir() }
            val outside = File(dir, "outside").apply { mkdir() }
            Files.createSymbolicLink(File(root, "link").toPath(), outside.toPath())
            assertThrows(IllegalArgumentException::class.java) { AdbPullPathPolicy.destination(root, "./link/data") }
        } finally { dir.deleteRecursively() }
    }
    @Test fun nestedFilesStayInsideDestination() {
        val root = Files.createTempDirectory("pull-path").toFile()
        try { assertEquals(File(root, "a/b"), AdbPullPathPolicy.destination(root, "./a/b")) }
        finally { root.deleteRecursively() }
    }

    @Test fun streamedListingRetainsExactNamesAcrossTinyReaderChunks() {
        val dirs = listOf(".", "./folder")
        val files = (0 until 4096).map {
            "./folder/file_" + it.toString().padStart(5, '0')
        } + listOf("./folder/line\nbreak", "./folder/back\\slash", "./folder/trailing ")
        val listing = (listOf("AFT_DIRS_BEGIN") + dirs +
            listOf("AFT_FILES_BEGIN") + files + "AFT_DONE").joinToString("\u0000", postfix = "\u0000")
        val reader = object : Reader() {
            private val source = StringReader(listing)
            override fun read(cbuf: CharArray, off: Int, len: Int): Int =
                source.read(cbuf, off, minOf(len, 3))
            override fun close() = source.close()
        }
        assertEquals(dirs to files, AdbPullPathPolicy.parseListing(reader))
        assertEquals(AdbPullPathPolicy.parseListing(listing), AdbPullPathPolicy.parseListing(StringReader(listing)))
    }

    @Test fun streamedListingRejectsMissingFinalNulAndTrailingRecords() {
        val base = "AFT_DIRS_BEGIN\u0000.\u0000AFT_FILES_BEGIN\u0000./file\u0000AFT_DONE"
        assertThrows(IllegalArgumentException::class.java) {
            AdbPullPathPolicy.parseListing(StringReader(base))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AdbPullPathPolicy.parseListing(StringReader(base + "\u0000./extra\u0000"))
        }
    }
}
