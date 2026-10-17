package com.amar.minimaltimers.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.amar.minimaltimers.MainActivity
import com.amar.minimaltimers.data.RunState
import com.amar.minimaltimers.data.Storage
import com.amar.minimaltimers.data.TimerPlan
import com.amar.minimaltimers.timer.TimerEngine
import com.amar.minimaltimers.util.secToHms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps the app process alive (not frozen) while a timer is active and shows the ongoing
 * notification. It does NOT tick: the countdown is drawn by the system chronometer and the
 * notification is rebuilt only when the timer state changes (plus a slow progress-bar refresh
 * while the screen is on). Timing itself is done by AlarmManager.
 */
class TimerForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null
    private var progressJob: Job? = null
    private var lastStartId = 0
    private var cachedPlan: TimerPlan? = null

    override fun onCreate() {
        super.onCreate()
        alive = true
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId

        // Satisfy the startForegroundService() contract right away, whatever the action is.
        goForeground(buildNotification(TimerEngine.current(this)))

        when (intent?.action) {
            ACTION_PAUSE      -> TimerEngine.pause(this)
            ACTION_RESUME     -> TimerEngine.resume(this)
            ACTION_STOP_TIMER -> TimerEngine.stop(this) // UI resets to step 1 (same as Restart)
        }

        if (TimerEngine.current(this) == null) {
            stopNow()
            return START_NOT_STICKY
        }
        stopping = false
        observe()
        return START_STICKY
    }

    private fun goForeground(n: Notification) {
        try {
            startForeground(NOTIFICATION_ID, n)
        } catch (e: Exception) {
            // e.g. ForegroundServiceStartNotAllowedException after a sticky restart in background.
            Log.w(TAG, "startForeground failed", e)
        }
    }

    private fun observe() {
        if (observeJob?.isActive != true) {
            observeJob = scope.launch {
                TimerEngine.snapshot.collect { snap ->
                    val s = snap.state
                    if (s == null) stopNow() else notifySafe(buildNotification(s))
                }
            }
        }
        if (progressJob?.isActive != true) {
            progressJob = scope.launch {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                while (isActive) {
                    delay(PROGRESS_REFRESH_MS)
                    val s = TimerEngine.snapshot.value.state ?: continue
                    // Only the progress bar needs this; skip it entirely while the screen is off.
                    if (!s.isPaused && pm.isInteractive) notifySafe(buildNotification(s))
                }
            }
        }
    }

    private fun notifySafe(n: Notification) {
        try {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, n)
        } catch (e: Exception) { Log.w(TAG, "notify failed", e) }
    }

    private fun stopNow() {
        stopping = true
        observeJob?.cancel(); observeJob = null
        progressJob?.cancel(); progressJob = null
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
        // Only stops if no newer start request arrived in the meantime.
        stopSelf(lastStartId)
    }

    private fun planFor(planId: String): TimerPlan? {
        cachedPlan?.let { if (it.id == planId) return it }
        val p = Storage(applicationContext).loadAll().firstOrNull { it.id == planId }
        cachedPlan = p
        return p
    }

    private fun buildNotification(state: RunState?): Notification {
        var planTitle    = "Minimal Timers"
        var activityName = "Timer"
        var leftMs       = 0L
        var totalMs      = 1000L
        var paused       = false
        var stepNum      = 1
        var totalSteps   = 1

        val now = SystemClock.elapsedRealtime()
        state?.let { s ->
            planFor(s.planId)?.let { p ->
                planTitle  = p.title.ifBlank { "Minimal Timers" }
                totalSteps = p.activities.size.coerceAtLeast(1)
                stepNum    = s.stepIdx + 1
                p.activities.getOrNull(s.stepIdx)?.let { act ->
                    activityName = act.title
                    totalMs = (act.durationSec * 1000L).coerceAtLeast(1000L)
                }
            }
            paused = s.isPaused
            leftMs = if (paused) s.pausedMs() else (s.stepEndElapsed - now)
            leftMs = leftMs.coerceIn(0L, totalMs)
        }

        val totalSec = (totalMs / 1000L).toInt()
        val elapsedSec = ((totalMs - leftMs) / 1000L).toInt().coerceIn(0, totalSec)
        val leftSec = ((leftMs + 999L) / 1000L).toInt()

        val openPI = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pauseOrResumePI = PendingIntent.getService(
            this, 1,
            Intent(this, TimerForegroundService::class.java).setAction(if (paused) ACTION_RESUME else ACTION_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPI = PendingIntent.getService(
            this, 2,
            Intent(this, TimerForegroundService::class.java).setAction(ACTION_STOP_TIMER),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(planTitle)
            .setContentText(
                if (paused) "$activityName  (Paused • ${secToHms(leftSec)})  ($stepNum/$totalSteps)"
                else "$activityName  ($stepNum/$totalSteps)"
            )
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(openPI)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setProgress(totalSec, elapsedSec, false)
            .addAction(
                if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (paused) "Resume" else "Pause",
                pauseOrResumePI
            )
            .addAction(android.R.drawable.ic_delete, "Stop", stopPI)

        if (!paused && state != null) {
            // System-drawn countdown: updates every second without waking the app.
            val endWall = System.currentTimeMillis() + (state.stepEndElapsed - now)
            builder.setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(endWall)
                .setShowWhen(true)
        } else {
            builder.setUsesChronometer(false).setShowWhen(false)
        }
        return builder.build()
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        // Old channel was IMPORTANCE_HIGH (heads-up). Channel importance can't be changed -> new id.
        try { nm.deleteNotificationChannel(OLD_CHANNEL_ID) } catch (_: Exception) {}
        val ch = NotificationChannel(CHANNEL_ID, "Timer", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Running timer"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannel(ch)
    }

    override fun onDestroy() {
        alive = false
        stopping = false
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "TimerFgService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID      = "timer_progress_v2"
        private const val OLD_CHANNEL_ID  = "timer_channel"
        private const val PROGRESS_REFRESH_MS = 15_000L

        const val ACTION_START      = "com.amar.minimaltimers.service.START"
        const val ACTION_PAUSE      = "com.amar.minimaltimers.service.PAUSE"
        const val ACTION_RESUME     = "com.amar.minimaltimers.service.RESUME"
        const val ACTION_STOP_TIMER = "com.amar.minimaltimers.service.STOP_TIMER"

        @Volatile private var alive = false
        @Volatile private var stopping = false

        /** Starts the service if it isn't running. Safe to call often; never throws. */
        fun ensureRunning(context: Context) {
            if (alive && !stopping) return // already running; it observes TimerEngine itself
            val i = Intent(context, TimerForegroundService::class.java).setAction(ACTION_START)
            try {
                ContextCompat.startForegroundService(context, i)
            } catch (e: Exception) {
                Log.w(TAG, "could not start service", e)
            }
        }
    }
}
