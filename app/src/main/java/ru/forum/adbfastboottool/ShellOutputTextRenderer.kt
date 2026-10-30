package ru.forum.adbfastboottool

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Stateful, bounded-heap renderer for ADB shell output.
 *
 * USB/shell-v2 packets have arbitrary boundaries: a single UTF-8 character or
 * CRLF line may span multiple packets. The on-screen text must not substitute
 * U+FFFD for valid text just because a packet ended inside a code point.
 *
 * This is only the human-readable view. AdbShellOutputRecord preserves every
 * original byte separately; no byte is removed or re-encoded in raw evidence.
 */
internal class ShellOutputTextRenderer(
    isStderr: Boolean,
    private val emit: (String) -> Unit
) {
    private val prefix = if (isStderr) "│ stderr: " else "│ "
    private val decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
    private val characters = CharBuffer.allocate(4096)
    private val currentLine = StringBuilder()
    private var pendingUtf8 = ByteArray(0)
    private var finished = false

    fun accept(bytes: ByteArray) {
        check(!finished) { "Shell output renderer is already finished" }
        if (bytes.isEmpty()) return
        val buffer = ByteBuffer.allocate(pendingUtf8.size + bytes.size)
        buffer.put(pendingUtf8)
        buffer.put(bytes)
        buffer.flip()
        decode(buffer, endOfInput = false)
        pendingUtf8 = ByteArray(buffer.remaining()).also { buffer.get(it) }
    }

    fun finish() {
        if (finished) return
        finished = true
        val buffer = ByteBuffer.wrap(pendingUtf8)
        decode(buffer, endOfInput = true)
        pendingUtf8 = ByteArray(0)
        while (true) {
            val result = decoder.flush(characters)
            drainCharacters()
            if (result.isUnderflow) break
            if (!result.isOverflow) result.throwException()
        }
        emitLine(trimCr = true)
    }

    private fun decode(buffer: ByteBuffer, endOfInput: Boolean) {
        while (true) {
            val result = decoder.decode(buffer, characters, endOfInput)
            drainCharacters()
            if (result.isUnderflow) return
            if (!result.isOverflow) result.throwException()
        }
    }

    private fun drainCharacters() {
        characters.flip()
        while (characters.hasRemaining()) {
            val char = characters.get()
            when (char) {
                '\u0000' -> Unit // Keep exact NUL in the raw record, not in terminal text.
                '\r' -> emitLine(trimCr = false) // Progress updates may use CR without LF.
                '\n' -> emitLine(trimCr = true)
                else -> {
                    currentLine.append(char)
                    // Long output without newlines is displayed in complete
                    // fragments rather than retained as an unbounded String.
                    // Do not split a UTF-16 surrogate pair at this boundary.
                    if (currentLine.length >= MAX_VIEW_FRAGMENT && !Character.isHighSurrogate(char)) {
                        emitLine(trimCr = false)
                    }
                }
            }
        }
        characters.clear()
    }

    private fun emitLine(trimCr: Boolean) {
        if (currentLine.isEmpty()) return
        val rendered = if (trimCr) currentLine.toString().trimEnd('\r') else currentLine.toString()
        if (rendered.isNotEmpty()) emit(prefix + rendered)
        currentLine.setLength(0)
    }

    private companion object {
        const val MAX_VIEW_FRAGMENT = 8192
    }
}
