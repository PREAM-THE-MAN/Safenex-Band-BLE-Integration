package com.safenex.app.domain.model

/**
 * Represents the trigger source detected from the SAFENEX band.
 */
enum class TriggerSource(val displayName: String) {
    BUTTON("Button"),
    SHAKE("Vigorous Shake"),
    VOICE("Voice (DITTU)"),
    SCHEDULE_OVERDUE("Scheduled Arrival Overdue"),
    UNKNOWN("Unknown Alert")
}

/**
 * Domain representation of an emergency event emitted by the hardware band.
 */
data class EmergencyEvent(
    val source: TriggerSource,
    val timestamp: Long = System.currentTimeMillis(),
    val rawPayload: String = ""
)
