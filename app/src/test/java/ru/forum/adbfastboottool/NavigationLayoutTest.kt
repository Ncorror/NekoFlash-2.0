package ru.forum.adbfastboottool

import android.app.Activity
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34])
class NavigationLayoutTest {
    @Test fun largeRussianLabelsLeaveRoomForContentAndStayInsideNavigation() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = controller.get()
            val config = Configuration(activity.resources.configuration).apply {
                fontScale = 1.3f
                setLocale(Locale.forLanguageTag("ru"))
            }
            val context = ContextThemeWrapper(activity.createConfigurationContext(config), R.style.Theme_NekoFlash)
            val root = LayoutInflater.from(context).inflate(R.layout.activity_main, null, false)
            val density = context.resources.displayMetrics.density
            val width = (320 * density).toInt()
            val height = (640 * density).toInt()
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, width, height)
            val navigation = root.findViewById<LinearLayout>(R.id.bottomNavigation)
            assertTrue("Navigation consumed the content area", navigation.height < 120 * density)
            assertTrue(root.findViewById<View>(R.id.contentContainer).height > height / 2)
            for (index in 0 until navigation.childCount) {
                val child = navigation.getChildAt(index)
                assertTrue("Navigation child clipped at top", child.top >= 0)
                assertTrue("Navigation child clipped at bottom", child.bottom <= navigation.height)
            }
        } finally { controller.pause().stop().destroy() }
    }
}
