package ru.forum.adbfastboottool

import org.junit.Assert.*
import org.junit.Test

class TransferReceivePolicyTest {
    @Test fun exactFetchSizeIsRequired() {
        assertEquals(16L, FastbootFetchPolicy.dataSize("00000010", 16L))
        assertNull(FastbootFetchPolicy.dataSize("0000000f", 16L))
        assertNull(FastbootFetchPolicy.dataSize("00000011", 16L))
    }
    @Test fun malformedWireSizesAreRejected() {
        listOf("10", "0x000010", "0000000g", "-0000001", "00000000", "100000000").forEach {
            assertNull(FastbootFetchPolicy.dataSize(it, null))
        }
    }
    @Test fun incompleteOrChangedPullDoesNotMatchStat() {
        assertTrue(AdbSyncSizePolicy.matchesLegacyStat(100L, 100L))
        assertFalse(AdbSyncSizePolicy.matchesLegacyStat(100L, 99L))
        assertFalse(AdbSyncSizePolicy.matchesLegacyStat(100L, 101L))
    }
    @Test fun legacyStatDoesNotInventFourGiBLimitOrRejectVirtualFiles() {
        assertTrue(AdbSyncSizePolicy.matchesLegacyStat(17L, 0x1_0000_0011L))
        assertTrue(AdbSyncSizePolicy.matchesLegacyStat(0L, 100L))
        assertTrue(AdbSyncSizePolicy.matchesLegacyStat(null, 100L))
    }
}
