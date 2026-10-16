package com.adityadixit.applock.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import com.adityadixit.applock.data.apps.AppIconLoader
import com.adityadixit.applock.domain.auth.Authenticator
import com.adityadixit.applock.domain.lock.LockEngine
import com.adityadixit.applock.domain.lock.LockState
import com.adityadixit.applock.domain.repository.SettingsRepository
import com.adityadixit.applock.overlay.LockOverlayController
import com.adityadixit.applock.overlay.OverlayTarget
import com.adityadixit.applock.system.BiometricAvailability
import com.adityadixit.applock.ui.biometric.BiometricUnlockActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Glue shared by both detection hosts so they contain only detection code:
 *  - forwards foreground-app changes to [LockEngine]
 *  - renders [LockState] into the overlay window / biometric activity
 *  - ends all sessions when the screen turns off
 *
 * Lives exactly as long as its host service. Main thread only.
 */
class LockCoordinator(
    private val context: Context,
    windowType: Int,
    private val engine: LockEngine,
    authenticator: Authenticator,
    iconLoader: AppIconLoader,
    private val biometricAvailability: BiometricAvailability,
    private val settingsRepository: SettingsRepository,
    private val leaveLockedApp: () -> Unit,
    private val onScreenInteractiveChanged: (interactive: Boolean) -> Unit = {},
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var biometricEnabled = false
    private var previousState: LockState = LockState.Idle

    private val overlay = LockOverlayController(
        context = context,
        windowType = windowType,
        authenticator = authenticator,
        iconLoader = iconLoader,
        onUnlocked = engine::onUnlockSucceeded,
        onBiometricRequested = engine::onBiometricRequested,
        onLeaveRequested = leaveLockedApp,
    )

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    engine.onScreenOff()
                    onScreenInteractiveChanged(false)
                }
                Intent.ACTION_SCREEN_ON -> onScreenInteractiveChanged(true)
            }
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(context, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        scope.launch { settingsRepository.settings.collect { biometricEnabled = it.biometricEnabled } }
        scope.launch { engine.state.collect(::render) }
    }

    fun onForegroundPackage(packageName: String) = engine.onForegroundAppChanged(packageName)

    fun stop() {
        scope.cancel()
        overlay.hide()
        runCatching { context.unregisterReceiver(screenReceiver) }
    }

    private fun render(state: LockState) {
        when (state) {
            LockState.Idle -> overlay.hide()
            is LockState.Locked -> {
                val shown = overlay.show(
                    OverlayTarget(
                        packageName = state.packageName,
                        autoPromptBiometric = state.autoPromptBiometric,
                        canUseBiometric = biometricEnabled && biometricAvailability.isAvailable(),
                    ),
                )
                // Fail closed: if we cannot cover the app, take the user away from it.
                if (!shown) leaveLockedApp()
            }
            is LockState.AwaitingBiometric -> when {
                state.promptVisible -> overlay.hide()
                previousState !is LockState.AwaitingBiometric -> launchBiometric(state.packageName)
            }
        }
        previousState = state
    }

    private fun launchBiometric(packageName: String) {
        try {
            context.startActivity(BiometricUnlockActivity.intent(context, packageName))
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not start biometric prompt", e)
            engine.onBiometricDismissed(packageName)
        }
    }

    private companion object {
        const val TAG = "LockCoordinator"
    }
}
