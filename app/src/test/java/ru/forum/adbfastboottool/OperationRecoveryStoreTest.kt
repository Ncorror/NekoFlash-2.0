package ru.forum.adbfastboottool

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class OperationRecoveryStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun record(phase: PersistedOperationPhase) = PersistedOperation(
        "attempt-1", "Прошивка boot_a", "52% · файл с пробелами\nпередано 12345 B", 123456L, 52, phase)

    @Test fun runningStateBecomesInterruptedAfterProcessDeath() {
        assertEquals(PersistedOperationPhase.INTERRUPTED, record(PersistedOperationPhase.RUNNING).afterProcessRestart().phase)
    }
    @Test fun pendingStartIsNeverResumedAfterRestart() {
        assertEquals(PersistedOperationPhase.INTERRUPTED, record(PersistedOperationPhase.PREPARING).afterProcessRestart().phase)
    }
    @Test fun completedAndUnverifiedResultsKeepTheirMeaning() {
        listOf(PersistedOperationPhase.SUCCESS, PersistedOperationPhase.FAILED, PersistedOperationPhase.CANCELLED,
            PersistedOperationPhase.VERIFY_PENDING, PersistedOperationPhase.INTERRUPTED).forEach {
            assertEquals(record(it), record(it).afterProcessRestart())
        }
    }
    @Test fun interruptionRecoveryIsIdempotent() {
        val restored = record(PersistedOperationPhase.RUNNING).afterProcessRestart()
        assertEquals(restored, restored.afterProcessRestart())
    }
    @Test fun unicodeAndMultilineCheckpointSurvivesStoreRecreation() {
        val dir = temporary.newFolder()
        val expected = record(PersistedOperationPhase.RUNNING)
        OperationRecoveryStore(dir).writeOperation(expected)
        assertEquals(expected, OperationRecoveryStore(dir).readOperation())
        assertEquals(1, dir.listFiles()!!.size)
    }
    @Test fun finalCheckpointReplacesTheRunningRecord() {
        val store = OperationRecoveryStore(temporary.newFolder())
        store.writeOperation(record(PersistedOperationPhase.RUNNING))
        val complete = record(PersistedOperationPhase.SUCCESS).copy(percent = 100)
        store.writeOperation(complete)
        assertEquals(complete, store.readOperation())
    }
    @Test fun missingCheckpointDoesNotInventAnOperation() {
        assertNull(OperationRecoveryStore(temporary.newFolder()).readOperation())
    }
    @Test(expected = IOException::class)
    fun truncatedCheckpointCannotBecomeSuccess() {
        val dir = temporary.newFolder()
        File(dir, "last-operation.properties").writeText("schema=1\nphase=SUCCESS\n")
        OperationRecoveryStore(dir).readOperation()
    }
    @Test(expected = IOException::class)
    fun unsupportedSchemaIsReported() {
        val dir = temporary.newFolder()
        File(dir, "last-operation.properties").writeText("schema=99\n")
        OperationRecoveryStore(dir).readOperation()
    }
    @Test fun orderedQueueSurvivesStoreRecreationWithoutAnExecutor() {
        val dir = temporary.newFolder()
        val image = temporary.newFile("boot image.img").apply { writeText("payload") }
        val first = FlashQueueDraftItem("boot_b", image.toURI().toString(), image.name, image.length(), 123456)
        val second = first.copy(partition = "boot_a")
        val draft = FlashOperationDraft(listOf(first, second))
        OperationRecoveryStore(dir).writeDraft(draft)
        val restored = OperationRecoveryStore(dir).readDraft()!!
        assertEquals(listOf("boot_b", "boot_a"), restored.items.map { it.partition })
        assertEquals(draft.items, restored.items)
    }
    @Test fun clearingDraftPersistsAnEmptyQueue() {
        val store = OperationRecoveryStore(temporary.newFolder())
        store.writeDraft(FlashOperationDraft())
        assertTrue(store.readDraft()!!.items.isEmpty())
    }
}
