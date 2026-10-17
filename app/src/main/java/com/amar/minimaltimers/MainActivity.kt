package com.amar.minimaltimers

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import com.amar.minimaltimers.alarm.AlarmScheduler
import com.amar.minimaltimers.ui.*

class MainActivity : ComponentActivity() {

    private lateinit var vm: MainViewModel

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result handled silently; next missing setting is asked on next resume */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm = ViewModelProvider(this)[MainViewModel::class.java]
        setContent {
            when (vm.screen) {
                Screen.LIST -> ListScreen(vm)
                Screen.EDIT -> EditScreen(vm)
                Screen.RUN  -> RunScreen(vm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.setUiVisible(true)
    }

    override fun onResume() {
        super.onResume()
        askNextMissingSettingOnce()
    }

    override fun onStop() {
        vm.setUiVisible(false)
        super.onStop()
    }

    /**
     * Each setting is asked at most ONCE (old code opened the battery and exact-alarm
     * screens on every app start / rotation), and only one screen at a time.
     */
    @SuppressLint("BatteryLife")
    private fun askNextMissingSettingOnce() {
        val prefs = getSharedPreferences("minimal_timers_setup", Context.MODE_PRIVATE)
        fun firstTime(key: String): Boolean {
            if (prefs.getBoolean(key, false)) return false
            prefs.edit().putBoolean(key, true).apply()
            return true
        }
        val pkgUri = Uri.parse("package:$packageName")

        // 1) Notifications (Android 13+) - needed for the ongoing timer + alert.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            if (firstTime("asked_notifications")) {
                notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        // 2) Exact alarms (only Android 12/12L ask; 13+ get USE_EXACT_ALARM automatically).
        if (!AlarmScheduler.canScheduleExact(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (firstTime("asked_exact_alarm") && tryStart(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkgUri))) return
        }

        // 3) Battery optimisation: protects the timer from aggressive OEM task killers.
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName) && firstTime("asked_battery")) {
            tryStart(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri))
        }
    }

    private fun tryStart(i: Intent): Boolean = try { startActivity(i); true } catch (_: Exception) { false }
}
