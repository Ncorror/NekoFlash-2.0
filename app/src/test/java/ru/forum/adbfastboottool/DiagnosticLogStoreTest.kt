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
                traceSegmentBytes = 64L * 1024L,
                maxSegmentsPerStream = 2
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
