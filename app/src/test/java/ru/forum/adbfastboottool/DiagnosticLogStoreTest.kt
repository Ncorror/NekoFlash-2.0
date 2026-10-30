package ru.forum.adbfastboottool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class DiagnosticLogStoreTest {
    @Test fun segmentsRemainAvailablePastLegacyLimit() {
        val folder = Files.createTempDirectory("nekoflash-log-test").toFile()
        try {
            val store = DiagnosticLogStore(
                logsDir = folder,
                stamp = "rev8-test",
                compactSegmentBytes = 64L * 1024L,
                traceSegmentBytes = 64L * 1024L
            )
            repeat(9) { index ->
                store.appendCompact("event$index:" + "x".repeat(70_000))
            }
            assertEquals(9, store.compactFiles().size)
            assertTrue(store.compactFiles().all { it.exists() })
            @Suppress("DEPRECATION")
            DiagnosticLogStore.pruneDirectory(folder, maxFiles = 1, maxTotalBytes = 1L)
            assertEquals(9, folder.listFiles { file -> file.name.startsWith("log-") }?.size)
        } finally {
            folder.deleteRecursively()
        }
    }


    @Test fun longProtocolTraceAndCompactEventsAreByteExactAcrossRotation() {
        val folder = Files.createTempDirectory("nekoflash-lossless-trace").toFile()
        try {
            val store = DiagnosticLogStore(
                logsDir = folder,
                stamp = "rev29-trace",
                compactSegmentBytes = 64L * 1024L,
                traceSegmentBytes = 64L * 1024L
            )
            val trace = buildString {
                repeat(140) { index ->
                    append("[fastboot-timing] phase=in-empty seq=").append(index)
                        .append(" serial=REAL-SERIAL-").append(index)
                        .append(" return=-1 elapsed_us=0 ")
                        .append("wire=").append("A".repeat(600)).append('\n')
                }
            }
            val events = buildString {
                repeat(140) { index ->
                    append("event-").append(index).append(" partition=system_a ")
                        .append("B".repeat(600)).append('\n')
                }
            }
            // Append in small independent blocks to model long-running sessions;
            // the source bytes, including serials and errors, are never redacted.
            trace.chunked(900).forEach(store::appendTrace)
            events.chunked(900).forEach(store::appendCompact)
            assertTrue(store.traceFiles().size >= 2)
            assertTrue(store.compactFiles().size >= 2)
            assertEquals(trace, store.traceFiles().joinToString("") { it.readText(Charsets.UTF_8) })
            assertEquals(events, store.compactFiles().joinToString("") { it.readText(Charsets.UTF_8) })
            @Suppress("DEPRECATION")
            DiagnosticLogStore.pruneDirectory(folder, maxFiles = 1, maxTotalBytes = 1)
            assertEquals(trace, store.traceFiles().joinToString("") { it.readText(Charsets.UTF_8) })
            assertEquals(events, store.compactFiles().joinToString("") { it.readText(Charsets.UTF_8) })
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test fun startupNeverDeletesPriorSessions() {
        val folder = Files.createTempDirectory("nekoflash-history").toFile()
        try {
            val older = folder.resolve("log-original-session.txt").apply {
                writeText("historical incident")
            }
            DiagnosticLogStore(folder, "fresh-session")
            assertTrue(older.exists())
            assertEquals("historical incident", older.readText())
        } finally {
            folder.deleteRecursively()
        }
    }
}
