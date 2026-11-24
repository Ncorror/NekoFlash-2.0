package ru.forum.adbfastboottool

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Loopback-only bridge used by the private `fastboot` shim installed for local
 * shell scripts. The child shell never receives a USB descriptor: Fastboot
 * requests are executed by the already-authorized app-owned FastbootProtocol.
 */
internal class FastbootShellBridge(
    private val execute: (arguments: List<String>, workingDirectory: String) -> Result,
) : Closeable {
    data class Result(val exitCode: Int, val lines: List<String> = emptyList())

    private val closed = AtomicBoolean(false)
    private val token: String = ByteArray(24).also(SecureRandom()::nextBytes).let {
        Base64.getUrlEncoder().withoutPadding().encodeToString(it)
    }
    private val tokenBytes = token.toByteArray(StandardCharsets.UTF_8)
    private val server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
    private val worker = Thread(::acceptLoop, "NekoFlash-FastbootShellBridge").apply {
        isDaemon = true
        start()
    }

    val port: Int get() = server.localPort
    val authToken: String get() = token

    private fun acceptLoop() {
        while (!closed.get()) {
            val client = try {
                server.accept()
            } catch (_: Exception) {
                if (closed.get()) return else continue
            }
            runCatching { client.use(::serve) }
        }
    }

    private fun serve(socket: Socket) {
        socket.soTimeout = 30_000
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
        val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
        val submittedToken = reader.readLine()?.toByteArray(StandardCharsets.UTF_8) ?: return
        if (!MessageDigest.isEqual(tokenBytes, submittedToken)) {
            writer.write("126")
            writer.newLine()
            writer.write("Fastboot bridge rejected an unauthenticated local request")
            writer.newLine()
            writer.flush()
            return
        }
        val workingDirectory = reader.readLine()?.takeIf { it.isNotBlank() } ?: "."
        val arguments = ArrayList<String>()
        var totalChars = workingDirectory.length
        while (true) {
            val line = reader.readLine() ?: break
            totalChars += line.length
            require(totalChars <= MAX_REQUEST_CHARS) { "Fastboot script request is too large" }
            arguments += line
            require(arguments.size <= MAX_ARGUMENTS) { "Too many Fastboot arguments" }
        }
        val result = runCatching { execute(arguments, workingDirectory) }
            .getOrElse { Result(1, listOf("Fastboot bridge failed: ${it.message ?: it.javaClass.simpleName}")) }
        writer.write(result.exitCode.coerceIn(0, 255).toString())
        writer.newLine()
        result.lines.take(MAX_RESPONSE_LINES).forEach {
            writer.write(it.replace('\n', ' ').replace('\r', ' ').take(MAX_OUTPUT_CHARS))
            writer.newLine()
        }
        writer.flush()
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            runCatching { server.close() }
            worker.interrupt()
        }
    }

    private companion object {
        const val MAX_ARGUMENTS = 64
        const val MAX_REQUEST_CHARS = 16_384
        const val MAX_OUTPUT_CHARS = 8_192
        const val MAX_RESPONSE_LINES = 128
    }
}
