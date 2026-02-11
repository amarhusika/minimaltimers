package com.amar.minimaltimers.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RunStateStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("minimal_timers_run_state", Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true }

    fun load(): RunState? {
        val raw = prefs.getString("state_json", null) ?: return null
        return try { json.decodeFromString<RunState>(raw) } catch (_: Exception) { null }
    }

    fun save(state: RunState) {
        prefs.edit().putString("state_json", json.encodeToString(state)).apply()
    }

    fun clear() {
        prefs.edit().remove("state_json").apply()
    }
}
