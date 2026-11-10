package ru.forum.adbfastboottool

import android.app.Activity
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34])
class SideloadSelectionUiTest {
    @Test fun chooserAndExplicitStartAreDifferentControlsAndStartDefaultsDisabled() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = controller.get()
            val themed = ContextThemeWrapper(activity, R.style.Theme_NekoFlash)
            val root = LayoutInflater.from(themed).inflate(R.layout.page_adb, null, false)
            val chooser = root.findViewById<Button>(R.id.btnAdbSideload)
            val start = root.findViewById<Button>(R.id.btnStartAdbSideload)
            val imported = root.findViewById<Button>(R.id.btnSideloadImport)
            val selected = root.findViewById<TextView>(R.id.tvSideloadSelectedZip)
            assertNotNull(chooser)
            assertNotNull(start)
            assertNotNull(imported)
            assertNotNull(selected)
            assertTrue("Choosing a ZIP must remain available independently", chooser.isEnabled)
            assertFalse("Sending may not start before selection and valid SIDELOAD mode", start.isEnabled)
            assertEquals(themed.getString(R.string.layout_choose_zip_sideload), chooser.text.toString())
            assertEquals(themed.getString(R.string.layout_sideload_start), start.text.toString())
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
