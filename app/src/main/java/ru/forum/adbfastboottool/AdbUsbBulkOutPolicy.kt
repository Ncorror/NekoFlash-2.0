package ru.forum.adbfastboottool

/**
 * USB host -> adbd frame boundary.
 *
 * The device's bulk OUT reader may have a larger receive request than our
 * apacket header/payload. When the host transfer is an exact multiple of the
 * endpoint's wMaxPacketSize, only an explicit zero-length USB transfer marks
 * the end of this frame. This is *USB* framing, never an extra ADB byte.
 *
 * See AOSP adb/docs/dev/zero_length_packet.md.
 */
internal object AdbUsbBulkOutPolicy {
    fun requiresZlp(transferBytes: Int, maxPacketSize: Int): Boolean {
        require(transferBytes >= 0) { "Negative USB OUT transfer length" }
        require(maxPacketSize > 0) { "Invalid USB OUT maxPacketSize" }
        return transferBytes > 0 && transferBytes % maxPacketSize == 0
    }

    /**
     * Called only after every data byte in this header/payload transfer has
     * been written successfully. A successful zero-byte USB write returns 0.
     * Never infer success from an absent / failed ZLP and never retry commands.
     */
    fun finishTransfer(
        transferBytes: Int,
        maxPacketSize: Int,
        sendZlp: () -> Int
    ): Boolean = !requiresZlp(transferBytes, maxPacketSize) || sendZlp() == 0
}
