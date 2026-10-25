package ru.forum.adbfastboottool

import org.junit.Assert.*
import org.junit.Test

class SideloadCoverageTest {
    @Test fun retriesDoNotCountTwiceAndTailUsesActualLength() {
        val progress = SideloadCoverage(10, 4)
        progress.acknowledge(1); progress.acknowledge(1)
        assertEquals(4L, progress.bytes)
        progress.acknowledge(2); assertEquals(6L, progress.bytes)
        progress.acknowledge(0); assertEquals(10L, progress.bytes)
    }
    @Test fun invalidBlocksDoNotChangeProgress() {
        val progress = SideloadCoverage(10, 4)
        assertThrows(IllegalArgumentException::class.java) { progress.acknowledge(-1) }
        assertThrows(IllegalArgumentException::class.java) { progress.acknowledge(3) }
        assertEquals(0L, progress.bytes)
    }
}
