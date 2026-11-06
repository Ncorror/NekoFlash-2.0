package ru.forum.adbfastboottool

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipInputStream

class DiagnosticEvidenceArchiveTest {
    @Test fun exportKeepsRawLogBytesAndSourceFiles() {
        val folder = Files.createTempDirectory("nekoflash-export-").toFile()
        try {
            val logs = folder.resolve("logs").apply { mkdirs() }
            val bytes = ByteArray(350_000) { i -> (i % 251).toByte() }
            val source = logs.resolve("trace-adb-shell-test-stdout.bin").apply { writeBytes(bytes) }
            val summary = logs.resolve("session-summary-test.json").apply { writeText("{\"status\":\"OK\"}") }
            logs.resolve("firmware-system.img").writeBytes(byteArrayOf(1, 2, 3))
            val out = ByteArrayOutputStream()
            assertEquals(2, DiagnosticEvidenceArchive.write(out, listOf(logs, logs)))
            val exported = mutableMapOf<String, ByteArray>()
            ZipInputStream(out.toByteArray().inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    exported[entry.name] = zip.readBytes()
                    zip.closeEntry()
                }
            }
            assertArrayEquals(bytes, exported["logs/1/${source.name}"])
            assertArrayEquals(summary.readBytes(), exported["logs/1/${summary.name}"])
            assertFalse(exported.keys.any { "firmware-system.img" in it })
            assertEquals(2, exported.size)
            assertArrayEquals(bytes, source.readBytes())
            assertTrue(summary.isFile)
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test fun emptyDirectoryReturnsExplicitMissingEvidenceManifest() {
        val folder = Files.createTempDirectory("nekoflash-empty-export-").toFile()
        try {
            val out = ByteArrayOutputStream()
            assertEquals(0, DiagnosticEvidenceArchive.write(out, listOf(folder)))
            ZipInputStream(out.toByteArray().inputStream()).use { zip ->
                assertEquals("NO_LOGS.txt", zip.nextEntry?.name)
            }
        } finally {
            folder.deleteRecursively()
        }
    }
}
