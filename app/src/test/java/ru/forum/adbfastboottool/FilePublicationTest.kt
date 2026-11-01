package ru.forum.adbfastboottool

import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class FilePublicationTest {
    private fun withDirectory(test: (File) -> Unit) {
        val dir = Files.createTempDirectory("publication-test").toFile()
        try { test(dir) } finally { dir.deleteRecursively() }
    }
    @Test fun replacePublishesCompletedBytes() = withDirectory { dir ->
        val target = File(dir, "image.img").apply { writeText("old") }
        val staged = FilePublication.temporarySibling(target).apply { writeText("complete") }
        FilePublication.replace(staged, target)
        assertEquals("complete", target.readText())
        assertFalse(staged.exists())
    }
    @Test fun missingStagingPreservesOldTarget() = withDirectory { dir ->
        val target = File(dir, "image.img").apply { writeText("old") }
        assertThrows(Exception::class.java) { FilePublication.replace(File(dir, "missing"), target) }
        assertEquals("old", target.readText())
    }
    @Test fun failedDirectoryReplacementPreservesContents() = withDirectory { dir ->
        val target = File(dir, "existing").apply { mkdir() }
        File(target, "keep").writeText("safe")
        val staged = FilePublication.temporarySibling(target).apply { writeText("new") }
        assertThrows(Exception::class.java) { FilePublication.replace(staged, target) }
        assertEquals("safe", File(target, "keep").readText())
        assertEquals("new", staged.readText())
    }
    @Test fun importCollisionPreservesBothFiles() = withDirectory { dir ->
        val target = File(dir, "image.img").apply { writeText("old") }
        val staged = FilePublication.temporarySibling(target).apply { writeText("new") }
        assertThrows(java.nio.file.FileAlreadyExistsException::class.java) { FilePublication.publishNew(staged, target) }
        assertEquals("old", target.readText())
        assertEquals("new", staged.readText())
    }
    @Test fun concurrentImportsHaveExactlyOneWinner() = withDirectory { dir ->
        val target = File(dir, "image.img")
        val start = CountDownLatch(1)
        val successes = AtomicInteger()
        val collisions = AtomicInteger()
        val files = listOf("first", "second").map { data -> FilePublication.temporarySibling(target).apply { writeText(data) } }
        val threads = files.map { staged -> Thread {
            start.await()
            try { FilePublication.publishNew(staged, target); successes.incrementAndGet() }
            catch (_: java.nio.file.FileAlreadyExistsException) { collisions.incrementAndGet() }
        }.apply { start() } }
        start.countDown(); threads.forEach { it.join() }
        assertEquals(1, successes.get()); assertEquals(1, collisions.get())
        assertEquals(setOf("first", "second"), setOf(target.readText(), files.single { it.exists() }.readText()))
    }
    @Test fun temporaryNamesDoNotReuseExistingPart() = withDirectory { dir ->
        val target = File(dir, "image.img")
        val old = File(dir, "image.img.part").apply { writeText("recoverable") }
        val first = FilePublication.temporarySibling(target)
        val second = FilePublication.temporarySibling(target)
        assertNotEquals(first, second)
        assertEquals("recoverable", old.readText())
    }
}
