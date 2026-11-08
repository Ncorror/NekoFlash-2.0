package ru.forum.adbfastboottool

/**
 * Read-only evidence that a Recovery environment changed after a Sideload attempt.
 *
 * This is a postcondition observation, NEVER an installer SUCCESS verdict. Both a
 * changed ADB product name and a changed USB vendor are required; a reconnect or
 * a mode switch by itself is not enough. The caller must also confirm the same
 * USB serial, device codename and completed/near-complete Sideload transfer.
 */
internal object SideloadRecoveryTransitionPolicy {
    data class Identity(
        val productName: String,
        val deviceCodename: String,
        val vendorId: Int,
        val productId: Int
    )

    data class Observation(val from: Identity, val to: Identity) {
        fun detail(packageName: String): String =
            "Recovery changed after Sideload of ${packageName.take(160)}: " +
                "${from.productName} (USB ${usb(from)}) → " +
                "${to.productName} (USB ${usb(to)}). " +
                "The ZIP installer result is still unverified."

        private fun usb(identity: Identity): String =
            "%04X:%04X".format(identity.vendorId, identity.productId)
    }

    fun fromAdbBanner(banner: String, vendorId: Int, productId: Int): Identity? {
        if (vendorId !in 1..0xffff || productId !in 0..0xffff) return null
        val mode = banner.substringBefore("::")
        if (!mode.equals("sideload", true) && !mode.equals("recovery", true)) return null
        if (!banner.contains("::")) return null
        val properties = banner.substringAfter("::").split(';')
            .mapNotNull { item ->
                val key = item.substringBefore('=', missingDelimiterValue = "")
                if (key.isBlank() || !item.contains('=')) null else key to item.substringAfter('=')
            }.toMap()
        val name = properties["ro.product.name"]?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val device = properties["ro.product.device"]?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return Identity(name, device, vendorId, productId)
    }

    fun observe(
        before: Identity?,
        after: Identity?,
        sameUsbSerial: Boolean,
        transferFinished: Boolean
    ): Observation? {
        if (!sameUsbSerial || !transferFinished || before == null || after == null) return null
        if (before.vendorId !in 1..0xffff || after.vendorId !in 1..0xffff) return null
        if (before.productId !in 0..0xffff || after.productId !in 0..0xffff) return null
        if (before.deviceCodename.isBlank() || after.deviceCodename.isBlank() ||
            !before.deviceCodename.equals(after.deviceCodename, true)) return null
        if (before.productName.isBlank() || after.productName.isBlank() ||
            before.productName.equals(after.productName, true)) return null
        if (before.vendorId == after.vendorId) return null
        return Observation(before, after)
    }
}
