package com.safenex.app.data.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Handles incoming actions from the Schedule Check-In Notification & Alarms.
 */
class ScheduleBroadcastReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "SAFENEX_ScheduleRecv"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(TAG, "Received schedule broadcast action: $action")

        val manager = SafenexScheduleManager.instance
        if (manager == null) {
            Log.w(TAG, "SafenexScheduleManager instance is null.")
            return
        }

        when (action) {
            SafenexScheduleManager.ACTION_SCHEDULE_CHECK_IN -> {
                val current = manager.scheduleStatus.value
                if (current is ScheduleStatus.Active) {
                    manager.triggerCheckInPrompt(current.schedule)
                }
            }
            SafenexScheduleManager.ACTION_ARRIVE_SAFELY -> {
                manager.confirmSafeArrival()
            }
            SafenexScheduleManager.ACTION_EXTEND_TIME -> {
                manager.extendSchedule(15)
            }
            SafenexScheduleManager.ACTION_TRIGGER_EMERGENCY,
            SafenexScheduleManager.ACTION_TIMEOUT_EMERGENCY -> {
                manager.triggerEmergencyNow()
            }
        }
    }
}
