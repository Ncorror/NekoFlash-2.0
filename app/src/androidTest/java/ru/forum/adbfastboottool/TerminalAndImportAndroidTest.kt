package ru.forum.adbfastboottool

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.app.LocaleManager
import android.content.Context
import android.content.DialogInterface
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.text.Spannable
import android.text.Selection
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import android.provider.Settings
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.RecyclerView
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
    private fun waitUntil(stage: String = "REV30 asynchronous state", condition: () -> Boolean) {
        val deadline = System.nanoTime() + 15_000_000_000L
        while (!condition()) {
            check(System.nanoTime() < deadline) { "$stage did not settle within 15s" }
            Thread.sleep(20)
        }
        instrumentation.waitForIdleSync()
    }
    private fun waitUi(
        scenario: ActivityScenario<MainActivity>,
        stage: String = "REV30 UI",
        condition: (MainActivity) -> Boolean
    ) = waitUntil(stage) {
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

    @Test fun closedTerminalDefersOutputAndReopensWithNewestTranscript() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var hiddenRows = -1
            scenario.onActivity {
                assertFalse(expanded(it))
                hiddenRows = it.findViewById<RecyclerView>(R.id.rvConsoleOutput).adapter!!.itemCount
            }
            repeat(220) { vm().log("> rev27-hidden-$it") }
            waitUntil { vm().terminalLines.value?.lastOrNull() == "> rev27-hidden-219" }
            scenario.onActivity {
                assertEquals("Closed Console must not bind hundreds of rows", hiddenRows,
                    it.findViewById<RecyclerView>(R.id.rvConsoleOutput).adapter!!.itemCount)
                assertTrue(it.findViewById<TextView>(R.id.tvConsolePeek).text.contains("rev27-hidden-219"))
                it.findViewById<View>(R.id.btnTerminalOpen).performClick()
            }
            waitUi(scenario) {
                expanded(it) && it.findViewById<RecyclerView>(R.id.rvConsoleOutput)
                    .adapter!!.itemCount == vm().terminalSnapshot().size
            }
            scenario.onActivity { it.findViewById<View>(R.id.btnConsoleClose).performClick() }
            waitUi(scenario) {
                BottomSheetBehavior.from(it.findViewById<View>(R.id.consolePanel))
                    .state == BottomSheetBehavior.STATE_COLLAPSED
            }
            var beforeSecondBurst = -1
            scenario.onActivity {
                beforeSecondBurst = it.findViewById<RecyclerView>(R.id.rvConsoleOutput).adapter!!.itemCount
            }
            repeat(240) { vm().log("> rev27-second-$it") }
            waitUntil { vm().terminalLines.value?.lastOrNull() == "> rev27-second-239" }
            scenario.onActivity {
                assertEquals(beforeSecondBurst,
                    it.findViewById<RecyclerView>(R.id.rvConsoleOutput).adapter!!.itemCount)
                // Reopen immediately after a new line, before LiveData debounce.
                vm().log("> rev27-before-publish")
                it.findViewById<View>(R.id.btnTerminalOpen).performClick()
            }
            waitUi(scenario) {
                expanded(it) && it.findViewById<RecyclerView>(R.id.rvConsoleOutput)
                    .adapter!!.itemCount == vm().terminalSnapshot().size
            }
            assertEquals("> rev27-before-publish", vm().terminalSnapshot().last())
            waitUi(scenario, "REV39 selectable console row") {
                it.findViewById<RecyclerView>(R.id.rvConsoleOutput).childCount > 0
            }
            scenario.onActivity {
                val view = it.findViewById<RecyclerView>(R.id.rvConsoleOutput).getChildAt(0)
                val line = view as TextView
                assertTrue("Visible Console lines must support native Select/Copy", line.isTextSelectable)
                assertTrue("No visible line text to copy", line.text.isNotBlank())
                // Test the actual visible TextView selection state used by
                // MainActivity's auto-follow guard, not just a policy Boolean.
                val span = line.text as Spannable
                Selection.setSelection(span, 0, minOf(3, span.length))
                val selected = MainActivity::class.java
                    .getDeclaredMethod("hasSelectedConsoleText").apply { isAccessible = true }
                assertEquals("Active text selection must block automatic following",
                    true, selected.invoke(it))
                Selection.removeSelection(span)
                assertEquals("Clearing selection must resume normal following",
                    false, selected.invoke(it))
                assertEquals("> rev27-before-publish", vm().terminalSnapshot().last())
            }
        }
    }

    @Test fun shellOperationCannotCollapseTerminalOrOpenProgressDialog() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.findViewById<View>(R.id.btnTerminalOpen).performClick() }
            waitUi(scenario, "terminal expanded") { expanded(it) }
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
            waitUi(scenario, "terminal expanded") { expanded(it) }
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
                waitUi(scenario, "terminal expanded with visible IME") {
                    expanded(it) && ViewCompat.getRootWindowInsets(it.findViewById(R.id.rootCoordinator))
                        ?.isVisible(WindowInsetsCompat.Type.ime()) == true
                }
                scenario.onActivity {
                    visible(it.findViewById(R.id.btnConsoleClose)); visible(it.findViewById(R.id.etCommand)); visible(it.findViewById(R.id.btnSend))
                    val editor = it.findViewById<PtyInputEditText>(R.id.etCommand)
                    val info = EditorInfo()
                    assertNotNull("Command input connection missing", editor.onCreateInputConnection(info))
                    assertTrue("IME must not open a separate fullscreen editor",
                        info.imeOptions and EditorInfo.IME_FLAG_NO_EXTRACT_UI != 0)
                    assertTrue("IME must remain inline on rotation",
                        info.imeOptions and EditorInfo.IME_FLAG_NO_FULLSCREEN != 0)
                    editor.setText("adb shell echo draft")
                }
                AndroidUiCapture.save(scenario, "terminal-keyboard.png")
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                waitUi(scenario) { ViewCompat.getRootWindowInsets(it.findViewById(R.id.rootCoordinator))?.isVisible(WindowInsetsCompat.Type.ime()) != true }
                scenario.recreate(); waitUi(scenario, "terminal expanded") { expanded(it) }
                scenario.onActivity {
                    assertEquals("adb shell echo draft", it.findViewById<EditText>(R.id.etCommand).text.toString())
                    it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }
                waitUi(scenario, "expanded landscape terminal") {
                    expanded(it) && it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                }
                // API34 can report LANDSCAPE and STATE_EXPANDED before its
                // final system-bar/IME inset frame. Preserve the strict full
                // visibility assertion, but wait for the final viewport.
                waitUi(scenario, "landscape terminal controls fully visible") { activity ->
                    listOf<View>(
                        activity.findViewById(R.id.btnConsoleClose),
                        activity.findViewById(R.id.etCommand)
                    ).all { view ->
                        val rect = Rect()
                        view.isShown && view.getGlobalVisibleRect(rect) &&
                            rect.width() == view.width && rect.height() == view.height
                    }
                }
                scenario.onActivity {
                    visible(it.findViewById(R.id.btnConsoleClose))
                    val editor = it.findViewById<PtyInputEditText>(R.id.etCommand)
                    visible(editor)
                    assertEquals("Draft must survive rotation", "adb shell echo draft", editor.text.toString())
                    val info = EditorInfo()
                    assertNotNull(editor.onCreateInputConnection(info))
                    assertTrue(info.imeOptions and EditorInfo.IME_FLAG_NO_EXTRACT_UI != 0)
                    assertTrue(info.imeOptions and EditorInfo.IME_FLAG_NO_FULLSCREEN != 0)
                }
                AndroidUiCapture.save(scenario, "terminal-landscape.png")
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                waitUi(scenario, "portrait restored") {
                    it.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
                }
                // Regression: a focused command editor must not reopen the
                // collapsed sheet from a delayed focus/insets notification.
                scenario.onActivity {
                    val editor = it.findViewById<EditText>(R.id.etCommand)
                    assertEquals("adb shell echo draft", editor.text.toString())
                    editor.requestFocus()
                    it.findViewById<View>(R.id.btnConsoleClose).performClick()
                }
                waitUi(scenario, "explicit terminal close") {
                    BottomSheetBehavior.from(it.findViewById<View>(R.id.consolePanel))
                        .state == BottomSheetBehavior.STATE_COLLAPSED
                }
                Thread.sleep(350)
                scenario.onActivity {
                    assertFalse("Terminal reopened after IME focus callback", expanded(it))
                    assertFalse("Close must relinquish editor focus",
                        it.findViewById<EditText>(R.id.etCommand).hasFocus())
                    assertEquals("Draft must not be cleared by Close",
                        "adb shell echo draft", it.findViewById<EditText>(R.id.etCommand).text.toString())
                }

                // Repeated explicit open/close immediately after rotation used
                // to race with bottom-sheet layout and delayed IME callbacks.
                // This is a state regression, not an emulator jank benchmark.
                repeat(3) { cycle ->
                    scenario.onActivity { it.findViewById<View>(R.id.btnTerminalOpen).performClick() }
                    waitUi(scenario, "REV34 terminal reopen cycle $cycle") { expanded(it) }
                    scenario.onActivity {
                        visible(it.findViewById(R.id.btnConsoleClose))
                        visible(it.findViewById(R.id.etCommand))
                        assertEquals("Draft changed on reopening", "adb shell echo draft",
                            it.findViewById<EditText>(R.id.etCommand).text.toString())
                        it.findViewById<View>(R.id.btnConsoleClose).performClick()
                    }
                    waitUi(scenario, "REV34 terminal close cycle $cycle") {
                        BottomSheetBehavior.from(it.findViewById<View>(R.id.consolePanel))
                            .state == BottomSheetBehavior.STATE_COLLAPSED &&
                            !it.findViewById<EditText>(R.id.etCommand).hasFocus()
                    }
                }
                Thread.sleep(350)
                scenario.onActivity {
                    assertFalse("Late IME callback reopened terminal after repeated Close", expanded(it))
                    assertEquals("Command draft changed after repeated Close", "adb shell echo draft",
                        it.findViewById<EditText>(R.id.etCommand).text.toString())
                }
            } finally {
                setFontScaleForTest(oldScale)
                waitUi(scenario) { kotlin.math.abs(it.resources.configuration.fontScale - oldScale) < 0.02f }
            }
        }
    }
    @Test fun ptyImeComposingDeletionUnicodeAndPasteDoNotRequireUsb() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.findViewById<View>(R.id.btnTerminalOpen).performClick() }
            waitUi(scenario, "REV38 PTY editor visible") { expanded(it) }
            scenario.onActivity { activity ->
                val editor = activity.findViewById<PtyInputEditText>(R.id.etCommand)
                val sent = mutableListOf<ByteArray>()
                editor.configurePty(
                    rawMode = { true },
                    sink = { bytes -> sent.add(bytes.copyOf()); true }
                )
                try {
                    val input = requireNotNull(editor.onCreateInputConnection(EditorInfo()))
                    assertTrue(input.setComposingText("прив", 1))
                    assertTrue(input.deleteSurroundingTextInCodePoints(1, 0))
                    assertTrue(input.setComposingText("при", 1))
                    assertTrue(input.commitText("привет 🌙", 1))
                    assertEquals(3, sent.size)
                    assertArrayEquals("прив".toByteArray(Charsets.UTF_8), sent[0])
                    assertArrayEquals(byteArrayOf(PtyInputDelta.DEL.toByte()), sent[1])
                    assertArrayEquals("вет 🌙".toByteArray(Charsets.UTF_8), sent[2])

                    // Both sides of the cursor must be processed, and an
                    // emoji is one code point even if it spans two UTF-16 units.
                    assertTrue(input.setComposingText("🌙", 1))
                    assertTrue(input.deleteSurroundingText(1, 2))
                    assertArrayEquals(byteArrayOf(PtyInputDelta.DEL.toByte()) +
                        "\u001B[3~\u001B[3~".toByteArray(Charsets.US_ASCII), sent.last())
                    assertTrue(input.commitText("✨", 1))
                    assertArrayEquals("✨".toByteArray(Charsets.UTF_8), sent.last())

                    // Test the actual Android context-menu paste route,
                    // not only IME commitText. Never read the user's clipboard.
                    val clipboard = activity.getSystemService(ClipboardManager::class.java)
                    clipboard.setPrimaryClip(ClipData.newPlainText("REV38 fixture", "echo Байт\n"))
                    assertTrue(editor.onTextContextMenuItem(android.R.id.paste))
                    assertArrayEquals("echo Байт\n".toByteArray(Charsets.UTF_8), sent.last())
                    if (Build.VERSION.SDK_INT >= 28) clipboard.clearPrimaryClip()
                    else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))

                    assertTrue(input.performEditorAction(EditorInfo.IME_ACTION_SEND))
                    assertArrayEquals(byteArrayOf('\n'.code.toByte()), sent.last())
                    assertTrue(input.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)))
                    assertArrayEquals("\u001B[A".toByteArray(Charsets.UTF_8), sent.last())
                    assertTrue("Raw input must not leave a host command draft", editor.text.isNullOrEmpty())

                    // A rejected write must not advance the IME composition.
                    editor.configurePty(rawMode = { true }, sink = { false })
                    val rejected = requireNotNull(editor.onCreateInputConnection(EditorInfo()))
                    assertFalse(rejected.setComposingText("пароль", 1))
                    editor.configurePty(rawMode = { true },
                        sink = { bytes -> sent.add(bytes.copyOf()); true })
                    assertTrue(rejected.commitText("пароль", 1))
                    assertArrayEquals("пароль".toByteArray(Charsets.UTF_8), sent.last())

                    // When raw mode ends, the same connection remains a
                    // normal Android EditText, not a phantom remote PTY.
                    editor.configurePty(rawMode = { false }, sink = { false })
                    assertTrue(rejected.commitText("local", 1))
                    assertTrue(editor.text.toString().contains("local"))
                } finally {
                    editor.configurePty(
                        rawMode = { vm().isInteractiveAdbShellActive() },
                        sink = { bytes -> vm().sendInteractiveAdbShellBytes(bytes) }
                    )
                    editor.text?.clear()
                }
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
