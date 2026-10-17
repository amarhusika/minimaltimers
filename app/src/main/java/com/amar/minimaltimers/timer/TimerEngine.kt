package com.amar.minimaltimers.timer

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.amar.minimaltimers.alarm.AlarmScheduler
import com.amar.minimaltimers.data.RunState
import com.amar.minimaltimers.data.RunStateStore
import com.amar.minimaltimers.data.Storage
import com.amar.minimaltimers.data.TimerPlan
import com.amar.minimaltimers.service.TimerForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Single source of truth for the running timer.
 *
 * Every component (UI, alarm receiver, notification actions, boot receiver) goes through
 * this object, so the persisted state, the scheduled alarm and the notification can never
 * disagree with each other. The alarm (AlarmManager) is the only thing that has to wake the
 * phone - nothing polls in the background.
 */
object TimerEngine {

    /** [finishedPlanId] is set only by the commit that ended a plan naturally (last step done). */
    data class Snapshot(
        val state: RunState?,
        val finishedPlanId: String? = null,
        val version: Long = 0L
    )

    /** What just completed - used for sound + alert notification. */
    data class Alert(
        val planTitle: String,
        val completedTitle: String,
        val nextTitle: String?,
        val finished: Boolean
    )

    private val _snapshot = MutableStateFlow(Snapshot(null))
    val snapshot: StateFlow<Snapshot> = _snapshot

    private var loaded = false

    /** True while MainActivity is started (visible). Set by the UI. */
    @Volatile
    var uiVisible: Boolean = false

    // ── Reading ──────────────────────────────────────────────────────────────

    @Synchronized
    fun current(ctx: Context): RunState? {
        ensureLoaded(ctx)
        return _snapshot.value.state
    }

    @Synchronized
    private fun ensureLoaded(ctx: Context) {
        if (loaded) return
        loaded = true
        val app = ctx.applicationContext
        var s = RunStateStore(app).load()
        if (s != null && !s.isPaused) {
            val bc = bootCount(app)
            if (s.bootCount > 0 && bc > 0 && s.bootCount != bc && s.stepEndWall > 0L) {
                // Phone was rebooted: elapsedRealtime restarted from 0, rebuild it from wall time.
                val newEnd = SystemClock.elapsedRealtime() + (s.stepEndWall - System.currentTimeMillis())
                s = s.copy(stepEndElapsed = newEnd, bootCount = bc)
                RunStateStore(app).save(s)
            } else if (s.bootCount == 0) {
                // State written by an older app version - add the clock info now.
                s = withClock(app, s)
                RunStateStore(app).save(s)
            }
        }
        _snapshot.value = Snapshot(s)
    }

    // ── Commands ─────────────────────────────────────────────────────────────

    /** Start running [stepIdx] of [planId] with [remainingMs] left. */
    @Synchronized
    fun start(ctx: Context, planId: String, stepIdx: Int, remainingMs: Long) {
        ensureLoaded(ctx)
        val left = remainingMs.coerceAtLeast(0L)
        commit(ctx, RunState(
            planId = planId,
            stepIdx = stepIdx,
            stepEndElapsed = SystemClock.elapsedRealtime() + left
        ))
    }

    @Synchronized
    fun pause(ctx: Context) {
        val s = current(ctx) ?: return
        if (s.isPaused) return
        val leftMs = (s.stepEndElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        commit(ctx, pausedState(s.planId, s.stepIdx, leftMs))
    }

    @Synchronized
    fun resume(ctx: Context) {
        val s = current(ctx) ?: return
        if (!s.isPaused) return
        start(ctx, s.planId, s.stepIdx, s.pausedMs())
    }

    /** Move to the next step (keeps running/paused). Returns false if the plan is over. */
    @Synchronized
    fun skip(ctx: Context): Boolean {
        val s = current(ctx) ?: return false
        val plan = loadPlan(ctx, s.planId)
        if (plan == null || plan.activities.isEmpty()) { commit(ctx, null); return false }
        val next = s.stepIdx + 1
        if (next >= plan.activities.size) {
            commit(ctx, null, finishedPlanId = plan.id)
            return false
        }
        val durMs = plan.activities[next].durationSec * 1000L
        if (s.isPaused) commit(ctx, pausedState(plan.id, next, durMs))
        else start(ctx, plan.id, next, durMs)
        return true
    }

    @Synchronized
    fun stop(ctx: Context) {
        ensureLoaded(ctx)
        commit(ctx, null)
    }

    /** Called by the UI after it showed the finished plan. */
    @Synchronized
    fun consumeFinished() {
        val cur = _snapshot.value
        if (cur.finishedPlanId != null) _snapshot.value = cur.copy(finishedPlanId = null)
    }

    /**
     * Brings the timer up to date with the clock. If the current step has ended
     * (allowing [earlyToleranceMs] of alarm jitter) it advances - catching up over several
     * steps if the phone was asleep/off for long - and returns the [Alert] to play.
     * If the step has not ended yet, it re-arms the alarm (never leaves the timer without one).
     */
    @Synchronized
    fun reconcile(ctx: Context, earlyToleranceMs: Long = 0L): Alert? {
        val s = current(ctx) ?: return null
        if (s.isPaused) return null
        val now = SystemClock.elapsedRealtime()
        if (now + earlyToleranceMs < s.stepEndElapsed) {
            // Not finished yet (early alarm, clock change, boot...). Re-arm with fresh wall time.
            commit(ctx, s)
            return null
        }
        return advanceOverdue(ctx, s, now)
    }

    /** Make sure the ongoing notification exists while a timer is active. */
    fun ensureNotification(ctx: Context) {
        if (current(ctx) != null) TimerForegroundService.ensureRunning(ctx)
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private fun advanceOverdue(ctx: Context, s: RunState, now: Long): Alert? {
        val plan = loadPlan(ctx, s.planId)
        if (plan == null || plan.activities.isEmpty() || s.stepIdx !in plan.activities.indices) {
            commit(ctx, null)
            return null
        }
        val planTitle = plan.title
        var idx = s.stepIdx
        var end = s.stepEndElapsed
        var completed = plan.activities[idx].title
        while (true) {
            val next = idx + 1
            if (next >= plan.activities.size) {
                commit(ctx, null, finishedPlanId = plan.id)
                return Alert(planTitle, completed, null, finished = true)
            }
            idx = next
            // Next step is measured from when the previous one was SUPPOSED to end,
            // so the plan's total time stays exact even if the alarm was a bit late.
            end += plan.activities[idx].durationSec * 1000L
            if (end > now) break
            completed = plan.activities[idx].title
        }
        commit(ctx, RunState(planId = plan.id, stepIdx = idx, stepEndElapsed = end))
        return Alert(planTitle, completed, plan.activities[idx].title, finished = false)
    }

    private fun pausedState(planId: String, stepIdx: Int, leftMs: Long) = RunState(
        planId = planId,
        stepIdx = stepIdx,
        stepEndElapsed = 0L,
        isPaused = true,
        pausedRemainingSec = ((leftMs + 999L) / 1000L).toInt(),
        pausedRemainingMs = leftMs
    )

    private fun commit(ctx: Context, newState: RunState?, finishedPlanId: String? = null) {
        val app = ctx.applicationContext
        val store = RunStateStore(app)
        val s = newState?.let { withClock(app, it) }
        if (s == null) store.clear() else store.save(s)

        val old = _snapshot.value
        _snapshot.value = Snapshot(s, finishedPlanId, old.version + 1)

        if (s == null || s.isPaused) AlarmScheduler.cancel(app) else AlarmScheduler.schedule(app, s)
        // The service observes [snapshot] and removes itself when the state becomes null.
        if (s != null) TimerForegroundService.ensureRunning(app)
    }

    private fun withClock(ctx: Context, s: RunState): RunState {
        val bc = bootCount(ctx)
        return if (s.isPaused) s.copy(stepEndWall = 0L, bootCount = bc)
        else s.copy(
            stepEndWall = System.currentTimeMillis() + (s.stepEndElapsed - SystemClock.elapsedRealtime()),
            bootCount = bc
        )
    }

    private fun loadPlan(ctx: Context, planId: String): TimerPlan? =
        Storage(ctx.applicationContext).loadAll().firstOrNull { it.id == planId }

    private fun bootCount(ctx: Context): Int = try {
        Settings.Global.getInt(ctx.contentResolver, Settings.Global.BOOT_COUNT)
    } catch (_: Exception) { -1 }
}
