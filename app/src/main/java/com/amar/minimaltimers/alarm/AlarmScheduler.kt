package com.amar.minimaltimers.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.amar.minimaltimers.data.RunState

object AlarmScheduler {

    private const val REQ_CODE = 1001

    private fun pendingIntent(context: Context, state: RunState): PendingIntent {
        val intent = Intent(context, TimerAlarmReceiver::class.java).apply {
            action = TimerAlarmReceiver.ACTION_TIMER_STEP
            putExtra(TimerAlarmReceiver.EXTRA_PLAN_ID,   state.planId)
            putExtra(TimerAlarmReceiver.EXTRA_STEP_IDX,  state.stepIdx)
            putExtra(TimerAlarmReceiver.EXTRA_STEP_END,  state.stepEndElapsed)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(context, REQ_CODE, intent, flags)
    }

    fun schedule(context: Context, state: RunState) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, state)
        val triggerAt = state.stepEndElapsed

        when {
            // Android 12+ - use setAlarmClock for MAXIMUM precision (never delayed by Doze)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (am.canScheduleExactAlarms()) {
                    val alarmInfo = AlarmManager.AlarmClockInfo(
                        System.currentTimeMillis() + (triggerAt - android.os.SystemClock.elapsedRealtime()),
                        pi
                    )
                    am.setAlarmClock(alarmInfo, pi)
                } else {
                    // Fallback if no exact alarm permission
                    am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
                }
            }
            // Android 6-11
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            }
            // Android 5
            else -> {
                am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            }
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, TimerAlarmReceiver::class.java).apply {
            action = TimerAlarmReceiver.ACTION_TIMER_STEP
        }
        val flags = PendingIntent.FLAG_NO_CREATE or
                if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        val pi = PendingIntent.getBroadcast(context, REQ_CODE, intent, flags)
        if (pi != null) { am.cancel(pi); pi.cancel() }
    }
}
