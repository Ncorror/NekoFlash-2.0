package ru.forum.adbfastboottool

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint

/**
 * ADB bulk-IN receiver backed by Linux usbfs URBs.
 *
 * Android's UsbDeviceConnection.bulkTransfer() wraps USBDEVFS_BULK and only
 * exposes a generic negative result. AOSP Linux adb host uses
 * USBDEVFS_SUBMITURB/REAPURB for bulk IN and checks errno + urb.status.
 * This backend follows that transport shape for one framed ADB payload.
 * It never retries a failed URB and never publishes a partial packet.
 */
internal object AdbNativeUsbfsIn {
    data class Result(
        val success: Boolean,
        val bytesTransferred: Int,
        val errnoCode: Int,
        val kernelIoctlErrno: Int,
        val urbStatus: Int,
        val actualLength: Int,
        val elapsedMs: Long,
        val stage: Int,
        val message: String
    ) {
        val disconnected: Boolean
            get() = errnoCode == ERRNO_ENODEV || errnoCode == ERRNO_ESHUTDOWN ||
                kernelIoctlErrno == ERRNO_ENODEV || kernelIoctlErrno == ERRNO_ESHUTDOWN

        val timedOut: Boolean
            get() = errnoCode == ERRNO_ETIMEDOUT || kernelIoctlErrno == ERRNO_ETIMEDOUT
    }

    private val loadFailure: Throwable? = runCatching {
        System.loadLibrary("nekoflash_usbfs")
    }.exceptionOrNull()

    val isAvailable: Boolean
        get() = loadFailure == null

    fun readExact(
        connection: UsbDeviceConnection,
        endpoint: UsbEndpoint,
        buffer: ByteArray,
        offset: Int,
        length: Int,
        timeoutMs: Int
    ): Result {
        if (!isAvailable) return failed("native library unavailable")
        if (endpoint.direction != UsbConstants.USB_DIR_IN) return failed("endpoint is not IN")
        if (offset < 0 || length < 0 || offset > buffer.size || length > buffer.size - offset) {
            return failed("invalid destination range")
        }
        if (length == 0) {
            return Result(true, 0, 0, 0, 0, 0, 0L, NativeUsbfsBackend.STAGE_DONE, "empty")
        }
        if (timeoutMs <= 0) return failed("invalid timeout")
        val fd = runCatching { connection.fileDescriptor }.getOrDefault(-1)
        if (fd < 0) return failed("invalid UsbDeviceConnection fd")

        val raw = try {
            nativeReadExact(fd, endpoint.address, buffer, offset, length, timeoutMs)
        } catch (error: Throwable) {
            return failed("JNI ${error.javaClass.simpleName}: ${error.message ?: "no detail"}")
        }
        return decode(raw, length)
    }

    internal fun decodeForTest(raw: LongArray, requested: Int): Result = decode(raw, requested)

    private fun decode(raw: LongArray, requested: Int): Result {
        if (raw.size < RESULT_FIELDS) return failed("short native result: ${raw.size}")
        val success = raw[IDX_SUCCESS] == 1L
        val bytes = raw[IDX_CONFIRMED_BYTES].coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        val errno = raw[IDX_STOP_ERRNO].toInt()
        val status = raw[IDX_LAST_STATUS].toInt()
        val actual = raw[IDX_LAST_ACTUAL].toInt()
        val elapsed = raw[IDX_ELAPSED_MS].coerceAtLeast(0L)
        val stage = raw[IDX_STAGE].toInt()
        val kernelErrno = raw[IDX_KERNEL_IOCTL_ERRNO].toInt()
        val state = if (success) "OK" else "FAIL"
        val message = "native usbfs IN $state: bytes=$bytes/$requested, errno=$errno, " +
            "kernelErrno=$kernelErrno, urbStatus=$status, actual=$actual, " +
            "stage=${NativeUsbfsBackend.stageLabel(stage)}, elapsedMs=$elapsed"
        return Result(success, bytes, errno, kernelErrno, status, actual, elapsed, stage, message)
    }

    private fun failed(detail: String) = Result(
        false, 0, 0, 0, 0, 0, 0L, NativeUsbfsBackend.STAGE_PREFLIGHT, detail
    )

    private external fun nativeReadExact(
        fd: Int,
        endpointAddress: Int,
        buffer: ByteArray,
        offset: Int,
        length: Int,
        timeoutMs: Int
    ): LongArray

    private const val IDX_SUCCESS = 0
    private const val IDX_CONFIRMED_BYTES = 1
    private const val IDX_STOP_ERRNO = 2
    private const val IDX_LAST_STATUS = 3
    private const val IDX_LAST_ACTUAL = 4
    private const val IDX_ELAPSED_MS = 5
    private const val IDX_STAGE = 6
    private const val IDX_KERNEL_IOCTL_ERRNO = 13
    private const val RESULT_FIELDS = 14

    private const val ERRNO_ETIMEDOUT = 110
    private const val ERRNO_ENODEV = 19
    private const val ERRNO_ESHUTDOWN = 108
}
