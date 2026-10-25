package ru.forum.adbfastboottool

internal object FastbootFetchPolicy {
    fun dataSize(reply: String, expected: Long?): Long? {
        if (reply.length != 8 || reply.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
        val size = reply.toLongOrNull(16) ?: return null
        return size.takeIf { it > 0L && (expected == null || it == expected) }
    }
}
