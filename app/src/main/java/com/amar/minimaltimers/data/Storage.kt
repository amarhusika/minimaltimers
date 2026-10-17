package com.amar.minimaltimers.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream

class Storage(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val fileName = "minimal_timers_data.json"
    private val serializer = ListSerializer(TimerPlan.serializer())

    // AtomicFile: a crash/kill during save can no longer leave a half-written file
    // (which used to make loadAll() return an empty list = all timers lost).
    private fun file(): AtomicFile = AtomicFile(File(context.filesDir, fileName))

    fun loadAll(): List<TimerPlan> {
        return try {
            json.decodeFromString(serializer, String(file().readFully(), Charsets.UTF_8))
        } catch (_: FileNotFoundException) {
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveAll(plans: List<TimerPlan>) {
        val f = file()
        var out: FileOutputStream? = null
        try {
            out = f.startWrite()
            out.write(json.encodeToString(serializer, plans).toByteArray(Charsets.UTF_8))
            f.finishWrite(out)
        } catch (e: Exception) {
            out?.let { f.failWrite(it) }
        }
    }
}
