package ru.forum.adbfastboottool

/** A host selector is never sent as part of a Fastboot wire command. */
internal object FastbootSerialSelection {
    fun select(previous: String?, value: String): String {
        require(value.isNotBlank()) { "-s SERIAL requires a non-empty serial number" }
        require(previous == null || previous == value) { "Conflicting -s SERIAL selectors" }
        return value
    }

    // USB serial is the desktop fastboot identity. getvar is a fallback only
    // when Android cannot read the descriptor, never a second chance on mismatch.
    fun actual(usbSerial: String?, getVar: () -> String?): String? =
        usbSerial?.takeIf { it.isNotBlank() }
            ?: getVar()?.takeIf { it.isNotBlank() }

    fun matches(expected: String, actual: String?): Boolean = expected == actual
}
