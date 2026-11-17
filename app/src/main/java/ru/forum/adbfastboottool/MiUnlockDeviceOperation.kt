package ru.forum.adbfastboottool

import java.io.File
import java.util.concurrent.CancellationException

/** Xiaomi staging uses the existing Fastboot transport, once, without command replay. */
internal object MiUnlockDeviceOperation {
    enum class Stage { PREFLIGHT, DOWNLOAD, TRANSFER, DOWNLOAD_FINAL, UNLOCK_COMMAND, UNLOCK_FINAL }
    data class Reply(val type: String, val payload: String, val raw: String)
    sealed interface Result {
        data object Confirmed : Result
        data class Failed(val stage: Stage, val reason: String) : Result
        data class Unconfirmed(val stage: Stage, val reason: String) : Result
    }
    interface Port {
        fun connected(): Boolean
        fun downloadLimitBytes(): Long?
        fun write(command: String, timeoutMs: Int): Boolean
        fun readDataOrFinal(timeoutMs: Int): Reply?
        fun acceptDataSize(reply: String, expectedBytes: Long): Boolean
        fun transfer(file: File): Boolean
        fun readFinal(timeoutMs: Int): Reply?
    }

    fun run(port: Port, file: File, onConfirmed: () -> Unit): Result {
        var stage = Stage.PREFLIGHT
        fun failed(reason: String) = Result.Failed(stage, reason)
        fun unconfirmed(reason: String) = Result.Unconfirmed(stage, reason)
        fun Reply.reason(): String = payload.ifBlank { raw }
        try {
            if (!port.connected()) return failed("No Fastboot connection")
            if (!file.isFile || !file.canRead() || file.length() <= 0L) {
                return failed("Unlock file is unavailable or empty")
            }
            val bytes = file.length()
            FastbootDownloadSizePolicy.validate(bytes, port.downloadLimitBytes())?.let {
                return failed("Unlock data size rejected: ${it.reason} ($bytes bytes)")
            }
            stage = Stage.DOWNLOAD
            if (!port.write("download:%08x".format(bytes), 5_000)) {
                return failed("Could not send unlock download request; oem unlock was not sent")
            }
            val ready = port.readDataOrFinal(10_000)
                ?: return failed("No response to unlock download request; oem unlock was not sent")
            if (ready.type != "DATA") return failed(ready.reason())
            if (!port.acceptDataSize(ready.payload, bytes)) {
                return failed("Unlock DATA size was not confirmed: expected $bytes bytes, reply=${ready.payload}")
            }
            stage = Stage.TRANSFER
            if (!port.transfer(file)) return failed("Unlock data transfer failed; oem unlock was not sent")
            stage = Stage.DOWNLOAD_FINAL
            val downloaded = port.readFinal(30_000)
                ?: return failed("Unlock data receipt was not confirmed; oem unlock was not sent")
            if (downloaded.type != "OKAY") return failed(downloaded.reason())

            // From this point a missing/partial response cannot establish that the device
            // stayed locked. Do not turn a reboot/disconnect into an explicit rejection.
            stage = Stage.UNLOCK_COMMAND
            if (!port.write("oem unlock", 10_000)) {
                return unconfirmed("Could not confirm sending oem unlock")
            }
            stage = Stage.UNLOCK_FINAL
            val unlocked = port.readFinal(30_000)
                ?: return unconfirmed("No final response to oem unlock; the device may have rebooted")
            return when (unlocked.type) {
                "OKAY" -> {
                    // Record the terminal receipt before logging, cleanup, or USB detach.
                    onConfirmed()
                    Result.Confirmed
                }
                "FAIL" -> failed(unlocked.reason())
                else -> unconfirmed("Unexpected response to oem unlock: ${unlocked.raw}")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val reason = error.message ?: error.javaClass.simpleName
            return if (stage == Stage.UNLOCK_COMMAND || stage == Stage.UNLOCK_FINAL) {
                unconfirmed(reason)
            } else failed(reason)
        }
    }
}
