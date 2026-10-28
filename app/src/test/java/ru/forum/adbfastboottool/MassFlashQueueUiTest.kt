package ru.forum.adbfastboottool

import android.app.Activity
import android.os.Bundle
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34])
class MassFlashQueueUiTest {
    class Screen : Activity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(R.style.Theme_NekoFlash)
            super.onCreate(savedInstanceState)
            setContentView(R.layout.page_fastboot)
        }
    }

    @Test fun formInflatesWithRecyclerLayoutParamsBeforeFirstRender() {
        val controller = Robolectric.buildActivity(Screen::class.java).setup()
        try {
            val activity = controller.get()
            val ui = MassFlashQueueUi(activity, { _, _ -> fail("Unexpected move") }, { fail("Unexpected remove") })
            ui.render(FlashOperationDraft(), QueueSourceState())
            assertTrue(ui.header.layoutParams is RecyclerView.LayoutParams)
            assertEquals(1, activity.findViewById<RecyclerView>(R.id.rvMassQueue).adapter!!.itemCount)
            assertFalse(ui.execute.isEnabled)
        } finally { controller.pause().stop().destroy() }
    }
}
