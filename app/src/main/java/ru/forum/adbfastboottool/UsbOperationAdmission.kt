package ru.forum.adbfastboottool

/**
 * Pure admission check: an operation that uses USB must not attempt to start a
 * connectedDevice foreground service without the required live transport.
 * This check runs before creating a ServiceOwnedOperation, writing a pending
 * operation or touching USB. The real service remains mandatory when admitted.
 */
internal object UsbOperationAdmission {
    enum class RequiredTransport { ANY_USB, FASTBOOT, ADB }

    fun allowed(required: RequiredTransport, fastbootConnected: Boolean, adbConnected: Boolean): Boolean =
        when (required) {
            RequiredTransport.ANY_USB -> fastbootConnected || adbConnected
            RequiredTransport.FASTBOOT -> fastbootConnected
            RequiredTransport.ADB -> adbConnected
        }
}
