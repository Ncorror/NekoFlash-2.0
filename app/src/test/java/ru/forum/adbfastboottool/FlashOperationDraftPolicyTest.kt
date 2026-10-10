package ru.forum.adbfastboottool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlashOperationDraftPolicyTest {
    private fun item(target: String, name: String = target) = FlashQueueDraftItem(
        partition = target,
        sourceUri = "file:///tmp/$name.img",
        displayName = "$name.img",
        expectedSizeBytes = 1L,
        addedAtEpochMs = 1L
    )

    @Test fun replacementKeepsExistingPositionAndFileChanges() {
        val original = FlashOperationDraft(listOf(item("boot_a"), item("system"), item("dtbo")))
        val changed = FlashOperationDraftPolicy.upsert(original, item("system", "system_new"))
        assertEquals(listOf("boot_a", "system", "dtbo"), changed.items.map { it.partition })
        assertEquals("system_new.img", changed.items[1].displayName)
    }

    @Test fun exactSlotTargetsAreDistinctAndExecutionOrderRemainsManual() {
        val original = FlashOperationDraft(listOf(item("vendor_boot_b"), item("boot_a"), item("boot_b")))
        val updated = FlashOperationDraftPolicy.upsert(original, item("boot_a", "new_a"))
        assertEquals(listOf("vendor_boot_b", "boot_a", "boot_b"), updated.items.map { it.partition })
        assertEquals("new_a.img", updated.items[1].displayName)
        assertEquals("boot_b.img", updated.items[2].displayName)
    }

    @Test fun moveAndRemoveAreStable() {
        val original = FlashOperationDraft(listOf(item("system"), item("boot"), item("dtbo")))
        val moved = FlashOperationDraftPolicy.move(original, "boot", -1)
        assertEquals(listOf("boot", "system", "dtbo"), moved.items.map { it.partition })
        assertEquals(moved, FlashOperationDraftPolicy.move(moved, "boot", -1))
        assertEquals(listOf("boot", "dtbo"), FlashOperationDraftPolicy.remove(moved, "system").items.map { it.partition })
    }

    @Test fun codecDoesNotSilentlyDropEntriesAfterThirtyTwo() {
        val items = (0 until 65).map { item("partition_$it") }
        val encoded = FlashOperationDraftCodec.encode(FlashOperationDraft(items))
        assertEquals(65, encoded.size)
        val restored = FlashOperationDraftCodec.decode(encoded)
        assertEquals(items.map { it.partition }, restored.items.map { it.partition })
        assertTrue(restored.items.all { it.displayName.endsWith(".img") })
    }
}
