package ru.forum.adbfastboottool

import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Best-effort append-only crash evidence. Never replaces the platform handler
 * and never overwrites a prior crash marker, including legacy last-crash files.
 */
object DiagnosticCrashMarker {
    private val installed = AtomicBoolean(false)

    fun install(directory: File, buildId: String) {
        if (!installed.compareAndSet(false, true)) return
        if (!directory.exists()) directory.mkdirs()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrashMarker(directory, buildId, thread.name, throwable) }
            if (previous != null) previous.uncaughtException(thread, throwable) else System.exit(10)
        }
    }

    /** Internal test seam; the uncaught exception hook remains unchanged. */
    internal fun writeCrashMarker(
        directory: File,
        buildId: String,
        threadName: String,
        throwable: Throwable
    ): File {
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IllegalStateException("Cannot create crash evidence directory: " + directory.absolutePath)
        }
        val timestamp = System.currentTimeMillis()
        val readable = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
        val marker = File(directory, "crash-$timestamp-${UUID.randomUUID()}.txt")
        check(marker.createNewFile()) { "Crash marker collision: " + marker.absolutePath }
        val content = buildString {
            appendLine("NekoFlash crash marker")
            appendLine("Created: $readable")
            appendLine("Build: $buildId")
            appendLine("Thread: $threadName")
            appendLine("Exception: ${throwable.javaClass.name}")
            appendLine("Message: ${throwable.message ?: "none"}")
            appendLine("Full stack including causes and suppressed exceptions:")
            append(throwable.stackTraceToString())
            appendLine()
        }
        FileOutputStream(marker, true).use { out ->
            out.write(content.toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        return marker
    }
}
