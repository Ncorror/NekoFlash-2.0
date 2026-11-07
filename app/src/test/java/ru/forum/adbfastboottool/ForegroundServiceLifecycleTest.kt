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
import java.time.Duration
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
    private fun operation(id: String = UUID.randomUUID().toString(), body: () -> Unit) = ServiceOwnedOperation(
        id, "flash", "boot.img", true, {},
        { task, _ -> FlashOperationService.complete(task.id) }
    ) { body() }

    /**
     * Exercise service lifecycle independently of USB admission. Robolectric
     * has no real USB device to authorize; injecting an admitted task into the
     * real service is what the lifecycle tests have always intended to test.
     */
    private fun seededState(session: DeviceViewModel, record: PersistedOperation) {
        val field = DeviceViewModel::class.java.getDeclaredField("persistedOperation")
        field.isAccessible = true
        field.set(session, record)
    }
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

    @Test fun realViewModelNoUsbFailsAdmissionWithoutStartingServiceOrPersistingOperation() {
        val session = DeviceSessionStore.get(app)
        val journal = OperationRecoveryStore(File(app.filesDir, "execution-state"))
        val previous = journal.readOperation()
        session.runFastbootCommand("getvar:product")
        shadowOf(Looper.getMainLooper()).idle()

        // No physical USB and no real Fastboot protocol: never start a
        // connectedDevice FGS. No PREPARING checkpoint or fake result card.
        assertNull(shadowOf(app).nextStartedService)
        assertEquals(previous, journal.readOperation())
        assertFalse(session.operationActive.value!!)
        assertTrue(session.logSnapshot().any { it.contains("Command was not sent.") })
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

    @Test fun serviceCompletionLeavesDismissibleResultNotificationForAnAdmittedOperation() {
        // A simulated *admitted* service task verifies the notification
        // lifecycle. This does not pretend that Robolectric has a USB target.
        val id = UUID.randomUUID().toString()
        val journal = OperationRecoveryStore(File(app.filesDir, "execution-state"))
        val final = PersistedOperation(id, "Fastboot operation", "Transport unavailable",
            System.currentTimeMillis(), -1, PersistedOperationPhase.FAILED)
        journal.writeOperation(final)
        val session = DeviceSessionStore.get(app)
        assertEquals(final, session.lastPersistedOperation(id))
        val task = operation(body = {}, id = id)
        FlashOperationService.submit(app, task)
        val start = requireNotNull(shadowOf(app).nextStartedService)
        val controller = Robolectric.buildService(FlashOperationService::class.java).create()
        val service = controller.get()
        try {
            service.onStartCommand(start, 0, 1)
            awaitFinished(task)
            assertEquals(PersistedOperationPhase.FAILED, journal.readOperation()?.phase)
            val notification = requireNotNull(shadowOf(app.getSystemService(NotificationManager::class.java))
                .getNotification(1401))
            assertEquals(0, notification.flags and Notification.FLAG_ONGOING_EVENT)
            assertNotEquals(0, notification.flags and Notification.FLAG_AUTO_CANCEL)
            assertTrue(shadowOf(service).isStoppedBySelf)
        } finally { controller.destroy() }
    }

    @Test fun nextRequestTimeoutStopsCompletedPreviousService() {
        val session = DeviceSessionStore.get(app)
        val first = operation(body = {})
        FlashOperationService.submit(app, first)
        val firstStart = requireNotNull(shadowOf(app).nextStartedService)
        val controller = Robolectric.buildService(FlashOperationService::class.java).create()
        val service = controller.get()
        try {
            service.onStartCommand(firstStart, 0, 1)
            // Do not drain the main queue yet: preserve the real scenario in
            // which an old task finished while a new FGS start was pending.
            runBlocking { withTimeout(5000) { first.finished.await() } }

            val id = UUID.randomUUID().toString()
            val journal = OperationRecoveryStore(File(app.filesDir, "execution-state"))
            val pending = PersistedOperation(id, "Next admitted operation", "Preparing",
                System.currentTimeMillis(), -1, PersistedOperationPhase.PREPARING)
            journal.writeOperation(pending)
            seededState(session, pending)
            val next = ServiceOwnedOperation(
                id, "Next admitted operation", "Preparing", true, {},
                { task, _ ->
                    val result = pending.copy(phase = PersistedOperationPhase.FAILED,
                        detail = task.startupFailure ?: "Service start failed")
                    journal.writeOperation(result)
                    seededState(session, result)
                    FlashOperationService.complete(task.id)
                }
            ) { error("Unacknowledged FGS task must never execute") }
            FlashOperationService.submit(app, next)
            val nextStart = requireNotNull(shadowOf(app).nextStartedService)
            assertEquals(next.id, nextStart.getStringExtra("operation_id"))
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse(shadowOf(service).isStoppedBySelf)
            // The second start intent is intentionally never delivered.
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(11))
            runBlocking { withTimeout(5000) { next.finished.await() } }
            shadowOf(Looper.getMainLooper()).idle()
            assertNotNull(next.startupFailure)
            val result = requireNotNull(journal.readOperation())
            assertEquals(next.id, result.id)
            assertEquals(PersistedOperationPhase.FAILED, result.phase)
            assertTrue(shadowOf(service).isStoppedBySelf)
            val notification = requireNotNull(shadowOf(app.getSystemService(NotificationManager::class.java))
                .getNotification(1401))
            assertEquals(result.detail, notification.extras.getString(Notification.EXTRA_TEXT))
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
