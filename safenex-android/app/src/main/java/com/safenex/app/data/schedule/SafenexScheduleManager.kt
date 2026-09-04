package com.safenex.app.data.schedule

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.safenex.app.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Manages Scheduled Safety Commute mode:
 * - Configures arrival timers.
 * - Triggers 60-second urgent check-in prompt with sound & notification.
 * - 3 interactive responses: Arrived Safely (sends SMS to guardian), Extend Time, or Emergency.
 * - Automatically escalates to full SAFENEX Emergency if not answered within 60 seconds.
 */
class SafenexScheduleManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    companion object {
        private const val TAG = "SAFENEX_ScheduleMgr"
        private const val PREFS_NAME = "safenex_schedule_prefs"
        private const val KEY_ACTIVE_SCHEDULE = "key_active_schedule"
        private const val NOTIFICATION_CHANNEL_ID = "safenex_schedule_checkin_channel"
        private const val NOTIFICATION_ID = 8888
        private const val CHECK_IN_TIMEOUT_SECONDS = 60

        const val ACTION_SCHEDULE_CHECK_IN = "com.safenex.app.ACTION_SCHEDULE_CHECK_IN"
        const val ACTION_ARRIVE_SAFELY = "com.safenex.app.ACTION_ARRIVE_SAFELY"
        const val ACTION_EXTEND_TIME = "com.safenex.app.ACTION_EXTEND_TIME"
        const val ACTION_TRIGGER_EMERGENCY = "com.safenex.app.ACTION_TRIGGER_EMERGENCY"
        const val ACTION_TIMEOUT_EMERGENCY = "com.safenex.app.ACTION_TIMEOUT_EMERGENCY"

        var instance: SafenexScheduleManager? = null
            private set
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    private val powerManager =
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val gson = Gson()

    private val _scheduleStatus = MutableStateFlow<ScheduleStatus>(ScheduleStatus.Idle)
    val scheduleStatus: StateFlow<ScheduleStatus> = _scheduleStatus.asStateFlow()

    private var countdownJob: Job? = null
    private var tickerJob: Job? = null

    var onEmergencyTriggerRequested: (() -> Unit)? = null
    var onSafeArrivalConfirmed: ((SafetySchedule) -> Unit)? = null

    init {
        instance = this
        createNotificationChannel()
        restoreSavedSchedule()
        startLiveTicker()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "SAFENEX Commute Safety Check-In",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent 60-second check-in alarm when commute arrival time is exceeded"
                setSound(soundUri, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 800)
                setBypassDnd(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun restoreSavedSchedule() {
        val json = prefs.getString(KEY_ACTIVE_SCHEDULE, null)
        if (!json.isNullOrBlank()) {
            try {
                val schedule = gson.fromJson(json, SafetySchedule::class.java)
                if (schedule != null && schedule.isActive) {
                    val remaining = schedule.getRemainingTimeMs()
                    if (remaining > 0) {
                        _scheduleStatus.value = ScheduleStatus.Active(schedule, remaining)
                        scheduleAlarm(schedule)
                    } else {
                        // Already overdue when app opened
                        triggerCheckInPrompt(schedule)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring schedule: ${e.message}")
            }
        }
    }

    private fun startLiveTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                val current = _scheduleStatus.value
                if (current is ScheduleStatus.Active) {
                    val remaining = current.schedule.getRemainingTimeMs()
                    if (remaining <= 0) {
                        triggerCheckInPrompt(current.schedule)
                    } else {
                        _scheduleStatus.value = ScheduleStatus.Active(current.schedule, remaining)
                    }
                }
                delay(1000L)
            }
        }
    }

    /**
     * Starts a new Safety Schedule (e.g., 8:00 AM to 8:00 PM @ Home).
     */
    fun startSchedule(
        title: String,
        destination: String,
        expectedArrivalEpochMs: Long,
        extendMinutesDefault: Int = 15,
        notifyGuardianOnArrival: Boolean = true
    ): SafetySchedule {
        val schedule = SafetySchedule(
            title = title.ifBlank { "Commute Safety" },
            destination = destination.ifBlank { "Home" },
            startTimeEpochMs = System.currentTimeMillis(),
            expectedArrivalEpochMs = expectedArrivalEpochMs,
            extendMinutesDefault = extendMinutesDefault,
            isActive = true,
            notifyGuardianOnArrival = notifyGuardianOnArrival
        )

        saveSchedule(schedule)
        scheduleAlarm(schedule)
        _scheduleStatus.value = ScheduleStatus.Active(schedule, schedule.getRemainingTimeMs())
        Log.i(TAG, "Safety Schedule started: ${schedule.title} -> ${schedule.destination} @ ${schedule.formattedArrivalTime}")
        return schedule
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleAlarm(schedule: SafetySchedule) {
        val intent = Intent(context, ScheduleBroadcastReceiver::class.java).apply {
            action = ACTION_SCHEDULE_CHECK_IN
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            schedule.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager?.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    schedule.expectedArrivalEpochMs,
                    pendingIntent
                )
            } else {
                alarmManager?.setExact(
                    AlarmManager.RTC_WAKEUP,
                    schedule.expectedArrivalEpochMs,
                    pendingIntent
                )
            }
            Log.d(TAG, "Exact alarm set for arrival: ${schedule.formattedArrivalTime}")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting exact alarm: ${e.message}")
        }
    }

    /**
     * Triggered when arrival time is exceeded: Launches 60-Second Check-In Prompt.
     */
    fun triggerCheckInPrompt(schedule: SafetySchedule) {
        Log.w(TAG, "ARRIVAL TIME EXCEEDED! Initiating 60-Second Check-In Prompt...")

        countdownJob?.cancel()
        postCheckInNotification(schedule, CHECK_IN_TIMEOUT_SECONDS)

        countdownJob = scope.launch(Dispatchers.Main) {
            var secondsLeft = CHECK_IN_TIMEOUT_SECONDS
            while (secondsLeft > 0 && isActive) {
                _scheduleStatus.value = ScheduleStatus.CheckInPrompt(schedule, secondsLeft)
                postCheckInNotification(schedule, secondsLeft)
                delay(1000L)
                secondsLeft--
            }

            if (secondsLeft <= 0 && isActive) {
                Log.e(TAG, "60-SECOND CHECK-IN TIMEOUT! Auto-activating full emergency mode!")
                dismissNotification()
                _scheduleStatus.value = ScheduleStatus.Idle
                clearSavedSchedule()
                onEmergencyTriggerRequested?.invoke()
            }
        }
    }

    /**
     * User Action 1: "I've Arrived Safely"
     */
    fun confirmSafeArrival() {
        val current = _scheduleStatus.value
        val schedule = when (current) {
            is ScheduleStatus.Active -> current.schedule
            is ScheduleStatus.CheckInPrompt -> current.schedule
            else -> null
        } ?: return

        countdownJob?.cancel()
        dismissNotification()
        clearSavedSchedule()
        _scheduleStatus.value = ScheduleStatus.Completed(schedule)

        Log.i(TAG, "Safe arrival confirmed for ${schedule.destination}!")
        onSafeArrivalConfirmed?.invoke(schedule)
    }

    /**
     * User Action 2: "Extend Time (+X Mins)"
     */
    fun extendSchedule(extraMinutes: Int = 15) {
        val current = _scheduleStatus.value
        val schedule = when (current) {
            is ScheduleStatus.Active -> current.schedule
            is ScheduleStatus.CheckInPrompt -> current.schedule
            else -> null
        } ?: return

        countdownJob?.cancel()
        dismissNotification()

        val newArrivalMs = System.currentTimeMillis() + (extraMinutes * 60 * 1000L)
        val updatedSchedule = schedule.copy(
            expectedArrivalEpochMs = newArrivalMs
        )

        saveSchedule(updatedSchedule)
        scheduleAlarm(updatedSchedule)
        _scheduleStatus.value = ScheduleStatus.Active(updatedSchedule, updatedSchedule.getRemainingTimeMs())

        Log.i(TAG, "Schedule extended by $extraMinutes mins. New arrival: ${updatedSchedule.formattedArrivalTime}")
    }

    /**
     * User Action 3: "Trigger Emergency SOS Now"
     */
    fun triggerEmergencyNow() {
        countdownJob?.cancel()
        dismissNotification()
        clearSavedSchedule()
        _scheduleStatus.value = ScheduleStatus.Idle
        onEmergencyTriggerRequested?.invoke()
    }

    /**
     * Cancels active schedule without sending alerts.
     */
    fun cancelSchedule() {
        countdownJob?.cancel()
        dismissNotification()
        clearSavedSchedule()
        _scheduleStatus.value = ScheduleStatus.Idle
        Log.i(TAG, "Schedule canceled.")
    }

    private fun postCheckInNotification(schedule: SafetySchedule, secondsLeft: Int) {
        val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra("EXTRA_SCHEDULE_CHECKIN", true)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            8001,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 1: Arrived Safely
        val arriveIntent = Intent(context, ScheduleBroadcastReceiver::class.java).apply {
            action = ACTION_ARRIVE_SAFELY
        }
        val arrivePendingIntent = PendingIntent.getBroadcast(
            context,
            8002,
            arriveIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 2: Extend +15 Mins
        val extendIntent = Intent(context, ScheduleBroadcastReceiver::class.java).apply {
            action = ACTION_EXTEND_TIME
        }
        val extendPendingIntent = PendingIntent.getBroadcast(
            context,
            8003,
            extendIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 3: Trigger SOS Now
        val sosIntent = Intent(context, ScheduleBroadcastReceiver::class.java).apply {
            action = ACTION_TRIGGER_EMERGENCY
        }
        val sosPendingIntent = PendingIntent.getBroadcast(
            context,
            8004,
            sosIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ COMMUTE SAFETY CHECK-IN (${secondsLeft}s)")
            .setContentText("Arrival time exceeded for ${schedule.destination}! Did you arrive safely?")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "You were expected at ${schedule.destination} by ${schedule.formattedArrivalTime}.\n" +
                "Auto-Emergency triggers in ${secondsLeft} seconds if not answered!"
            ))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 400, 200, 400))
            .setOngoing(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(android.R.drawable.checkbox_on_background, "✅ I've Arrived", arrivePendingIntent)
            .addAction(android.R.drawable.ic_menu_recent_history, "⏳ +15 Mins", extendPendingIntent)
            .addAction(android.R.drawable.ic_delete, "🚨 SOS NOW", sosPendingIntent)
            .build()

        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    fun dismissNotification() {
        notificationManager?.cancel(NOTIFICATION_ID)
    }

    private fun saveSchedule(schedule: SafetySchedule) {
        val json = gson.toJson(schedule)
        prefs.edit().putString(KEY_ACTIVE_SCHEDULE, json).apply()
    }

    private fun clearSavedSchedule() {
        prefs.edit().remove(KEY_ACTIVE_SCHEDULE).apply()
    }
}
