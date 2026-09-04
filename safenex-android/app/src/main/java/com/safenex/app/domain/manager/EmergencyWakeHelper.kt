package com.safenex.app.domain.manager

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.safenex.app.MainActivity

/**
 * Ensures the device wakes up, turns on the screen, bypasses lockscreen,
 * and brings SAFENEX EmergencyScreen to the foreground the moment SOS is triggered.
 */
class EmergencyWakeHelper(private val context: Context) {

    companion object {
        private const val TAG = "SAFENEX_WakeHelper"
        private const val CHANNEL_ID = "safenex_emergency_channel"
        private const val NOTIFICATION_ID = 9999
        private const val WAKE_LOCK_TIMEOUT_MS = 60_000L // 1 minute
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    init {
        createEmergencyNotificationChannel()
    }

    private fun createEmergencyNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SAFENEX Emergency Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority emergency alerts that wake the screen"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                setBypassDnd(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    /**
     * Wakes up the screen and brings the MainActivity EmergencyScreen to the foreground.
     */
    @SuppressLint("WakelockTimeout")
    fun wakeAndLaunchEmergency() {
        Log.i(TAG, "Initiating Emergency Screen Wakeup and Auto-Launch...")

        // 1. Acquire WakeLock to turn on the screen
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager != null) {
                wakeLock?.release()
                @Suppress("DEPRECATION")
                val flags = PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE

                val lock = powerManager.newWakeLock(flags, "safenex:EmergencyWakeLock")
                lock.acquire(WAKE_LOCK_TIMEOUT_MS)
                wakeLock = lock
                Log.d(TAG, "Screen WakeLock acquired successfully.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring WakeLock: ${e.message}", e)
        }

        // 2. Build Intent to bring MainActivity to top
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_EMERGENCY_TRIGGERED", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. Post Full-Screen Intent Notification (ensures Android 10+ launches over lockscreen)
        try {
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("🚨 SAFENEX EMERGENCY ACTIVE")
                .setContentText("SAFENEX-BAND activated! Tap to view emergency controls.")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(pendingIntent, true)
                .setContentIntent(pendingIntent)
                .setAutoCancel(false)
                .setOngoing(true)
                .build()

            notificationManager?.notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "Full-screen emergency notification posted.")
        } catch (e: Exception) {
            Log.e(TAG, "Error posting full-screen notification: ${e.message}", e)
        }

        // 4. Directly launch Activity
        try {
            context.startActivity(launchIntent)
            Log.i(TAG, "Direct startActivity called for MainActivity.")
        } catch (e: Exception) {
            Log.e(TAG, "Error launching MainActivity: ${e.message}", e)
        }
    }

    /**
     * Cleans up wake lock and dismisses the full-screen notification when safe.
     */
    fun dismissEmergency() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
            wakeLock = null
            notificationManager?.cancel(NOTIFICATION_ID)
            Log.d(TAG, "Emergency WakeLock released and notification cancelled.")
        } catch (e: Exception) {
            Log.w(TAG, "Error dismissing emergency wake lock: ${e.message}")
        }
    }
}
