package ru.forum.adbfastboottool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbHostReceiveWindowTest {
    @Test fun negotiatedPacketMaximumMatchesHostUsbReadUnit() {
        assertEquals(16 * 1024, AdbHostReceiveWindow.ADVERTISED_MAXDATA)
        assertTrue(AdbHostReceiveWindow.ADVERTISED_MAXDATA >= 4096)
        assertEquals(0, AdbHostReceiveWindow.ADVERTISED_MAXDATA % 512)
    }

    @Test fun adbFrameIsBoundedButLogicalFileSizeIsNot() {
        assertTrue(AdbHostReceiveWindow.isAccepted(0))
        assertTrue(AdbHostReceiveWindow.isAccepted(24))
        assertTrue(AdbHostReceiveWindow.isAccepted(16_384))
        assertFalse(AdbHostReceiveWindow.isAccepted(-1))
        assertFalse(AdbHostReceiveWindow.isAccepted(16_385))
        assertFalse(AdbHostReceiveWindow.isAccepted(1_048_576))
        // The SYNC protocol can split any sized file into multiple WRTE
        // frames; there is no 16 KiB limit on a 256 MiB file.
        val logicalFileBytes = 256L * 1024L * 1024L
        val requiredFrames = (logicalFileBytes + AdbHostReceiveWindow.ADVERTISED_MAXDATA - 1) /
            AdbHostReceiveWindow.ADVERTISED_MAXDATA
        assertEquals(16_384L, requiredFrames)
    }
}
