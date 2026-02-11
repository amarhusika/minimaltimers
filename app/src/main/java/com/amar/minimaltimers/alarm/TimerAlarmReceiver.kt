package com.amar.minimaltimers.alarm

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.amar.minimaltimers.MainActivity
import com.amar.minimaltimers.R
import com.amar.minimaltimers.data.RunState
import com.amar.minimaltimers.data.RunStateStore
import com.amar.minimaltimers.data.Storage
import com.amar.minimaltimers.service.TimerForegroundService
import com.amar.minimaltimers.ui.LockScreenTimerActivity

class TimerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_TIMER_STEP) return

        val planId = intent.getStringExtra(EXTRA_PLAN_ID) ?: return
        val stepIdx = intent.getIntExtra(EXTRA_STEP_IDX, -1)
        val stepEnd = intent.getLongExtra(EXTRA_STEP_END, -1L)
        if (stepIdx < 0 || stepEnd <= 0L) return

        val store = RunStateStore(context)
        val current = store.load() ?: return

        if (current.planId != planId || current.stepIdx != stepIdx || current.stepEndElapsed != stepEnd) return

        val now = SystemClock.elapsedRealtime()
        if (now + 200 < current.stepEndElapsed) return

        // UVIJEK pusti zvuk - i za zadnju activity!
        playCompletionSound(context)

        // Determine if there's a next step BEFORE advancing
        val plans = Storage(context).loadAll()
        val plan = plans.firstOrNull { it.id == current.planId }
        val isLastActivity = plan == null || (current.stepIdx + 1) >= (plan.activities.size)

        // Show lock screen
        showLockScreenActivity(context)

        // Show alarm notification
        showAlarmNotification(context, plan?.title ?: "", 
            plan?.activities?.getOrNull(current.stepIdx)?.title ?: "Timer")

        // Advance or finish
        advanceTimer(context, store, current)

        // If last activity, stop the foreground service
        if (isLastActivity) {
            TimerForegroundService.stop(context)
        }
    }

    private fun playCompletionSound(context: Context) {
        try {
            val mp = MediaPlayer.create(context, R.raw.timer_done)
            mp?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp?.start()
            mp?.setOnCompletionListener { it.release() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showLockScreenActivity(context: Context) {
        try {
            val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            val isLocked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                keyguardManager.isKeyguardLocked
            } else true

            if (isLocked) {
                val lockScreenIntent = Intent(context, LockScreenTimerActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                }
                context.startActivity(lockScreenIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showAlarmNotification(context: Context, planTitle: String, activityTitle: String) {
        createNotificationChannel(context)

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val fullScreenIntent = Intent(context, LockScreenTimerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context, 1, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("✅ $activityTitle completed!")
            .setContentText(if (planTitle.isNotEmpty()) planTitle else "Timer done")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Timer Alarms", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Timer completion alerts"
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            (context.getSystemService(NotificationManager::class.java))
                .createNotificationChannel(channel)
        }
    }

    private fun advanceTimer(context: Context, store: RunStateStore, current: RunState) {
        try {
            val plans = Storage(context).loadAll()
            val plan = plans.firstOrNull { it.id == current.planId }
            if (plan == null || plan.activities.isEmpty()) {
                store.clear(); AlarmScheduler.cancel(context); return
            }
            val nextIdx = current.stepIdx + 1
            if (nextIdx >= plan.activities.size) {
                store.clear(); AlarmScheduler.cancel(context); return
            }
            val nextDurSec = plan.activities[nextIdx].durationSec
            val nextEnd = SystemClock.elapsedRealtime() + (nextDurSec * 1000L)
            val nextState = RunState(planId = current.planId, stepIdx = nextIdx, stepEndElapsed = nextEnd)
            store.save(nextState)
            AlarmScheduler.schedule(context, nextState)
        } catch (e: Exception) {
            e.printStackTrace()
            store.clear(); AlarmScheduler.cancel(context)
        }
    }

    companion object {
        const val ACTION_TIMER_STEP = "com.amar.minimaltimers.ACTION_TIMER_STEP"
        const val EXTRA_PLAN_ID = "plan_id"
        const val EXTRA_STEP_IDX = "step_idx"
        const val EXTRA_STEP_END = "step_end_elapsed"
        private const val CHANNEL_ID = "timer_alarm_channel"
        private const val NOTIFICATION_ID = 2001
    }
}
