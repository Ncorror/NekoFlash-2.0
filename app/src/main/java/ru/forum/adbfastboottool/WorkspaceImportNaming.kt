package ru.forum.adbfastboottool

import java.io.File

/**
 * File naming logic independent from Android UI and USB.
 *
 * Preserves the approved /sdcard/Download/NekoFlash workspace in the caller.
 * A provider-supplied filename is never trusted as a directory component.
 * We do not silently cut filenames at a made-up character limit; the filesystem
 * reports its actual length/encoding constraints to the importing operation.
 */
internal object WorkspaceImportNaming {
    fun sanitizeImportedFileName(name: String): String {
        val safe = name.trim()
            .replace(Regex("[\\\\/:*?\"<>|\\r\\n]+"), "_")
            .replace(Regex("\\s+"), "_")
        return safe.takeUnless { it.isBlank() || it == "." || it == ".." }
            ?: "imported-${System.currentTimeMillis()}"
    }

    fun uniqueTargetFile(workspace: File, fileName: String): File {
        require(fileName != "." && fileName != ".." &&
            fileName.isNotBlank() && !fileName.contains('/') && !fileName.contains('\\')) {
            "Invalid import filename"
        }
        var candidate = File(workspace, fileName)
        if (!candidate.exists()) return candidate

        val dot = fileName.lastIndexOf('.')
        val base = if (dot > 0) fileName.substring(0, dot) else fileName
        val ext = if (dot > 0) fileName.substring(dot) else ""
        var index = 1
        while (candidate.exists()) {
            candidate = File(workspace, "$base-$index$ext")
            index++
        }
        return candidate
    }
}
