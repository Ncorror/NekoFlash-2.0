package ru.forum.adbfastboottool

import android.os.ParcelFileDescriptor
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class AdbSyncUsbInAndroidTest {
    @Test fun packagedJniPreservesIoctlErrnoAndLeavesDestinationUntouched() {
        assertTrue("Synchronous IN JNI must be packaged", AdbSyncUsbIn.available)
        // A valid non-USB descriptor exercises the real syscall and JNI result
        // path without touching hardware. Linux rejects USBDEVFS_BULK: ENOTTY.
        ParcelFileDescriptor.open(File("/dev/null"), ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            val destination = ByteArray(64) { 0x5a }
            val result = AdbSyncUsbIn.read(fd.fd, 0x81, destination, 7, 16, 5000)
            assertEquals(-1, result.result)
            assertEquals(25, result.errno)
            assertEquals(1, result.stage)
            assertTrue(result.elapsedNs >= 0)
            assertArrayEquals(ByteArray(64) { 0x5a }, destination)
            val invalid = AdbSyncUsbIn.read(fd.fd, 0x81, destination, 60, 16, 5000)
            assertEquals(0, invalid.stage)
            assertEquals(22, invalid.errno)
        }
    }
}
