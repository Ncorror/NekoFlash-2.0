package ru.forum.adbfastboottool

/** Acknowledged unique Recovery blocks; retries do not advance progress. */
internal class SideloadCoverage(private val fileSize: Long, private val blockSize: Int) {
    private val acknowledged = HashSet<Int>()
    var bytes: Long = 0L
        private set

    fun acknowledge(block: Int) {
        val offset = block.toLong() * blockSize
        require(block >= 0 && offset < fileSize)
        if (acknowledged.add(block)) bytes += minOf(blockSize.toLong(), fileSize - offset)
    }
}
