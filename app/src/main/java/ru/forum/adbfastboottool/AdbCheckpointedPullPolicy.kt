package ru.forum.adbfastboottool

import java.security.MessageDigest

/**
 * Pure policy for the large-file ADB Pull fallback used on normal Android.
 *
 * The fallback never retries a failed ADB packet. It checkpoints only fully
 * received byte ranges, so a later manually reconnected transport can resume
 * from the last durable range without publishing a partial destination file.
 */
internal object AdbCheckpointedPullPolicy {
    const val RANGE_BYTES: Int = 256 * 1024
    const val MIN_FILE_BYTES: Long = 16L * 1024L * 1024L

    data class Range(val offset: Long, val length: Int)

    fun shouldUse(isDeviceMode: Boolean, supportsShellV2: Boolean, fileSize: Long): Boolean =
        isDeviceMode && supportsShellV2 && fileSize >= MIN_FILE_BYTES

    fun nextRange(totalBytes: Long, committedBytes: Long): Range? {
        require(totalBytes >= 0L)
        require(committedBytes >= 0L)
        require(committedBytes <= totalBytes)
        if (committedBytes == totalBytes) return null
        val remaining = totalBytes - committedBytes
        return Range(committedBytes, minOf(remaining, RANGE_BYTES.toLong()).toInt())
    }

    fun isValidCheckpointLength(totalBytes: Long, checkpointBytes: Long): Boolean {
        if (totalBytes < 0L || checkpointBytes < 0L || checkpointBytes > totalBytes) return false
        if (checkpointBytes == totalBytes) return true
        return checkpointBytes % RANGE_BYTES.toLong() == 0L
    }

    fun parseSha256Sum(output: String): String? {
        val match = Regex("""(?i)^\s*([0-9a-f]{64})(?:\s|$)""").find(output) ?: return null
        return match.groupValues[1].lowercase()
    }

    fun checkpointFileName(
        localAbsolutePath: String,
        remotePath: String,
        size: Long,
        mtime: Int,
        remoteSha256: String
    ): String {
        require(Regex("""(?i)^[0-9a-f]{64}$""").matches(remoteSha256))
        val identity = buildString {
            append(localAbsolutePath).append('\u0000')
            append(remotePath).append('\u0000')
            append(size).append('\u0000')
            append(mtime.toLong() and 0xffffffffL).append('\u0000')
            append(remoteSha256.lowercase())
        }.toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(identity)
            .joinToString("") { "%02x".format(it) }
        return ".nekoflash-pull-${digest.take(24)}.part"
    }
}
