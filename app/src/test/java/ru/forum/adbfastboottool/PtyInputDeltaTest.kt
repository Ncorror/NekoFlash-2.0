package ru.forum.adbfastboottool

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PtyInputDeltaTest {
    @Test fun appendingCompositionSendsOnlyNewSuffix() {
        assertArrayEquals("e".toByteArray(), PtyInputDelta.rewrite("h", "he"))
    }

    @Test fun replacingCompositionSendsDeleteThenReplacement() {
        assertArrayEquals(
            byteArrayOf(PtyInputDelta.DEL.toByte(), 'a'.code.toByte()),
            PtyInputDelta.rewrite("he", "ha")
        )
    }

    @Test fun shorterCompositionSendsDeletes() {
        assertArrayEquals(
            byteArrayOf(PtyInputDelta.DEL.toByte(), PtyInputDelta.DEL.toByte()),
            PtyInputDelta.rewrite("hello", "hel")
        )
    }

    @Test fun pasteOrFreshCommitKeepsUtf8Bytes() {
        val text = "echo Привет\n"
        assertArrayEquals(text.toByteArray(Charsets.UTF_8), PtyInputDelta.rewrite("", text))
    }

    @Test fun unchangedCompositionSendsNothing() {
        assertArrayEquals(byteArrayOf(), PtyInputDelta.rewrite("pwd", "pwd"))
    }

    @Test fun deleteInsideCompositionRetainsPrefixForNextImeRewrite() {
        val planned = PtyInputDelta.deletion("прив", 1, 0, inCodePoints = true)
        assertEquals("при", planned.remainingComposition)
        assertArrayEquals(byteArrayOf(PtyInputDelta.DEL.toByte()), planned.bytes)
        // A following Gboard composition update for the surviving prefix
        // must not transmit "при" a second time.
        assertArrayEquals(byteArrayOf(), PtyInputDelta.rewrite(planned.remainingComposition, "при"))
        assertArrayEquals("вет 🌙".toByteArray(Charsets.UTF_8),
            PtyInputDelta.rewrite(planned.remainingComposition, "привет 🌙"))
    }

    @Test fun deleteSurroundingTextHandlesBothBeforeAndAfterCursor() {
        val planned = PtyInputDelta.deletion("abc", 1, 2, inCodePoints = false)
        assertEquals("ab", planned.remainingComposition)
        assertArrayEquals(
            byteArrayOf(PtyInputDelta.DEL.toByte()) +
                "\u001B[3~\u001B[3~".toByteArray(Charsets.US_ASCII),
            planned.bytes
        )
    }

    @Test fun utf16DeleteCannotCutSurrogateOrSendTwoBackspacesForOneEmoji() {
        val oneUtf16Unit = PtyInputDelta.deletion("пр🌙", 1, 0, inCodePoints = false)
        val twoUtf16Units = PtyInputDelta.deletion("пр🌙", 2, 0, inCodePoints = false)
        val oneCodePoint = PtyInputDelta.deletion("пр🌙", 1, 0, inCodePoints = true)
        for (planned in listOf(oneUtf16Unit, twoUtf16Units, oneCodePoint)) {
            assertEquals("пр", planned.remainingComposition)
            assertArrayEquals(byteArrayOf(PtyInputDelta.DEL.toByte()), planned.bytes)
        }
    }

    @Test fun deletingPastComposingBoundaryStillDeletesEarlierCommittedText() {
        val plan = PtyInputDelta.deletion("🌙a", 4, 0, inCodePoints = false)
        assertEquals("", plan.remainingComposition)
        assertArrayEquals(ByteArray(3) { PtyInputDelta.DEL.toByte() }, plan.bytes)
    }

    @Test fun emptyDeleteIsNoopAndInvalidLengthsAreRejected() {
        val noop = PtyInputDelta.deletion("а", 0, 0, inCodePoints = true)
        assertEquals("а", noop.remainingComposition)
        assertArrayEquals(byteArrayOf(), noop.bytes)
        for (units in listOf(true, false)) {
            try {
                PtyInputDelta.deletion("abc", -1, 0, units)
                throw AssertionError("Negative IME backspace length must fail")
            } catch (_: IllegalArgumentException) { }
            try {
                PtyInputDelta.deletion("abc", 0, -1, units)
                throw AssertionError("Negative IME forward length must fail")
            } catch (_: IllegalArgumentException) { }
        }
    }

}
