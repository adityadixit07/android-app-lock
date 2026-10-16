package com.adityadixit.applock.service

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.adityadixit.applock.system.DetectionMode
import com.adityadixit.applock.system.PermissionStatus

/**
 * Decides which detection host should run. The AccessibilityService is started/stopped by the
 * system when the user toggles it, so only the UsageStats fallback is managed here.
 */
object LockServiceLauncher {
    private const val TAG = "LockServiceLauncher"

    fun ensureRunning(context: Context) {
        val app = context.applicationContext
        if (PermissionStatus.detectionMode(app) != DetectionMode.USAGE_STATS) {
            stopUsageMonitor(app)
            return
        }
        try {
            ContextCompat.startForegroundService(app, Intent(app, UsageMonitorService::class.java))
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (Android 12+) when called from the
            // background. The next app open or boot will start it.
            Log.w(TAG, "Foreground service start not allowed right now", e)
        }
    }

    fun stopUsageMonitor(context: Context) {
        context.stopService(Intent(context, UsageMonitorService::class.java))
    }
}
