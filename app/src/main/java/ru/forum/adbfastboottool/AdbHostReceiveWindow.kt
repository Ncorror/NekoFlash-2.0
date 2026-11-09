package ru.forum.adbfastboottool

/**
 * Our Java Android USB host must advertise what it can actually receive
 * as a *single* ADB WRTE packet. This is CNXN.maxdata, NOT a file-size cap.
 *
 * ADB's receiver controls the size of packets sent by the other side.
 * Field evidence on the POCO F7 host showed unstable sustained transfers
 * with 1 MiB WRTE packets. The receive engine is now native usbfs URB-based,
 * but 16 KiB remains the conservative negotiated wire-frame limit until a
 * larger value has separate physical acceptance.
 *
 * Large logical streams (e.g. 256 MiB adb pull) still transfer in many
 * WRTE frames; SYNC DATA messages are reassembled independently.
 */
internal object AdbHostReceiveWindow {
    const val ADVERTISED_MAXDATA = 16 * 1024

    fun isAccepted(payloadLength: Int): Boolean =
        payloadLength in 0..ADVERTISED_MAXDATA
}
