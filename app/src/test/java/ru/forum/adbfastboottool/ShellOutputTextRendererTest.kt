package ru.forum.adbfastboottool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellOutputTextRendererTest {
    @Test fun splitUtf8CodePointsAreReassembledBeforeRendering() {
        val events = mutableListOf<String>()
        val renderer = ShellOutputTextRenderer(false, events::add)
        val bytes = "Привет 🌙 Байт\n".toByteArray(Charsets.UTF_8)
        bytes.forEach { renderer.accept(byteArrayOf(it)) }
        renderer.finish()
        assertEquals(listOf("│ Привет 🌙 Байт"), events)
    }

    @Test fun crlfAndPartialLinesSpanArbitraryPackets() {
        val events = mutableListOf<String>()
        val renderer = ShellOutputTextRenderer(false, events::add)
        listOf("first\r", "\nsecond", " par", "t\r", "\n", "tail").forEach {
            renderer.accept(it.toByteArray(Charsets.UTF_8))
        }
        assertEquals(listOf("│ first", "│ second part"), events)
        renderer.finish()
        renderer.finish() // duplicate close must not produce duplicate terminal lines
        assertEquals(listOf("│ first", "│ second part", "│ tail"), events)
    }

    @Test fun progressOverCarriageReturnsIsVisibleBeforeCommandExits() {
        val events = mutableListOf<String>()
        val renderer = ShellOutputTextRenderer(false, events::add)
        renderer.accept("10%\r20".toByteArray(Charsets.UTF_8))
        assertEquals(listOf("│ 10%"), events)
        renderer.accept("%\r100%\r\n".toByteArray(Charsets.UTF_8))
        assertEquals(listOf("│ 10%", "│ 20%", "│ 100%"), events)
        renderer.finish()
        assertEquals(3, events.size)
    }

    @Test fun stdoutAndStderrKeepSeparateUtf8DecoderState() {
        val events = mutableListOf<String>()
        val out = ShellOutputTextRenderer(false, events::add)
        val err = ShellOutputTextRenderer(true, events::add)
        val stdout = "🐈".toByteArray(Charsets.UTF_8)
        val stderr = "⚠".toByteArray(Charsets.UTF_8)
        out.accept(stdout.copyOfRange(0, 2))
        err.accept(stderr.copyOfRange(0, 1))
        out.accept(stdout.copyOfRange(2, stdout.size))
        err.accept(stderr.copyOfRange(1, stderr.size))
        out.finish()
        err.finish()
        assertEquals(listOf("│ 🐈", "│ stderr: ⚠"), events)
    }

    @Test fun continuousHugeLineIsRenderedInBoundedFragmentsWithoutTruncation() {
        val events = mutableListOf<String>()
        val renderer = ShellOutputTextRenderer(false, events::add)
        val text = "abc🌙".repeat(10_000)
        val bytes = text.toByteArray(Charsets.UTF_8)
        var offset = 0
        while (offset < bytes.size) {
            val count = minOf(7, bytes.size - offset)
            renderer.accept(bytes.copyOfRange(offset, offset + count))
            offset += count
        }
        renderer.finish()
        assertTrue("Expected multiple bounded display fragments", events.size >= 3)
        assertTrue(events.all { it.removePrefix("│ ").length <= 8193 }) // surrogate pair may add one UTF-16 code unit
        assertEquals(text, events.joinToString("") { it.removePrefix("│ ") })
    }

    @Test fun promptIsShownWithoutNewlineWhileSplitEmojiStaysValid() {
        val events = mutableListOf<String>()
        val renderer = ShellOutputTextRenderer(false, events::add)
        val emoji = "🌙".toByteArray(Charsets.UTF_8)
        renderer.accept("shell@device:$ ".toByteArray(Charsets.UTF_8))
        renderer.accept(emoji.copyOfRange(0, 2))
        renderer.flushVisibleFragment()
        assertEquals(listOf("│ shell@device:$ "), events)
        renderer.accept(emoji.copyOfRange(2, 4))
        renderer.accept(" готово".toByteArray(Charsets.UTF_8))
        renderer.flushVisibleFragment()
        renderer.accept("\n".toByteArray(Charsets.UTF_8))
        renderer.finish()
        assertEquals(listOf("│ shell@device:$ ", "│ 🌙 готово"), events)
    }

    @Test fun splitUtf8ChannelsStayIndependentWhenPtyCloses() {
        val events = mutableListOf<String>()
        val stdout = ShellOutputTextRenderer(false, events::add)
        val stderr = ShellOutputTextRenderer(true, events::add)
        val out = "Привет".toByteArray(Charsets.UTF_8)
        val err = "Ошибка".toByteArray(Charsets.UTF_8)
        out.forEachIndexed { i, b ->
            stdout.accept(byteArrayOf(b))
            if (i < err.size) stderr.accept(byteArrayOf(err[i]))
        }
        if (err.size > out.size) stderr.accept(err.copyOfRange(out.size, err.size))
        stdout.flushVisibleFragment()
        stderr.finish()
        stdout.finish()
        assertEquals(listOf("│ Привет", "│ stderr: Ошибка"), events)
    }

    @Test fun binaryNulIsOnlyHiddenInTextViewAndMalformedUtf8DoesNotCrash() {
        val events = mutableListOf<String>()
        val renderer = ShellOutputTextRenderer(true, events::add)
        renderer.accept(byteArrayOf('A'.code.toByte(), 0, 0xC3.toByte()))
        renderer.finish()
        assertEquals(listOf("│ stderr: A\uFFFD"), events)
    }
}
