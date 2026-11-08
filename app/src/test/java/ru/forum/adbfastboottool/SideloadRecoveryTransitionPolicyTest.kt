package ru.forum.adbfastboottool

import org.junit.Assert.*
import org.junit.Test

class SideloadRecoveryTransitionPolicyTest {
    private val lineageBanner =
        "sideload::ro.product.name=lineage_vayu;ro.product.model=M2102J20SG;ro.product.device=vayu;features=shell_v2,cmd"
    private val orangeFoxBanner =
        "sideload::ro.product.name=vayu;ro.product.model=Poco X3 Pro NFC (M2102J20SG);ro.product.device=vayu;features=shell_v2,cmd"

    private fun lineage() = SideloadRecoveryTransitionPolicy.fromAdbBanner(
        lineageBanner, 0x18d1, 0xd001
    )
    private fun orangeFox() = SideloadRecoveryTransitionPolicy.fromAdbBanner(
        orangeFoxBanner, 0x2717, 0xd001
    )

    @Test fun actualVayuRecoverySwitchIsObservedEvenWhenNewRecoveryBootsInSideload() {
        val evidence = SideloadRecoveryTransitionPolicy.observe(
            lineage(), orangeFox(), sameUsbSerial = true, transferFinished = true
        )
        assertNotNull(evidence)
        assertEquals("lineage_vayu", evidence!!.from.productName)
        assertEquals("vayu", evidence.to.productName)
        val detail = evidence.detail("OrangeFox-R11.3-vayu.zip")
        assertTrue(detail.contains("OrangeFox-R11.3-vayu.zip"))
        assertTrue(detail.contains("18D1:D001"))
        assertTrue(detail.contains("2717:D001"))
        assertTrue(detail.contains("unverified"))
        assertFalse(detail.contains("SUCCESS"))
    }

    @Test fun returningToNewRecoveryNormalModeIsAlsoObservable() {
        val replacement = orangeFoxBanner.replace("sideload::", "recovery::")
        val evidence = SideloadRecoveryTransitionPolicy.observe(
            lineage(),
            SideloadRecoveryTransitionPolicy.fromAdbBanner(replacement, 0x2717, 0xd001),
            sameUsbSerial = true, transferFinished = true
        )
        assertNotNull(evidence)
    }

    @Test fun usbDetachAndReconnectWithoutRecoveryChangeIsNotEvidence() {
        assertNull(SideloadRecoveryTransitionPolicy.observe(
            lineage(), lineage(), sameUsbSerial = true, transferFinished = true
        ))
    }

    @Test fun onlyChangingUsbVendorIsNotEnough() {
        val after = SideloadRecoveryTransitionPolicy.fromAdbBanner(lineageBanner, 0x2717, 0xd001)
        assertNull(SideloadRecoveryTransitionPolicy.observe(
            lineage(), after, sameUsbSerial = true, transferFinished = true
        ))
    }

    @Test fun onlyChangingRecoveryNameIsNotEnough() {
        val after = SideloadRecoveryTransitionPolicy.fromAdbBanner(orangeFoxBanner, 0x18d1, 0xd001)
        assertNull(SideloadRecoveryTransitionPolicy.observe(
            lineage(), after, sameUsbSerial = true, transferFinished = true
        ))
    }

    @Test fun differentDeviceOrUsbSerialCannotInheritPreviousAttempt() {
        val otherDevice = orangeFoxBanner.replace("ro.product.device=vayu", "ro.product.device=rodin")
        val other = SideloadRecoveryTransitionPolicy.fromAdbBanner(otherDevice, 0x2717, 0xd001)
        assertNull(SideloadRecoveryTransitionPolicy.observe(
            lineage(), other, sameUsbSerial = true, transferFinished = true
        ))
        assertNull(SideloadRecoveryTransitionPolicy.observe(
            lineage(), orangeFox(), sameUsbSerial = false, transferFinished = true
        ))
    }

    @Test fun unfinishedTransferCannotClaimRecoverySwitch() {
        assertNull(SideloadRecoveryTransitionPolicy.observe(
            lineage(), orangeFox(), sameUsbSerial = true, transferFinished = false
        ))
    }

    @Test fun normalAndroidBannerOrMissingPropertiesAreNotRecovery() {
        assertNull(SideloadRecoveryTransitionPolicy.fromAdbBanner(
            "device::ro.product.name=vayu;ro.product.device=vayu", 0x2717, 0xd001
        ))
        assertNull(SideloadRecoveryTransitionPolicy.fromAdbBanner(
            "sideload::ro.product.name=vayu", 0x2717, 0xd001
        ))
        assertNull(SideloadRecoveryTransitionPolicy.fromAdbBanner(
            "recovery::ro.product.name=vayu;ro.product.device=vayu", -1, 0xd001
        ))
    }
}
