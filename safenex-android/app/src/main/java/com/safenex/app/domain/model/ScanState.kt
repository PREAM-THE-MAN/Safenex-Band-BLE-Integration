package com.safenex.app.domain.model

import android.bluetooth.BluetoothDevice

/**
 * State representing the BLE discovery phase.
 */
sealed class ScanState {
    object Idle : ScanState()
    object Scanning : ScanState()
    data class DeviceFound(
        val device: BluetoothDevice,
        val name: String,
        val rssi: Int
    ) : ScanState()
    object NotFound : ScanState()
    data class Failed(val reason: String) : ScanState()
}
