package com.amar.minimaltimers.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

class Storage(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val fileName = "minimal_timers_data.json"

    private fun file(): File = File(context.filesDir, fileName)

    fun loadAll(): List<TimerPlan> {
        val f = file()
        if (!f.exists()) return emptyList()
        return try {
            json.decodeFromString(f.readText())
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveAll(plans: List<TimerPlan>) {
        file().writeText(json.encodeToString(plans))
    }
}
