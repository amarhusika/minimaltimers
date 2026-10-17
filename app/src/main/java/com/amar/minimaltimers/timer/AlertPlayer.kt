package com.amar.minimaltimers.timer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import com.amar.minimaltimers.R

/**
 * Plays the "step done" beep once, reliably, even with the screen off:
 *  - uses the ALARM stream (audible when media volume is 0 / in DND "alarms allowed")
 *  - holds a short partial wake lock so the CPU can't sleep in the middle of the sound
 *  - always calls onDone exactly once (used to finish the alarm broadcast)
 */
object AlertPlayer {

    private const val TAG = "AlertPlayer"
    private const val MAX_PLAY_MS = 8_000L
    /** Beep volume relative to the phone's alarm volume setting (1.0 = full). */
    private const val VOLUME = 0.5f

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var focusRequest: AudioFocusRequest? = null
    private var pendingDone: (() -> Unit)? = null
    private var appCtx: Context? = null
    private val timeout = Runnable { finish() }

    private val attrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun play(ctx: Context, onDone: () -> Unit = {}) {
        val app = ctx.applicationContext
        appCtx = app
        finish() // stop a previous beep (and complete its callback)
        pendingDone = onDone

        try {
            val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MinimalTimers:alert").apply {
                setReferenceCounted(false)
                acquire(MAX_PLAY_MS + 1_000L)
            }
        } catch (e: Exception) { Log.w(TAG, "wake lock", e) }

        try {
            val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            // Attributes MUST be given before prepare() - MediaPlayer.create(ctx, res) prepares
            // immediately, so setting them afterwards (old code) silently used the media stream.
            val mp = MediaPlayer.create(app, R.raw.timer_done, attrs, am.generateAudioSessionId())
            if (mp == null) { finish(); return }
            requestFocus(am)
            mp.setOnCompletionListener { finish() }
            mp.setOnErrorListener { _, _, _ -> finish(); true }
            player = mp
            mp.setVolume(VOLUME, VOLUME)
            mp.start()
            handler.postDelayed(timeout, MAX_PLAY_MS)
        } catch (e: Exception) {
            Log.w(TAG, "play failed", e)
            finish()
        }
    }

    private fun requestFocus(am: AudioManager) {
        try {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs)
                .build()
            am.requestAudioFocus(req)
            focusRequest = req
        } catch (_: Exception) {}
    }

    private fun finish() {
        handler.removeCallbacks(timeout)
        player?.let {
            try { if (it.isPlaying) it.stop() } catch (_: Exception) {}
            try { it.release() } catch (_: Exception) {}
        }
        player = null
        focusRequest?.let { req ->
            try {
                (appCtx?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
                    ?.abandonAudioFocusRequest(req)
            } catch (_: Exception) {}
        }
        focusRequest = null
        wakeLock?.let { try { if (it.isHeld) it.release() } catch (_: Exception) {} }
        wakeLock = null
        val done = pendingDone
        pendingDone = null
        done?.invoke()
    }
}
