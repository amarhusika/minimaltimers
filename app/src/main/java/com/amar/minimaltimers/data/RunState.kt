package com.amar.minimaltimers.data

import kotlinx.serialization.Serializable

@Serializable
data class RunState(
    val planId: String,
    val stepIdx: Int,
    val stepEndElapsed: Long, // SystemClock.elapsedRealtime() when this step should end

    // Pause support: keep the notification visible and allow Resume.
    // When isPaused = true, stepEndElapsed should be 0 and pausedRemainingSec contains the value to resume from.
    val isPaused: Boolean = false,
    val pausedRemainingSec: Int = 0
)
