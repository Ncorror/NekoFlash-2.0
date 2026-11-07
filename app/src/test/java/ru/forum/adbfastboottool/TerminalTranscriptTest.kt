package ru.forum.adbfastboottool

import org.junit.Assert.*
import org.junit.Test

class TerminalTranscriptTest {
    private fun selected(s: String, terminal: Boolean = false, protocol: Boolean = false, input: Boolean = false) =
        TerminalTranscript.select(s, null, input, protocol, terminal)
    @Test fun routineDiagnosticsStayOutOfTheTerminal() {
        for (s in listOf("=== ADB CONNECTION ESTABLISHED ===", "Device/product: fixture", "Wake lock acquired", "[debug] interface descriptor")) {
            assertNull(selected(s)); assertNull(selected(s, true, true))
        }
    }
    @Test fun commandPayloadAndExitStatusArePreserved() {
        assertEquals("0 errors; bootloader LOCKED", selected("│ 0 errors; bootloader LOCKED", true, true))
        assertEquals("product: fixture", selected("↩ product: fixture", true, true))
        assertEquals("stderr: missing", selected("│ stderr: missing", true, true))
        assertEquals("exit: 7", selected("=== ADB SHELL EXIT: 7 ===", true, true))
        assertEquals("device list", selected("device list", input = true))
        assertEquals("> adb shell id", selected("> adb shell id"))
    }
    @Test fun errorsAndWarningsRemainVisibleWithoutACommand() {
        assertEquals("ERROR: unplugged", selected("ERROR: unplugged"))
        assertEquals("⚠️ cancelled", selected("⚠️ cancelled"))
        assertEquals("custom failure", TerminalTranscript.select("custom failure", DiagnosticLogPolicy.Level.ERROR, false, false, false))
    }
    @Test fun recoveredEarlyShellCloseIsInformationalAndTerminalOnlyShowsRealFailures() {
        // The wire-level CLSE still happens on Android 14, but the one-shot
        // shell-v2 decoder can recover a complete stdout and explicit EXIT=0.
        // Preserve the entire event in the disk journal; do not display it as
        // an alarming warning when no operation actually failed.
        val recovered = "ℹ️ ADB stream closed during write local=5, expectedRemote=4, " +
            "receivedRemote=4, payloadBytes=5, bufferedResponseBytes=26, " +
            "dispatcherGeneration=1, transportConnected=true"
        assertEquals(DiagnosticLogPolicy.Level.INFO, DiagnosticLogPolicy.classify(recovered).level)
        assertNull(selected(recovered, terminal = true, protocol = true))
        assertNull(selected(recovered))
        // A failed/nonrecoverable stream must still remain visible.
        val failed = recovered.replaceFirst("ℹ️", "❌")
        assertEquals(DiagnosticLogPolicy.Level.ERROR, DiagnosticLogPolicy.classify(failed).level)
        assertEquals(failed, selected(failed, terminal = true, protocol = true))
        assertEquals("exit: 0", selected("=== ADB SHELL EXIT: 0 ===", terminal = true, protocol = true))
        assertEquals("REV53_SHORT_OK", selected("│ REV53_SHORT_OK", terminal = true, protocol = true))
    }

    @Test fun repeatedOutputRemainsInOrderAndOnlyViewportIsBounded() {
        val transcript = TerminalTranscript()
        repeat(3500) { transcript.append("line $it") }
        transcript.append("same"); transcript.append("same")
        val lines = transcript.snapshot()
        assertEquals(3000, lines.size)
        assertEquals(listOf("same", "same"), lines.takeLast(2))
        transcript.clear(); assertTrue(transcript.snapshot().isEmpty())
    }
    @Test fun formattingDoesNotDecoratePayloadOrTreatWordsAsErrors() {
        for (line in listOf("0 ERROR messages", "LOCKED=no", "=== output ===", "plain text")) {
            assertEquals(line, CompactLogRenderPolicy.format(line).text)
            assertEquals(CompactLogRenderPolicy.Tone.INFO, CompactLogRenderPolicy.format(line).tone)
        }
        assertEquals(CompactLogRenderPolicy.Tone.ERROR, CompactLogRenderPolicy.format("❌ failed").tone)
    }
}
