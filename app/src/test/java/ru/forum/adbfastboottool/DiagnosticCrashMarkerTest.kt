package ru.forum.adbfastboottool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class DiagnosticCrashMarkerTest {
    @Test fun repeatedCrashesProduceDistinctAppendOnlyFullTraces() {
        val root = Files.createTempDirectory("nekoflash-crash-history-").toFile()
        try {
            val old = root.resolve("last-crash-marker.txt").apply { writeText("older crash evidence") }
            val deep = IllegalStateException("inner failure").apply {
                stackTrace = (0 until 60).map { index ->
                    StackTraceElement("Stack$index", "call$index", "File$index.kt", index + 1)
                }.toTypedArray()
            }
            val error = RuntimeException("outer failure", deep)
            val first = DiagnosticCrashMarker.writeCrashMarker(root, "test-build", "worker", error)
            val second = DiagnosticCrashMarker.writeCrashMarker(root, "test-build", "worker", error)
            assertFalse(first.absolutePath == second.absolutePath)
            assertEquals("older crash evidence", old.readText())
            assertTrue(first.isFile && second.isFile)
            assertTrue(first.readText().contains("Stack59.call59"))
            assertTrue(first.readText().contains("inner failure"))
            assertTrue(first.readText().contains("outer failure"))
            assertEquals(3, root.listFiles()?.size)
        } finally {
            root.deleteRecursively()
        }
    }
}
