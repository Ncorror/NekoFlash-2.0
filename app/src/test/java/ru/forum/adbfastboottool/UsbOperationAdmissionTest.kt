package ru.forum.adbfastboottool

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbOperationAdmissionTest {
    @Test fun noUsbRefusesEveryConnectedDeviceOperationBeforeService() {
        for (kind in UsbOperationAdmission.RequiredTransport.values()) {
            assertFalse(UsbOperationAdmission.allowed(kind, fastbootConnected = false, adbConnected = false))
        }
    }

    @Test fun fastbootOperationDoesNotUseAdbConnectionAsPermissionToExecute() {
        assertFalse(UsbOperationAdmission.allowed(
            UsbOperationAdmission.RequiredTransport.FASTBOOT, fastbootConnected = false, adbConnected = true))
        assertTrue(UsbOperationAdmission.allowed(
            UsbOperationAdmission.RequiredTransport.FASTBOOT, fastbootConnected = true, adbConnected = false))
    }

    @Test fun adbOperationDoesNotUseFastbootConnectionAsPermissionToExecute() {
        assertFalse(UsbOperationAdmission.allowed(
            UsbOperationAdmission.RequiredTransport.ADB, fastbootConnected = true, adbConnected = false))
        assertTrue(UsbOperationAdmission.allowed(
            UsbOperationAdmission.RequiredTransport.ADB, fastbootConnected = false, adbConnected = true))
    }

    @Test fun genericUsbOperationAdmitsEitherConnectedTransport() {
        val required = UsbOperationAdmission.RequiredTransport.ANY_USB
        assertTrue(UsbOperationAdmission.allowed(required, fastbootConnected = true, adbConnected = false))
        assertTrue(UsbOperationAdmission.allowed(required, fastbootConnected = false, adbConnected = true))
        assertTrue(UsbOperationAdmission.allowed(required, fastbootConnected = true, adbConnected = true))
    }
}
