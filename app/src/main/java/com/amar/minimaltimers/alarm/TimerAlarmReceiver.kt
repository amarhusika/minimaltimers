package com.amar.minimaltimers.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.amar.minimaltimers.timer.TimerAlerts
import com.amar.minimaltimers.timer.TimerEngine

/** Fired by AlarmManager at the end of a step. */
class TimerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_TIMER_STEP) return

        // Keep the broadcast (and the alarm's wake lock) alive until the beep has played,
        // otherwise the phone can fall asleep / the process can be frozen mid-sound.
        val pending = goAsync()
        var handedOff = false
        try {
            // An alarm that fires slightly early (RTC adjustments) is accepted; one that fires
            // clearly early is re-armed instead of being dropped (old code dropped it and the
            // timer stayed stuck forever with the screen locked).
            val alert = TimerEngine.reconcile(context, EARLY_TOLERANCE_MS)
            if (alert != null) {
                handedOff = true
                TimerAlerts.deliver(context, alert) { pending.finish() }
            }
        } catch (e: Exception) {
            Log.e("TimerAlarmReceiver", "step handling failed", e)
        } finally {
            if (!handedOff) pending.finish()
        }
    }

    companion object {
        const val ACTION_TIMER_STEP = "com.amar.minimaltimers.ACTION_TIMER_STEP"
        private const val EARLY_TOLERANCE_MS = 500L
    }
}
