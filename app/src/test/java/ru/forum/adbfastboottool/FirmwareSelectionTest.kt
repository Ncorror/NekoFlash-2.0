package ru.forum.adbfastboottool

import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FirmwareSelectionTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun image() = temporary.newFile("образ с пробелами.img").apply { writeBytes(ByteArray(8192) { it.toByte() }) }

    @Test fun selectionCapturesActualSizeAndKeepsCanonicalFileUri() {
        val file = image()
        val selected = FirmwareSelection.from(FirmwareSource.WorkspaceFile(file)).verify()
        assertEquals(8192L, selected.expectedSizeBytes)
        assertEquals(file.canonicalFile, (selected.source() as FirmwareSource.WorkspaceFile).file)
        assertEquals(1, temporary.root.listFiles()!!.size)
    }
    @Test fun changedSizeFailsUntilExplicitReselection() {
        val file = image(); val selected = FirmwareSelection.from(FirmwareSource.WorkspaceFile(file)).verify()
        file.appendBytes(byteArrayOf(3))
        assertThrows(IOException::class.java) { selected.verify() }
        assertEquals(8193L, FirmwareSelection.from(FirmwareSource.WorkspaceFile(file)).verify().expectedSizeBytes)
    }
    @Test fun missingFileIsNotRecreatedByRestoration() {
        val file = image(); val selection = FirmwareSelection.from(FirmwareSource.WorkspaceFile(file)).verify()
        val directory = temporary.newFolder()
        OperationRecoveryStore(directory).writeQuickSource(selection)
        assertTrue(file.delete())
        val restored = OperationRecoveryStore(directory).readQuickSource()!!
        assertEquals(selection, restored)
        assertThrows(IOException::class.java) { restored.verify() }
        assertFalse(file.exists())
    }
    @Test fun unicodeAndSizeSurviveAtomicStoreReplacement() {
        val dir = temporary.newFolder(); val store = OperationRecoveryStore(dir)
        val selection = FirmwareSelection.from(FirmwareSource.WorkspaceFile(image())).verify()
        store.writeQuickSource(selection.copy(displayName = "первый.img"))
        store.writeQuickSource(selection)
        assertEquals(selection, OperationRecoveryStore(dir).readQuickSource())
        assertEquals(listOf("quick-source.properties"), dir.list()!!.toList())
        assertNull(store.readOperation())
    }
    @Test fun invalidMetadataCannotReplacePreviousSelection() {
        val store = OperationRecoveryStore(temporary.newFolder())
        val selection = FirmwareSelection.from(FirmwareSource.WorkspaceFile(image())).verify()
        store.writeQuickSource(selection)
        assertThrows(IOException::class.java) { store.writeQuickSource(selection.copy(sourceUri = "https://example.invalid/image")) }
        assertEquals(selection, store.readQuickSource())
    }
    @Test fun badSchemaAndTruncatedSelectionAreReported() {
        val dir = temporary.newFolder()
        for (text in listOf("schema=2\nuri=file:///tmp/a\nname=a", "schema=1\nname=a", "schema=1\nuri=file:///tmp/a\nname=a\nsize=-1")) {
            File(dir, "quick-source.properties").writeText(text)
            assertThrows(IOException::class.java) { OperationRecoveryStore(dir).readQuickSource() }
        }
    }
    @Test fun queueCodecPreservesMixedUriSchemesAndExactTargets() {
        val local = FirmwareSelection.from(FirmwareSource.WorkspaceFile(image())).verify().queueItem("boot_b")
        val document = FirmwareSelection("content://images/tree/1/document/A%20B", "образ.img", 9000).queueItem("Custom_a")
        val draft = FlashOperationDraft(listOf(local, document))
        assertEquals(draft.items, FlashOperationDraftCodec.decode(FlashOperationDraftCodec.encode(draft)).items)
    }
    @Test fun cancellationDuringProbeClosesTheFileWithoutPublishingAnExecution() {
        val file = image(); var checks = 0
        val selection = FirmwareSelection.from(FirmwareSource.WorkspaceFile(file))
        assertThrows(InterruptedException::class.java) { selection.verify { if (++checks == 2) throw InterruptedException() } }
        assertEquals(2, checks)
        assertEquals(8192L, selection.verify().expectedSizeBytes)
    }
}
