package ru.forum.adbfastboottool

import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue

/** Wait for rendered frames, not just an empty main-thread message queue. */
internal object AndroidUiCapture {
    fun save(scenario: ActivityScenario<MainActivity>, name: String, directory: String = "ui-test-output") {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val frames = CountDownLatch(1)
        scenario.onActivity { activity ->
            activity.window.decorView.apply {
                invalidate()
                postOnAnimation { postOnAnimation { frames.countDown() } }
            }
        }
        assertTrue("Window did not render", frames.await(5, TimeUnit.SECONDS))
        instrumentation.uiAutomation.waitForIdle(200, 5000)
        val output = File(instrumentation.targetContext.filesDir, directory).also { it.mkdirs() }
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(output, name).outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { bitmap.recycle() }
    }
}
