package ru.forum.adbfastboottool

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.util.Base64
import java.util.Random
import java.util.zip.ZipInputStream

class DiagnosticAtomicSummarySnapshotTest {
    @Test fun atomicSummaryRepublishDuringExportRetainsOneWholeRevision() {
        val dir = Files.createTempDirectory("nekoflash-summary-republish-").toFile()
        try {
            val summary = dir.resolve("session-summary-2026-11-07-15-48-12.json")
            val randomBytes = ByteArray(512 * 1024).apply { Random(51L).nextBytes(this) }
            val oldRevision = ("{\"snapshot\":\"" +
                Base64.getEncoder().encodeToString(randomBytes) + "\"}\n").toByteArray(Charsets.UTF_8)
            val newRevision = "{\"snapshot\":\"NEW\"}\n".toByteArray(Charsets.UTF_8)
            summary.writeBytes(oldRevision)

            val resultBytes = ByteArrayOutputStream()
            var written = 0L
            var replaced = false
            // Swap the *path* during the actual ZIP data write. The exporter
            // must retain the already opened old file descriptor, not read an
            // incomplete mixture or mistake an atomic republish for corruption.
            val replacingOutput = object : OutputStream() {
                override fun write(value: Int) {
                    resultBytes.write(value)
                    written++
                    maybeReplace()
                }

                override fun write(bytes: ByteArray, offset: Int, length: Int) {
                    resultBytes.write(bytes, offset, length)
                    written += length.toLong()
                    maybeReplace()
                }

                private fun maybeReplace() {
                    if (!replaced && written > 16_384L) {
                        val staged = FilePublication.temporarySibling(summary)
                        staged.writeBytes(newRevision)
                        FilePublication.replace(staged, summary)
                        replaced = true
                    }
                }
            }
            val result = DiagnosticEvidenceArchive.writeDetailed(replacingOutput, listOf(dir))
            assertTrue("Test must republish during export", replaced)
            assertEquals(1, result.fileCount)
            assertTrue(result.warnings.toString(), result.snapshotConsistent)
            val entries = mutableMapOf<String, ByteArray>()
            ZipInputStream(resultBytes.toByteArray().inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries[entry.name] = zip.readBytes()
                    zip.closeEntry()
                }
            }
            assertArrayEquals(oldRevision, entries["logs/1/" + summary.name])
            assertArrayEquals(newRevision, summary.readBytes())
            val manifest = entries["EXPORT-MANIFEST.txt"]!!.toString(Charsets.UTF_8)
            assertTrue(manifest.contains("status=CONSISTENT_SNAPSHOT"))
            assertTrue(manifest.contains("note=ATOMIC_SUMMARY_REPUBLISHED"))
            assertFalse(manifest.contains("warning=SOURCE_CHANGED_DURING_EXPORT"))
        } finally {
            dir.deleteRecursively()
        }
    }
}
