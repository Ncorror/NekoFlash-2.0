package ru.forum.adbfastboottool

/** SYNC v1 STAT carries only uint32 size; zero also occurs for virtual files. */
internal object AdbSyncSizePolicy {
    fun matchesLegacyStat(expected: Long?, received: Long): Boolean {
        require(received >= 0L)
        return expected == null || expected == 0L || (received and 0xFFFF_FFFFL) == expected
    }
}
