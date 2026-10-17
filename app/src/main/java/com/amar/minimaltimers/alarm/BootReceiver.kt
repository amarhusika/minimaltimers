package com.amar.minimaltimers.alarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.amar.minimaltimers.timer.TimerAlerts
import com.amar.minimaltimers.timer.TimerEngine

/**
 * Re-arms the timer after events that wipe or shift alarms:
 * reboot, app update, manual clock change, exact-alarm permission granted.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> Unit
            else -> return
        }

        val pending = goAsync()
        var handedOff = false
        try {
            if (TimerEngine.current(context) != null) {
                // After reboot the engine rebuilds the step end from wall-clock time.
                val alert = TimerEngine.reconcile(context)
                TimerEngine.ensureNotification(context)
                if (alert != null) {
                    handedOff = true
                    TimerAlerts.deliver(context, alert) { pending.finish() }
                }
            }
        } catch (e: Exception) {
            Log.e("BootReceiver", "reschedule failed", e)
        } finally {
            if (!handedOff) pending.finish()
        }
    }
}
