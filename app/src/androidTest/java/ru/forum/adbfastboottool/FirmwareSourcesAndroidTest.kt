package ru.forum.adbfastboottool

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.widget.Spinner
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirmwareSourcesAndroidTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val resolver get() = context.contentResolver
    private val uri = Uri.parse("content://ru.forum.adbfastboottool.dev.test.images/image")
    private fun source() = FirmwareSource.SafDocument(uri, "provider image.img", 1)
    @Before fun resetProvider() { resolver.call(uri, "reset", null, null) }

    @Test fun realProviderDescriptorSupportsProbeReadsAndResparseWithoutFullCopy() {
        val selection = FirmwareSelection.from(source()).verify(resolver)
        assertEquals(8192L, selection.expectedSizeBytes)
        val work = File(context.cacheDir, "saf-packets").also { it.mkdirs() }
        val reconstructed = ByteArray(8192) { 0x5a }
        val opened = selection.source().open(resolver)
        try {
            assertNotNull(opened.nativeFd)
            val plan = FastbootFlashImage.prepare(opened, 5000)
            assertEquals(2L, plan.partCount)
            assertTrue(plan.forEachPart(work) { file, _, _ ->
                assertEquals(1, work.listFiles()!!.size)
                val data = file.readBytes(); val header = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                assertTrue(data.size <= 5000)
                var pos = header.getShort(8).toInt(); var output = 0
                repeat(header.getInt(20)) {
                    val kind = header.getShort(pos).toInt() and 0xffff
                    val bytes = header.getInt(pos + 4) * 4096
                    when (kind) {
                        0xcac1 -> data.copyInto(reconstructed, output, pos + 12, pos + 12 + bytes)
                        0xcac3 -> Unit
                        else -> error("Unexpected raw resparse chunk")
                    }
                    output += bytes; pos += header.getInt(pos + 8)
                }
                assertEquals(8192, output)
                true
            })
            assertArrayEquals(ByteArray(8192) { (it % 251).toByte() }, reconstructed)
            assertTrue(work.listFiles()!!.isEmpty())
        } finally { opened.close(); work.deleteRecursively() }
        assertThrows(IOException::class.java) { opened.readFullyAt(0, ByteArray(1)) }
    }
    @Test fun pipeIsRejectedAsANonSeekableImage() {
        val pipe = source().copy(uri = uri.buildUpon().path("pipe").build())
        assertThrows(IOException::class.java) { FirmwareSelection.from(pipe).verify(resolver) }
    }
    @Test fun persistedMetadataDoesNotBypassProviderAccessDenial() {
        val selection = FirmwareSelection.from(source()).verify(resolver)
        val work = File(context.cacheDir, "saf-selection-state").also { it.mkdirs() }
        try {
            OperationRecoveryStore(work).writeQuickSource(selection)
            resolver.call(uri, "deny", null, null)
            val restored = OperationRecoveryStore(work).readQuickSource()!!
            assertEquals(selection, restored)
            assertThrows(SecurityException::class.java) { restored.verify(resolver) }
            assertNull(OperationRecoveryStore(work).readOperation())
        } finally { work.deleteRecursively() }
    }
    @Test fun changedDocumentSizeRequiresExplicitReselection() {
        val selection = FirmwareSelection.from(source()).verify(resolver)
        resolver.call(uri, "resize", "8193", null)
        assertThrows(IOException::class.java) { selection.verify(resolver) }
        assertEquals(8193L, FirmwareSelection.from(source()).verify(resolver).expectedSizeBytes)
    }
    @Test fun mixedFileAndSafQueueKeepsExactOrderAndReopensAfterStoreRecreation() {
        val work = File(context.cacheDir, "saf-queue-state").also { it.mkdirs() }
        try {
            val file = File(work, "local.img").apply { writeBytes(ByteArray(512)) }
            val local = FirmwareSelection.from(FirmwareSource.WorkspaceFile(file)).verify().queueItem("boot_b")
            val document = FirmwareSelection.from(source()).verify(resolver).queueItem("boot_a")
            OperationRecoveryStore(work).writeDraft(FlashOperationDraft(listOf(local, document)))
            val restored = OperationRecoveryStore(work).readDraft()!!
            assertEquals(listOf(local, document), restored.items)
            restored.items.forEach { assertEquals(it.expectedSizeBytes, FirmwareSelection.from(it).verify(resolver).expectedSizeBytes) }
        } finally { work.deleteRecursively() }
    }
    @Test fun realQuickScreenRestoresImageSlotAndPendingPickerTarget() {
        instrumentation.runOnMainSync { OnboardingGate.complete(); DeviceSessionStore.viewModelStore.clear() }
        val image = File(context.filesDir, "selected-for-recreation.img").apply { writeBytes(ByteArray(8192)) }
        val vm = arrayOfNulls<DeviceViewModel>(1)
        fun waitForCheck() {
            val deadline = System.nanoTime() + 10_000_000_000L
            while (vm[0]!!.quickFlashSource.value?.checking != false) {
                check(System.nanoTime() < deadline) { "Source check did not finish" }
                Thread.sleep(10)
            }
            instrumentation.waitForIdleSync()
        }
        fun screenshot(name: String) {
            val directory = File(context.filesDir, "source-test-output").also { it.mkdirs() }
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try { File(directory, name).outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
            finally { bitmap.recycle() }
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    vm[0] = DeviceSessionStore.get(context.applicationContext as Application)
                    vm[0]!!.selectQuickFlashSource(FirmwareSource.WorkspaceFile(image))
                    activity.findViewById<View>(R.id.tabFastboot).performClick()
                    activity.findViewById<Spinner>(R.id.spinQuickPartition).setSelection(2)
                    activity.findViewById<Spinner>(R.id.spinQuickSlot).setSelection(3)
                    MainActivity::class.java.getDeclaredField("pendingMassPartition").apply { isAccessible = true }
                        .set(activity, "vendor_boot_b")
                }
                instrumentation.waitForIdleSync(); waitForCheck()
                scenario.recreate()
                waitForCheck()
                scenario.onActivity { activity ->
                    assertTrue(activity.findViewById<TextView>(R.id.tvQuickSelectedImage).text.contains(image.name))
                    assertTrue(activity.findViewById<View>(R.id.btnQuickExecute).isEnabled)
                    assertEquals(2, activity.findViewById<Spinner>(R.id.spinQuickPartition).selectedItemPosition)
                    assertEquals(3, activity.findViewById<Spinner>(R.id.spinQuickSlot).selectedItemPosition)
                    assertEquals("vendor_boot_b", MainActivity::class.java.getDeclaredField("pendingMassPartition")
                        .apply { isAccessible = true }.get(activity))
                    assertFalse(vm[0]!!.operationActive.value!!)
                }
                screenshot("quick-restored.png")
                assertTrue(image.delete())
                scenario.onActivity { vm[0]!!.refreshQuickFlashSource() }
                instrumentation.waitForIdleSync(); waitForCheck()
                scenario.onActivity { activity ->
                    assertFalse(activity.findViewById<View>(R.id.btnQuickExecute).isEnabled)
                    assertNotNull(vm[0]!!.quickFlashSource.value!!.error)
                }
                screenshot("quick-unavailable.png")
            }
        } finally {
            image.delete()
            instrumentation.runOnMainSync { DeviceSessionStore.viewModelStore.clear() }
        }
    }
}
