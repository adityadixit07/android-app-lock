package com.adityadixit.applock.domain.lock

import com.adityadixit.applock.core.ApplicationScope
import com.adityadixit.applock.core.TimeSource
import com.adityadixit.applock.core.TransientPackages
import com.adityadixit.applock.domain.model.LockSettings
import com.adityadixit.applock.domain.repository.CredentialRepository
import com.adityadixit.applock.domain.repository.LockedAppsRepository
import com.adityadixit.applock.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/** What the lock UI should currently be doing. */
sealed interface LockState {
    data object Idle : LockState

    /** Show the PIN/pattern overlay for [packageName]. */
    data class Locked(
        val packageName: String,
        /** Launch biometric prompt automatically (false after the user dismissed it once). */
        val autoPromptBiometric: Boolean,
    ) : LockState

    /**
     * Biometric activity is being shown for [packageName]. The overlay stays up until the
     * activity reports [promptVisible] so the locked app never flashes in between.
     */
    data class AwaitingBiometric(
        val packageName: String,
        val promptVisible: Boolean,
    ) : LockState
}

/**
 * The single brain of the locker. Detection hosts (AccessibilityService or the UsageStats
 * foreground service) feed it foreground-app changes; the UI observes [state].
 *
 * It is detection-agnostic and has no Android dependencies, so the security-critical
 * decisions are fully unit-testable. All mutating calls are serialised on [lock].
 */
@Singleton
class LockEngine @Inject constructor(
    lockedAppsRepository: LockedAppsRepository,
    settingsRepository: SettingsRepository,
    credentialRepository: CredentialRepository,
    time: TimeSource,
    @TransientPackages private val transientPackages: Set<String>,
    @ApplicationScope scope: CoroutineScope,
) {
    private data class Config(
        val lockedPackages: Set<String> = emptySet(),
        val settings: LockSettings = LockSettings(),
        val hasCredential: Boolean = false,
    ) {
        /** Never lock without a credential: that would lock the user out of their own apps. */
        val enforcing: Boolean
            get() = hasCredential && settings.onboardingCompleted && settings.lockingEnabled
    }

    private val lock = Any()
    private val tracker = LockSessionTracker(time::elapsedRealtime)
    private var foregroundPackage: String? = null

    private val _state = MutableStateFlow<LockState>(LockState.Idle)
    val state: StateFlow<LockState> = _state.asStateFlow()

    private val config: StateFlow<Config> = combine(
        lockedAppsRepository.lockedPackages,
        settingsRepository.settings,
        credentialRepository.credentialType,
    ) { locked, settings, type -> Config(locked, settings, type != null) }
        .stateIn(scope, SharingStarted.Eagerly, Config())

    init {
        // Re-evaluate when configuration loads or changes (e.g. app locked, master switch).
        config.onEach { synchronized(lock) { evaluate() } }.launchIn(scope)
    }

    fun onForegroundAppChanged(packageName: String) = synchronized(lock) {
        // Shade, share sheet, permission dialogs and our own windows don't count as "leaving".
        if (packageName in transientPackages) return@synchronized
        val previous = foregroundPackage
        if (previous != packageName) {
            previous?.let(tracker::onLeft)
            foregroundPackage = packageName
        }
        evaluate()
    }

    fun onUnlockSucceeded(packageName: String) = synchronized(lock) {
        tracker.onUnlocked(packageName)
        if (foregroundPackage != packageName) tracker.onLeft(packageName)
        evaluate()
    }

    fun onBiometricRequested(packageName: String) = synchronized(lock) {
        val current = _state.value
        if (current is LockState.Locked && current.packageName == packageName) {
            _state.value = LockState.AwaitingBiometric(packageName, promptVisible = false)
        }
    }

    fun onBiometricPromptVisible(packageName: String) = synchronized(lock) {
        val current = _state.value
        if (current is LockState.AwaitingBiometric && current.packageName == packageName) {
            _state.value = current.copy(promptVisible = true)
        }
    }

    /** Biometric cancelled/failed: fall back to the PIN overlay without re-prompting. */
    fun onBiometricDismissed(packageName: String) = synchronized(lock) {
        val current = _state.value
        if (current is LockState.AwaitingBiometric && current.packageName == packageName) {
            _state.value = LockState.Locked(packageName, autoPromptBiometric = false)
            evaluate()
        }
    }

    /** Screen off ends every session; the overlay is ready before the screen turns back on. */
    fun onScreenOff() = synchronized(lock) {
        tracker.clearAll()
        evaluate()
    }

    private fun evaluate() {
        val pkg = foregroundPackage
        val cfg = config.value
        if (pkg == null || !cfg.enforcing || pkg !in cfg.lockedPackages) {
            _state.value = LockState.Idle
            return
        }
        if (tracker.resumeSessionIfValid(pkg, cfg.settings.relockPolicy)) {
            _state.value = LockState.Idle
            return
        }
        // Already locking this app: keep the in-flight state (e.g. biometric prompt).
        when (val current = _state.value) {
            is LockState.Locked -> if (current.packageName == pkg) return
            is LockState.AwaitingBiometric -> if (current.packageName == pkg) return
            LockState.Idle -> Unit
        }
        _state.value = LockState.Locked(pkg, autoPromptBiometric = cfg.settings.biometricEnabled)
    }
}
