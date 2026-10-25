package ru.forum.adbfastboottool

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/** One process session shared by screens and the execution service. Holds no Activity. */
internal object DeviceSessionStore : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
    private var uiClients = 0
    private var receiverRegistered = false

    @Synchronized fun get(application: Application): DeviceViewModel {
        val session = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                DeviceViewModel(application, SavedStateHandle()) as T
        })[DeviceViewModel::class.java]
        if (!receiverRegistered) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action != UsbManager.ACTION_USB_DEVICE_DETACHED || hasUi()) return
                    val device = IntentCompat.getParcelableExtra(intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    if (device != null && session.isCurrentUsbDevice(device)) session.disconnectCurrent()
                }
            }
            ContextCompat.registerReceiver(application, receiver,
                IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED), ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        }
        return session
    }

    @Synchronized fun attachUi(application: Application): DeviceViewModel = get(application).also { uiClients++ }
    @Synchronized fun detachUi() { uiClients = (uiClients - 1).coerceAtLeast(0) }
    @Synchronized private fun hasUi(): Boolean = uiClients > 0
}
