package ru.forum.adbfastboottool

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager

/**
 * File/workflow permission status helper.
 *
 * Storage access is optional for entering the app and direct SAF flows.
 * It is required only for workspace operations that directly access
 * /sdcard/Download/NekoFlash. Notifications/battery status are recommendations.
 */
object PermissionGate {

    data class Status(
        val storage: Boolean,
        val notifications: Boolean,
        val batteryOptIgnored: Boolean
    ) {
    }

    fun status(context: Context): Status = Status(
        storage = hasStorage(context),
        notifications = hasNotifications(context),
        batteryOptIgnored = isBatteryOptIgnored(context)
    )

    fun hasStorage(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val readGranted = context.checkSelfPermission(
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            val writeGranted = context.checkSelfPermission(
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            readGranted && writeGranted
        }
    }

    fun hasNotifications(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isBatteryOptIgnored(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }
}
