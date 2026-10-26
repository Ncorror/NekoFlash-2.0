package ru.forum.adbfastboottool

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.UUID
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Framework lifecycle simulation; no claim of physical USB or OEM power-manager coverage. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34])
class ForegroundServiceLifecycleTest {
    class SessionScreen : ComponentActivity() {
        lateinit var session: DeviceViewModel
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            session = DeviceSessionStore.attachUi(application)
        }
        override fun onDestroy() {
            DeviceSessionStore.detachUi()
            super.onDestroy()
        }
    }

    private val app: Application get() = RuntimeEnvironment.getApplication()
    private fun intent(task: ServiceOwnedOperation) = Intent(app, FlashOperationService::class.java)
        .putExtra("operation_id", task.id)
    private fun operation(body: () -> Unit) = ServiceOwnedOperation(
        UUID.randomUUID().toString(), "flash", "boot.img", true, {},
        { task, _ -> FlashOperationService.complete(task.id) }
    ) { body() }
    private fun awaitFinished(task: ServiceOwnedOperation) = runBlocking {
        withTimeout(5000) { task.finished.await() }
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Suppress("DEPRECATION") // API 26 has no PermissionInfo.getProtection().
    @Before fun grantMergedSignaturePermissionInSimulator() {
        if (Build.VERSION.SDK_INT < 33) {
            // Robolectric #9124: the simulator does not grant this merged
            // AndroidX signature permission automatically on API 26–32.
            // Verify the production manifest first; never hide a missing entry.
            val permission = "${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
            val info = app.packageManager.getPermissionInfo(permission, 0)
            assertEquals(PermissionInfo.PROTECTION_SIGNATURE,
                info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE)
            assertTrue(app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions.orEmpty().contains(permission))
            shadowOf(app).grantPermissions(permission)
        }
    }

    @After fun clearSession() {
        DeviceSessionStore.viewModelStore.clear()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun foregroundPromotionFailurePreventsAnyExecution() {
        val calls = AtomicInteger()
        val task = operation { calls.incrementAndGet() }
        FlashOperationService.submit(app, task)
        val controller = Robolectric.buildService(FlashOperationService::class.java).create()
        val service = controller.get()
        shadowOf(service).setThrowInStartForeground(SecurityException("permission denied"))
        try {
            assertEquals(android.app.Service.START_NOT_STICKY, service.onStartCommand(intent(task), 0, 1))
            awaitFinished(task)
            assertEquals(0, calls.get())
            assertNotNull(task.startupFailure)
            assertTrue(shadowOf(service).isStoppedBySelf)
        } finally { controller.destroy() }
    }

    @Test fun serviceCreationAloneDoesNotAcknowledgeExecution() {
        val calls = AtomicInteger()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val task = operation {
            calls.incrementAndGet()
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        FlashOperationService.submit(app, task)
        val controller = Robolectric.buildService(FlashOperationService::class.java).create()
        try {
            assertEquals(0, calls.get())
            assertFalse(task.job.isActive)
            controller.get().onStartCommand(intent(task), 0, 1)
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertEquals(1, calls.get())
            assertNotNull(shadowOf(controller.get()).lastForegroundNotification)
        } finally {
            release.countDown()
            awaitFinished(task)
            controller.destroy()
        }
    }

    @Test fun realViewModelPersistsPromotionFailureBeforeCommandExecution() {
        val session = DeviceSessionStore.get(app)
        session.runFastbootCommand("getvar:product")
        val start = requireNotNull(shadowOf(app).nextStartedService)
        val journal = OperationRecoveryStore(File(app.filesDir, "execution-state"))
        val pending = requireNotNull(journal.readOperation())
        assertEquals(PersistedOperationPhase.PREPARING, pending.phase)
        val controller = Robolectric.buildService(FlashOperationService::class.java).create()
        val service = controller.get()
        shadowOf(service).setThrowInStartForeground(SecurityException("test promotion refusal"))
        try {
            service.onStartCommand(start, 0, 1)
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse(session.operationActive.value!!)
            val progress = requireNotNull(session.operationProgress.value)
            assertEquals(DeviceViewModel.OperationOutcomeKind.FAILED, progress.outcome)
            assertTrue(progress.detail.contains("test promotion refusal"))
            val final = requireNotNull(journal.readOperation())
            assertEquals(pending.id, final.id)
            assertEquals(PersistedOperationPhase.FAILED, final.phase)
            // Executing the command body would instead report a missing USB
            // protocol. The admission failure must win before that body starts.
            assertTrue(final.detail.contains("test promotion refusal"))
        } finally { controller.destroy() }
    }

    @Test fun realViewModelRecreationRestoresDraftWithoutSubmittingWork() {
        val first = DeviceSessionStore.get(app)
        val image = File(app.filesDir, "queue image.img").apply { writeText("test image") }
        first.addFlashQueueFile("boot_b", image)
        first.addFlashQueueFile("boot_a", image)
        DeviceSessionStore.viewModelStore.clear()
        val restored = DeviceSessionStore.get(app)
        assertNotSame(first, restored)
        assertEquals(listOf("boot_b", "boot_a"), restored.currentFlashOperationDraft().items.map { it.partition })
        assertNull(shadowOf(app).nextStartedService)
        assertFalse(restored.operationActive.value!!)
    }

    @Test fun realViewModelCompletionLeavesDismissibleResultNotification() {
        val session = DeviceSessionStore.get(app)
        session.runFastbootCommand("getvar:product")
        val start = requireNotNull(shadowOf(app).nextStartedService)
        val controller = Robolectric.buildService(FlashOperationService::class.java).create()
        val service = controller.get()
        try {
            service.onStartCommand(start, 0, 1)
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (session.operationProgress.value?.finished != true) {
                check(System.nanoTime() < deadline) { "Operation did not complete" }
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(10)
            }
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse(session.operationActive.value!!)
            assertEquals(DeviceViewModel.OperationOutcomeKind.FAILED, session.operationProgress.value!!.outcome)
            val notification = requireNotNull(shadowOf(app.getSystemService(NotificationManager::class.java))
                .getNotification(1401))
            assertEquals(0, notification.flags and Notification.FLAG_ONGOING_EVENT)
            assertNotEquals(0, notification.flags and Notification.FLAG_AUTO_CANCEL)
            assertTrue(shadowOf(service).isStoppedBySelf)
        } finally { controller.destroy() }
    }

    @Test fun destroyingScreenKeepsServiceTaskAndSharedSessionAlive() {
        val screen = Robolectric.buildActivity(SessionScreen::class.java).setup()
        val shared = screen.get().session
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val task = operation { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
        FlashOperationService.submit(app, task)
        val controller = Robolectric.buildService(FlashOperationService::class.java).create()
        try {
            controller.get().onStartCommand(intent(task), 0, 1)
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            screen.pause().stop().destroy()
            assertTrue(task.job.isActive)
            val replacement = Robolectric.buildActivity(SessionScreen::class.java).setup()
            assertSame(shared, replacement.get().session)
            replacement.pause().stop().destroy()
        } finally {
            release.countDown()
            awaitFinished(task)
            controller.destroy()
        }
    }

    @Test fun serviceDestructionCancelsButDoesNotDeclareBlockingWorkFinished() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val task = operation { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
        FlashOperationService.submit(app, task)
        val controller = Robolectric.buildService(FlashOperationService::class.java).create()
        try {
            controller.get().onStartCommand(intent(task), 0, 1)
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            controller.destroy()
            assertTrue(task.serviceLost)
            assertTrue(task.job.isCancelled)
            assertFalse(task.finished.isCompleted)
        } finally {
            release.countDown()
            awaitFinished(task)
        }
    }

    @Test fun lateStartOfCancelledTokenCannotStartOrStopTheNextTask() {
        val stale = operation { error("Cancelled request must never run") }
        FlashOperationService.submit(app, stale)
        stale.cancel("cancel before arrival")
        awaitFinished(stale)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val current = operation { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
        FlashOperationService.submit(app, current)
        val controller = Robolectric.buildService(FlashOperationService::class.java).create()
        val service = controller.get()
        try {
            service.onStartCommand(intent(stale), 0, 1)
            assertFalse(current.job.isActive)
            service.onStartCommand(intent(current), 0, 2)
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            service.onStartCommand(intent(stale), 0, 3)
            assertTrue(current.job.isActive)
            assertFalse(shadowOf(service).isStoppedBySelf)
        } finally {
            release.countDown()
            awaitFinished(current)
            controller.destroy()
        }
    }
}
