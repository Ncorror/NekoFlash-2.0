package ru.forum.adbfastboottool

import android.net.Uri
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalTerminalAdmissionTest {
    @Before fun installPrimaryExternalDirectory() {
        RobolectricStorageFixture.installPrimaryExternalDirectory()
    }

    @Test fun localCancellationKeepsUsbAndWorkspaceBlockedUntilCleanupFinishes() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        val vm = DeviceViewModel(app)
        val entered = CompletableDeferred<Unit>()
        val cleaning = CompletableDeferred<Unit>()
        val finishCleanup = CompletableDeferred<Unit>()
        try {
            assertEquals(DeviceViewModel.LocalTerminalAdmission.STARTED, vm.launchLocalTerminalCommand(this) {
                try {
                    entered.complete(Unit)
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) {
                        cleaning.complete(Unit)
                        finishCleanup.await()
                    }
                }
            })
            val job = coroutineContext[Job]!!.children.single()
            withTimeout(5_000) { entered.await() }

            vm.cancelLocalTerminalCommand()
            withTimeout(5_000) { cleaning.await() }
            assertFalse(job.isCompleted)
            assertEquals(DeviceViewModel.LocalTerminalAdmission.LOCAL_BUSY,
                vm.launchLocalTerminalCommand(this) { fail("Local command started during cleanup") })

            vm.runFastbootCommand("getvar product", heavy = false)
            assertEquals(app.getString(R.string.local_terminal_usb_blocked), vm.logSnapshot().last())
            assertFalse(vm.runWorkspaceMaintenanceIfIdle { fail("Workspace cleared during cleanup") })
            assertFalse(vm.importWorkspaceFile(Uri.parse("content://fixture/image"), File(app.filesDir, "workspace")))

            finishCleanup.complete(Unit)
            withTimeout(5_000) { job.join() }
            assertTrue(vm.runWorkspaceMaintenanceIfIdle { })
        } finally {
            finishCleanup.complete(Unit)
            vm.cancelLocalTerminalCommand()
        }
    }

    @Test fun usbReservationRejectsLocalCommandAndIgnoresLocalCancel() = runBlocking {
        val vm = DeviceViewModel(RuntimeEnvironment.getApplication())
        val usbJob = Job()
        val operationField = DeviceViewModel::class.java.getDeclaredField("operationJob").apply { isAccessible = true }
        operationField.set(vm, usbJob)
        try {
            assertEquals(DeviceViewModel.LocalTerminalAdmission.USB_BUSY,
                vm.launchLocalTerminalCommand(this) { fail("Local command started during USB operation") })
            vm.cancelLocalTerminalCommand()
            assertTrue(usbJob.isActive)
        } finally {
            operationField.set(vm, null)
            usbJob.cancel()
        }
    }
}
