package com.amar.minimaltimers.ui

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amar.minimaltimers.data.Storage
import com.amar.minimaltimers.data.TimerActivity
import com.amar.minimaltimers.data.TimerPlan
import com.amar.minimaltimers.timer.TimerAlerts
import com.amar.minimaltimers.timer.TimerEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

enum class Screen { LIST, EDIT, RUN }

/**
 * UI state only. The running timer lives in [TimerEngine]; this class just mirrors it and
 * redraws the countdown once per second while the screen is actually visible.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx = app.applicationContext
    private val storage = Storage(ctx)

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
    var running        by mutableStateOf(false)
        private set
    var isPaused       by mutableStateOf(false)
        private set
    var hasActiveTimer by mutableStateOf(false)
        private set

    // Position shown on the Run screen before Start is pressed (no active timer yet).
    private var idleIdx = 0
    private var idleRemainingMs = 0L
    private var wasActive = false

    private var uiVisible = false
    private var ticker: Job? = null

    init {
        restoreRunIfAny()
        viewModelScope.launch {
            TimerEngine.snapshot.collect { snap ->
                if (snap.state == null && snap.finishedPlanId != null &&
                    snap.finishedPlanId == runPlanId && screen == Screen.RUN
                ) {
                    // Plan finished (possibly while the screen was off) -> back to the list.
                    TimerEngine.consumeFinished()
                    leaveRun()
                    return@collect
                }
                refresh()
                updateTicker()
            }
        }
    }

    private fun restoreRunIfAny() {
        val state = TimerEngine.current(ctx) ?: return
        val plan  = plans.firstOrNull { it.id == state.planId }
        if (plan == null || plan.activities.isEmpty()) {
            TimerEngine.stop(ctx)
            return
        }
        runPlanId = plan.id
        screen    = Screen.RUN
        refresh()
        TimerEngine.ensureNotification(ctx)
    }

    /** Called from MainActivity onStart/onStop. No ticking at all while not visible. */
    fun setUiVisible(visible: Boolean) {
        uiVisible = visible
        TimerEngine.uiVisible = visible
        if (visible) {
            TimerAlerts.cancel(ctx)
            refresh()
        }
        updateTicker()
    }

    private fun msToSec(ms: Long): Int = ((ms.coerceAtLeast(0L) + 999L) / 1000L).toInt()

    /** Copies the engine state into the UI fields. */
    private fun refresh() {
        val plan = runPlanId?.let { id -> plans.firstOrNull { it.id == id } }
        val s = TimerEngine.current(ctx)
        if (plan != null && plan.activities.isNotEmpty() && s != null && s.planId == plan.id) {
            wasActive      = true
            hasActiveTimer = true
            runIdx         = s.stepIdx.coerceIn(0, plan.activities.lastIndex)
            if (s.isPaused) {
                running      = false
                isPaused     = true
                remainingSec = msToSec(s.pausedMs())
            } else {
                running      = true
                isPaused     = false
                remainingSec = msToSec(s.stepEndElapsed - SystemClock.elapsedRealtime())
            }
            return
        }
        if (wasActive) {
            // Timer was stopped (Restart, notification Stop...) -> back to the first step.
            wasActive = false
            idleIdx = 0
            idleRemainingMs = (plan?.activities?.firstOrNull()?.durationSec ?: 0) * 1000L
        }
        hasActiveTimer = false
        running        = false
        isPaused       = false
        runIdx         = idleIdx
        remainingSec   = msToSec(idleRemainingMs)
    }

    private fun updateTicker() {
        if (!(uiVisible && running)) {
            ticker?.cancel(); ticker = null
            return
        }
        if (ticker?.isActive == true) return
        ticker = viewModelScope.launch {
            while (isActive) {
                val s = TimerEngine.current(ctx) ?: break
                if (s.isPaused) break
                val msLeft = s.stepEndElapsed - SystemClock.elapsedRealtime()
                if (msLeft <= -OVERDUE_MS) {
                    // Alarm late (e.g. no exact-alarm permission): advance from here.
                    TimerEngine.reconcile(ctx)?.let { TimerAlerts.deliver(ctx, it) }
                    refresh()
                    continue
                }
                refresh()
                // Wake up exactly when the displayed second changes (1x/s instead of 4x/s).
                val wait = if (msLeft > 0) ((msLeft - 1) % 1000L) + 1L else 250L
                delay(wait + 5L)
            }
        }
    }

    private fun newId(): String = UUID.randomUUID().toString().replace("-", "")
    private fun persist() = storage.saveAll(plans)

    // ── Navigation ────────────────────────────────────────────────────────────

    fun goList() {
        TimerEngine.stop(ctx)
        leaveRun()
    }

    private fun leaveRun() {
        wasActive      = false
        screen         = Screen.LIST
        selectedPlanId = null
        editingPlanId  = null
        runPlanId      = null
        refresh()
        updateTicker()
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

        TimerEngine.stop(ctx)
        runPlanId       = planId
        wasActive       = false
        idleIdx         = 0
        idleRemainingMs = plan.activities[0].durationSec * 1000L
        screen          = Screen.RUN
        refresh()
        return true
    }

    fun startPause() {
        when {
            running  -> TimerEngine.pause(ctx)
            isPaused -> TimerEngine.resume(ctx)
            else -> {
                val planId = runPlanId ?: return
                val plan = plans.firstOrNull { it.id == planId } ?: return
                if (plan.activities.isEmpty()) return
                val idx = idleIdx.coerceIn(0, plan.activities.lastIndex)
                TimerEngine.start(ctx, planId, idx, idleRemainingMs)
            }
        }
        refresh()
        updateTicker()
    }

    fun restart() {
        val planId = runPlanId ?: return
        val plan   = plans.firstOrNull { it.id == planId } ?: return
        TimerEngine.stop(ctx)
        wasActive       = false
        idleIdx         = 0
        idleRemainingMs = (plan.activities.firstOrNull()?.durationSec ?: 0) * 1000L
        refresh()
        updateTicker()
    }

    fun skipToNext() {
        val planId = runPlanId ?: return
        val plan   = plans.firstOrNull { it.id == planId } ?: return
        if (hasActiveTimer) {
            // Keeps running / paused state; on the last step the plan ends -> list.
            if (!TimerEngine.skip(ctx)) { TimerEngine.consumeFinished(); leaveRun(); return }
        } else {
            val nextIdx = idleIdx + 1
            if (nextIdx >= plan.activities.size) { goList(); return }
            idleIdx         = nextIdx
            idleRemainingMs = plan.activities[nextIdx].durationSec * 1000L
        }
        refresh()
        updateTicker()
    }

    private companion object {
        const val OVERDUE_MS = 1_500L
    }
}
