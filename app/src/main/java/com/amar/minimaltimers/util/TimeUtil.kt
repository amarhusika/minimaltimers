package com.amar.minimaltimers.util

import com.amar.minimaltimers.data.TimerActivity

fun secToHms(sec: Int): String {
    val s = sec.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val r = s % 60
    return "%02d:%02d:%02d".format(h, m, r)
}

fun secToHm(sec: Int): String {
    val s = sec.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    return "%02d:%02d".format(h, m)
}

fun parseHhMm(text: String): Int? {
    val t = text.trim()
    val parts = t.split(":")
    if (parts.size != 2) return null
    val hh = parts[0].toIntOrNull() ?: return null
    val mm = parts[1].toIntOrNull() ?: return null
    if (hh !in 0..99) return null
    if (mm !in 0..59) return null
    val sec = hh * 3600 + mm * 60
    return if (sec > 0) sec else null
}

fun sumSec(acts: List<TimerActivity>): Int = acts.sumOf { it.durationSec }
