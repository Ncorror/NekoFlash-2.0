package ru.forum.adbfastboottool

/**
 * Stage-based, read-only USB diagnostics. A new Android target is not classified as
 * an ADB protocol failure until Android USB Host has enumerated an ADB interface.
 */
internal object UsbScanDiagnostics {
    data class Evidence(
        val hostFeatureAvailable: Boolean,
        val deviceCount: Int,
        val adbCandidateCount: Int,
        val fastbootCandidateCount: Int,
        val permittedAdbDeviceCount: Int
    ) {
        init {
            require(deviceCount >= 0 && adbCandidateCount >= 0 &&
                fastbootCandidateCount >= 0 && permittedAdbDeviceCount >= 0)
        }
    }

    fun explain(e: Evidence): String = when {
        !e.hostFeatureAvailable ->
            "USB_HOST_UNAVAILABLE: Android does not report USB Host support. ADB handshake was not attempted."
        e.deviceCount == 0 ->
            "USB_ENUMERATION_EMPTY: Android Host sees no connected USB device. Check the host OTG/USB role, USB data cable and target connection mode; ADB handshake has not started."
        e.adbCandidateCount == 0 && e.fastbootCandidateCount == 0 ->
            "USB_DEVICE_NO_ADB_INTERFACE: Android Host sees ${e.deviceCount} USB device(s), but none exposes a recognized ADB/Fastboot bulk interface. Check USB debugging and USB mode on the target; inspect interface descriptors below."
        e.adbCandidateCount == 0 ->
            "USB_FASTBOOT_ONLY: A Fastboot-compatible USB interface is visible, but no ADB interface. Check whether the target is booted into Android with USB debugging enabled."
        e.permittedAdbDeviceCount == 0 ->
            "USB_ADB_PERMISSION_PENDING: ADB interface is visible. Tap Scan to request host USB access, then approve the USB debugging RSA prompt on the unlocked target."
        else ->
            "USB_ADB_INTERFACE_READY: Android Host sees ADB and has USB permission. If connection still fails, inspect ADB CNXN/AUTH handshake logs; this stage alone does not prove ADB authorization."
    }
}
