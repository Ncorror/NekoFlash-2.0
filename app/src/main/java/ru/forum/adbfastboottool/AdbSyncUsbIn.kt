package ru.forum.adbfastboottool

import android.hardware.usb.UsbDeviceConnection

/** One bulkTransfer-equivalent call; never retries or switches backend after IN. */
internal object AdbSyncUsbIn {
    private val loaded: Boolean = try {
        System.loadLibrary("nekoflash_usbfs")
        true
    } catch (_: LinkageError) {
        false
    }

    val available: Boolean get() = loaded

    data class Completion(val result: Int, val errno: Int, val elapsedNs: Long, val stage: Int) {
        fun detail(requested: Int): String =
            "backend=sync-usbfs result=$result errno=$errno kernelErrno=${if (stage == 1) errno else "unknown"} " +
                "requested=$requested actual=${if (result >= 0) result else "unknown"} " +
                "elapsedNs=$elapsedNs stage=$stage"
    }

    /** Read-only USB GET_STATUS after failure, never CLEAR_FEATURE/reset. */
    fun endpointStatus(connection: UsbDeviceConnection, endpoint: Int): String {
        val status = ByteArray(2)
        val result = connection.controlTransfer(0x82, 0, 0, endpoint, status, 2, 500)
        return if (result == 2) {
            val bits = (status[0].toInt() and 255) or ((status[1].toInt() and 255) shl 8)
            "postFailureEndpointStatus=$bits halted=${bits and 1 != 0}"
        } else "postFailureEndpointStatus=unknown getStatusResult=$result"
    }

    fun read(fd: Int, endpoint: Int, buffer: ByteArray, offset: Int, length: Int, timeoutMs: Int): Completion =
        decode(nativeRead(fd, endpoint, buffer, offset, length, timeoutMs), length)

    internal fun decode(raw: LongArray, requested: Int): Completion {
        require(raw.size == 4 && raw[0] in -1L..requested.toLong() &&
            raw[1] in 0L..Int.MAX_VALUE.toLong() && raw[2] >= 0 && raw[3] in 0L..2L) {
            "Invalid synchronous USB IN completion"
        }
        return Completion(raw[0].toInt(), raw[1].toInt(), raw[2], raw[3].toInt())
    }

    private external fun nativeRead(
        fd: Int, endpoint: Int, buffer: ByteArray, offset: Int, length: Int, timeoutMs: Int
    ): LongArray
}
