package com.amar.minimaltimers.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.amar.minimaltimers.data.RunStateStore

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != "android.intent.action.QUICKBOOT_POWERON") return

        // Reschedule any active timer after reboot
        val store = RunStateStore(context)
        val state = store.load() ?: return
        AlarmScheduler.schedule(context, state)
    }
}
