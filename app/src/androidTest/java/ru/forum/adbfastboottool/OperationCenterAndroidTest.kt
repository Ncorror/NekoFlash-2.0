package ru.forum.adbfastboottool

import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.LocaleList
import android.os.ParcelFileDescriptor
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.MutableLiveData
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.File
import java.util.Locale
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** State fixtures exercise the installed UI; no device command or USB write is submitted. */
@RunWith(AndroidJUnit4::class)
class OperationCenterAndroidTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val prefs get() = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private var oldLanguage: String? = null
    private var oldPlatformLanguage = ""
    private fun vm() = DeviceSessionStore.get(context.applicationContext as Application)

    private fun locale(tag: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
        } else AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    @Before fun prepare() {
        oldLanguage = prefs.getString("language_tag", null)
        if (Build.VERSION.SDK_INT >= 33) {
            oldPlatformLanguage = context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
        }
        prefs.edit().putString("language_tag", "ru").commit()
        instrumentation.runOnMainSync {
            DeviceSessionStore.viewModelStore.clear()
            OnboardingGate.complete()
            locale("ru")
        }
        File(context.filesDir, "execution-state").deleteRecursively()
    }

    @After fun clean() {
        instrumentation.runOnMainSync {
            DeviceSessionStore.viewModelStore.clear()
            locale(if (Build.VERSION.SDK_INT >= 33) oldPlatformLanguage else oldLanguage.orEmpty())
        }
        prefs.edit().putString("language_tag", oldLanguage).commit()
        File(context.filesDir, "execution-state").deleteRecursively()
    }

    private fun waitUi(scenario: ActivityScenario<MainActivity>, condition: (MainActivity) -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (true) {
            var ready = false
            scenario.onActivity { ready = condition(it) }
            if (ready) return
            check(System.nanoTime() < deadline) { "Operation Center did not reach the expected UI state" }
            Thread.sleep(20)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun active(value: Boolean) {
        (DeviceViewModel::class.java.getDeclaredField("_operationActive")
            .apply { isAccessible = true }.get(vm()) as MutableLiveData<Boolean>).value = value
    }

    private fun dialog(activity: MainActivity) = MainActivity::class.java.getDeclaredField("operationCenterDialog")
        .apply { isAccessible = true }.get(activity) as BottomSheetDialog

    private fun open(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { it.findViewById<View>(R.id.cardOperationStrip).performClick() }
        waitUi(scenario) { dialog(it).isShowing && dialog(it).findViewById<View>(R.id.btnOperationCenterMinimize)!!.width > 0 }
    }

    private fun begin(scenario: ActivityScenario<MainActivity>, detail: String = "Передача данных на устройство") {
        scenario.onActivity {
            vm().postOperationProgress(DeviceViewModel.OperationProgress("Прошивка vendor_boot_a", 42, detail))
        }
        waitUi(scenario) { vm().operationProgress.value?.finished == false }
        scenario.onActivity { active(true) }
    }

    private fun fullyVisible(view: View): Boolean {
        val rect = Rect()
        return view.isShown && view.getGlobalVisibleRect(rect) && rect.width() == view.width && rect.height() == view.height
    }

    @Test fun operationWithoutPercentageOpensAndMinimizeDoesNotCancelIt() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { active(true) }
            open(scenario)
            lateinit var oldDialog: BottomSheetDialog
            scenario.onActivity {
                oldDialog = dialog(it)
                assertTrue(oldDialog.findViewById<ProgressBar>(R.id.operationCenterProgressBar)!!.isIndeterminate)
                oldDialog.findViewById<View>(R.id.btnOperationCenterMinimize)!!.performClick()
                assertFalse(oldDialog.isShowing)
                assertTrue(vm().operationActive.value!!)
                assertEquals(View.VISIBLE, it.findViewById<View>(R.id.cardOperationStrip).visibility)
            }
            open(scenario)
            scenario.recreate()
            waitUi(scenario) { dialog(it).isShowing }
            assertFalse("Destroyed Activity retained an open dialog", oldDialog.isShowing)
            scenario.onActivity {
                assertTrue(vm().operationActive.value!!)
                dialog(it).findViewById<View>(R.id.btnOperationCenterConsole)!!.performClick()
                assertFalse(dialog(it).isShowing)
            }
            waitUi(scenario) { it.findViewById<View>(R.id.btnConsoleClose).isShown }
            scenario.onActivity { active(false) }
        }
    }

    @Test fun terminalOutcomesAtOneHundredPercentKeepTheirActualVerdicts() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            for ((outcome, label) in listOf(
                DeviceViewModel.OperationOutcomeKind.VERIFY_PENDING to R.string.layout_operation_center_verify_pending,
                DeviceViewModel.OperationOutcomeKind.FAILED to R.string.layout_operation_center_failed,
                DeviceViewModel.OperationOutcomeKind.SUCCESS to R.string.layout_operation_center_completed,
                DeviceViewModel.OperationOutcomeKind.CANCELLED to R.string.layout_operation_center_cancelled,
                DeviceViewModel.OperationOutcomeKind.INTERRUPTED to R.string.rev20_operation_interrupted
            )) {
                begin(scenario)
                scenario.onActivity {
                    vm().postOperationProgress(DeviceViewModel.OperationProgress("ADB Sideload", 100,
                        "Результат тестового сценария", finished = true,
                        success = outcome == DeviceViewModel.OperationOutcomeKind.SUCCESS, outcome = outcome))
                }
                waitUi(scenario) { vm().operationProgress.value?.finished == true }
                scenario.onActivity { active(false) }
                open(scenario)
                scenario.onActivity {
                    val sheet = dialog(it)
                    assertEquals(it.getString(label), sheet.findViewById<TextView>(R.id.tvOperationCenterStatus)!!.text.toString())
                    assertEquals(View.GONE, sheet.findViewById<View>(R.id.tvOperationCenterPercent)!!.visibility)
                    assertEquals(View.GONE, sheet.findViewById<View>(R.id.operationCenterProgressBar)!!.visibility)
                    assertEquals(View.GONE, it.findViewById<View>(R.id.tvOperationStripPercent).visibility)
                    assertEquals(outcome, vm().operationProgress.value!!.outcome)
                }
                AndroidUiCapture.save(scenario, "operation-${outcome.name.lowercase(Locale.ROOT)}.png")
                scenario.onActivity { dialog(it).dismiss() }
            }
        }
    }

    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use {
        ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().use { reader -> reader.readText().trim() }
    }

    @Test fun longDetailsScrollWithLargeTextInLandscapeAndCancellationStaysPending() {
        val oldScale = shell("settings get system font_scale").toFloatOrNull() ?: 1f
        try {
            shell("settings put system font_scale 1.3")
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                waitUi(scenario) { it.resources.configuration.fontScale >= 1.29f }
                begin(scenario, (1..18).joinToString("\n") { "Шаг $it: подробности операции и выбранного раздела" })
                open(scenario)
                AndroidUiCapture.save(scenario, "operation-running.png")
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                waitUi(scenario) { it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && dialog(it).isShowing }
                scenario.onActivity {
                    dialog(it).findViewById<NestedScrollView>(R.id.operationCenterScroll)!!.fullScroll(View.FOCUS_DOWN)
                }
                waitUi(scenario) { fullyVisible(dialog(it).findViewById(R.id.btnOperationCenterCancel)!!) }
                scenario.onActivity {
                    assertTrue(fullyVisible(dialog(it).findViewById(R.id.btnOperationCenterMinimize)!!))
                    assertTrue(dialog(it).findViewById<NestedScrollView>(R.id.operationCenterScroll)!!.scrollY > 0)
                }
                scenario.recreate()
                waitUi(scenario) { dialog(it).isShowing && fullyVisible(dialog(it).findViewById(R.id.btnOperationCenterCancel)!!) }
                AndroidUiCapture.save(scenario, "operation-landscape-large.png")
                scenario.onActivity {
                    val sheet = dialog(it)
                    sheet.findViewById<View>(R.id.btnOperationCenterCancel)!!.performClick()
                    assertFalse(sheet.findViewById<View>(R.id.btnOperationCenterCancel)!!.isEnabled)
                    assertFalse(it.findViewById<View>(R.id.btnCancel).isEnabled)
                    assertTrue("Cancel request must not fabricate a terminal result", vm().operationActive.value!!)
                    assertEquals(View.GONE, sheet.findViewById<View>(R.id.tvOperationCenterPercent)!!.visibility)
                    sheet.findViewById<NestedScrollView>(R.id.operationCenterScroll)!!.scrollTo(0, 0)
                }
                AndroidUiCapture.save(scenario, "operation-cancelling.png")
                scenario.onActivity { active(false) }
            }
        } finally { shell("settings put system font_scale $oldScale") }
    }

    @Test fun oldToolsSelectionFallsBackToQuickAndMassStillWorks() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.findViewById<View>(R.id.tabFastboot).performClick()
                // Simulate a Bundle written by a version whose section 2 was Tools.
                MainActivity::class.java.getDeclaredField("selectedFastbootSection").apply { isAccessible = true }.setInt(it, 2)
            }
            scenario.recreate()
            scenario.onActivity {
                assertEquals(View.GONE, it.findViewById<View>(R.id.btnFastbootSectionTools).visibility)
                assertEquals(View.GONE, it.findViewById<View>(R.id.fastbootToolsSection).visibility)
                assertEquals(View.VISIBLE, it.findViewById<View>(R.id.fastbootQuickSection).visibility)
                it.findViewById<View>(R.id.btnFastbootSectionMass).performClick()
            }
            scenario.recreate()
            scenario.onActivity {
                assertEquals(View.VISIBLE, it.findViewById<View>(R.id.fastbootMassSection).visibility)
                assertEquals(View.GONE, it.findViewById<View>(R.id.fastbootToolsSection).visibility)
            }
        }
    }
}
