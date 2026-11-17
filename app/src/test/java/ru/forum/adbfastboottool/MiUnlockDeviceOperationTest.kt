package ru.forum.adbfastboottool

import java.io.File
import java.io.IOException
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MiUnlockDeviceOperationTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun payload() = temporary.newFile().apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
    private class Device(
        val replies: MutableList<String?> = mutableListOf("DATA00000004", "OKAY", "OKAY"),
        var transferWorks: Boolean = true
    ) : MiUnlockDeviceOperation.Port {
        val commands = mutableListOf<String>()
        var transfers = 0
        var connected = true
        var limit = 1024L
        var writeFailure: String? = null
        var finalError: Exception? = null
        override fun connected() = connected
        override fun downloadLimitBytes() = limit
        override fun write(command: String, timeoutMs: Int): Boolean {
            commands += command
            return command != writeFailure
        }
        private fun read(): MiUnlockDeviceOperation.Reply? {
            if (commands.lastOrNull() == "oem unlock") finalError?.let { throw it }
            val raw = if (replies.isEmpty()) null else replies.removeAt(0)
            return raw?.let { MiUnlockDeviceOperation.Reply(it.take(4), it.drop(4), it) }
        }
        override fun readDataOrFinal(timeoutMs: Int) = read()
        override fun acceptDataSize(reply: String, expectedBytes: Long) =
            FastbootDataReplyPolicy.validate(reply, expectedBytes) is FastbootDataReplyPolicy.Verdict.Accepted
        override fun transfer(file: File): Boolean { transfers++; return transferWorks }
        override fun readFinal(timeoutMs: Int) = read()
    }

    @Test fun onlyFinalUnlockOkayConfirmsOnceWithoutAnyReconnectCommand() {
        val device = Device()
        var receipts = 0
        val result = MiUnlockDeviceOperation.run(device, payload()) { receipts++ }
        assertEquals(MiUnlockDeviceOperation.Result.Confirmed, result)
        assertEquals(1, receipts)
        assertEquals(listOf("download:00000004", "oem unlock"), device.commands)
        assertEquals(1, device.transfers)
    }

    @Test fun downloadOkayFollowedByDisconnectDoesNotConfirmUnlockOrReplayIt() {
        val device = Device(mutableListOf("DATA00000004", "OKAY", null))
        val result = MiUnlockDeviceOperation.run(device, payload()) { fail("False success") }
        assertTrue(result is MiUnlockDeviceOperation.Result.Unconfirmed)
        assertEquals(MiUnlockDeviceOperation.Stage.UNLOCK_FINAL,
            (result as MiUnlockDeviceOperation.Result.Unconfirmed).stage)
        assertEquals(1, device.commands.count { it == "oem unlock" })
    }

    @Test fun explicitDeviceRefusalPreservesItsFullReason() {
        val reason = "Token rejected: account/device mismatch; exact device diagnostic " + "x".repeat(600)
        val device = Device(mutableListOf("DATA00000004", "OKAY", "FAIL$reason"))
        val result = MiUnlockDeviceOperation.run(device, payload()) { fail("False success") }
        assertEquals(MiUnlockDeviceOperation.Result.Failed(MiUnlockDeviceOperation.Stage.UNLOCK_FINAL, reason), result)
    }

    @Test fun rejectedDownloadNeverSendsUnlock() {
        val device = Device(mutableListOf("FAILdownload rejected by device"))
        assertEquals(
            MiUnlockDeviceOperation.Result.Failed(MiUnlockDeviceOperation.Stage.DOWNLOAD, "download rejected by device"),
            MiUnlockDeviceOperation.run(device, payload()) { fail("False success") }
        )
        assertEquals(listOf("download:00000004"), device.commands)
        assertEquals(0, device.transfers)
    }

    @Test fun mismatchedAndMalformedDataRepliesNeverTransferOrUnlock() {
        for (reply in listOf("DATA00000005", "DATAxyz", "DATA0000000g")) {
            val device = Device(mutableListOf(reply))
            assertTrue(MiUnlockDeviceOperation.run(device, payload()) { fail("False success") }
                is MiUnlockDeviceOperation.Result.Failed)
            assertEquals(0, device.transfers)
            assertFalse("oem unlock" in device.commands)
        }
    }

    @Test fun incompletePayloadNeverSendsUnlock() {
        val device = Device(transferWorks = false)
        assertTrue(MiUnlockDeviceOperation.run(device, payload()) { fail("False success") }
            is MiUnlockDeviceOperation.Result.Failed)
        assertFalse("oem unlock" in device.commands)
    }

    @Test fun failedOrMissingDownloadFinalNeverSendsUnlock() {
        for (reply in listOf(null, "FAILwrite rejected")) {
            val device = Device(mutableListOf("DATA00000004", reply))
            assertTrue(MiUnlockDeviceOperation.run(device, payload()) { fail("False success") }
                is MiUnlockDeviceOperation.Result.Failed)
            assertFalse("oem unlock" in device.commands)
        }
    }

    @Test fun partialCommandWriteAndFinalReadExceptionRemainUnconfirmed() {
        val writing = Device().apply { writeFailure = "oem unlock" }
        val reading = Device().apply { finalError = IOException("USB disconnected during reboot") }
        for (device in listOf(writing, reading)) {
            assertTrue(MiUnlockDeviceOperation.run(device, payload()) { fail("False success") }
                is MiUnlockDeviceOperation.Result.Unconfirmed)
            assertEquals(1, device.commands.count { it == "oem unlock" })
        }
    }

    @Test fun unexpectedFinalDataCannotBecomeSuccess() {
        val device = Device(mutableListOf("DATA00000004", "OKAY", "DATA00000004"))
        assertTrue(MiUnlockDeviceOperation.run(device, payload()) { fail("False success") }
            is MiUnlockDeviceOperation.Result.Unconfirmed)
    }

    @Test fun cancellationIsNotConvertedIntoAnExplicitDeviceRefusal() {
        val device = Device().apply { finalError = CancellationException("user stopped") }
        assertThrows(CancellationException::class.java) {
            MiUnlockDeviceOperation.run(device, payload()) { fail("False success") }
        }
    }

    @Test fun unavailableOversizedAndDisconnectedPreflightNeverWritesCommands() {
        val file = payload()
        val missing = File(temporary.root, "missing")
        val devices = listOf(
            Device() to missing,
            Device().apply { limit = 3 } to file,
            Device().apply { connected = false } to file
        )
        for ((device, input) in devices) {
            assertTrue(MiUnlockDeviceOperation.run(device, input) { fail("False success") }
                is MiUnlockDeviceOperation.Result.Failed)
            assertTrue(device.commands.isEmpty())
        }
    }
}
