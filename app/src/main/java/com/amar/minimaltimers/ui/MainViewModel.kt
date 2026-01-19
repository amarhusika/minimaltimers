package com.amar.minimaltimers.ui

import android.app.Application
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amar.minimaltimers.R
import com.amar.minimaltimers.data.Storage
import com.amar.minimaltimers.data.TimerActivity
import com.amar.minimaltimers.data.TimerPlan
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

enum class Screen { LIST, EDIT, RUN }

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val storage = Storage(app.applicationContext)

    // ✅ Compose state (UI se odmah osvježava)
    var screen by mutableStateOf(Screen.LIST)
        private set

    var plans by mutableStateOf(storage.loadAll())
        private set

    var selectedPlanId by mutableStateOf<String?>(null)
        private set

    var editingPlanId by mutableStateOf<String?>(null)
        private set

    var runPlanId by mutableStateOf<String?>(null)
        private set

    var runIdx by mutableStateOf(0)
        private set

    var remainingSec by mutableStateOf(0)
        private set

    var running by mutableStateOf(false)
        private set

    private var ticker: Job? = null
    private var player: MediaPlayer? = null

    private fun newId(): String = UUID.randomUUID().toString().replace("-", "")

    private fun persist() = storage.saveAll(plans)

    fun goList() {
        pause()
        screen = Screen.LIST
        selectedPlanId = null
        editingPlanId = null
        runPlanId = null
    }

    fun selectPlan(id: String) {
        selectedPlanId = id
    }

    fun newPlan() {
        val id = newId()
        val draft = TimerPlan(
            id = id,
            title = "",
            activities = emptyList(),
            draft = true,
            updatedAt = System.currentTimeMillis()
        )
        plans = listOf(draft) + plans
        persist()
        editingPlanId = id
        screen = Screen.EDIT
    }

    fun openEdit(planId: String) {
        editingPlanId = planId
        screen = Screen.EDIT
    }

    fun savePlanTitle(planId: String, title: String): Boolean {
        val t = title.trim()
        if (t.isEmpty()) return false

        plans = plans.map {
            if (it.id == planId) it.copy(
                title = t,
                draft = false,
                updatedAt = System.currentTimeMillis()
            ) else it
        }
        persist()
        return true
    }

    fun deletePlan(planId: String) {
        plans = plans.filterNot { it.id == planId }
        persist()
        goList()
    }

    fun addActivity(planId: String, name: String, durationSec: Int): Boolean {
        val n = name.trim()
        if (n.isEmpty()) return false
        if (durationSec <= 0) return false

        plans = plans.map { p ->
            if (p.id == planId) {
                val act = TimerActivity(id = newId(), title = n, durationSec = durationSec)
                p.copy(
                    activities = p.activities + act,
                    draft = false,
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

    fun openRun(planId: String): Boolean {
        val plan = plans.firstOrNull { it.id == planId } ?: return false
        if (plan.activities.isEmpty()) return false

        runPlanId = planId
        runIdx = 0
        remainingSec = plan.activities[0].durationSec
        running = false
        screen = Screen.RUN
        return true
    }

    fun startPause() {
        if (running) pause() else start()
    }

    private fun start() {
        val plan = plans.firstOrNull { it.id == runPlanId } ?: return
        if (plan.activities.isEmpty()) return

        running = true
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (running) {
                delay(1000)
                if (!running) break

                remainingSec = (remainingSec - 1).coerceAtLeast(0)

                if (remainingSec == 0) {
                    playDoneSound()
                    advanceStepOrFinish()
                }
            }
        }
    }

    fun pause() {
        running = false
        ticker?.cancel()
        ticker = null
    }

    fun restart() {
        val plan = plans.firstOrNull { it.id == runPlanId } ?: return
        pause()
        runIdx = 0
        remainingSec = plan.activities.firstOrNull()?.durationSec ?: 0
    }

    // ✅ Nema "Skip" dugmeta, ali logika i dalje postoji interno (kad dođe 0)
    private fun advanceStepOrFinish() {
        val plan = plans.firstOrNull { it.id == runPlanId } ?: return

        runIdx += 1
        if (runIdx >= plan.activities.size) {
            pause()
            goList()
            return
        }
        remainingSec = plan.activities[runIdx].durationSec
    }

    // ✅ Pravi MP3 zvuk iz res/raw/timer_done.mp3
    private fun playDoneSound() {
        try {
            player?.release()
            player = null

            val ctx = getApplication<Application>().applicationContext
            val mp = MediaPlayer.create(ctx, R.raw.timer_done)
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp.setOnCompletionListener {
                it.release()
                if (player === it) player = null
            }
            mp.start()
            player = mp
        } catch (_: Exception) {
            // ako faila mp3, samo ignoriši (da app ne crasha)
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            player?.release()
        } catch (_: Exception) {}
        player = null
    }
}
