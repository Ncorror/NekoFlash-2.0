package ru.forum.adbfastboottool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalWelcomeMessageTest {
    private fun details() = TerminalWelcomeMessage.Details(
        title = "Welcome to NekoFlash-2.0",
        subtitle = "HOST + DEVICE TERMINAL",
        environment = "ENVIRONMENT",
        hostLabel = "HOST", host = "Android 16 · API 36",
        abiLabel = "ABI", abi = "arm64-v8a",
        workspaceLabel = "HOME", workspace = "Private HOME + public shared link",
        deviceBinaryLabel = "SYSTEM SHELL", deviceBinaryStatus = "ENABLED · SAVED",
        timeLabel = "TIME", time = "21:56",
        dateLabel = "DATE", date = "22 Nov 2026",
        commandSource = "COMMAND ROUTING",
        hostRoute = "HOST = ls · cd · pwd · clear",
        adbRoute = "ADB = adb shell <command>",
        fastbootRoute = "FASTBOOT = fastboot <command>",
        localAlias = "local <command> = force host routing",
        mixedFastboot = "FASTBOOT chain = shell bridge (confirmation)",
        systemShell = "Scripts require opt-in and confirmation",
        enableCommand = "export NEKO_USE_DEVICE_BINARIES=1",
        disableCommand = "export NEKO_USE_DEVICE_BINARIES=0",
        accessLabel = "ACCESS", access = "App UID; not root",
        help = "QUICK HELP", helpHint = "For syntax and examples, execute:",
        transport = "Interactive ADB shell stays on device; local forces host",
    )

    @Test fun everyWelcomeRowFitsTheFixedTerminalWidth() {
        val lines = TerminalWelcomeMessage.layout(details())
        assertTrue(lines.isNotEmpty())
        assertTrue(lines.all { TerminalWelcomeMessage.displayWidth(it) == 48 })
    }

    @Test fun welcomeTitleAndHelpAreProminentWithoutListingEveryCommand() {
        val lines = TerminalWelcomeMessage.layout(details())
        assertTrue(lines[1].contains("Welcome to NekoFlash-2.0"))
        assertTrue(lines.any { it.contains("help") })
        assertTrue(lines.any { it.contains("HOST = ls") })
        assertTrue(lines.any { it.contains("ADB = adb shell") })
        assertTrue(lines.any { it.contains("FASTBOOT = fastboot") })
        assertTrue(lines.any { it.contains("shell bridge") })
        assertTrue(lines.any { it.contains("NEKO_USE_DEVICE_BINARIES=1") })
        assertTrue(lines.any { it.contains("NEKO_USE_DEVICE_BINARIES=0") })
        assertTrue(lines.any { it.contains("SYSTEM SHELL") && it.contains("ENABLED") })
        assertFalse(lines.any { it.contains("local ls") || it.contains("local cat") || it.contains("local grep") })
    }

    @Test fun framedRowsRestoreEmojiAndKeepBordersAligned() {
        val lines = TerminalWelcomeMessage.layout(details())
        val framed = lines.filter { it.startsWith("║ ") }
        assertTrue(framed.isNotEmpty())
        assertTrue(framed.all { it.first() == '║' && it.last() == '║' })
        assertTrue(framed.all { TerminalWelcomeMessage.displayWidth(it) == 48 })
        listOf("✨", "📱", "🧩", "📁", "🟢", "🕒", "📅", "⌂", "↗", "⚙", "↩", "🔗", "▣", "🔐", "💡", "🔄")
            .forEach { emoji -> assertTrue("Missing $emoji", lines.any { it.contains(emoji) }) }
        assertFalse(framed.any { it.contains("@ ") || it.contains("# ") || it.contains("-> ") })
    }

    @Test fun visualRowsAreRecognizedForSemanticColouring() {
        val lines = TerminalWelcomeMessage.layout(details())
        assertTrue(lines.all(TerminalWelcomeMessage::isWelcomeLine))
        assertFalse(TerminalWelcomeMessage.isWelcomeLine("[Local] command completed"))
        assertFalse(TerminalWelcomeMessage.isWelcomeLine("===="))
    }
}
