package com.amar.minimaltimers.data

import kotlinx.serialization.Serializable

@Serializable
data class TimerActivity(
    val id: String,
    val title: String,
    val durationSec: Int
)

@Serializable
data class TimerPlan(
    val id: String,
    val title: String = "",
    val activities: List<TimerActivity> = emptyList(),
    val updatedAt: Long = 0L,
    val draft: Boolean = false
)
