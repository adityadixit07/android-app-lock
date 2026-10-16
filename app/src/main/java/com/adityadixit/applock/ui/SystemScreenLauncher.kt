package com.adityadixit.applock.ui

import android.content.Intent
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Opens system Settings screens. Routed through the activity so the PIN gate knows the user
 * left on purpose (permission grant) and can apply a short grace period on return.
 */
fun interface SystemScreenLauncher {
    fun launch(intent: Intent)
}

val LocalSystemScreenLauncher = staticCompositionLocalOf<SystemScreenLauncher> {
    error("SystemScreenLauncher not provided")
}
