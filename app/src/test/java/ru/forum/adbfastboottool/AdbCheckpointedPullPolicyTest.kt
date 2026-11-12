package ru.forum.adbfastboottool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbCheckpointedPullPolicyTest {
    @Test
    fun largeNormalAndroidShellV2FileUsesCheckpointedMode() {
        assertTrue(AdbCheckpointedPullPolicy.shouldUse(
            isDeviceMode = true,
            supportsShellV2 = true,
            fileSize = AdbCheckpointedPullPolicy.MIN_FILE_BYTES
        ))
        assertFalse(AdbCheckpointedPullPolicy.shouldUse(
            isDeviceMode = false,
            supportsShellV2 = true,
            fileSize = 256L * 1024L * 1024L
        ))
        assertFalse(AdbCheckpointedPullPolicy.shouldUse(
            isDeviceMode = true,
            supportsShellV2 = false,
            fileSize = 256L * 1024L * 1024L
        ))
    }

    @Test
    fun rangesAreFixedAndFinalRangeIsExact() {
        val four = AdbCheckpointedPullPolicy.RANGE_BYTES.toLong()
        assertEquals(
            AdbCheckpointedPullPolicy.Range(0L, four.toInt()),
            AdbCheckpointedPullPolicy.nextRange(four * 2 + 123L, 0L)
        )
        assertEquals(
            AdbCheckpointedPullPolicy.Range(four * 2, 123),
            AdbCheckpointedPullPolicy.nextRange(four * 2 + 123L, four * 2)
        )
        assertNull(AdbCheckpointedPullPolicy.nextRange(four * 2, four * 2))
    }

    @Test
    fun onlyDurableWholeRangesAreValidResumePoints() {
        val four = AdbCheckpointedPullPolicy.RANGE_BYTES.toLong()
        val total = four * 3 + 17L
        assertTrue(AdbCheckpointedPullPolicy.isValidCheckpointLength(total, 0L))
        assertTrue(AdbCheckpointedPullPolicy.isValidCheckpointLength(total, four))
        assertTrue(AdbCheckpointedPullPolicy.isValidCheckpointLength(total, four * 2))
        assertTrue(AdbCheckpointedPullPolicy.isValidCheckpointLength(total, total))
        assertFalse(AdbCheckpointedPullPolicy.isValidCheckpointLength(total, four + 1L))
        assertFalse(AdbCheckpointedPullPolicy.isValidCheckpointLength(total, total + 1L))
    }

    @Test
    fun sha256sumParserIsStrictAndCaseNormalizing() {
        val hash = "A".repeat(64)
        assertEquals("a".repeat(64), AdbCheckpointedPullPolicy.parseSha256Sum("${hash}  /system/file\n"))
        assertNull(AdbCheckpointedPullPolicy.parseSha256Sum("not-a-hash"))
        assertNull(AdbCheckpointedPullPolicy.parseSha256Sum("abc123"))
    }

    @Test
    fun checkpointIdentityChangesWithSourceOrDestination() {
        val hashA = "1".repeat(64)
        val hashB = "2".repeat(64)
        val first = AdbCheckpointedPullPolicy.checkpointFileName(
            "/host/a.bin", "/system/a.bin", 100L, 7, hashA
        )
        assertEquals(first, AdbCheckpointedPullPolicy.checkpointFileName(
            "/host/a.bin", "/system/a.bin", 100L, 7, hashA
        ))
        assertNotEquals(first, AdbCheckpointedPullPolicy.checkpointFileName(
            "/host/b.bin", "/system/a.bin", 100L, 7, hashA
        ))
        assertNotEquals(first, AdbCheckpointedPullPolicy.checkpointFileName(
            "/host/a.bin", "/system/a.bin", 100L, 7, hashB
        ))
        assertTrue(first.startsWith(".nekoflash-pull-"))
        assertTrue(first.endsWith(".part"))
    }
}
