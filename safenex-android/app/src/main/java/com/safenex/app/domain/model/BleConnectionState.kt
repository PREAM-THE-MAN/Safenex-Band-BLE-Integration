package com.safenex.app.domain.model

/**
 * State representing the active BLE connection with the SAFENEX Band.
 */
sealed class BleConnectionState {
    object Disconnected : BleConnectionState()
    object Connecting : BleConnectionState()
    data class Connected(
        val deviceName: String = "SAFENEX-BAND",
        val deviceAddress: String = ""
    ) : BleConnectionState()
    object Disconnecting : BleConnectionState()
    data class Error(val message: String) : BleConnectionState()
}
