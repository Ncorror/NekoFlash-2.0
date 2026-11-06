package ru.forum.adbfastboottool

import android.app.Application
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.MutableLiveData
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** UI/session fixtures only: no USB connection or operation is submitted. */
@RunWith(AndroidJUnit4::class)
class OperationResultRestoreAndroidTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val state get() = File(context.filesDir, "execution-state")
    private val checkpoint get() = File(state, "last-operation.properties")
    private val journal get() = OperationRecoveryStore(state)
    private val pending = PersistedOperation(
        "rev47-ui-fixture", "Sideload fixture", "Recovery verification required",
        1_000L, 100, PersistedOperationPhase.VERIFY_PENDING
    )
    private fun vm() = DeviceSessionStore.get(context.applicationContext as Application)
    private fun sessionId() = vm().currentDiagnosticSessionSummary().sessionId

    @Before fun prepareSession() {
        instrumentation.runOnMainSync {
            DeviceSessionStore.viewModelStore.clear()
            OnboardingGate.complete()
        }
        state.deleteRecursively()
    }

    @After fun clearFixtures() {
        instrumentation.runOnMainSync { DeviceSessionStore.viewModelStore.clear() }
        state.deleteRecursively()
    }

    private fun waitUi(
        scenario: ActivityScenario<MainActivity>,
        timeoutMs: Long = 10_000L,
        condition: (MainActivity) -> Boolean
    ) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        while (true) {
            var ready = false
            scenario.onActivity { ready = condition(it) }
            if (ready) return
            check(System.nanoTime() < deadline) { "Operation result UI did not settle" }
            Thread.sleep(20)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun setActive(value: Boolean) {
        (DeviceViewModel::class.java.getDeclaredField("_operationActive")
            .apply { isAccessible = true }.get(vm()) as MutableLiveData<Boolean>).value = value
    }

    private fun beginProgress(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity {
            vm().postOperationProgress(DeviceViewModel.OperationProgress(pending.title, 30, "Sending fixture"))
        }
        waitUi(scenario) { vm().operationProgress.value?.finished == false }
        scenario.onActivity { setActive(true) }
        waitUi(scenario) { it.findViewById<View>(R.id.cardOperationStrip).visibility == View.VISIBLE }
    }

    private fun finishPending(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity {
            vm().postOperationProgress(DeviceViewModel.OperationProgress(
                pending.title, 100, pending.detail, finished = true,
                outcome = DeviceViewModel.OperationOutcomeKind.VERIFY_PENDING
            ))
        }
        waitUi(scenario) { vm().operationProgress.value?.finished == true }
        scenario.onActivity { setActive(false) }
        waitUi(scenario) { it.findViewById<View>(R.id.btnOperationStripDismiss).visibility == View.VISIBLE }
    }

    @Test fun coldLaunchHidesPendingStripWithoutChangingCheckpoint() {
        journal.writeOperation(pending)
        val originalBytes = checkpoint.readBytes()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitUi(scenario) { vm().operationProgress.value?.finished == true }
            scenario.onActivity {
                assertEquals(View.GONE, it.findViewById<View>(R.id.cardOperationStrip).visibility)
                assertEquals(DeviceViewModel.OperationOutcomeKind.VERIFY_PENDING, vm().operationProgress.value?.outcome)
                assertFalse(vm().operationActive.value!!)
            }
            scenario.recreate()
            scenario.onActivity {
                assertEquals(View.GONE, it.findViewById<View>(R.id.cardOperationStrip).visibility)
            }
            assertEquals(pending, journal.readOperation())
            assertArrayEquals(originalBytes, checkpoint.readBytes())
        }
    }

    @Test fun liveProgressSurvivesRotationAndPendingStripAutoHidesWithoutChangingVerdict() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            beginProgress(scenario)
            val originalSession = sessionId()
            scenario.recreate()
            scenario.onActivity {
                assertEquals(originalSession, sessionId())
                assertEquals(View.VISIBLE, it.findViewById<View>(R.id.cardOperationStrip).visibility)
                assertTrue(vm().operationActive.value!!)
            }
            finishPending(scenario)
            journal.writeOperation(pending)
            val originalBytes = checkpoint.readBytes()
            // Rotation must not restart the original 20-second result hold.
            Thread.sleep(12_000L)
            scenario.recreate()
            waitUi(scenario, timeoutMs = 12_000L) {
                it.findViewById<View>(R.id.cardOperationStrip).visibility == View.GONE
            }
            scenario.onActivity {
                assertEquals(DeviceViewModel.OperationOutcomeKind.VERIFY_PENDING, vm().operationProgress.value?.outcome)
                assertFalse(vm().operationProgress.value!!.success)
            }
            assertArrayEquals(originalBytes, checkpoint.readBytes())
        }
    }

    @Test fun savedActivityStateWithNewExecutionSessionDoesNotRevivePendingStrip() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            beginProgress(scenario)
            finishPending(scenario)
            journal.writeOperation(pending)
            val originalBytes = checkpoint.readBytes()
            val originalSession = sessionId()
            scenario.onActivity { it.findViewById<EditText>(R.id.etCommand).setText("rev47 saved draft") }
            // First prove ordinary recreation keeps this session's result visible.
            scenario.recreate()
            waitUi(scenario) { it.findViewById<View>(R.id.btnOperationStripDismiss).width > 0 }
            scenario.onActivity {
                assertEquals(originalSession, sessionId())
                assertEquals(View.VISIBLE, it.findViewById<View>(R.id.cardOperationStrip).visibility)
                val close = it.findViewById<TextView>(R.id.btnOperationStripDismiss)
                assertTrue("Dismiss button has no room for its close glyph",
                    close.width - close.compoundPaddingLeft - close.compoundPaddingRight >=
                        close.paint.measureText(close.text.toString()))
            }
            // Keep Android's saved Bundle but replace the execution session after
            // the old Activity is destroyed, as happens after process recreation.
            // This is a simulation, not a claim of killing the emulator process.
            var replaced = false
            val callback = ActivityLifecycleCallback { activity, stage ->
                if (activity is MainActivity && stage == Stage.DESTROYED && !replaced) {
                    DeviceSessionStore.viewModelStore.clear()
                    replaced = true
                }
            }
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            instrumentation.runOnMainSync { monitor.addLifecycleCallback(callback) }
            try {
                scenario.recreate()
                assertTrue("Execution session was not replaced", replaced)
                waitUi(scenario) { vm().operationProgress.value?.finished == true }
                scenario.onActivity {
                    assertNotEquals(originalSession, sessionId())
                    assertEquals("rev47 saved draft", it.findViewById<EditText>(R.id.etCommand).text.toString())
                    assertEquals(View.GONE, it.findViewById<View>(R.id.cardOperationStrip).visibility)
                    assertEquals(DeviceViewModel.OperationOutcomeKind.VERIFY_PENDING, vm().operationProgress.value?.outcome)
                    assertFalse(vm().operationActive.value!!)
                }
                assertEquals(pending, journal.readOperation())
                assertArrayEquals(originalBytes, checkpoint.readBytes())
                // A hidden restored result must not hide the next operation.
                beginProgress(scenario)
                finishPending(scenario)
                scenario.onActivity { it.findViewById<View>(R.id.btnOperationStripDismiss).performClick() }
                scenario.recreate()
                scenario.onActivity {
                    assertEquals(View.GONE, it.findViewById<View>(R.id.cardOperationStrip).visibility)
                }
            } finally {
                instrumentation.runOnMainSync { monitor.removeLifecycleCallback(callback) }
            }
        }
    }
}
