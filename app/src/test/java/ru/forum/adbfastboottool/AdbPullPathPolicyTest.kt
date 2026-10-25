package ru.forum.adbfastboottool

import java.io.File
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
}
