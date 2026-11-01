package ru.forum.adbfastboottool

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AdbPushDirectoryWalkerTest {
    @get:Rule val temp = TemporaryFolder()

    private fun createFile(parent: File, name: String) {
        File(parent, name).apply {
            parentFile!!.mkdirs()
            writeText("test payload $name")
        }
    }

    @Test fun preflightThenAllDirectoriesThenFilesPreservesLegacyOrder() {
        val root = temp.newFolder("src")
        val alpha = File(root, "α").apply { mkdirs() }
        val nested = File(alpha, "nested").apply { mkdirs() }
        File(root, "empty").mkdir()
        createFile(root, "boot.img")
        createFile(alpha, "vendor_boot.img")
        createFile(nested, "device-🐈.txt")
        val expected = root.walkTopDown().toList()
        val walker = AdbPushDirectoryWalker(root)
        val count = walker.scan {}
        assertEquals(AdbPushDirectoryWalker.Counts(4, 3), count)
        val actualDirs = mutableListOf<File>()
        val actualFiles = mutableListOf<File>()
        assertEquals(count, walker.visitDirectories({}, { actualDirs.add(it) }))
        assertEquals(count, walker.visitFiles({}, { actualFiles.add(it) }))
        assertEquals(expected.filter { it.isDirectory }, actualDirs)
        assertEquals(expected.filter { it.isFile }, actualFiles)
    }

    @Test fun thousandFilesUseRepeatedLazyTraversalWithoutMissingEntries() {
        val root = temp.newFolder("large-tree")
        repeat(1_024) { index ->
            createFile(File(root, "part-${index / 64}"), "img-${index.toString().padStart(4, '0')}")
        }
        val walker = AdbPushDirectoryWalker(root)
        val counts = walker.scan {}
        assertEquals(17L, counts.directories)
        assertEquals(1_024L, counts.files)
        var directories = 0L
        var files = 0L
        assertEquals(counts, walker.visitDirectories({}, { directories++ }))
        assertEquals(counts, walker.visitFiles({}, { files++ }))
        assertEquals(counts.directories, directories)
        assertEquals(counts.files, files)
    }

    @Test fun cancellationStopsInMiddleOfTreeBeforeNextFile() {
        val root = temp.newFolder("cancel")
        repeat(20) { createFile(root, "file-$it") }
        val walker = AdbPushDirectoryWalker(root)
        var transferred = 0
        try {
            walker.visitFiles(
                checkCancelled = {
                    if (transferred >= 3) throw CancellationException("stop requested")
                },
                action = { transferred++ }
            )
            throw AssertionError("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertEquals(3, transferred)
        }
    }

    @Test fun modifiedOrDeletedSourceIsDetectedBetweenPasses() {
        val root = temp.newFolder("changing")
        createFile(root, "original")
        val walker = AdbPushDirectoryWalker(root)
        val before = walker.scan {}
        createFile(root, "new")
        assertTrue(before != walker.visitDirectories({}, {}))
        assertTrue(before != walker.visitFiles({}, {}))
        root.deleteRecursively()
        try {
            walker.scan {}
            throw AssertionError("Missing source must fail")
        } catch (_: IOException) {
        }
    }

    @Test fun directorySymlinkIsNotFollowedIntoInfiniteCycle() {
        val root = temp.newFolder("symlinks")
        Files.createSymbolicLink(File(root, "loop").toPath(), root.toPath())
        try {
            AdbPushDirectoryWalker(root).scan {}
            throw AssertionError("Recursive symlink must be rejected")
        } catch (expected: IOException) {
            assertTrue(expected.message!!.contains("symlink"))
        }
    }
}
