package ru.forum.adbfastboottool

/** Accept only one plausible Android release token from the current ADB SYSTEM peer. */
internal object AndroidReleaseMetadataPolicy {
    private val allowed = Regex("^[0-9][0-9A-Za-z._-]{0,31}$")

    fun parse(stdout: String, success: Boolean, exitCode: Int?): String? {
        if (!success || (exitCode != null && exitCode != 0)) return null
        return stdout.trim().takeIf { allowed.matches(it) }
    }
}
