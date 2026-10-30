package ru.forum.adbfastboottool

import java.io.File
import java.util.Base64
import java.util.Random
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AdbShellOutputRecordTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun multiMegabyteInterleavedBinaryIsStoredExactlyWithoutLoadingAsStrings() {
        val directory = File(temporary.root, "logs")
        val command = "printf '\\000'; adb-test-✓"
        val record = AdbShellOutputRecord(directory, "shell_v2", command)
        val stdout = ByteArray(6 * 1024 * 1024 + 53).also { Random(73).nextBytes(it) }
        val stderr = ByteArray(3 * 1024 * 1024 + 17).also { Random(84).nextBytes(it) }
        var a = 0
        var b = 0
        while (a < stdout.size || b < stderr.size) {
            if (a < stdout.size) {
                val end = minOf(a + 15_853, stdout.size)
                record.writeStdout(stdout.copyOfRange(a, end))
                a = end
            }
            if (b < stderr.size) {
                val end = minOf(b + 11_521, stderr.size)
                record.writeStderr(stderr.copyOfRange(b, end))
                b = end
            }
        }
        assertEquals("" to "", record.finish(17, success = false, captureText = false))
        record.close()
        assertArrayEquals(stdout, record.stdoutFile.readBytes())
        assertArrayEquals(stderr, record.stderrFile.readBytes())
        val meta = record.metadataFile.readText()
        assertTrue(meta.contains("command_utf8_base64=" +
            Base64.getEncoder().encodeToString(command.toByteArray(Charsets.UTF_8))))
        assertTrue(meta.contains("stdout_bytes=${stdout.size}"))
        assertTrue(meta.contains("stderr_bytes=${stderr.size}"))
        assertTrue(meta.contains("exit_code=17"))
        assertTrue(meta.contains("status=FAILED_OR_INTERRUPTED"))
    }

    @Test fun utf8CharactersSplitAcrossPacketsAreDecodedOnlyAfterFullCapture() {
        val record = AdbShellOutputRecord(temporary.root, "shell_v2", "echo")
        val text = "Hello, тест ✅ 日\n"
        val bytes = text.toByteArray(Charsets.UTF_8)
        bytes.forEach { record.writeStdout(byteArrayOf(it)) }
        record.writeStderr(byteArrayOf(0, -1, -2, 1))
        val strings = record.finish(0, success = true, captureText = true)
        assertEquals(text, strings.first)
        assertArrayEquals(byteArrayOf(0, -1, -2, 1), record.stderrFile.readBytes())
        assertTrue(record.metadataFile.readText().contains("status=SUCCESS"))
        record.close()
    }

    @Test fun interruptedCloseKeepsRawEvidenceAndNeverDeletesHistoricalSessions() {
        val before = AdbShellOutputRecord(temporary.root, "shell_legacy", "first")
        before.writeStdout(byteArrayOf(1, 2, 3))
        before.close()
        assertArrayEquals(byteArrayOf(1, 2, 3), before.stdoutFile.readBytes())
        assertTrue(before.metadataFile.readText().contains("status=FAILED_OR_INTERRUPTED"))

        val after = AdbShellOutputRecord(temporary.root, "shell_legacy", "second")
        after.writeStdout(byteArrayOf(4, 5))
        after.finish(null, success = true, captureText = false)
        after.close()
        assertNotEquals(before.stdoutFile.name, after.stdoutFile.name)
        assertArrayEquals(byteArrayOf(1, 2, 3), before.stdoutFile.readBytes())
        assertArrayEquals(byteArrayOf(4, 5), after.stdoutFile.readBytes())
    }

    @Test fun emptyChannelsAreStillAccountedForExactly() {
        val record = AdbShellOutputRecord(temporary.root, "shell_v2", "true")
        assertEquals("" to "", record.finish(0, success = true, captureText = true))
        assertEquals(0L, record.stdoutFile.length())
        assertEquals(0L, record.stderrFile.length())
        assertTrue(record.metadataFile.readText().contains("stdout_bytes=0"))
        record.close()
    }
}
