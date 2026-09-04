package com.safenex.app.data.guardian

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import com.safenex.app.data.location.SafenexLocation
import com.safenex.app.domain.model.EmergencyEvent
import com.safenex.app.domain.model.TriggerSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Dispatches automated SOS alerts to trusted guardians via SMS, WhatsApp, and Phone Dialer.
 */
class AlertDispatcher(
    private val context: Context,
    private val guardianRepository: GuardianRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    companion object {
        private const val TAG = "SAFENEX_AlertDispatcher"
    }

    private val _alerts = MutableStateFlow<List<GuardianAlert>>(emptyList())
    val alerts: StateFlow<List<GuardianAlert>> = _alerts.asStateFlow()

    /**
     * Builds standard emergency message payload.
     */
    fun buildEmergencyMessage(event: EmergencyEvent, location: SafenexLocation?): String {
        val triggerText = when (event.source) {
            TriggerSource.BUTTON -> "Band SOS Button"
            TriggerSource.SHAKE -> "Vigorous Shake Detection"
            TriggerSource.VOICE -> "Voice Command (DITTU \"HELP!\")"
            TriggerSource.SCHEDULE_OVERDUE -> "Scheduled Commute Overdue (No 60s Check-In Response)"
            TriggerSource.UNKNOWN -> "Emergency Alert"
        }

        val locationText = if (location != null) {
            val addr = location.address?.let { " ($it)" } ?: ""
            "\nLocation: ${location.formattedCoordinates}$addr\nMap: ${location.mapsUrl}"
        } else {
            "\nLocation: Acquiring GPS..."
        }

        return "🚨 SAFENEX EMERGENCY ALERT!\n" +
                "Activated by $triggerText!$locationText\n" +
                "Please check on me immediately!"
    }

    /**
     * Dispatches emergency alert SMS to all registered Guardians.
     */
    @SuppressLint("MissingPermission")
    fun dispatchAlerts(event: EmergencyEvent, location: SafenexLocation?) {
        val guardians = guardianRepository.guardians.value
        if (guardians.isEmpty()) {
            Log.w(TAG, "No guardians configured to dispatch alerts.")
            return
        }

        val message = buildEmergencyMessage(event, location)
        val initialAlerts = guardians.map { GuardianAlert(it, AlertStatus.SENDING) }
        _alerts.value = initialAlerts

        scope.launch {
            val smsManager: SmsManager? = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
            } catch (e: Exception) {
                null
            }

            val updatedAlerts = mutableListOf<GuardianAlert>()

            for (guardian in guardians) {
                try {
                    if (smsManager != null && guardian.phoneNumber.isNotBlank()) {
                        val parts = smsManager.divideMessage(message)
                        smsManager.sendMultipartTextMessage(
                            guardian.phoneNumber,
                            null,
                            parts,
                            null,
                            null
                        )
                        Log.i(TAG, "SOS SMS sent to ${guardian.name} (${guardian.phoneNumber})")
                        updatedAlerts.add(GuardianAlert(guardian, AlertStatus.SENT))
                    } else {
                        Log.w(TAG, "Cannot send SMS to ${guardian.name}: SmsManager unavailable")
                        updatedAlerts.add(GuardianAlert(guardian, AlertStatus.FAILED, errorMessage = "SmsManager unavailable"))
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "SMS Permission not granted for ${guardian.name}", e)
                    updatedAlerts.add(GuardianAlert(guardian, AlertStatus.FAILED, errorMessage = "SMS Permission denied"))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send SMS to ${guardian.name}", e)
                    updatedAlerts.add(GuardianAlert(guardian, AlertStatus.FAILED, errorMessage = e.localizedMessage))
                }
            }

            _alerts.value = updatedAlerts
        }
    }

    /**
     * Initiates 1-tap phone call to the primary guardian.
     */
    fun callPrimaryGuardian(): Intent? {
        val primary = guardianRepository.getPrimaryGuardian() ?: return null
        return Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:${primary.phoneNumber}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Creates an Intent to share emergency live location directly to Primary Guardian on WhatsApp.
     */
    fun createWhatsAppShareIntent(location: SafenexLocation?, guardian: Guardian? = null): Intent {
        val targetGuardian = guardian ?: guardianRepository.getPrimaryGuardian()
        val locText = if (location != null) {
            val addr = location.address?.let { " ($it)" } ?: ""
            "Location: ${location.formattedCoordinates}$addr\nLive Google Map: ${location.mapsUrl}"
        } else {
            "Location: Fetching satellite GPS coordinates..."
        }

        val message = "🚨 *SAFENEX EMERGENCY ALERT!*\n" +
                "I am in an emergency and need immediate help!\n\n" +
                "📍 *My Live GPS Location:*\n$locText\n\n" +
                "⚡ Sent automatically by SAFENEX Emergency System."

        val cleanPhone = targetGuardian?.phoneNumber
            ?.replace("+", "")
            ?.replace(" ", "")
            ?.replace("-", "")
            ?.replace("(", "")
            ?.replace(")", "") ?: ""

        val url = if (cleanPhone.isNotBlank()) {
            "https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(message)}"
        } else {
            "https://api.whatsapp.com/send?text=${Uri.encode(message)}"
        }

        return Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Dispatches Safe Arrival SMS notification to Primary Guardian.
     */
    @SuppressLint("MissingPermission")
    fun dispatchArrivalConfirmation(destination: String, timeStr: String) {
        val primary = guardianRepository.getPrimaryGuardian()
        if (primary == null || primary.phoneNumber.isBlank()) {
            Log.w(TAG, "No primary guardian to send arrival confirmation to.")
            return
        }

        val message = "✅ SAFENEX UPDATE:\n" +
                "I have safely arrived at $destination on schedule at $timeStr.\n" +
                "All is well! 🛡️"

        scope.launch {
            try {
                val smsManager: SmsManager? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                smsManager?.sendTextMessage(primary.phoneNumber, null, message, null, null)
                Log.i(TAG, "Safe arrival SMS dispatched to ${primary.name} (${primary.phoneNumber})")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send arrival SMS: ${e.message}")
            }
        }
    }

    /**
     * Resets active alert statuses when disarming.
     */
    fun resetAlerts() {
        _alerts.value = emptyList()
    }
}
