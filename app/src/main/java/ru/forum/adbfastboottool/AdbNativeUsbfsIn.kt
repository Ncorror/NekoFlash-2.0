package ru.forum.adbfastboottool

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint

/**
 * ADB bulk-IN receiver backed by Linux usbfs URBs.
 *
 * Current AOSP Linux adb treats each ADB header/payload as one USB transfer:
 * the header receive buffer is rounded to the endpoint packet size, while a
 * payload receive buffer is rounded up only when needed to avoid packet
 * overflow. A completed transfer is accepted only when actual_length equals
 * the ADB protocol length. No failed URB or ADB command is replayed here.
 */
internal object AdbNativeUsbfsIn {
    data class Result(
        val success: Boolean,
        val bytesTransferred: Int,
        val errnoCode: Int,
        val kernelIoctlErrno: Int,
        val urbStatus: Int,
        val actualLength: Int,
        val urbSubmissions: Int,
        val elapsedMs: Long,
        val stage: Int,
        val message: String
    ) {
        val disconnected: Boolean
            get() = errnoCode == ERRNO_ENODEV || errnoCode == ERRNO_ESHUTDOWN ||
                kernelIoctlErrno == ERRNO_ENODEV || kernelIoctlErrno == ERRNO_ESHUTDOWN

        val timedOut: Boolean
            get() = errnoCode == ERRNO_ETIMEDOUT || kernelIoctlErrno == ERRNO_ETIMEDOUT

        val isCleanNoDataTimeout: Boolean
            get() = !success &&
                timedOut &&
                bytesTransferred == 0 &&
                kernelIoctlErrno == 0 &&
                urbStatus == 0 &&
                actualLength == 0 &&
                stage == NativeUsbfsBackend.STAGE_TIMEOUT
    }

    private val loadFailure: Throwable? = runCatching {
        System.loadLibrary("nekoflash_usbfs")
    }.exceptionOrNull()

    val isAvailable: Boolean
        get() = loadFailure == null

    internal fun aospRequestLength(expectedLength: Int, maxPacketSize: Int): Int {
        if (expectedLength <= 0 || maxPacketSize <= 0) return -1
        val remainder = expectedLength % maxPacketSize
        val padding = if (remainder == 0) 0 else maxPacketSize - remainder
        if (expectedLength > Int.MAX_VALUE - padding) return -1
        return expectedLength + padding
    }

    fun readTransfer(
        connection: UsbDeviceConnection,
        endpoint: UsbEndpoint,
        buffer: ByteArray,
        offset: Int,
        expectedLength: Int,
        requestLength: Int,
        timeoutMs: Int
    ): Result {
        if (!isAvailable) return failed("native library unavailable")
        if (endpoint.direction != UsbConstants.USB_DIR_IN) return failed("endpoint is not IN")
        if (offset < 0 || expectedLength <= 0 || offset > buffer.size ||
            expectedLength > buffer.size - offset) {
            return failed("invalid destination range")
        }
        if (requestLength < expectedLength) return failed("USB request is smaller than expected transfer")
        if (timeoutMs <= 0) return failed("invalid timeout")
        val fd = runCatching { connection.fileDescriptor }.getOrDefault(-1)
        if (fd < 0) return failed("invalid UsbDeviceConnection fd")

        val raw = try {
            nativeReadTransfer(
                fd, endpoint.address, buffer, offset,
                expectedLength, requestLength, timeoutMs
            )
        } catch (error: Exception) {
            return failed("JNI ${error.javaClass.simpleName}: ${error.message ?: "no detail"}")
        } catch (error: LinkageError) {
            return failed("JNI ${error.javaClass.simpleName}: ${error.message ?: "no detail"}")
        }
        return decode(raw, expectedLength, requestLength)
    }

    internal fun decodeForTest(
        raw: LongArray,
        expectedLength: Int,
        requestLength: Int = expectedLength
    ): Result = decode(raw, expectedLength, requestLength)

    private fun decode(raw: LongArray, expectedLength: Int, requestLength: Int): Result {
        if (raw.size < RESULT_FIELDS) return failed("short native result: ${raw.size}")
        val success = raw[IDX_SUCCESS] == 1L
        val bytes = raw[IDX_CONFIRMED_BYTES].coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        val errno = raw[IDX_STOP_ERRNO].toInt()
        val status = raw[IDX_LAST_STATUS].toInt()
        val actual = raw[IDX_LAST_ACTUAL].toInt()
        val submissions = raw[IDX_URB_SUBMISSIONS]
            .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        val elapsed = raw[IDX_ELAPSED_MS].coerceAtLeast(0L)
        val stage = raw[IDX_STAGE].toInt()
        val kernelErrno = raw[IDX_KERNEL_IOCTL_ERRNO].toInt()
        val state = if (success) "OK" else "FAIL"
        val message = "native usbfs IN $state: bytes=$bytes/$expectedLength, request=$requestLength, " +
            "errno=$errno, kernelErrno=$kernelErrno, urbStatus=$status, actual=$actual, " +
            "urbSubmissions=$submissions, " +
            "stage=${NativeUsbfsBackend.stageLabel(stage)}, elapsedMs=$elapsed"
        return Result(
            success, bytes, errno, kernelErrno, status, actual, submissions, elapsed, stage, message
        )
    }

    private fun failed(detail: String) = Result(
        false, 0, 0, 0, 0, 0, 0, 0L, NativeUsbfsBackend.STAGE_PREFLIGHT, detail
    )

    private external fun nativeReadTransfer(
        fd: Int,
        endpointAddress: Int,
        buffer: ByteArray,
        offset: Int,
        expectedLength: Int,
        requestLength: Int,
        timeoutMs: Int
    ): LongArray

    private const val IDX_SUCCESS = 0
    private const val IDX_CONFIRMED_BYTES = 1
    private const val IDX_STOP_ERRNO = 2
    private const val IDX_LAST_STATUS = 3
    private const val IDX_LAST_ACTUAL = 4
    private const val IDX_ELAPSED_MS = 5
    private const val IDX_STAGE = 6
    private const val IDX_URB_SUBMISSIONS = 7
    private const val IDX_KERNEL_IOCTL_ERRNO = 13
    private const val RESULT_FIELDS = 14

    private const val ERRNO_ETIMEDOUT = 110
    private const val ERRNO_ENODEV = 19
    private const val ERRNO_ESHUTDOWN = 108
}
