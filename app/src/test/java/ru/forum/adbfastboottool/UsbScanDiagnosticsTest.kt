package ru.forum.adbfastboottool

import org.junit.Assert.*
import org.junit.Test

class UsbScanDiagnosticsTest {
    private fun evidence(devices: Int, adb: Int = 0, fastboot: Int = 0, permitted: Int = 0,
        host: Boolean = true
    ) = UsbScanDiagnostics.Evidence(host, devices, adb, fastboot, permitted)

    @Test fun missingUsbHostIsNotReportedAsAdbProtocolError() {
        assertTrue(UsbScanDiagnostics.explain(evidence(0, host = false)).startsWith("USB_HOST_UNAVAILABLE:"))
    }

    @Test fun emptyUsbHostEnumerationDoesNotClaimAdbFailure() {
        val message = UsbScanDiagnostics.explain(evidence(0))
        assertTrue(message.startsWith("USB_ENUMERATION_EMPTY:"))
        assertTrue(message.contains("ADB handshake has not started"))
    }

    @Test fun connectedDeviceWithoutAdbInterfaceIsDistinctFromEmptyBus() {
        assertTrue(UsbScanDiagnostics.explain(evidence(1)).startsWith("USB_DEVICE_NO_ADB_INTERFACE:"))
    }

    @Test fun fastbootIsNotMislabelledAsAdbOnModernDevices() {
        assertTrue(UsbScanDiagnostics.explain(evidence(1, fastboot = 1)).startsWith("USB_FASTBOOT_ONLY:"))
    }

    @Test fun recognizedAdbWithoutUsbPermissionShowsHostPermissionStage() {
        assertTrue(UsbScanDiagnostics.explain(evidence(1, adb = 1)).startsWith("USB_ADB_PERMISSION_PENDING:"))
    }

    @Test fun usbHostPermissionDoesNotImplyAdbRsaAuthorization() {
        val message = UsbScanDiagnostics.explain(evidence(1, adb = 1, permitted = 1))
        assertTrue(message.startsWith("USB_ADB_INTERFACE_READY:"))
        assertTrue(message.contains("does not prove ADB authorization"))
    }
}
