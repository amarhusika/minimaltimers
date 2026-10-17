package com.amar.minimaltimers.data

import kotlinx.serialization.Serializable

@Serializable
data class RunState(
    val planId: String,
    val stepIdx: Int,
    val stepEndElapsed: Long, // SystemClock.elapsedRealtime() when this step should end

    // Pause support: when isPaused = true, stepEndElapsed is 0 and the remaining time is kept here.
    val isPaused: Boolean = false,
    val pausedRemainingSec: Int = 0,      // kept for compatibility with older saved state
    val pausedRemainingMs: Long = 0L,     // exact remaining time (no rounding drift on pause/resume)

    // Survive reboot: elapsedRealtime restarts at 0, so we also keep the wall-clock end
    // and the boot number to detect that a reboot happened.
    val stepEndWall: Long = 0L,
    val bootCount: Int = 0
) {
    fun pausedMs(): Long =
        if (pausedRemainingMs > 0L) pausedRemainingMs else pausedRemainingSec.coerceAtLeast(0) * 1000L
}
