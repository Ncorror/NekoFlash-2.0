package ru.forum.adbfastboottool

/** Verifies the eight hex size digits in a Fastboot DATA acknowledgement. */
internal object FastbootDataReplyPolicy {
    sealed interface Verdict {
        data class Accepted(val bytes: Long) : Verdict
        data class Invalid(val reply: String) : Verdict
        data class Mismatch(val expected: Long, val actual: Long) : Verdict
    }

    fun validate(reply: String, expectedBytes: Long): Verdict {
        // This is the wire format (DATAxxxxxxxx), not a UI length restriction.
        if (reply.length != 8 || reply.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) {
            return Verdict.Invalid(reply)
        }
        val actualBytes = reply.toLongOrNull(16) ?: return Verdict.Invalid(reply)
        return if (actualBytes == expectedBytes) {
            Verdict.Accepted(actualBytes)
        } else {
            Verdict.Mismatch(expectedBytes, actualBytes)
        }
    }
}
