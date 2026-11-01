package ru.forum.adbfastboottool

import org.junit.Assert.*
import org.junit.Test

class FastbootSerialSelectionTest {
    @Test fun mismatchedUsbSerialCannotBeOverriddenByGetvar() {
        val actual = FastbootSerialSelection.actual("other-device") { error("must not query getvar") }
        assertFalse(FastbootSerialSelection.matches("requested-device", actual))
    }
    @Test fun serialComparisonIsCaseSensitive() {
        assertTrue(FastbootSerialSelection.matches("AbC", "AbC"))
        assertFalse(FastbootSerialSelection.matches("AbC", "abc"))
    }
    @Test fun missingDescriptorUsesGetvarButUnknownNeverMatches() {
        assertTrue(FastbootSerialSelection.matches("AbC", FastbootSerialSelection.actual(null) { "AbC" }))
        assertFalse(FastbootSerialSelection.matches("AbC", FastbootSerialSelection.actual("") { null }))
    }
    @Test(expected = IllegalArgumentException::class)
    fun conflictingSelectorsAreRejected() { FastbootSerialSelection.select("one", "two") }
    @Test(expected = IllegalArgumentException::class)
    fun emptySelectorIsRejected() { FastbootSerialSelection.select(null, "") }
    @Test fun repeatedSameSelectorRemainsValid() {
        assertEquals("same", FastbootSerialSelection.select("same", "same"))
    }
    @Test fun hostOptionsAndRawOemQuotingStaySeparate() {
        val tokens = FastbootCommandLine.tokenize("-s 'AbC' oem test \"two words\" escaped\\ value")
        assertEquals(listOf("-s", "AbC", "oem", "test", "two words", "escaped value"), tokens.map { it.value })
        assertEquals("oem test \"two words\" escaped\\ value", tokens.drop(2).joinToString(" ") { it.literal })
    }
    @Test fun quotedEmptyValueAndUppercaseSparseOptionArePreserved() {
        assertEquals(listOf("-s", "", "-S", "1G"), FastbootCommandLine.tokenize("-s '' -S 1G").map { it.value })
    }
    @Test(expected = IllegalArgumentException::class)
    fun unfinishedQuotedCommandIsRejected() { FastbootCommandLine.tokenize("-s 'abc") }
}
