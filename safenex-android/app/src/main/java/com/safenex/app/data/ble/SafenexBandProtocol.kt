package com.safenex.app.data.ble

import com.safenex.app.domain.model.EmergencyEvent
import com.safenex.app.domain.model.TriggerSource

/**
 * Protocol parser for incoming BLE notifications from the ESP32 SAFENEX-BAND.
 */
object SafenexBandProtocol {

    private const val PREFIX_SOS = "SOS:"
    private const val ACTION_BUTTON = "BUTTON"
    private const val ACTION_SHAKE = "SHAKE"

    /**
     * Parses raw byte payload received over GATT notification.
     */
    fun parseNotification(characteristicUuid: String, data: ByteArray?): ProtocolResult {
        if (data == null || data.isEmpty()) {
            return ProtocolResult.Ignored("Empty payload")
        }

        val text = String(data, Charsets.UTF_8).trim()

        return if (characteristicUuid.equals(BleConstants.SOS_CHAR_UUID.toString(), ignoreCase = true)) {
            parseSosPayload(text)
        } else if (characteristicUuid.equals(BleConstants.STATUS_CHAR_UUID.toString(), ignoreCase = true)) {
            ProtocolResult.StatusUpdate(text)
        } else {
            // Also attempt fallback parse if text begins with SOS:
            if (text.startsWith(PREFIX_SOS, ignoreCase = true)) {
                parseSosPayload(text)
            } else {
                ProtocolResult.Ignored("Unknown characteristic: $characteristicUuid, payload: $text")
            }
        }
    }

    private fun parseSosPayload(payload: String): ProtocolResult {
        val trimmed = payload.trim()
        val upper = trimmed.uppercase()

        return when {
            upper == "SOS:BUTTON" || upper.endsWith(":BUTTON") || upper == "BUTTON" -> {
                ProtocolResult.SosEvent(
                    EmergencyEvent(
                        source = TriggerSource.BUTTON,
                        timestamp = System.currentTimeMillis(),
                        rawPayload = trimmed
                    )
                )
            }
            upper == "SOS:SHAKE" || upper.endsWith(":SHAKE") || upper == "SHAKE" -> {
                ProtocolResult.SosEvent(
                    EmergencyEvent(
                        source = TriggerSource.SHAKE,
                        timestamp = System.currentTimeMillis(),
                        rawPayload = trimmed
                    )
                )
            }
            upper.startsWith("SOS:") -> {
                ProtocolResult.SosEvent(
                    EmergencyEvent(
                        source = TriggerSource.UNKNOWN,
                        timestamp = System.currentTimeMillis(),
                        rawPayload = trimmed
                    )
                )
            }
            else -> ProtocolResult.Ignored("Non-SOS payload: $trimmed")
        }
    }
}

/**
 * Result wrapper for parsed protocol packets.
 */
sealed class ProtocolResult {
    data class SosEvent(val event: EmergencyEvent) : ProtocolResult()
    data class StatusUpdate(val status: String) : ProtocolResult()
    data class Ignored(val reason: String) : ProtocolResult()
}
