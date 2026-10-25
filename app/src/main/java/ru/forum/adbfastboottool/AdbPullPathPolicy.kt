package ru.forum.adbfastboottool

import java.io.File
import java.nio.file.Files

internal object AdbPullPathPolicy {
    /** NUL-delimited find records preserve newlines, spaces and backslashes. */
    fun parseListing(output: String): Pair<List<String>, List<String>> {
        val records = output.split('\u0000')
        require(records.firstOrNull() == "AFT_DIRS_BEGIN" && records.lastOrNull() == "") {
            "Incomplete directory listing"
        }
        val filesAt = records.indexOf("AFT_FILES_BEGIN")
        val doneAt = records.indexOf("AFT_DONE")
        require(filesAt > 0 && doneAt > filesAt && doneAt == records.lastIndex - 1) {
            "Invalid directory listing sections"
        }
        val dirs = records.subList(1, filesAt)
        val files = records.subList(filesAt + 1, doneAt)
        (dirs + files).forEach { normalize(it) }
        require(files.none { normalize(it).isEmpty() }) { "Invalid file entry" }
        return dirs.distinct() to files.distinct()
    }

    fun normalize(path: String): String {
        require(path == "." || path.startsWith("./")) { "Non-relative remote entry" }
        val relative = if (path == ".") "" else path.removePrefix("./")
        require(!relative.contains('\u0000') && (relative.isEmpty() ||
            relative.split('/').none { it.isEmpty() || it == "." || it == ".." })) {
            "Unsafe remote entry"
        }
        return relative
    }

    fun destination(root: File, path: String): File {
        val relative = normalize(path)
        val target = if (relative.isEmpty()) root else File(root, relative)
        require(target.canonicalFile.toPath().startsWith(root.canonicalFile.toPath())) {
            "Remote entry escapes the destination directory"
        }
        require(!Files.isSymbolicLink(target.toPath())) { "Destination is a symbolic link" }
        return target
    }
}
