package ru.forum.adbfastboottool

import android.os.Build
import org.robolectric.shadows.ShadowEnvironment

internal object RobolectricStorageFixture {
    @Suppress("NewApi")
    fun installPrimaryExternalDirectory() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ShadowEnvironment.addExternalDir("primary")
        }
    }
}
