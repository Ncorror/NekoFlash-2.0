package ru.forum.adbfastboottool

import java.util.ArrayDeque

/** Command output is independent of the complete diagnostic journal. */
internal class TerminalTranscript {
    private val lines = ArrayDeque<String>()
    fun append(message: String) {
        lines.addLast(message)
        // This bounds only the live viewport. The full diagnostic files remain available.
        while (lines.size > CompactLogRenderPolicy.MAX_VISIBLE_LINES) lines.removeFirst()
    }
    fun snapshot(): List<String> = lines.toList()
    fun clear() = lines.clear()

    companion object {
        fun select(message: String, declaredLevel: DiagnosticLogPolicy.Level?,
                   userInput: Boolean, protocol: Boolean, terminalOperation: Boolean): String? {
            // Keep actual command payload verbatim, including repeated lines.
            if (protocol && terminalOperation) {
                if (message.startsWith("│ ")) return message.removePrefix("│ ")
                if (message.startsWith("↩ ")) return message.removePrefix("↩ ")
                if (message.startsWith("✅ ")) return message
                if (message.startsWith("=== ADB") && message.contains("EXIT:")) {
                    return "exit: " + message.substringAfter("EXIT:").substringBefore("===").trim()
                }
            }
            if (userInput || message.startsWith("> ")) return message
            val level = DiagnosticLogPolicy.classify(message, declaredLevel).level
            return message.takeIf { level == DiagnosticLogPolicy.Level.ERROR || level == DiagnosticLogPolicy.Level.WARNING }
        }
    }
}
