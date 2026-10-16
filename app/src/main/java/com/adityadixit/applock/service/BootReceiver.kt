package com.adityadixit.applock.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restarts Usage Access detection after a reboot or app update. (Accessibility services are
 * re-bound by the system automatically.) `specialUse` services may start from BOOT_COMPLETED
 * on Android 15, unlike dataSync/mediaPlayback.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> LockServiceLauncher.ensureRunning(context)
        }
    }
}
