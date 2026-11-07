package ru.forum.adbfastboottool

import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Explicit, lossless raw diagnostic export for Insiders.
 *
 * All known diagnostic files and all rotated segments are streamed unchanged.
 * The manifest contains sizes, SHA-256 checksums, and evidence warnings; a
 * successful ZIP write alone is not proof that earlier source events exist.
 * Source files are never modified, compressed into summaries, or deleted.
 */
object DiagnosticEvidenceArchive {
    data class ExportResult(val fileCount: Int, val warnings: List<String>) {
        val snapshotConsistent: Boolean get() = fileCount > 0 && warnings.isEmpty()
    }

    internal fun directories(workspaces: List<File>): List<File> = workspaces.flatMap {
        listOf(File(it, "logs"), File(it, "reports/usb-sessions"))
    }.distinctBy { it.absolutePath }

    // Only diagnostic artifacts: do not accidentally bundle firmware IMG/APK
    // placed in the same folder. Extensions are not capped for known evidence.
    private val diagnosticName = Regex(
        "^(?:(?:log-|trace-|session-summary-|usb-|diagnostic-|session-|crash-).+|last-crash-marker\\.txt)$",
        RegexOption.IGNORE_CASE
    )

    fun write(output: OutputStream, directories: List<File>): Int =
        writeDetailed(output, directories).fileCount

    fun writeDetailed(output: OutputStream, directories: List<File>): ExportResult {
        var count = 0
        val warnings = mutableListOf<String>()
        val notes = mutableListOf<String>()
        val seenRoots = HashSet<String>()
        val seenEntries = HashSet<String>()
        val manifest = StringBuilder()
        val buffer = ByteArray(64 * 1024)

        ZipOutputStream(output.buffered()).use { zip ->
            directories.forEachIndexed { index, root ->
                if (!root.exists()) return@forEachIndexed
                if (!root.isDirectory) {
                    warnings.add("NOT_A_DIRECTORY " + root.absolutePath)
                    return@forEachIndexed
                }
                val canonicalRoot = root.canonicalPath
                if (!seenRoots.add(canonicalRoot)) return@forEachIndexed
                val rootPrefix = canonicalRoot + File.separator

                root.walkTopDown().onFail { failed, error ->
                    warnings.add("DIRECTORY_SCAN_FAILED " + failed.absolutePath + ": " + error.javaClass.simpleName)
                }.filter { it.isFile && diagnosticName.matches(it.name) }.forEach { source ->
                    // Do not follow symbolic links out of the diagnostic root.
                    if (!source.canonicalPath.startsWith(rootPrefix)) {
                        warnings.add("OUTSIDE_DIAGNOSTIC_ROOT " + source.absolutePath)
                        return@forEach
                    }
                    val relative = source.relativeTo(root).invariantSeparatorsPath
                    if (relative.split('/').any { it == "." || it == ".." }) {
                        warnings.add("INVALID_RELATIVE_PATH " + source.absolutePath)
                        return@forEach
                    }
                    val name = "logs/" + (index + 1) + "/" + relative
                    if (!seenEntries.add(name)) return@forEach

                    val originalSize = source.length()
                    val originalModified = source.lastModified()
                    val isAtomicallyPublishedSummary = source.name.startsWith("session-summary-") &&
                        source.name.endsWith(".json")
                    val digest = MessageDigest.getInstance("SHA-256")
                    val leading = ByteArray(96)
                    var leadingBytes = 0
                    var copiedBytes = 0L
                    var descriptorSizeBefore = -1L
                    var descriptorSizeAfter = -1L

                    zip.putNextEntry(ZipEntry(name))
                    try {
                        FileInputStream(source).use { fileInput ->
                            // Keep the actual opened inode, not a new pathname
                            // lookup. writeSessionSummary publishes via atomic
                            // rename, so a newer summary may replace the path
                            // while the old *complete* revision is being copied.
                            descriptorSizeBefore = fileInput.channel.size()
                            // The outer FileInputStream owns the descriptor.
                            // Do not use buffered().use here: it would close
                            // fileInput before channel.size() is checked.
                            val input = fileInput.buffered()
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                if (n == 0) continue
                                if (leadingBytes < leading.size) {
                                    val prefixSize = minOf(leading.size - leadingBytes, n)
                                    buffer.copyInto(leading, leadingBytes, 0, prefixSize)
                                    leadingBytes += prefixSize
                                }
                                zip.write(buffer, 0, n)
                                digest.update(buffer, 0, n)
                                copiedBytes += n
                            }
                            descriptorSizeAfter = fileInput.channel.size()
                        }
                    } finally {
                        zip.closeEntry()
                    }
                    count++

                    // Main segment headers are mandatory in the current writer.
                    // Older or externally modified files remain in the archive,
                    // but are never silently described as a complete history.
                    val namePart = source.name
                    val isMainSegment = (namePart.startsWith("log-20") ||
                        namePart.startsWith("trace-20")) &&
                        namePart.endsWith(".txt") && !namePart.contains("-part")
                    if (isMainSegment) {
                        val expected = if (namePart.startsWith("log-")) {
                            "# NekoFlash compact log"
                        } else {
                            "# NekoFlash protocol trace"
                        }
                        val actualPrefix = String(leading, 0, leadingBytes, Charsets.UTF_8)
                        if (!actualPrefix.startsWith(expected)) {
                            warnings.add("MISSING_SESSION_PROLOGUE " + name)
                        }
                    }
                    val sourcePathChanged = source.length() != originalSize ||
                        source.lastModified() != originalModified
                    if (isAtomicallyPublishedSummary) {
                        // An atomically replaced JSON summary is a valid frozen
                        // revision on the open FileDescriptor. Do not mistake
                        // later publication under the same path for corrupted
                        // or missing evidence. Detect mutation of the open file.
                        if (descriptorSizeBefore != descriptorSizeAfter ||
                            copiedBytes != descriptorSizeBefore) {
                            warnings.add("SOURCE_CHANGED_DURING_EXPORT " + name)
                        } else if (sourcePathChanged || originalSize != descriptorSizeBefore) {
                            notes.add("ATOMIC_SUMMARY_REPUBLISHED " + name)
                        }
                    } else if (sourcePathChanged || copiedBytes != originalSize) {
                        warnings.add("SOURCE_CHANGED_DURING_EXPORT " + name)
                    }
                    val checksum = digest.digest().joinToString("") { byte ->
                        "%02x".format(byte.toInt() and 0xff)
                    }
                    manifest.append("file=").append(name.replace("\n", "\\n"))
                        .append("\tbytes=").append(copiedBytes)
                        .append("\tsha256=").append(checksum).append('\n')
                }
            }

            if (count == 0) {
                warnings.add("NO_DIAGNOSTIC_FILES_FOUND")
                zip.putNextEntry(ZipEntry("NO_LOGS.txt"))
                zip.write("No persisted diagnostic files were found for export.\n".toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            val status = if (warnings.isEmpty()) "CONSISTENT_SNAPSHOT" else "INCOMPLETE_OR_UNVERIFIED"
            val header = buildString {
                appendLine("NekoFlash diagnostic export manifest v1")
                appendLine("status=" + status)
                appendLine("file_count=" + count)
                appendLine("note=Source-history completeness cannot be proven from a ZIP alone.")
                appendLine("note=SHA-256 covers exactly the copied raw bytes; no source files were changed.")
                append(manifest)
                notes.forEach { appendLine("note=" + it.replace("\n", "\\n")) }
                warnings.forEach { appendLine("warning=" + it.replace("\n", "\\n")) }
            }
            zip.putNextEntry(ZipEntry("EXPORT-MANIFEST.txt"))
            zip.write(header.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        return ExportResult(count, warnings.toList())
    }
}
