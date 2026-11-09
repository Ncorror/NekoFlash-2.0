package ru.forum.adbfastboottool

/**
 * Our Java Android USB host must advertise what it can actually receive
 * as a *single* ADB WRTE packet. This is CNXN.maxdata, NOT a file-size cap.
 *
 * ADB's receiver controls the size of packets sent by the other side.
 * The current synchronous USB bulk reader has field-verified 16 KiB
 * transfers but fails on device-generated 1 MiB WRTE packets. The AOSP
 * 16 KiB USBFS read unit is a safe receive window for this host API.
 *
 * Large logical streams (e.g. 256 MiB adb pull) still transfer in many
 * WRTE frames; SYNC DATA messages are reassembled independently.
 */
internal object AdbHostReceiveWindow {
    const val ADVERTISED_MAXDATA = 16 * 1024

    fun isAccepted(payloadLength: Int): Boolean =
        payloadLength in 0..ADVERTISED_MAXDATA
}
