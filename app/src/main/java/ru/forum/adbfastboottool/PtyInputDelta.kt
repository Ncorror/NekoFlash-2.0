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
}
