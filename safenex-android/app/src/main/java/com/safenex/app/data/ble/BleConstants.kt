package com.safenex.app.data.ble

import java.util.UUID

object BleConstants {
    const val DEVICE_NAME = "SAFENEX-BAND"

    // Primary SAFENEX GATT Service
    val SERVICE_UUID: UUID = UUID.fromString("7b7a1000-8f2a-4c5e-9b11-123456789001")

    // SOS Emergency Characteristic (Notify / Read)
    val SOS_CHAR_UUID: UUID = UUID.fromString("7b7a1001-8f2a-4c5e-9b11-123456789001")

    // STATUS / Heartbeat Characteristic (Notify / Read)
    val STATUS_CHAR_UUID: UUID = UUID.fromString("7b7a1002-8f2a-4c5e-9b11-123456789001")

    // Client Characteristic Configuration Descriptor (CCCD) for enabling notifications
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // Default scan timeout in milliseconds
    const val SCAN_TIMEOUT_MS = 12000L

    // Auto-reconnect retry delay
    const val RECONNECT_DELAY_MS = 3000L
}
