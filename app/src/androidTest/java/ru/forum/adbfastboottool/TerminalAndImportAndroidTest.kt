package ru.forum.adbfastboottool

import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.content.DialogInterface
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.MutableLiveData
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** No USB device: command admission/failure and Binder provider fixtures only. */
@RunWith(AndroidJUnit4::class)
class TerminalAndImportAndroidTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val resolver get() = context.contentResolver
    private val uri = Uri.parse("content://ru.forum.adbfastboottool.dev.test.images/import")
    private val workspace get() = File(context.filesDir, "rev26-import-fixture")
    private val prefs get() = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private var previousLanguage: String? = null
    private var previousPlatformLanguage = ""
    private fun vm() = DeviceSessionStore.get(context.applicationContext as Application)

    @Before fun prepare() {
        previousLanguage = prefs.getString("language_tag", null)
        if (Build.VERSION.SDK_INT >= 33) previousPlatformLanguage = context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
        prefs.edit().putString("language_tag", "ru").commit()
        resolver.call(uri, "reset", null, null)
        workspace.mkdirs()
        instrumentation.runOnMainSync { DeviceSessionStore.viewModelStore.clear(); OnboardingGate.complete(); setLocale("ru") }
        File(context.filesDir, "execution-state").deleteRecursively()
    }
    @After fun cleanup() {
        resolver.call(uri, "release", null, null)
        instrumentation.runOnMainSync {
            vm().workspaceImport.cancel()
            setLocale(if (Build.VERSION.SDK_INT >= 33) previousPlatformLanguage else previousLanguage.orEmpty())
        }
        waitUntil { !vm().workspaceImport.isBusy }
        instrumentation.runOnMainSync { DeviceSessionStore.viewModelStore.clear() }
        prefs.edit().putString("language_tag", previousLanguage).commit()
        workspace.deleteRecursively(); File(context.filesDir, "execution-state").deleteRecursively()
    }
    private fun setLocale(tag: String) {
        if (Build.VERSION.SDK_INT >= 33) context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
        else AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }
    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!condition()) { check(System.nanoTime() < deadline) { "REV26 state did not settle" }; Thread.sleep(20) }
        instrumentation.waitForIdleSync()
    }
    private fun waitUi(scenario: ActivityScenario<MainActivity>, condition: (MainActivity) -> Boolean) = waitUntil {
        var ready = false; scenario.onActivity { ready = condition(it) }; ready
    }
    private fun expanded(activity: MainActivity) = BottomSheetBehavior.from(activity.findViewById<View>(R.id.consolePanel)).state == BottomSheetBehavior.STATE_EXPANDED
    private fun visible(view: View) {
        val rect = Rect()
        assertTrue("Control not visible: ${view.id}", view.getGlobalVisibleRect(rect))
        assertEquals("Clipped width", view.width, rect.width()); assertEquals("Clipped height", view.height, rect.height())
    }
    private fun field(activity: MainActivity, name: String): Any? = MainActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(activity)
    private fun command(activity: MainActivity, text: String) {
        activity.findViewById<EditText>(R.id.etCommand).setText(text); activity.findViewById<View>(R.id.btnSend).performClick()
    }
    private fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command).use {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().use { reader -> reader.readText().trim() }
    }

    @Test fun shellOperationCannotCollapseTerminalOrOpenProgressDialog() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.findViewById<View>(R.id.btnTerminalOpen).performClick() }
            waitUi(scenario, ::expanded)
            // Keep activation observable: immediate no-device failure can coalesce true/false.
            @Suppress("UNCHECKED_CAST")
            val active = DeviceViewModel::class.java.getDeclaredField("_operationActive").apply { isAccessible = true }.get(vm()) as MutableLiveData<Boolean>
            scenario.onActivity { active.value = true }
            waitUi(scenario) { it.findViewById<View>(R.id.terminalOperationStatus).isShown }
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                assertTrue("Operation activation collapsed terminal", expanded(it))
                visible(it.findViewById(R.id.btnTerminalStop))
                assertFalse((field(it, "operationCenterDialog") as BottomSheetDialog).isShowing)
                it.findViewById<View>(R.id.btnConsoleClose).performClick()
            }
            waitUi(scenario) { BottomSheetBehavior.from(it.findViewById<View>(R.id.consolePanel)).state == BottomSheetBehavior.STATE_COLLAPSED }
            assertTrue("Closing the terminal cancelled the operation", active.value == true)
            scenario.onActivity { it.findViewById<View>(R.id.btnTerminalOpen).performClick() }
            waitUi(scenario, ::expanded)
            scenario.onActivity {
                it.findViewById<View>(R.id.btnTerminalStop).performClick()
                assertEquals(true, field(it, "operationCancelRequested"))
                assertFalse(it.findViewById<View>(R.id.btnTerminalStop).isEnabled)
                active.value = false
                command(it, "adb shell id")
            }
            waitUntil { vm().terminalSnapshot().any { it.startsWith("ERROR:") } }
            // Real service completion path, without sending anything to USB.
            scenario.onActivity { vm().withTerminalCommand { vm().runAdbShell("id") } }
            waitUntil { vm().operationProgress.value?.finished == true && vm().operationActive.value == false }
            scenario.onActivity {
                assertTrue("Command completion collapsed terminal", expanded(it))
                assertFalse((field(it, "operationCenterDialog") as BottomSheetDialog).isShowing)
                assertTrue(vm().terminalSnapshot().any { line -> line == "> adb shell id" })
                assertTrue(vm().terminalSnapshot().any { line -> line.startsWith("❌") })
                vm().log("Routine connection inventory fixture")
                DeviceViewModel::class.java.getDeclaredMethod("notifyLogStorageFailure", Exception::class.java)
                    .apply { isAccessible = true }.invoke(vm(), java.io.IOException("fixture full disk"))
                assertTrue(vm().terminalSnapshot().any { line -> line.startsWith("❌ Diagnostic storage error:") })
            }
            assertFalse(vm().terminalSnapshot().contains("Routine connection inventory fixture"))
            assertTrue(vm().logSnapshot().contains("Routine connection inventory fixture"))
            scenario.onActivity { vm().clearTerminal(); command(it, "adb devices") }
            waitUntil { vm().terminalSnapshot().any { it == "> adb devices" } }
            AndroidUiCapture.save(scenario, "terminal-command.png")
            scenario.onActivity { it.findViewById<View>(R.id.btnConsoleClose).performClick() }
            waitUi(scenario) { BottomSheetBehavior.from(it.findViewById<View>(R.id.consolePanel)).state == BottomSheetBehavior.STATE_COLLAPSED }
            assertFalse(vm().operationActive.value!!)
        }
    }
    /**
     * Use the platform Settings API on Q+ to notify ActivityManager of the
     * configuration change, then wait for the Activity's actual Resources.
     * Writing the settings database just before launch can race with the
     * configuration observer on API 34.
     */
    private fun setFontScaleForTest(value: Float) {
        if (Build.VERSION.SDK_INT >= 29) {
            val automation = instrumentation.uiAutomation
            automation.adoptShellPermissionIdentity()
            try {
                assertTrue("Cannot set font scale", Settings.System.putFloat(
                    instrumentation.context.contentResolver, Settings.System.FONT_SCALE, value))
            } finally {
                automation.dropShellPermissionIdentity()
            }
        } else {
            shell("settings put system font_scale $value")
        }
    }

    @Test fun terminalControlsRemainReachableWithImeRotationAndLargeFont() {
        // Reading Settings through the instrumentation package throws a UID
        // ownership SecurityException on API 26; shell is valid across APIs.
        val oldScale = shell("settings get system font_scale").toFloatOrNull() ?: 1f
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            try {
                setFontScaleForTest(1.3f)
                waitUi(scenario) { it.resources.configuration.fontScale >= 1.29f }
                scenario.onActivity { it.findViewById<View>(R.id.btnTerminalOpen).performClick() }
                waitUi(scenario) { expanded(it) && ViewCompat.getRootWindowInsets(it.findViewById(R.id.rootCoordinator))?.isVisible(WindowInsetsCompat.Type.ime()) == true }
                scenario.onActivity {
                    visible(it.findViewById(R.id.btnConsoleClose)); visible(it.findViewById(R.id.etCommand)); visible(it.findViewById(R.id.btnSend))
                    it.findViewById<EditText>(R.id.etCommand).setText("adb shell echo draft")
                }
                AndroidUiCapture.save(scenario, "terminal-keyboard.png")
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                waitUi(scenario) { ViewCompat.getRootWindowInsets(it.findViewById(R.id.rootCoordinator))?.isVisible(WindowInsetsCompat.Type.ime()) != true }
                scenario.recreate(); waitUi(scenario, ::expanded)
                scenario.onActivity {
                    assertEquals("adb shell echo draft", it.findViewById<EditText>(R.id.etCommand).text.toString())
                    it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }
                waitUi(scenario) { expanded(it) && it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
                scenario.onActivity { visible(it.findViewById(R.id.btnConsoleClose)); visible(it.findViewById(R.id.etCommand)) }
                AndroidUiCapture.save(scenario, "terminal-landscape.png")
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                waitUi(scenario) { it.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
            } finally {
                setFontScaleForTest(oldScale)
                waitUi(scenario) { kotlin.math.abs(it.resources.configuration.fontScale - oldScale) < 0.02f }
            }
        }
    }
    @Test fun providerImportPreservesExistingFileAndCopiesExactBytes() {
        File(workspace, "provider_image.img").writeText("old image")
        instrumentation.runOnMainSync { assertTrue(vm().importWorkspaceFile(uri, workspace)) }
        waitUntil { !vm().workspaceImport.isBusy }
        assertEquals(WorkspaceImportController.Phase.COMPLETED, vm().workspaceImport.state.value!!.phase)
        assertEquals("old image", File(workspace, "provider_image.img").readText())
        assertArrayEquals(ByteArray(8192) { (it % 251).toByte() }, File(workspace, "provider_image-1.img").readBytes())
        assertFalse(File(workspace, ".nekoflash-import-${context.packageName}/pending.part").exists())
    }
    @Test fun incorrectProviderSizeFailsWithoutPublishingPartialFile() {
        instrumentation.runOnMainSync { assertTrue(vm().importWorkspaceFile(uri.buildUpon().path("image").build(), workspace)) }
        waitUntil { !vm().workspaceImport.isBusy }
        assertEquals(WorkspaceImportController.Phase.FAILED, vm().workspaceImport.state.value!!.phase)
        assertFalse(File(workspace, "provider_image.img").exists())
        assertFalse(File(workspace, ".nekoflash-import-${context.packageName}/pending.part").exists())
    }
    @Test fun slowPipeImportSurvivesRotationAndCancelsWithoutReopeningSource() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { assertTrue(vm().importWorkspaceFile(uri.buildUpon().path("slow").build(), workspace)) }
            waitUntil { vm().workspaceImport.state.value?.copied == 4096L }
            scenario.onActivity { assertFalse(vm().runWorkspaceMaintenanceIfIdle { fail("Cleanup ran during import") }) }
            scenario.recreate()
            waitUi(scenario) { (field(it, "importDialog") as? AlertDialog)?.isShowing == true }
            assertEquals(1, resolver.call(uri, "stats", null, null)!!.getInt("opens"))
            AndroidUiCapture.save(scenario, "import-copying.png")
            scenario.onActivity { (field(it, "importDialog") as AlertDialog).getButton(DialogInterface.BUTTON_NEGATIVE).performClick() }
            waitUntil { !vm().workspaceImport.isBusy }
            assertEquals(WorkspaceImportController.Phase.CANCELLED, vm().workspaceImport.state.value!!.phase)
            assertFalse(File(workspace, "provider_image.img").exists())
            assertFalse(File(workspace, ".nekoflash-import-${context.packageName}/pending.part").exists())
            AndroidUiCapture.save(scenario, "import-cancelled.png")
            scenario.onActivity {
                assertTrue(vm().runWorkspaceMaintenanceIfIdle { })
                (field(it, "importDialog") as AlertDialog).getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            }
            waitUntil { vm().workspaceImport.state.value == null }
        }
    }
}
