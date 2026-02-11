package com.amar.minimaltimers.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.amar.minimaltimers.MainActivity
import com.amar.minimaltimers.data.RunStateStore
import com.amar.minimaltimers.data.Storage
import com.amar.minimaltimers.util.secToHms
import kotlinx.coroutines.*

class TimerForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var updateJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                startUpdating()
            }
            // Notification actions
            ACTION_PAUSE  -> sendBroadcast(Intent(ACTION_TIMER_PAUSE))
            ACTION_RESUME -> sendBroadcast(Intent(ACTION_TIMER_RESUME))
            ACTION_SKIP   -> sendBroadcast(Intent(ACTION_TIMER_SKIP))
            ACTION_STOP_TIMER -> {
                // Stop from the notification should RESET the timer inside the app (same as "Restart/Reset" button)
                sendBroadcast(Intent(ACTION_TIMER_STOP))
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            // Internal: remove notification (used when app navigates away / fully stops)
            ACTION_STOP_SERVICE -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startUpdating() {
        updateJob?.cancel()
        updateJob = serviceScope.launch {
            while (isActive) {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, buildNotification())
                delay(500) // update every 500ms for smoother countdown
            }
        }
    }

    private fun buildNotification(): Notification {
        val store  = RunStateStore(applicationContext)
        val storage = Storage(applicationContext)
        val state  = store.load()

        // Defaults
        var planTitle    = "Minimal Timers"
        var activityName = "Timer running"
        var secLeft      = 0
        var totalSec     = 1
        var paused       = false
        var stepNum      = 1
        var totalSteps   = 1
        var endElapsed   = SystemClock.elapsedRealtime()

        state?.let { s ->
            val plans = storage.loadAll()
            val plan  = plans.firstOrNull { it.id == s.planId }
            plan?.let { p ->
                planTitle    = p.title.ifBlank { "Minimal Timers" }
                totalSteps   = p.activities.size
                stepNum      = s.stepIdx + 1
                if (s.stepIdx < p.activities.size) {
                    val act  = p.activities[s.stepIdx]
                    activityName = act.title
                    totalSec = act.durationSec.coerceAtLeast(1)
                    paused = s.isPaused
                    if (paused) {
                        secLeft = s.pausedRemainingSec.coerceIn(0, totalSec)
                        endElapsed = SystemClock.elapsedRealtime() + (secLeft * 1000L)
                    } else {
                        val msLeft = s.stepEndElapsed - SystemClock.elapsedRealtime()
                        secLeft  = ((msLeft + 999L) / 1000L).coerceAtLeast(0L).toInt()
                        endElapsed = s.stepEndElapsed
                    }
                }
            }
        }

        val elapsed = totalSec - secLeft
        val progress = elapsed.coerceIn(0, totalSec)

        // Open app intent
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Pause action
        val pauseOrResumePI = PendingIntent.getService(
            this, 1,
            Intent(this, TimerForegroundService::class.java).apply {
                action = if (paused) ACTION_RESUME else ACTION_PAUSE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Stop action
        val stopPI = PendingIntent.getService(
            this, 2,
            Intent(this, TimerForegroundService::class.java).apply { action = ACTION_STOP_TIMER },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Convert endElapsed to wall-clock time for chronometer
        val nowRealtime = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()
        val endWall = nowWall + (endElapsed - nowRealtime)

        val pauseIcon = if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause
        val pauseLabel = if (paused) "Resume" else "Pause"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            // Title = plan name, Text = activity name + step
            .setContentTitle(planTitle)
            .setContentText(
                if (paused) "$activityName  (Paused • ${secToHms(secLeft)})  ($stepNum/$totalSteps)"
                else "$activityName  ($stepNum/$totalSteps)"
            )
            // Big timer shown as countdown chronometer
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // Progress bar
            .setProgress(totalSec, progress, false)
            // Actions - Pause/Resume and Stop
            .addAction(pauseIcon, pauseLabel, pauseOrResumePI)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPI)

        // Countdown timer in notification (only when running)
        if (!paused) {
            builder
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(endWall)
                .setShowWhen(true)
        } else {
            builder
                .setUsesChronometer(false)
                .setShowWhen(false)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "Timer", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Timer progress"
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null) // no sound on every update
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        updateJob?.cancel()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID      = "timer_channel"

        const val ACTION_START       = "com.amar.minimaltimers.service.START"
        const val ACTION_STOP_SERVICE = "com.amar.minimaltimers.service.STOP_SERVICE"
        const val ACTION_PAUSE       = "com.amar.minimaltimers.service.PAUSE"
        const val ACTION_RESUME      = "com.amar.minimaltimers.service.RESUME"
        const val ACTION_SKIP        = "com.amar.minimaltimers.service.SKIP"
        const val ACTION_STOP_TIMER  = "com.amar.minimaltimers.service.STOP_TIMER"
        const val ACTION_TIMER_PAUSE = "com.amar.minimaltimers.TIMER_PAUSE"
        const val ACTION_TIMER_RESUME= "com.amar.minimaltimers.TIMER_RESUME"
        const val ACTION_TIMER_SKIP  = "com.amar.minimaltimers.TIMER_SKIP"
        const val ACTION_TIMER_STOP  = "com.amar.minimaltimers.TIMER_STOP"

        fun start(context: Context) {
            val i = Intent(context, TimerForegroundService::class.java).apply { action = ACTION_START }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(i)
            else
                context.startService(i)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, TimerForegroundService::class.java).apply { action = ACTION_STOP_SERVICE }
            )
        }
    }
}
