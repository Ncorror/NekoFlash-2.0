package ru.forum.adbfastboottool

import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * Rewalks source lazily to count, create every directory, then send files.
 * Retains the legacy directory-before-file / walkTopDown order without a
 * complete list of File objects in memory.
 */
internal class AdbPushDirectoryWalker(private val root: File) {
    data class Counts(val directories: Long, val files: Long)

    fun scan(checkCancelled: () -> Unit): Counts = walk(checkCancelled)

    fun visitDirectories(checkCancelled: () -> Unit, action: (File) -> Unit): Counts =
        walk(checkCancelled, onDirectory = action)

    fun visitFiles(checkCancelled: () -> Unit, action: (File) -> Unit): Counts =
        walk(checkCancelled, onFile = action)

    private fun walk(
        checkCancelled: () -> Unit,
        onDirectory: (File) -> Unit = {},
        onFile: (File) -> Unit = {}
    ): Counts {
        checkCancelled()
        if (!root.isDirectory || !root.canRead()) {
            throw IOException("ADB push source directory cannot be read: ${root.absolutePath}")
        }
        var directories = 0L
        var files = 0L
        root.walkTopDown()
            .onEnter { directory ->
                checkCancelled()
                if (Files.isSymbolicLink(directory.toPath())) {
                    throw IOException("ADB push directory symlink would be recursive: ${directory.absolutePath}")
                }
                if (!directory.canRead()) {
                    throw IOException("ADB push cannot read directory: ${directory.absolutePath}")
                }
                true
            }
            .onFail { directory, error ->
                throw IOException("ADB push cannot enumerate directory: ${directory.absolutePath}", error)
            }
            .forEach { entry ->
                checkCancelled()
                when {
                    entry.isDirectory -> {
                        directories = Math.addExact(directories, 1L)
                        onDirectory(entry)
                    }
                    entry.isFile -> {
                        files = Math.addExact(files, 1L)
                        onFile(entry)
                    }
                    else -> throw IOException("ADB push source entry unavailable: ${entry.absolutePath}")
                }
            }
        return Counts(directories, files)
    }
}
