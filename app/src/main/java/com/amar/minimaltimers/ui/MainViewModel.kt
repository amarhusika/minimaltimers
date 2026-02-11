package com.amar.minimaltimers.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amar.minimaltimers.R
import com.amar.minimaltimers.alarm.AlarmScheduler
import com.amar.minimaltimers.data.RunState
import com.amar.minimaltimers.data.RunStateStore
import com.amar.minimaltimers.data.Storage
import com.amar.minimaltimers.data.TimerActivity
import com.amar.minimaltimers.data.TimerPlan
import com.amar.minimaltimers.service.TimerForegroundService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

enum class Screen { LIST, EDIT, RUN }

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val storage  = Storage(app.applicationContext)
    private val runStore = RunStateStore(app.applicationContext)

    var screen         by mutableStateOf(Screen.LIST)
        private set
    var plans          by mutableStateOf(storage.loadAll())
        private set
    var selectedPlanId by mutableStateOf<String?>(null)
        private set
    var editingPlanId  by mutableStateOf<String?>(null)
        private set
    var runPlanId      by mutableStateOf<String?>(null)
        private set
    var runIdx         by mutableStateOf(0)
        private set
    var remainingSec   by mutableStateOf(0)
        private set
    private var stepEndElapsed by mutableStateOf(0L)
    var running        by mutableStateOf(false)
        private set
    var isPaused       by mutableStateOf(false)
        private set
    var hasActiveTimer by mutableStateOf(false)
        private set


    private var ticker: Job? = null
    private var player: MediaPlayer? = null

    // Broadcast receiver for notification actions (Pause/Resume, Skip, Stop)
    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                TimerForegroundService.ACTION_TIMER_PAUSE -> pause()
                TimerForegroundService.ACTION_TIMER_RESUME -> start()
                TimerForegroundService.ACTION_TIMER_SKIP  -> skipToNext()
                TimerForegroundService.ACTION_TIMER_STOP  -> stopAndReset()
            }
        }
    }

    init {
        restoreRunIfAny()
        registerNotificationReceiver()
    }

    private fun registerNotificationReceiver() {
        val filter = IntentFilter().apply {
            addAction(TimerForegroundService.ACTION_TIMER_PAUSE)
            addAction(TimerForegroundService.ACTION_TIMER_RESUME)
            addAction(TimerForegroundService.ACTION_TIMER_SKIP)
            addAction(TimerForegroundService.ACTION_TIMER_STOP)
        }
        getApplication<Application>().registerReceiver(
            notificationReceiver, filter, Context.RECEIVER_EXPORTED
        )
    }

    private fun restoreRunIfAny() {
        val state = runStore.load() ?: return
        val plan  = plans.firstOrNull { it.id == state.planId } ?: return
        if (plan.activities.isEmpty()) return

        runPlanId      = state.planId
        hasActiveTimer = true
        runIdx         = state.stepIdx.coerceIn(0, plan.activities.lastIndex)
        if (state.isPaused) {
            running        = false
            isPaused       = true
            stepEndElapsed = 0L
            remainingSec   = state.pausedRemainingSec.coerceAtLeast(0)
        } else {
            isPaused       = false
            stepEndElapsed = state.stepEndElapsed
            remainingSec   = computeRemainingSeconds(stepEndElapsed)
            running        = remainingSec > 0
        }
        screen         = Screen.RUN

        // Keep the notification visible across process restarts, whether running or paused.
        if (running) startTicker()
        TimerForegroundService.start(getApplication())
    }

    private fun computeRemainingSeconds(endElapsed: Long): Int {
        val msLeft = endElapsed - SystemClock.elapsedRealtime()
        return ((msLeft + 999L) / 1000L).coerceAtLeast(0L).toInt()
    }

    private fun newId(): String = UUID.randomUUID().toString().replace("-", "")
    private fun persist() = storage.saveAll(plans)

    // ── Navigation ────────────────────────────────────────────────────────────

    fun goList() {
        fullStopAndClear()
        screen         = Screen.LIST
        selectedPlanId = null
        editingPlanId  = null
        runPlanId      = null
    }

    fun newPlan() {
        val id    = newId()
        val draft = TimerPlan(id = id, title = "", activities = emptyList(),
            draft = true, updatedAt = System.currentTimeMillis())
        plans = listOf(draft) + plans
        persist()
        editingPlanId = id
        screen = Screen.EDIT
    }

    fun openEdit(planId: String) {
        editingPlanId = planId
        screen = Screen.EDIT
    }

    // ── Plan CRUD ─────────────────────────────────────────────────────────────

    fun savePlanTitle(planId: String, title: String): Boolean {
        val t = title.trim()
        if (t.isEmpty()) return false
        plans = plans.map {
            if (it.id == planId) it.copy(title = t, draft = false,
                updatedAt = System.currentTimeMillis()) else it
        }
        persist()
        return true
    }

    fun deletePlan(planId: String) {
        plans = plans.filterNot { it.id == planId }
        persist()
        goList()
    }

    // ── Activity CRUD ─────────────────────────────────────────────────────────

    fun addActivity(planId: String, name: String, durationSec: Int): Boolean {
        val n = name.trim()
        if (n.isEmpty() || durationSec <= 0) return false
        plans = plans.map { p ->
            if (p.id == planId) {
                val act = TimerActivity(id = newId(), title = n, durationSec = durationSec)
                p.copy(activities = p.activities + act, draft = false,
                    updatedAt = System.currentTimeMillis())
            } else p
        }
        persist()
        return true
    }

    fun editActivity(planId: String, actId: String, name: String, durationSec: Int): Boolean {
        val n = name.trim()
        if (n.isEmpty() || durationSec <= 0) return false
        plans = plans.map { p ->
            if (p.id == planId) {
                p.copy(
                    activities = p.activities.map { a ->
                        if (a.id == actId) a.copy(title = n, durationSec = durationSec) else a
                    },
                    updatedAt = System.currentTimeMillis()
                )
            } else p
        }
        persist()
        return true
    }

    fun removeActivity(planId: String, actId: String) {
        plans = plans.map { p ->
            if (p.id == planId) p.copy(
                activities = p.activities.filterNot { it.id == actId },
                updatedAt = System.currentTimeMillis()
            ) else p
        }
        persist()
    }

    // ── Timer control ─────────────────────────────────────────────────────────

    fun openRun(planId: String): Boolean {
        val plan = plans.firstOrNull { it.id == planId } ?: return false
        if (plan.activities.isEmpty()) return false

        val ctx = getApplication<Application>().applicationContext
        fullStopAndClear()

        runPlanId      = planId
        runIdx         = 0
        remainingSec   = plan.activities[0].durationSec
        stepEndElapsed = 0L
        running        = false
        isPaused       = false
        hasActiveTimer = false
        screen         = Screen.RUN
        return true
    }

    fun startPause() { if (running) pause() else start() }

    /** Full stop + reset — called from notification Stop button */
    fun stopAndReset() {
        val planId = runPlanId ?: return
        val plan   = plans.firstOrNull { it.id == planId } ?: return
        if (plan.activities.isEmpty()) return

        // Stop alarms/service + clear persisted run, then reset UI to the beginning of the plan.
        fullStopAndClear()
        runIdx         = 0
        remainingSec   = plan.activities[0].durationSec
        stepEndElapsed = 0L
        running        = false
        isPaused       = false
        hasActiveTimer = false
        screen         = Screen.RUN
    }

    private fun start() {
        val planId = runPlanId ?: return
        val plan   = plans.firstOrNull { it.id == planId } ?: return
        if (plan.activities.isEmpty()) return

        running        = true
        isPaused       = false
        hasActiveTimer = true
        stepEndElapsed = SystemClock.elapsedRealtime() + (remainingSec * 1000L)

        val state = RunState(
            planId = planId,
            stepIdx = runIdx,
            stepEndElapsed = stepEndElapsed,
            isPaused = false,
            pausedRemainingSec = 0
        )
        val ctx   = getApplication<Application>().applicationContext
        runStore.save(state)
        AlarmScheduler.schedule(ctx, state)
        TimerForegroundService.start(ctx)
        startTicker()
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (running) {
                delay(250)
                if (!running) break
                syncFromRunStateOrFallback()
            }
        }
    }

    private fun syncFromRunStateOrFallback() {
        val ctx   = getApplication<Application>().applicationContext
        val state = runStore.load()

        if (state == null) {
            running = false
            isPaused = false
            hasActiveTimer = false
            ticker?.cancel()
            ticker = null
            TimerForegroundService.stop(ctx)
            screen    = Screen.LIST
            runPlanId = null
            return
        }

        hasActiveTimer = true

        val plan = plans.firstOrNull { it.id == state.planId }
        if (plan == null || plan.activities.isEmpty()) {
            runStore.clear()
            AlarmScheduler.cancel(ctx)
            TimerForegroundService.stop(ctx)
            running   = false
            screen    = Screen.LIST
            runPlanId = null
            return
        }

        runPlanId      = state.planId
        hasActiveTimer = true
        runIdx         = state.stepIdx.coerceIn(0, plan.activities.lastIndex)
        if (state.isPaused) {
            running        = false
            isPaused       = true
            stepEndElapsed = 0L
            remainingSec   = state.pausedRemainingSec.coerceAtLeast(0)
            return
        }

        isPaused       = false
        stepEndElapsed = state.stepEndElapsed
        val secLeft    = computeRemainingSeconds(stepEndElapsed)
        remainingSec   = secLeft

        if (secLeft == 0) {
            val now = SystemClock.elapsedRealtime()
            if (now - stepEndElapsed > 1200L) advanceAndRescheduleInForeground(plan)
        }
    }

    private fun advanceAndRescheduleInForeground(plan: TimerPlan) {
        val ctx     = getApplication<Application>().applicationContext
        val nextIdx = runIdx + 1
        if (nextIdx >= plan.activities.size) {
            fullStopAndClear()
            goList()
            return
        }
        playDoneSound()
        runIdx         = nextIdx
        remainingSec   = plan.activities[nextIdx].durationSec
        stepEndElapsed = SystemClock.elapsedRealtime() + (remainingSec * 1000L)
        val nextState  = RunState(planId = plan.id, stepIdx = runIdx, stepEndElapsed = stepEndElapsed)
        runStore.save(nextState)
        AlarmScheduler.schedule(ctx, nextState)
    }

    fun pause() {
        // Pause should NOT remove the notification. It should keep it visible and swap Pause -> Resume.
        if (!running) return

        val planId = runPlanId ?: return
        val ctx = getApplication<Application>().applicationContext

        val secLeft = computeRemainingSeconds(stepEndElapsed)
        remainingSec = secLeft
        running = false
        isPaused = true
        hasActiveTimer = true

        ticker?.cancel(); ticker = null
        AlarmScheduler.cancel(ctx)

        runStore.save(
            RunState(
                planId = planId,
                stepIdx = runIdx,
                stepEndElapsed = 0L,
                isPaused = true,
                pausedRemainingSec = secLeft
            )
        )

        // Ensure notification refreshes to show Resume
        TimerForegroundService.start(ctx)
    }

    private fun fullStopAndClear() {
        val ctx = getApplication<Application>().applicationContext
        running = false
        isPaused = false
        hasActiveTimer = false
        ticker?.cancel(); ticker = null
        AlarmScheduler.cancel(ctx)
        TimerForegroundService.stop(ctx)
        runStore.clear()
    }

    fun restart() {
        val planId = runPlanId ?: return
        val plan   = plans.firstOrNull { it.id == planId } ?: return
        fullStopAndClear()
        runIdx         = 0
        remainingSec   = plan.activities.firstOrNull()?.durationSec ?: 0
        stepEndElapsed = 0L
        running        = false
    }

    fun skipToNext() {
        val planId = runPlanId ?: return
        val plan   = plans.firstOrNull { it.id == planId } ?: return
        val nextIdx = runIdx + 1
        if (nextIdx >= plan.activities.size) { fullStopAndClear(); goList(); return }
        val wasRunning = running
        fullStopAndClear()
        runIdx         = nextIdx
        remainingSec   = plan.activities[nextIdx].durationSec
        stepEndElapsed = 0L
        if (wasRunning) start()
    }

    private fun playDoneSound() {
        try {
            player?.release(); player = null
            val ctx = getApplication<Application>().applicationContext
            val mp  = MediaPlayer.create(ctx, R.raw.timer_done)
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp.setOnCompletionListener { it.release(); if (player === it) player = null }
            mp.start()
            player = mp
        } catch (_: Exception) {}
    }

    override fun onCleared() {
        super.onCleared()
        try {
            player?.release()
            getApplication<Application>().unregisterReceiver(notificationReceiver)
        } catch (_: Exception) {}
        player = null
    }
}
