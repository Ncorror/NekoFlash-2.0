package ru.forum.adbfastboottool

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * Pure PTY composition-delta policy.
 *
 * Android IMEs may repeatedly replace a composing word ("h" -> "he" -> "hel").
 * A terminal must not resend the whole composition each time. Emit DEL for the
 * old suffix and UTF-8 only for the new suffix.
 */
internal object PtyInputDelta {
    const val DEL: Int = 0x7f

    fun rewrite(previous: String, next: String): ByteArray {
        val oldPoints = previous.codePoints().toArray()
        val newPoints = next.codePoints().toArray()
        var common = 0
        while (common < oldPoints.size &&
            common < newPoints.size &&
            oldPoints[common] == newPoints[common]
        ) {
            common++
        }

        val out = ByteArrayOutputStream()
        repeat(oldPoints.size - common) { out.write(DEL) }
        if (common < newPoints.size) {
            val suffix = String(newPoints, common, newPoints.size - common)
                .toByteArray(StandardCharsets.UTF_8)
            out.write(suffix)
        }
        return out.toByteArray()
    }
    /**
     * IMEs may backspace an *active* composing word and then send another
     * setComposingText for the shortened word. Do not forget the surviving
     * composition: otherwise that next update duplicates its prefix remotely.
     *
     * deleteSurroundingText uses UTF-16 units; the CodePoints variant counts
     * Unicode scalar values. A partially requested surrogate pair is removed
     * as one scalar to avoid transmitting two DEL bytes for one emoji.
     *
     * Cursor-at-end composition is the common PTY contract. Untracked text
     * before/after this composition still receives the requested key actions.
     * A forward delete is a terminal CSI Delete, never a backward DEL.
     */
    data class Deletion(
        val bytes: ByteArray,
        val remainingComposition: String,
    )

    fun deletion(
        composing: String,
        beforeLength: Int,
        afterLength: Int,
        inCodePoints: Boolean,
    ): Deletion {
        require(beforeLength >= 0 && afterLength >= 0) { "Negative IME delete length" }

        val retainedEnd = if (inCodePoints) {
            val points = composing.codePointCount(0, composing.length)
            composing.offsetByCodePoints(0, (points - beforeLength).coerceAtLeast(0))
        } else {
            val requestedEnd = (composing.length - beforeLength).coerceAtLeast(0)
            // Do not cut the high-surrogate/low-surrogate pair in half.
            if (requestedEnd > 0 && requestedEnd < composing.length &&
                Character.isHighSurrogate(composing[requestedEnd - 1]) &&
                Character.isLowSurrogate(composing[requestedEnd])
            ) requestedEnd - 1 else requestedEnd
        }
        val retained = composing.substring(0, retainedEnd)
        val deletedInsideComposition = composing.codePointCount(retainedEnd, composing.length)
        val totalComposingUnits = if (inCodePoints) composing.codePointCount(0, composing.length)
            else composing.length
        val deletionsOutside = (beforeLength - totalComposingUnits).coerceAtLeast(0)
        val backwardCount = deletedInsideComposition + deletionsOutside

        val out = ByteArrayOutputStream()
        repeat(backwardCount) { out.write(DEL) }
        val forwardDelete = "\u001B[3~".toByteArray(StandardCharsets.US_ASCII)
        repeat(afterLength) { out.write(forwardDelete) }
        return Deletion(out.toByteArray(), retained)
    }

}
