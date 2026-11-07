package ru.forum.adbfastboottool

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.security.MessageDigest
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
            assertEquals(3, exported.size)
            val manifest = exported["EXPORT-MANIFEST.txt"]!!.toString(Charsets.UTF_8)
            assertTrue(manifest.contains("status=CONSISTENT_SNAPSHOT"))
            assertTrue(manifest.contains("file=logs/1/" + source.name))
            val checksum = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            assertTrue(manifest.contains("sha256=" + checksum))
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

    @Test fun exportIncludesLogsAndUsbSessionSnapshotsAcrossWorkspaceRoots() {
        val folder = Files.createTempDirectory("nekoflash-usb-export-").toFile()
        try {
            val privateWorkspace = folder.resolve("private").apply { mkdirs() }
            val publicWorkspace = folder.resolve("public").apply { mkdirs() }
            val expected = listOf(privateWorkspace, publicWorkspace).flatMapIndexed { index, workspace ->
                listOf("logs/trace-$index.bin", "reports/usb-sessions/usb-session-$index.json",
                    "reports/usb-sessions/usb-session-$index.txt").map { relative ->
                    workspace.resolve(relative).apply {
                        parentFile.mkdirs()
                        writeBytes(byteArrayOf(0, 127, -1, index.toByte()))
                    }
                }
            }
            // Do not scan arbitrary workspace files or import an earlier whole report.
            publicWorkspace.resolve("usb-unrelated.txt").writeText("outside diagnostic directories")
            publicWorkspace.resolve("reports/usb-unrelated.txt").writeText("unrelated report")
            val roots = DiagnosticEvidenceArchive.directories(
                listOf(privateWorkspace, publicWorkspace, privateWorkspace)
            )
            val out = ByteArrayOutputStream()
            assertEquals(6, DiagnosticEvidenceArchive.write(out, roots))
            val exported = mutableMapOf<String, ByteArray>()
            ZipInputStream(out.toByteArray().inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name != "EXPORT-MANIFEST.txt") {
                        exported[entry.name.substringAfterLast('/')] = zip.readBytes()
                    }
                    zip.closeEntry()
                }
            }
            assertEquals(expected.map { it.name }.toSet(), exported.keys)
            expected.forEach { assertArrayEquals(it.readBytes(), exported[it.name]) }
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test fun missingSessionPrologueIsReportedAndRawBytesRemainUntouched() {
        val folder = Files.createTempDirectory("nekoflash-truncated-session-").toFile()
        try {
            val logs = folder.resolve("logs").apply { mkdirs() }
            val missingStart = "[2026-11-07 13:53:45] ADB shell started\n".toByteArray(Charsets.UTF_8)
            val raw = logs.resolve("log-2026-11-07-13-51-12.txt").apply { writeBytes(missingStart) }
            val out = ByteArrayOutputStream()
            val result = DiagnosticEvidenceArchive.writeDetailed(out, listOf(logs))
            assertEquals(1, result.fileCount)
            assertFalse(result.snapshotConsistent)
            assertTrue(result.warnings.any { it.startsWith("MISSING_SESSION_PROLOGUE ") })
            val entries = mutableMapOf<String, ByteArray>()
            ZipInputStream(out.toByteArray().inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries[entry.name] = zip.readBytes()
                    zip.closeEntry()
                }
            }
            assertArrayEquals(missingStart, entries["logs/1/" + raw.name])
            val manifest = entries["EXPORT-MANIFEST.txt"]!!.toString(Charsets.UTF_8)
            assertTrue(manifest.contains("status=INCOMPLETE_OR_UNVERIFIED"))
            assertTrue(manifest.contains("warning=MISSING_SESSION_PROLOGUE "))
            assertArrayEquals(missingStart, raw.readBytes())
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test fun rotatedLogsAndRepeatedEventsHaveExactHashesAndNoFileLimit() {
        val folder = Files.createTempDirectory("nekoflash-multisegment-export-").toFile()
        try {
            val logs = folder.resolve("logs").apply { mkdirs() }
            val store = DiagnosticLogStore(logs, "stress-logs", 64L * 1024, 64L * 1024)
            val event = "[event] same content, must never disappear " + "x".repeat(600) + "\n"
            repeat(400) { store.appendCompact(event) }
            repeat(400) { store.appendTrace("wire:" + "y".repeat(600) + "\n") }
            val sources = store.compactFiles() + store.traceFiles()
            assertTrue(sources.size >= 6)
            val out = ByteArrayOutputStream()
            val result = DiagnosticEvidenceArchive.writeDetailed(out, listOf(logs))
            assertEquals(sources.size, result.fileCount)
            assertTrue(result.warnings.toString(), result.snapshotConsistent)
            val zipFiles = mutableMapOf<String, ByteArray>()
            ZipInputStream(out.toByteArray().inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    zipFiles[entry.name] = zip.readBytes()
                    zip.closeEntry()
                }
            }
            val manifest = zipFiles["EXPORT-MANIFEST.txt"]!!.toString(Charsets.UTF_8)
            for (source in sources) {
                val bytes = source.readBytes()
                val name = "logs/1/" + source.name
                assertArrayEquals(bytes, zipFiles[name])
                val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
                    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
                assertTrue(manifest.contains("file=" + name))
                assertTrue(manifest.contains("sha256=" + hash))
            }
            assertEquals(event.repeat(400), store.compactFiles().joinToString("") { it.readText() })
        } finally {
            folder.deleteRecursively()
        }
    }
}
