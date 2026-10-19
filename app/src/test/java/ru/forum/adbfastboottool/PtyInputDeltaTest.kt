package ru.forum.adbfastboottool

import org.junit.Assert.assertArrayEquals
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
}
