package ru.forum.adbfastboottool

import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Base64
import java.util.UUID

/**
 * Exact, append-only evidence of an individual ADB one-shot shell session.
 *
 * stdout and stderr are written as binary streams straight to disk, never via
 * a ByteArrayOutputStream or a packet-by-packet UTF-8 decoder. The optional
 * String result is materialized only for existing callers that must parse
 * shell results (e.g. recovery verification and package-manager metadata).
 *
 * None of these files are auto-pruned or removed on command failure. Each
 * session gets unique names so Activity recreation cannot overwrite evidence.
 */
internal class AdbShellOutputRecord(
    private val directory: File,
    mode: String,
    command: String,
) : Closeable {
    private val token = UUID.randomUUID().toString()
    val stdoutFile: File = File(directory, "trace-adb-shell-$token-stdout.bin")
    val stderrFile: File = File(directory, "trace-adb-shell-$token-stderr.bin")
    val metadataFile: File = File(directory, "trace-adb-shell-$token-meta.txt")

    private val stdout: FileOutputStream
    private val stderr: FileOutputStream
    private var finished = false
    private var stdoutBytes = 0L
    private var stderrBytes = 0L

    init {
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Cannot create ADB shell evidence directory: ${directory.absolutePath}")
        }
        if (!stdoutFile.createNewFile() || !stderrFile.createNewFile() ||
            !metadataFile.createNewFile()
        ) {
            throw IOException("ADB shell evidence file collision in ${directory.absolutePath}")
        }
        metadataFile.writeText(
            "format=nekoflash-adb-shell-raw-v1\n" +
                "mode=$mode\n" +
                "command_utf8_base64=${Base64.getEncoder().encodeToString(command.toByteArray(Charsets.UTF_8))}\n" +
                "started_at_ms=${System.currentTimeMillis()}\n" +
                "stdout=${stdoutFile.name}\n" +
                "stderr=${stderrFile.name}\n" +
                "status=IN_PROGRESS\n",
            Charsets.UTF_8
        )
        stdout = FileOutputStream(stdoutFile, true)
        stderr = FileOutputStream(stderrFile, true)
    }

    fun writeStdout(data: ByteArray) {
        check(!finished) { "ADB shell stdout already finished" }
        stdout.write(data)
        stdoutBytes += data.size.toLong()
    }

    fun writeStderr(data: ByteArray) {
        check(!finished) { "ADB shell stderr already finished" }
        stderr.write(data)
        stderrBytes += data.size.toLong()
    }

    /**
     * Always keep the raw files, even when a command failed or was cancelled.
     * Never read the files into RAM when the caller only needs an exit status.
     */
    fun finish(exitCode: Int?, success: Boolean, captureText: Boolean): Pair<String, String> {
        if (!finished) {
            stdout.fd.sync()
            stderr.fd.sync()
            stdout.close()
            stderr.close()
            finished = true
            FileOutputStream(metadataFile, true).use { meta ->
                meta.write(
                    ("ended_at_ms=${System.currentTimeMillis()}\n" +
                        "stdout_bytes=$stdoutBytes\n" +
                        "stderr_bytes=$stderrBytes\n" +
                        "exit_code=${exitCode?.toString() ?: "unknown"}\n" +
                        "status=${if (success) "SUCCESS" else "FAILED_OR_INTERRUPTED"}\n"
                    ).toByteArray(Charsets.UTF_8)
                )
                meta.fd.sync()
            }
        }
        return if (captureText) {
            stdoutFile.readText(Charsets.UTF_8) to stderrFile.readText(Charsets.UTF_8)
        } else {
            "" to ""
        }
    }

    override fun close() {
        if (!finished) finish(exitCode = null, success = false, captureText = false)
    }
}
