package ru.forum.adbfastboottool

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Welcome / onboarding screen.
 *
 * The original user-owned artwork remains an immutable centerCrop background.
 * Entry authorization is session-scoped:
 * every cold/full app restart returns here. Exported USB attach intents cannot bypass
 * the gate. A clear file-access action is available, but never blocks entry.
 */
class WelcomeActivity : AppCompatActivity() {

    private lateinit var btnPrimaryAction: Button
    private lateinit var tvStorageChip: TextView
    private var pendingUsbAttach = false

    private val settingsLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) {
            refreshGateState()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pendingUsbAttach = intent.getBooleanExtra(EXTRA_PENDING_USB_ATTACH, false)
        if (OnboardingGate.canEnterMain()) {
            launchMainAfterGate()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) { ThemePalette.extractAndCache(applicationContext) }
        setContentView(R.layout.activity_welcome)

        btnPrimaryAction = findViewById(R.id.btnPrimaryAction)
        tvStorageChip = findViewById(R.id.tvStorageChip)

        btnPrimaryAction.setOnClickListener { handlePrimaryAction() }
        tvStorageChip.setOnClickListener { openStoragePermissionSettings() }

        refreshGateState()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingUsbAttach = pendingUsbAttach || intent.getBooleanExtra(EXTRA_PENDING_USB_ATTACH, false)
    }

    override fun onResume() {
        super.onResume()
        if (::tvStorageChip.isInitialized) refreshGateState()
    }

    private fun handlePrimaryAction() {
        // Entry is available without a mandatory risk checkbox or storage grant.
        // File-dependent workflows request their actual permission when needed.
        OnboardingGate.complete()
        launchMainAfterGate()
    }

    private fun launchMainAfterGate() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_STARTUP_SCAN_AFTER_WELCOME, pendingUsbAttach)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
        finish()
    }

    /** Recheck access after returning from Android system settings. */
    private fun refreshGateState() {
        val storageGranted = PermissionGate.hasStorage(this)
        tvStorageChip.text = getString(
            if (storageGranted) R.string.onboarding_storage_granted
            else R.string.onboarding_storage_grant
        )
        tvStorageChip.setCompoundDrawablesRelativeWithIntrinsicBounds(
            if (storageGranted) R.drawable.ic_status_check_green
            else R.drawable.ic_status_warning_amber, 0, 0, 0
        )
        tvStorageChip.setTextColor(getColor(R.color.text_primary))
        tvStorageChip.alpha = 1f
        btnPrimaryAction.text = getString(R.string.onboarding_enter_button)
        btnPrimaryAction.isEnabled = true
        btnPrimaryAction.alpha = 1f
    }

    private fun openStoragePermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                settingsLauncher.launch(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = "package:$packageName".toUri()
                    }
                )
            } catch (_: Exception) {
                try {
                    settingsLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (_: Exception) {
                    openApplicationDetails(getString(R.string.perm_open_settings_manually))
                }
            }
        } else if (PermissionGate.hasStorage(this)) {
            openApplicationDetails(getString(R.string.permission_status_granted))
        } else {
            requestPermissions(
                arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                REQ_STORAGE
            )
        }
    }

    private fun openApplicationDetails(message: String) {
        try {
            settingsLauncher.launch(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = "package:$packageName".toUri()
                }
            )
        } catch (_: Exception) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_STORAGE) refreshGateState()
    }

    companion object {
        const val EXTRA_PENDING_USB_ATTACH = "nekoflash_pending_usb_attach"
        const val EXTRA_STARTUP_SCAN_AFTER_WELCOME = "nekoflash_startup_scan_after_welcome"
        private const val REQ_STORAGE = 200
    }
}
