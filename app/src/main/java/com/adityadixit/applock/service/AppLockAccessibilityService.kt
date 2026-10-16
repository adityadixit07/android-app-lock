package com.adityadixit.applock.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.adityadixit.applock.data.apps.AppIconLoader
import com.adityadixit.applock.domain.auth.Authenticator
import com.adityadixit.applock.domain.lock.LockEngine
import com.adityadixit.applock.domain.repository.SettingsRepository
import com.adityadixit.applock.system.BiometricAvailability
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Primary detection host: the system pushes a window-state event the moment any activity
 * comes to the foreground. No polling, no wakelocks, ~0 battery cost, and the lock appears
 * before the target app has drawn its first frame in most cases.
 *
 * Least privilege: configured without window-content access, so it only sees package and
 * class names (see res/xml/accessibility_service_config.xml).
 */
@AndroidEntryPoint
class AppLockAccessibilityService : AccessibilityService() {

    @Inject lateinit var engine: LockEngine
    @Inject lateinit var authenticator: Authenticator
    @Inject lateinit var iconLoader: AppIconLoader
    @Inject lateinit var biometricAvailability: BiometricAvailability
    @Inject lateinit var settingsRepository: SettingsRepository

    private var coordinator: LockCoordinator? = null
    private val activityCache = object : LinkedHashMap<String, Boolean>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>) =
            size > MAX_CACHED_COMPONENTS
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // This host supersedes the polling fallback.
        LockServiceLauncher.stopUsageMonitor(this)
        coordinator?.stop()
        coordinator = LockCoordinator(
            context = this,
            windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            engine = engine,
            authenticator = authenticator,
            iconLoader = iconLoader,
            biometricAvailability = biometricAvailability,
            settingsRepository = settingsRepository,
            leaveLockedApp = { performGlobalAction(GLOBAL_ACTION_HOME) },
        ).also { it.start() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: return
        // Dialogs, keyboards, toasts and floating bubbles also fire this event. Only real
        // activities mean "the user switched apps"; otherwise a chat-head could hide the lock.
        if (!isActivity(packageName, className)) return
        coordinator?.onForegroundPackage(packageName)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        // Accessibility was turned off: fall back to Usage Access detection if permitted.
        LockServiceLauncher.ensureRunning(applicationContext)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        coordinator?.stop()
        coordinator = null
    }

    private fun isActivity(packageName: String, className: String): Boolean {
        val key = "$packageName/$className"
        return activityCache.getOrPut(key) {
            try {
                val component = ComponentName(packageName, className)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getActivityInfo(component, PackageManager.ComponentInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getActivityInfo(component, 0)
                }
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
    }

    private companion object {
        const val MAX_CACHED_COMPONENTS = 256
    }
}
