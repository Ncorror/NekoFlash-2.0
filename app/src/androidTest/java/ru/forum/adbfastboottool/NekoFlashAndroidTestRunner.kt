package ru.forum.adbfastboottool

import android.app.Activity
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import java.util.IdentityHashMap

/**
 * Instrumentation-only API 26 large-font fixture.
 *
 * ActivityScenario runs AFTER Activity.attach(), but the system font setting
 * is not reliably propagated into Activity Resources on Android 8. Apply the
 * override after attach and BEFORE MainActivity.onCreate() inflates UI.
 *
 * The existing OperationCenterAndroidTest remains fully enabled on API26.
 * All other tests, other activities and production APK code are unaffected.
 */
class NekoFlashAndroidTestRunner : AndroidJUnitRunner() {
    @Volatile private var legacyFontScale: Float? = null
    private val originalConfigurations = IdentityHashMap<Resources, Configuration>()

    @Synchronized
    fun enableLegacyFontScale(fontScale: Float) {
        check(Build.VERSION.SDK_INT < 29) { "Use system font settings on API 29+" }
        check(fontScale > 0f) { "Invalid font scale" }
        check(legacyFontScale == null && originalConfigurations.isEmpty()) {
            "Legacy font fixture already active"
        }
        legacyFontScale = fontScale
    }

    override fun callActivityOnCreate(activity: Activity, icicle: Bundle?) {
        val scale = legacyFontScale
        if (scale != null && Build.VERSION.SDK_INT < 29 && activity is MainActivity) {
            val resources = activity.resources
            synchronized(this) {
                if (!originalConfigurations.containsKey(resources)) {
                    originalConfigurations[resources] = Configuration(resources.configuration)
                }
            }
            val config = Configuration(resources.configuration).apply { fontScale = scale }
            @Suppress("DEPRECATION")
            resources.updateConfiguration(config, resources.displayMetrics)
        }
        super.callActivityOnCreate(activity, icicle)
    }

    @Synchronized
    fun restoreLegacyFontScale() {
        // Stop affecting future Activity launches before restoring resources.
        legacyFontScale = null
        try {
            for ((resources, original) in originalConfigurations) {
                @Suppress("DEPRECATION")
                resources.updateConfiguration(original, resources.displayMetrics)
            }
        } finally {
            originalConfigurations.clear()
        }
    }
}
