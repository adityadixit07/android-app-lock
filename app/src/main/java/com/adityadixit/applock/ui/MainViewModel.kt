package com.adityadixit.applock.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adityadixit.applock.core.TimeSource
import com.adityadixit.applock.domain.auth.Authenticator
import com.adityadixit.applock.domain.repository.SettingsRepository
import com.adityadixit.applock.system.BiometricAvailability
import com.adityadixit.applock.ui.lock.LockScreenController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface RootState {
    data object Loading : RootState
    data object NeedsSetup : RootState
    data class Locked(val biometricEnabled: Boolean) : RootState
    data object Ready : RootState
}

/**
 * Routes between onboarding, the in-app PIN gate and the main UI. The management UI is itself
 * protected: otherwise anyone could open App Lock and simply unlock everything.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val authenticator: Authenticator,
    private val biometricAvailability: BiometricAvailability,
    private val time: TimeSource,
) : ViewModel() {

    private val gateUnlocked = MutableStateFlow(false)
    private var stoppedAt: Long? = null
    private var externalNavigationPending = false

    val gateController = LockScreenController(authenticator, viewModelScope) { gateUnlocked.value = true }

    val rootState: StateFlow<RootState> = combine(
        settingsRepository.settings,
        authenticator.credentialType,
        gateUnlocked,
    ) { settings, credentialType, unlocked ->
        when {
            !settings.onboardingCompleted || credentialType == null -> RootState.NeedsSetup
            !unlocked -> RootState.Locked(settings.biometricEnabled && biometricAvailability.isAvailable())
            else -> RootState.Ready
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RootState.Loading)

    fun onOnboardingFinished() {
        // The user has just created their PIN, so open the gate before routing to the main UI.
        gateUnlocked.value = true
        viewModelScope.launch { settingsRepository.setOnboardingCompleted(true) }
    }

    fun onBiometricSuccess() {
        gateUnlocked.value = true
        viewModelScope.launch { authenticator.onBiometricSuccess() }
    }

    /** Called right before we open a system Settings screen on the user's behalf. */
    fun markExternalNavigation() {
        externalNavigationPending = true
    }

    fun onAppStopped() {
        stoppedAt = time.elapsedRealtime()
    }

    /**
     * Re-lock whenever the app returns from the background, except for a short grace window
     * after we sent the user to grant a permission — otherwise onboarding would be painful.
     */
    fun onAppStarted() {
        val stopped = stoppedAt ?: return
        stoppedAt = null
        val grace = if (externalNavigationPending) EXTERNAL_NAVIGATION_GRACE_MS else 0L
        externalNavigationPending = false
        if (time.elapsedRealtime() - stopped > grace) gateUnlocked.value = false
    }

    private companion object {
        const val EXTERNAL_NAVIGATION_GRACE_MS = 2 * 60_000L
    }
}
