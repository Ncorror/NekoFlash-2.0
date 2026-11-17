package ru.forum.adbfastboottool

import android.app.Application
import android.os.Looper
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MiUnlockCompletionTest {
    private fun finish(vm: DeviceViewModel, task: ServiceOwnedOperation,
        reported: DeviceViewModel.OperationOutcome, cause: Throwable?) {
        DeviceViewModel::class.java.getDeclaredMethod("finishServiceOperation", ServiceOwnedOperation::class.java,
            Long::class.javaPrimitiveType, DeviceViewModel.OperationOutcome::class.java, Throwable::class.java)
            .apply { isAccessible = true }.invoke(vm, task, 0L, reported, cause)
    }

    private fun seed(vm: DeviceViewModel, id: String) {
        DeviceViewModel::class.java.getDeclaredField("persistedOperation").apply { isAccessible = true }.set(vm,
            PersistedOperation(id, "Mi Unlock", "running", 1L, -1, PersistedOperationPhase.RUNNING))
    }

    @Test fun terminalReceiptSurvivesDetachAndIsDurableBeforeJobCleanup() = runBlocking {
        val app: Application = RuntimeEnvironment.getApplication()
        val vm = DeviceViewModel(app)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val message = app.getString(R.string.mi_unlock_success_detail)
        lateinit var task: ServiceOwnedOperation
        task = ServiceOwnedOperation("confirmed-unlock", "Mi Unlock", "test", true, {},
            { completed, cause -> finish(vm, completed, DeviceViewModel.OperationOutcome.Success, cause) },
            dispatcher = Dispatchers.Default) {
            vm.confirmOperationSuccess(task, message)
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        seed(vm, task.id)
        assertTrue(task.acknowledgeForeground())
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val persisted = OperationRecoveryStore(File(app.filesDir, "execution-state")).readOperation()!!
            assertEquals(PersistedOperationPhase.SUCCESS, persisted.phase)
            assertEquals(message, persisted.detail)
            assertFalse(task.job.isCompleted)
            task.cancel("USB detached during expected reboot")
        } finally { release.countDown() }
        withTimeout(5000) { task.finished.await() }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(DeviceViewModel.OperationOutcomeKind.SUCCESS, vm.operationProgress.value?.outcome)
        assertEquals(message, vm.operationProgress.value?.detail)
        assertEquals(PersistedOperationPhase.SUCCESS,
            OperationRecoveryStore(File(app.filesDir, "execution-state")).readOperation()?.phase)
    }

    @Test fun terminalReceiptAlsoSurvivesServiceLossAfterDeviceConfirmation() = runBlocking {
        val app: Application = RuntimeEnvironment.getApplication()
        val vm = DeviceViewModel(app)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        lateinit var task: ServiceOwnedOperation
        task = ServiceOwnedOperation("confirmed-service-loss", "Mi Unlock", "test", true, {},
            { completed, cause -> finish(vm, completed, DeviceViewModel.OperationOutcome.Success, cause) },
            dispatcher = Dispatchers.Default) {
            vm.confirmOperationSuccess(task, app.getString(R.string.mi_unlock_success_detail))
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        seed(vm, task.id)
        task.acknowledgeForeground()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            task.cancel("service destroyed after terminal response", serviceDestroyed = true)
        } finally { release.countDown() }
        withTimeout(5000) { task.finished.await() }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(DeviceViewModel.OperationOutcomeKind.SUCCESS, vm.operationProgress.value?.outcome)
    }

    @Test fun normalCancellationWithoutAReceiptStillCancels() = runBlocking {
        val vm = DeviceViewModel(RuntimeEnvironment.getApplication())
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val task = ServiceOwnedOperation("no-receipt", "Mi Unlock", "test", true, {},
            { completed, cause -> finish(vm, completed, DeviceViewModel.OperationOutcome.Success, cause) },
            dispatcher = Dispatchers.Default) {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        seed(vm, task.id)
        task.acknowledgeForeground()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            task.cancel("cancel before terminal reply")
        } finally { release.countDown() }
        withTimeout(5000) { task.finished.await() }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(DeviceViewModel.OperationOutcomeKind.CANCELLED, vm.operationProgress.value?.outcome)
    }

    @Test fun lostReplyStaysPendingAndPreservesItsReasonThroughDetach() = runBlocking {
        val vm = DeviceViewModel(RuntimeEnvironment.getApplication())
        val reason = "No final response to oem unlock"
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val task = ServiceOwnedOperation("lost-reply", "Mi Unlock", "test", true, {},
            { completed, cause -> finish(vm, completed, DeviceViewModel.OperationOutcome.VerifyPending(reason), cause) },
            dispatcher = Dispatchers.Default) {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        seed(vm, task.id)
        task.acknowledgeForeground()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            task.cancel("device restarted before response")
        } finally { release.countDown() }
        withTimeout(5000) { task.finished.await() }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(DeviceViewModel.OperationOutcomeKind.VERIFY_PENDING, vm.operationProgress.value?.outcome)
        assertEquals(reason, vm.operationProgress.value?.detail)
    }
}
