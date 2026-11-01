package ru.forum.adbfastboottool

import java.io.File
import java.io.Reader
import java.io.StringReader
import java.nio.file.Files

internal object AdbPullPathPolicy {
    /** NUL-delimited find records preserve newlines, spaces and backslashes. */
    fun parseListing(output: String): Pair<List<String>, List<String>> =
        parseListing(StringReader(output))

    /**
     * Parse a potentially large remote directory listing without materializing
     * the entire stdout as a String. Only individual paths and the resulting
     * entry index remain in memory; the exact binary stdout is already on disk.
     */
    fun parseListing(reader: Reader): Pair<List<String>, List<String>> {
        val dirs = LinkedHashSet<String>()
        val files = LinkedHashSet<String>()
        val entry = StringBuilder()
        val buffer = CharArray(8192)
        // 0: expecting first marker, 1: directories, 2: files, 3: complete.
        var section = 0
        var terminated = false

        fun add(record: String) {
            when (section) {
                0 -> {
                    require(record == "AFT_DIRS_BEGIN") { "Incomplete directory listing" }
                    section = 1
                }
                1 -> {
                    if (record == "AFT_FILES_BEGIN") {
                        section = 2
                    } else {
                        normalize(record)
                        dirs.add(record)
                    }
                }
                2 -> {
                    if (record == "AFT_DONE") {
                        section = 3
                    } else {
                        require(normalize(record).isNotEmpty()) { "Invalid file entry" }
                        files.add(record)
                    }
                }
                else -> throw IllegalArgumentException("Unexpected data after listing terminator")
            }
        }

        while (true) {
            val size = reader.read(buffer)
            if (size < 0) break
            for (index in 0 until size) {
                val character = buffer[index]
                if (character == '\u0000') {
                    add(entry.toString())
                    entry.setLength(0)
                    terminated = true
                } else {
                    entry.append(character)
                    terminated = false
                }
            }
        }
        require(terminated && section == 3) { "Incomplete directory listing" }
        return dirs.toList() to files.toList()
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
