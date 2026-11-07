package ru.forum.adbfastboottool

import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Explicit raw diagnostic export for Insiders. No source files are modified or deleted.
 * The archive is streamed directly to the user-selected SAF destination.
 */
object DiagnosticEvidenceArchive {
    internal fun directories(workspaces: List<File>): List<File> = workspaces.flatMap {
        listOf(File(it, "logs"), File(it, "reports/usb-sessions"))
    }.distinctBy { it.absolutePath }

    private val diagnosticName = Regex(
        "^(?:log-|trace-|session-summary-|usb-|diagnostic-|session-).+\\.(?:txt|json|log|bin|gz|csv|zip)$",
        RegexOption.IGNORE_CASE
    )

    fun write(output: OutputStream, directories: List<File>): Int {
        var count = 0
        val seenRoots = HashSet<String>()
        val seenEntries = HashSet<String>()
        ZipOutputStream(output.buffered()).use { zip ->
            directories.forEachIndexed { index, root ->
                if (!root.isDirectory || !seenRoots.add(root.canonicalPath)) return@forEachIndexed
                root.walkTopDown().filter { it.isFile && diagnosticName.matches(it.name) }
                    .forEach { source ->
                        val relative = source.relativeTo(root).invariantSeparatorsPath
                        if (relative.split('/').any { it == "." || it == ".." }) return@forEach
                        val name = "logs/${index + 1}/$relative"
                        if (!seenEntries.add(name)) return@forEach
                        zip.putNextEntry(ZipEntry(name))
                        source.inputStream().buffered().use { it.copyTo(zip) }
                        zip.closeEntry()
                        count++
                    }
            }
            if (count == 0) {
                zip.putNextEntry(ZipEntry("NO_LOGS.txt"))
                zip.write("No persisted diagnostic files were found for export.\n".toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return count
    }
}
