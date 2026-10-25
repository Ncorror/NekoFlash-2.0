package ru.forum.adbfastboottool

/** An append boundary captured in Recovery before entering Sideload. */
data class RecoveryLogBoundary(val bootId: String, val byteCount: Long, val sha256: String) {
    fun matches(currentBootId: String, prefixSha256: String): Boolean =
        bootId == currentBootId && sha256 == prefixSha256

    companion object {
        fun parse(bootId: String?, count: String?, digest: String?): RecoveryLogBoundary? {
            val boot = bootId?.trim() ?: return null
            val size = count?.trim()?.toLongOrNull() ?: return null
            val hash = digest?.trim()?.substringBefore(' ') ?: return null
            if (!boot.matches(Regex("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")) ||
                size < 0L || size == Long.MAX_VALUE || !hash.matches(Regex("[0-9a-f]{64}"))) return null
            return RecoveryLogBoundary(boot, size, hash)
        }
    }
}
