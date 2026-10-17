package com.amar.minimaltimers.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.amar.minimaltimers.MainActivity
import com.amar.minimaltimers.data.RunState

/**
 * One exact alarm for the end of the current step.
 * setAlarmClock() is the only alarm type that is never deferred by Doze / App Standby
 * and has no "once per 9 minutes" limit, so it fires on time with the screen locked.
 */
object AlarmScheduler {

    private const val TAG = "AlarmScheduler"
    private const val REQ_CODE = 1001
    private const val REQ_SHOW = 1002

    private fun operation(ctx: Context, extraFlags: Int): PendingIntent? {
        val intent = Intent(ctx, TimerAlarmReceiver::class.java).setAction(TimerAlarmReceiver.ACTION_TIMER_STEP)
        return PendingIntent.getBroadcast(ctx, REQ_CODE, intent, extraFlags or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Opened when the user taps the alarm icon / "next alarm" in the status bar. */
    private fun showIntent(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, REQ_SHOW,
        Intent(ctx, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        ),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun canScheduleExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    fun schedule(ctx: Context, state: RunState) {
        if (state.isPaused) { cancel(ctx); return }
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = operation(ctx, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val triggerElapsed = state.stepEndElapsed

        try {
            if (canScheduleExact(ctx)) {
                val triggerWall = System.currentTimeMillis() + (triggerElapsed - SystemClock.elapsedRealtime())
                am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerWall, showIntent(ctx)), pi)
                return
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "exact alarm not allowed", e)
        }
        // No exact-alarm permission: best effort (may be late in Doze). The UI tells the user.
        am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerElapsed, pi)
    }

    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = operation(ctx, PendingIntent.FLAG_NO_CREATE)
        if (pi != null) { am.cancel(pi); pi.cancel() }
    }
}
