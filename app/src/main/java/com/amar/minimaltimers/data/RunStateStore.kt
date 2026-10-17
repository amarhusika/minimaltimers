package com.amar.minimaltimers.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.json.Json

class RunStateStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("minimal_timers_run_state", Context.MODE_PRIVATE)

    fun load(): RunState? {
        val raw = prefs.getString(KEY, null) ?: return null
        return try { json.decodeFromString(RunState.serializer(), raw) } catch (_: Exception) { null }
    }

    // commit() (synchronous): the state changes rarely, and it must be on disk before the
    // alarm broadcast finishes, otherwise a killed process could lose the step change.
    fun save(state: RunState) {
        prefs.edit().putString(KEY, json.encodeToString(RunState.serializer(), state)).commit()
    }

    fun clear() {
        prefs.edit().remove(KEY).commit()
    }

    private companion object {
        const val KEY = "state_json"
        val json = Json { ignoreUnknownKeys = true }
    }
}
