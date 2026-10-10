package ru.forum.adbfastboottool

import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Lossless segmented two-stream log storage.
 *
 * COMPACT contains user-relevant events. TRACE contains raw protocol/timing
 * diagnostics. Segments rotate by size, but previous files are never pruned.
 */
class DiagnosticLogStore(
    private val logsDir: File,
    private val stamp: String,
    private val compactSegmentBytes: Long = 2L * 1024L * 1024L,
    private val traceSegmentBytes: Long = 8L * 1024L * 1024L,
    private val maxSegmentsPerStream: Int = 5
) {
    enum class Stream { COMPACT, TRACE }

    private data class State(
        val stream: Stream,
        val files: MutableList<File> = mutableListOf(),
        var nextPart: Int = 1
    )

    private val compact = State(Stream.COMPACT)
    private val trace = State(Stream.TRACE)

    init {
        require(compactSegmentBytes >= 64L * 1024L)
        require(traceSegmentBytes >= 64L * 1024L)
        require(maxSegmentsPerStream >= 1)
        if (!logsDir.exists() && !logsDir.mkdirs()) {
            throw IllegalStateException("Cannot create logs directory: ${logsDir.absolutePath}")
        }
        // Never delete historical logs on startup or on segment rollover.
    }

    @Synchronized
    fun appendCompact(text: String) = append(compact, text, compactSegmentBytes)

    @Synchronized
    fun appendTrace(text: String) = append(trace, text, traceSegmentBytes)

    @Synchronized
    fun compactFiles(): List<File> = compact.files.filter { it.exists() }.toList()

    @Synchronized
    fun traceFiles(): List<File> = trace.files.filter { it.exists() }.toList()

    @Synchronized
    fun currentCompactFile(): File? = compact.files.lastOrNull()?.takeIf { it.exists() }

    @Synchronized
    fun currentTraceFile(): File? = trace.files.lastOrNull()?.takeIf { it.exists() }

    @Synchronized
    fun writeSessionSummary(json: String): File {
        val file = File(logsDir, "session-summary-$stamp.json")
        val temp = File(logsDir, ".${file.name}.tmp")
        temp.writeText(json, Charsets.UTF_8)
        if (file.exists() && !file.delete()) {
            temp.delete()
            throw IllegalStateException("Cannot replace ${file.absolutePath}")
        }
        if (!temp.renameTo(file)) {
            temp.delete()
            throw IllegalStateException("Cannot publish ${file.absolutePath}")
        }
        return file
    }

    private fun append(state: State, text: String, limit: Long) {
        if (text.isEmpty()) return
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        var file = state.files.lastOrNull()
        if (file == null || (file.length() > 0L && file.length() + bytes.size > limit)) {
            file = createSegment(state)
        }
        file.appendBytes(bytes)
    }

    private fun createSegment(state: State): File {
        val part = state.nextPart++
        val prefix = if (state.stream == Stream.COMPACT) "log" else "trace"
        val suffix = if (part == 1) "" else "-part${part.toString().padStart(2, '0')}"
        val file = File(logsDir, "$prefix-$stamp$suffix.txt")
        if (file.exists()) {
            throw IllegalStateException("Log segment collision; refusing to overwrite: ${file.absolutePath}")
        }
        if (!file.createNewFile()) {
            throw IllegalStateException("Cannot create log segment: ${file.absolutePath}")
        }
        state.files.add(file)
        // All segments remain on disk until an explicit user action removes them.
        return file
    }

    companion object {
        /**
         * Kept only as a source-compatible shim. Automatic pruning destroys
         * diagnostic evidence, so it is intentionally disabled for REV8.
         */
        @Deprecated("Log retention must be user-controlled; this operation never deletes files")
        fun pruneDirectory(
            dir: File,
            maxFiles: Int = 30,
            maxTotalBytes: Long = 64L * 1024L * 1024L
        ) {
            // No-op: compatibility with external callers; no silent deletion.
            @Suppress("UNUSED_VARIABLE")
            val unused = dir to (maxFiles to maxTotalBytes)
        }
    }
}
