package com.safenex.app.data.schedule

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Data representation of a user-configured Commute Safety Schedule.
 */
data class SafetySchedule(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Commute Safety",
    val destination: String = "Home",
    val startTimeEpochMs: Long = System.currentTimeMillis(),
    val expectedArrivalEpochMs: Long = System.currentTimeMillis() + (60 * 60 * 1000L), // 1 hr default
    val extendMinutesDefault: Int = 15,
    val isActive: Boolean = true,
    val notifyGuardianOnArrival: Boolean = true
) {
    val formattedArrivalTime: String
        get() {
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            return sdf.format(Date(expectedArrivalEpochMs))
        }

    val formattedStartTime: String
        get() {
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            return sdf.format(Date(startTimeEpochMs))
        }

    fun getRemainingTimeMs(): Long {
        return (expectedArrivalEpochMs - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    fun formatRemainingTime(): String {
        val remaining = getRemainingTimeMs()
        val totalSecs = remaining / 1000
        val hours = totalSecs / 3600
        val mins = (totalSecs % 3600) / 60
        val secs = totalSecs % 60
        return when {
            hours > 0 -> String.format("%dh %02dm", hours, mins)
            mins > 0 -> String.format("%dm %02ds", mins, secs)
            else -> String.format("%02ds", secs)
        }
    }
}

/**
 * Live lifecycle state of the Scheduled Safety Mode.
 */
sealed class ScheduleStatus {
    object Idle : ScheduleStatus()
    data class Active(val schedule: SafetySchedule, val remainingMs: Long) : ScheduleStatus()
    data class CheckInPrompt(val schedule: SafetySchedule, val secondsRemaining: Int) : ScheduleStatus()
    data class Completed(val schedule: SafetySchedule) : ScheduleStatus()
}
