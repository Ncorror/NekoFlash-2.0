package ru.forum.adbfastboottool

import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.LocaleList
import android.view.View
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Installed-app UI checks use emulator fixtures only and never execute a device operation. */
@RunWith(AndroidJUnit4::class)
class InterfacePolishAndroidTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val prefs get() = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private var previousLanguage: String? = null
    private var previousPlatformLanguage = ""
    private val state get() = File(context.filesDir, "execution-state")
    private val fixture get() = File(context.filesDir, "ui-queue-fixture.img")
    private val tabs = listOf(
        Triple(R.id.tabHome, R.id.pageHome, "home"),
        Triple(R.id.tabFastboot, R.id.containerFastboot, "quick"),
        Triple(R.id.tabAdb, R.id.containerAdb, "sideload"),
        Triple(R.id.tabUnlock, R.id.pageUnlock, "unlock"),
        Triple(R.id.tabSettings, R.id.pageSettings, "settings")
    )

    @Before fun prepareEmulator() {
        previousLanguage = prefs.getString("language_tag", null)
        if (Build.VERSION.SDK_INT >= 33) {
            previousPlatformLanguage = context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
        }
        prefs.edit().putString("language_tag", "ru").commit()
        instrumentation.runOnMainSync {
            OnboardingGate.complete()
            DeviceSessionStore.viewModelStore.clear()
            setTestLocale("ru")
        }
        state.deleteRecursively()
    }

    @After fun clearEmulatorFixtures() {
        instrumentation.runOnMainSync {
            DeviceSessionStore.viewModelStore.clear()
            setTestLocale(if (Build.VERSION.SDK_INT >= 33) previousPlatformLanguage else previousLanguage.orEmpty())
        }
        prefs.edit().putString("language_tag", previousLanguage).commit()
        state.deleteRecursively()
        fixture.delete()
    }

    private fun vm() = DeviceSessionStore.get(context.applicationContext as Application)
    private fun setTestLocale(tag: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            // No active AppCompatActivity exists in setup; use the framework's per-app locale API.
            context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }
    }
    private fun list(activity: MainActivity) = activity.findViewById<RecyclerView>(R.id.rvMassQueue)
    private fun waitUi(scenario: ActivityScenario<MainActivity>, condition: (MainActivity) -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (true) {
            var ready = false
            scenario.onActivity { ready = condition(it) }
            if (ready) return
            check(System.nanoTime() < deadline) { "UI did not reach the expected state" }
            Thread.sleep(20)
        }
    }
    private fun fullyVisible(view: View) {
        val rect = Rect()
        assertTrue(view.getGlobalVisibleRect(rect))
        assertEquals(view.height, rect.height())
        assertEquals(view.width, rect.width())
    }
    private fun openMass(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity {
            it.findViewById<View>(R.id.tabFastboot).performClick()
            it.findViewById<View>(R.id.btnFastbootSectionMass).performClick()
        }
        waitUi(scenario) { list(it).childCount > 0 }
    }

    @Test fun thousandRowsRecycleAndKeepOrderFormAndScrollAcrossRecreation() {
        fixture.writeBytes(ByteArray(512))
        val source = FirmwareSelection.from(FirmwareSource.WorkspaceFile(fixture)).verify()
        val items = (0 until 1000).map { source.queueItem("p${it.toString().padStart(4, '0')}") }
        OperationRecoveryStore(state).writeDraft(FlashOperationDraft(items))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openMass(scenario)
            scenario.onActivity { activity ->
                assertEquals(1001, list(activity).adapter!!.itemCount)
                assertEquals("Populated ConcatAdapter must allow saved scroll restoration",
                    RecyclerView.Adapter.StateRestorationPolicy.ALLOW,
                    list(activity).adapter!!.stateRestorationPolicy)
                assertTrue("Queue inflated too many views", list(activity).childCount < 30)
                val spinner = activity.findViewById<Spinner>(R.id.spinMassPartition)
                spinner.setSelection(spinner.count - 1)
                activity.findViewById<EditText>(R.id.edMassManualPartition).setText("vendor_boot_b")
            }
            AndroidUiCapture.save(scenario, "mass-1000-header.png")
            // The first RecyclerView layout may still be consuming a queued
            // layout pass on API 26. Request scroll after that frame, then
            // require the *actual* adapter row 251 before changing its order.
            scenario.onActivity {
                val queue = list(it)
                queue.post { (queue.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(250, 0) }
            }
            waitUi(scenario) { list(it).findViewHolderForAdapterPosition(251) != null }
            scenario.onActivity { activity ->
                assertNull(activity.findViewById<View>(R.id.spinMassPartition))
                val row = list(activity).findViewHolderForAdapterPosition(251)!!.itemView
                assertTrue(row.findViewById<TextView>(R.id.tvQueuePartition).text.contains("p0250"))
                row.findViewById<View>(R.id.btnQueueUp).performClick()
                assertEquals("p0250", vm().currentFlashOperationDraft().items[249].partition)
            }
            waitUi(scenario) {
                list(it).findViewHolderForAdapterPosition(250)?.itemView
                    ?.findViewById<TextView>(R.id.tvQueuePartition)?.text?.contains("p0250") == true
            }
            scenario.onActivity {
                list(it).findViewHolderForAdapterPosition(250)!!.itemView.findViewById<View>(R.id.btnQueueDown).performClick()
                assertEquals("p0250", vm().currentFlashOperationDraft().items[250].partition)
            }
            waitUi(scenario) {
                list(it).findViewHolderForAdapterPosition(251)?.itemView
                    ?.findViewById<TextView>(R.id.tvQueuePartition)?.text?.contains("p0250") == true
            }
            var firstVisible = -1
            var anchorText = ""
            scenario.onActivity {
                list(it).findViewHolderForAdapterPosition(251)!!.itemView.findViewById<View>(R.id.btnQueueRemove).performClick()
                assertEquals(items.filter { item -> item.partition != "p0250" }, vm().currentFlashOperationDraft().items)
            }
            waitUi(scenario) { list(it).adapter!!.itemCount == 1000 }
            scenario.onActivity {
                val queue = list(it)
                firstVisible = (queue.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
                assertTrue("Queue lost its deep scroll anchor before recreation", firstVisible > 1)
                anchorText = queue.findViewHolderForAdapterPosition(firstVisible)!!
                    .itemView.findViewById<TextView>(R.id.tvQueuePartition).text.toString()
            }
            scenario.recreate()
            waitUi(scenario) {
                val queue = list(it)
                queue.adapter!!.itemCount == 1000 &&
                    (queue.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition() == firstVisible &&
                    queue.findViewHolderForAdapterPosition(firstVisible)?.itemView
                        ?.findViewById<TextView>(R.id.tvQueuePartition)?.text?.toString() == anchorText
            }
            scenario.onActivity {
                assertEquals(View.VISIBLE, it.findViewById<View>(R.id.fastbootMassSection).visibility)
                fullyVisible(it.findViewById(R.id.btnMassExecute))
                assertTrue(list(it).childCount < 30)
                assertFalse(vm().operationActive.value!!)
            }
            AndroidUiCapture.save(scenario, "mass-999-restored.png")
            scenario.onActivity { (list(it).layoutManager as LinearLayoutManager).scrollToPositionWithOffset(0, 0) }
            waitUi(scenario) { it.findViewById<Spinner>(R.id.spinMassPartition) != null }
            scenario.onActivity {
                val spinner = it.findViewById<Spinner>(R.id.spinMassPartition)
                assertEquals(spinner.count - 1, spinner.selectedItemPosition)
                assertEquals("vendor_boot_b", it.findViewById<EditText>(R.id.edMassManualPartition).text.toString())
            }
        }
    }

    private fun capturePages(scenario: ActivityScenario<MainActivity>, prefix: String) {
        waitUi(scenario) { it.resources.configuration.locales[0].language == "ru" }
        tabs.forEach { (tab, page, name) ->
            scenario.onActivity {
                it.findViewById<View>(tab).performClick()
                if (tab == R.id.tabFastboot) it.findViewById<View>(R.id.btnFastbootSectionQuick).performClick()
            }
            AndroidUiCapture.save(scenario, "$prefix-$name.png")
            // An animation-free emulator can still deliver a final system-bar
            // resize after the captured frame. Keep the exact full visibility
            // requirement, but wait for stable window insets before asserting.
            waitUi(scenario) { activity ->
                tabs.all { (button, _, _) ->
                    val label = activity.findViewById<TextView>(button)
                    val rect = Rect()
                    label.isShown && label.getGlobalVisibleRect(rect) &&
                        rect.width() == label.width && rect.height() == label.height
                }
            }
            scenario.onActivity {
                assertEquals(View.VISIBLE, it.findViewById<View>(page).visibility)
                assertEquals("Главная", it.findViewById<TextView>(R.id.tabHome).text.toString())
                tabs.forEach { (button, _, _) ->
                    val label = it.findViewById<TextView>(button)
                    fullyVisible(label)
                    assertEquals("Navigation label wraps: ${label.text}", 1, label.lineCount)
                }
                if (page == R.id.containerFastboot) {
                    listOf(R.id.btnFastbootSectionQuick, R.id.btnFastbootSectionMass, R.id.btnFastbootSectionTools).forEach { id ->
                        val label = it.findViewById<TextView>(id)
                        assertEquals("Section label wraps: ${label.text}", 1, label.lineCount)
                    }
                }
                assertFalse(vm().operationActive.value!!)
            }
        }
        openMass(scenario)
        AndroidUiCapture.save(scenario, "$prefix-mass-empty.png")
        scenario.onActivity {
            assertEquals("Header alone must not permit queue state restoration",
                RecyclerView.Adapter.StateRestorationPolicy.PREVENT,
                list(it).adapter!!.stateRestorationPolicy)
            assertFalse(it.findViewById<View>(R.id.btnMassExecute).isEnabled)
            fullyVisible(it.findViewById(R.id.btnMassExecute))
            if (it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                fullyVisible(it.findViewById(R.id.spinMassPartition))
                fullyVisible(it.findViewById(R.id.btnMassSelectImage))
            }
            it.findViewById<View>(R.id.btnFastbootSectionTools).performClick()
        }
        AndroidUiCapture.save(scenario, "$prefix-tools.png")
    }

    @Test fun allFiveTabsAndFastbootSectionsRenderInRussian() {
        ActivityScenario.launch(MainActivity::class.java).use { capturePages(it, "portrait") }
    }

    private fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command).use {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().use { reader -> reader.readText().trim() }
    }

    @Test fun largeFontAndLandscapeKeepNavigationAndMassActionVisible() {
        val oldScale = shell("settings get system font_scale").toFloatOrNull() ?: 1f
        try {
            shell("settings put system font_scale 1.3")
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                waitUi(scenario) { it.resources.configuration.fontScale >= 1.29f }
                capturePages(scenario, "large")
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                waitUi(scenario) { it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
                capturePages(scenario, "landscape-large")
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                waitUi(scenario) { it.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
            }
        } finally { shell("settings put system font_scale $oldScale") }
    }
}
