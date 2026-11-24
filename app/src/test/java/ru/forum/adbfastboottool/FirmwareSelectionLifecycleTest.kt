package ru.forum.adbfastboottool

import android.app.Application
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34])
class FirmwareSelectionLifecycleTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    class Screen : ComponentActivity() {
        lateinit var session: DeviceViewModel
        internal var seen: QuickFlashSourceState? = null
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            session = DeviceSessionStore.attachUi(application)
            session.quickFlashSource.observe(this) { seen = it }
        }
        override fun onDestroy() { DeviceSessionStore.detachUi(); super.onDestroy() }
    }
    private fun image(name: String = "selected.img") = File(app.filesDir, name).apply { writeBytes(ByteArray(8192) { 7 }) }
    @Suppress("DEPRECATION")
    @Before fun grantMergedPermissionOnOldRobolectric() {
        RobolectricStorageFixture.installPrimaryExternalDirectory()
        if (Build.VERSION.SDK_INT < 33) {
            val permission = "${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
            val info = app.packageManager.getPermissionInfo(permission, 0)
            assertEquals(PermissionInfo.PROTECTION_SIGNATURE, info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE)
            assertTrue(app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions.orEmpty().contains(permission))
            shadowOf(app).grantPermissions(permission)
        }
    }
    private fun finishReads(vm: DeviceViewModel, drainUi: Boolean = true) {
        val field = DeviceViewModel::class.java.getDeclaredField("transportScope").apply { isAccessible = true }
        val scope = field.get(vm) as CoroutineScope
        runBlocking { withTimeout(5000) { scope.coroutineContext[Job]!!.children.toList().joinAll() } }
        if (drainUi) shadowOf(Looper.getMainLooper()).idle()
    }
    @After fun clearSession() {
        DeviceSessionStore.viewModelStore.clear()
        shadowOf(Looper.getMainLooper()).idle()
    }
    @Test fun recreatedScreenObservesTheSameVerifiedSelection() {
        val first = Robolectric.buildActivity(Screen::class.java).setup()
        val vm = first.get().session
        vm.selectQuickFlashSource(FirmwareSource.WorkspaceFile(image()))
        finishReads(vm)
        val expected = first.get().seen!!.selection
        first.pause().stop().destroy()
        val next = Robolectric.buildActivity(Screen::class.java).setup()
        try {
            assertSame(vm, next.get().session)
            assertEquals(expected, next.get().seen!!.selection)
            assertTrue(next.get().seen!!.ready)
            assertNull(shadowOf(app).nextStartedService)
        } finally { next.pause().stop().destroy() }
    }
    @Test fun newProcessSessionReopensSelectionWithoutSubmittingAnOperation() {
        val first = DeviceSessionStore.get(app)
        first.selectQuickFlashSource(FirmwareSource.WorkspaceFile(image()))
        finishReads(first)
        val expected = first.currentQuickFlashSelection()
        DeviceSessionStore.viewModelStore.clear()
        val restored = DeviceSessionStore.get(app)
        finishReads(restored)
        assertEquals(expected, restored.currentQuickFlashSelection())
        assertFalse(restored.operationActive.value!!)
        assertNull(shadowOf(app).nextStartedService)
    }
    @Test fun changedFileAfterRestartKeepsNameButCannotBecomeReady() {
        val vm = DeviceSessionStore.get(app); val file = image()
        vm.selectQuickFlashSource(FirmwareSource.WorkspaceFile(file)); finishReads(vm)
        DeviceSessionStore.viewModelStore.clear(); file.appendText("changed")
        val restored = DeviceSessionStore.get(app); finishReads(restored)
        assertNull(restored.currentQuickFlashSelection())
        assertEquals(file.name, restored.quickFlashSource.value!!.selection!!.displayName)
        assertNotNull(restored.quickFlashSource.value!!.error)
        assertNull(shadowOf(app).nextStartedService)
    }
    @Test fun returningSourceCanBeRevalidatedWithoutDiscardingItsMetadata() {
        val vm = DeviceSessionStore.get(app); val file = image()
        vm.selectQuickFlashSource(FirmwareSource.WorkspaceFile(file)); finishReads(vm)
        assertTrue(file.delete()); vm.refreshQuickFlashSource(); finishReads(vm)
        assertNull(vm.currentQuickFlashSelection())
        image(); vm.refreshQuickFlashSource(); finishReads(vm)
        assertTrue(vm.quickFlashSource.value!!.ready)
        assertNull(shadowOf(app).nextStartedService)
    }
    @Test fun oldQueuedCallbackCannotRestoreAnEarlierQuickChoice() {
        val vm = DeviceSessionStore.get(app)
        vm.selectQuickFlashSource(FirmwareSource.WorkspaceFile(image("old.img")))
        finishReads(vm, drainUi = false)
        vm.selectQuickFlashSource(FirmwareSource.WorkspaceFile(image("new.img")))
        finishReads(vm)
        assertEquals("new.img", vm.quickFlashSource.value!!.selection!!.displayName)
        assertEquals("new.img", OperationRecoveryStore(File(app.filesDir, "execution-state")).readQuickSource()!!.displayName)
    }
    @Test fun removingPendingQueueSourceCannotResurrectItOrStartWork() {
        val vm = DeviceSessionStore.get(app)
        vm.addFlashQueueSource("boot_a", FirmwareSource.WorkspaceFile(image()))
        vm.executeFlashQueueDraft()
        assertNull(shadowOf(app).nextStartedService)
        vm.removeFlashQueueDraftItem("boot_a")
        finishReads(vm)
        assertTrue(vm.currentFlashOperationDraft().isEmpty)
        assertTrue(vm.queueSourceState.value!!.pending.isEmpty())
    }
    @Test fun laterWorkspaceChoiceWinsOverPendingProviderStyleSelection() {
        val vm = DeviceSessionStore.get(app)
        vm.addFlashQueueSource("boot_b", FirmwareSource.WorkspaceFile(image("old.img")))
        vm.addFlashQueueFile("boot_b", image("new.img"))
        finishReads(vm)
        assertEquals("new.img", vm.currentFlashOperationDraft().items.single().displayName)
        assertTrue(vm.queueSourceState.value!!.pending.isEmpty())
    }

    @Test fun multipleSelectionsKeepOrderAndReplaceAnExistingTargetInPlace() {
        val vm = DeviceSessionStore.get(app)
        vm.addFlashQueueFile("vendor_boot", image("old-vendor.img"))
        vm.addFlashQueueSources(listOf(
            "dtbo" to FirmwareSource.WorkspaceFile(image("dtbo.img")),
            "vendor_boot" to FirmwareSource.WorkspaceFile(image("new-vendor.img")),
            "boot_a" to FirmwareSource.WorkspaceFile(image("boot.img"))
        ))
        finishReads(vm)
        val items = vm.currentFlashOperationDraft().items
        assertEquals(listOf("vendor_boot", "dtbo", "boot_a"), items.map { it.partition })
        assertEquals(listOf("new-vendor.img", "dtbo.img", "boot.img"), items.map { it.displayName })
        assertEquals(items, OperationRecoveryStore(File(app.filesDir, "execution-state")).readDraft()?.items)
        assertTrue(vm.queueSourceState.value!!.pending.isEmpty())
        assertNull(shadowOf(app).nextStartedService)
    }

    @Test fun aBatchDoesNotReviveRemovedRowsOrUndoANewerChoice() {
        val vm = DeviceSessionStore.get(app)
        vm.addFlashQueueSources(listOf(
            "recovery" to FirmwareSource.WorkspaceFile(image("recovery.img")),
            "boot" to FirmwareSource.WorkspaceFile(image("old-boot.img")),
            "dtbo" to FirmwareSource.WorkspaceFile(File(app.filesDir, "missing.img"))
        ))
        vm.removeFlashQueueDraftItem("recovery")
        vm.addFlashQueueFile("boot", image("new-boot.img"))
        finishReads(vm)
        assertEquals("new-boot.img", vm.currentFlashOperationDraft().items.single().displayName)
        assertTrue(vm.queueSourceState.value!!.pending.isEmpty())
        assertNotNull(vm.queueSourceState.value!!.error)
    }
}
