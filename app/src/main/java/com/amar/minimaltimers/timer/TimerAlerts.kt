package com.amar.minimaltimers.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.amar.minimaltimers.MainActivity

/** Sound + "step completed" notification in the notification bar (no full-screen, no vibration). */
object TimerAlerts {

    // New id: channel settings can't be changed after creation and the old channel
    // played the default notification sound on top of our beep (double sound).
    private const val CHANNEL_ID = "timer_alerts_v3"
    private val OLD_CHANNELS = listOf("timer_alarm_channel", "timer_alerts_v2")
    const val NOTIFICATION_ID = 2001

    /** Plays the beep and, when the app is not on screen, posts the alert. [onDone] runs once the beep ends. */
    fun deliver(ctx: Context, alert: TimerEngine.Alert, onDone: () -> Unit = {}) {
        val app = ctx.applicationContext
        if (!TimerEngine.uiVisible) {
            try { postNotification(app, alert) } catch (e: Exception) { Log.w("TimerAlerts", "notify", e) }
        }
        AlertPlayer.play(app, onDone)
    }

    fun cancel(ctx: Context) {
        try {
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}
    }

    private fun postNotification(ctx: Context, alert: TimerEngine.Alert) {
        ensureChannel(ctx)

        val contentPI = PendingIntent.getActivity(
            ctx, 10,
            Intent(ctx, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (alert.finished) "✅ ${alert.planTitle.ifBlank { "Timer" }} finished"
                    else "✅ ${alert.completedTitle} completed"
        val text = when {
            alert.nextTitle != null -> "Next: ${alert.nextTitle}"
            alert.finished -> "Last activity: ${alert.completedTitle}"
            else -> alert.planTitle
        }

        val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(contentPI)
            .setSilent(true)              // beep is played by the app; no vibration
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (!alert.finished) builder.setTimeoutAfter(60_000L) // intermediate steps don't pile up

        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, builder.build())
    }

    private fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        OLD_CHANNELS.forEach { try { nm.deleteNotificationChannel(it) } catch (_: Exception) {} }
        val ch = NotificationChannel(CHANNEL_ID, "Timer alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Alert when an activity / timer is completed"
            setSound(null, null)          // the beep is played by the app on the alarm stream
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(ch)
    }
}
