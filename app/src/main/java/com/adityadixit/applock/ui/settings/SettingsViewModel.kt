package com.adityadixit.applock.ui.settings

import android.app.admin.DevicePolicyManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adityadixit.applock.admin.AppLockDeviceAdminReceiver
import com.adityadixit.applock.domain.model.CredentialType
import com.adityadixit.applock.domain.model.LockSettings
import com.adityadixit.applock.domain.model.RelockPolicy
import com.adityadixit.applock.domain.repository.CredentialRepository
import com.adityadixit.applock.domain.repository.LockedAppsRepository
import com.adityadixit.applock.domain.repository.SettingsRepository
import com.adityadixit.applock.system.BiometricAvailability
import com.adityadixit.applock.system.BiometricStatus
import com.adityadixit.applock.system.DetectionMode
import com.adityadixit.applock.system.PermissionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SystemStatus(
    val detectionMode: DetectionMode = DetectionMode.NONE,
    val biometric: BiometricStatus = BiometricStatus.UNAVAILABLE,
    val deviceAdminActive: Boolean = false,
)

data class SettingsUiState(
    val settings: LockSettings = LockSettings(),
    val credentialType: CredentialType? = null,
    val system: SystemStatus = SystemStatus(),
    val systemSettingsLocked: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val lockedAppsRepository: LockedAppsRepository,
    credentialRepository: CredentialRepository,
    private val biometricAvailability: BiometricAvailability,
) : ViewModel() {

    private val system = MutableStateFlow(readSystemStatus())

    val state: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        credentialRepository.credentialType,
        lockedAppsRepository.lockedPackages,
        system,
    ) { settings, credentialType, locked, systemStatus ->
        SettingsUiState(
            settings = settings,
            credentialType = credentialType,
            system = systemStatus,
            systemSettingsLocked = SETTINGS_PACKAGE in locked,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(system = system.value))

    fun refresh() {
        system.value = readSystemStatus()
    }

    fun setLockingEnabled(enabled: Boolean) = runAsync { settingsRepository.setLockingEnabled(enabled) }

    fun setRelockPolicy(policy: RelockPolicy) = runAsync { settingsRepository.setRelockPolicy(policy) }

    fun setBiometricEnabled(enabled: Boolean) = runAsync { settingsRepository.setBiometricEnabled(enabled) }

    /**
     * Locking Settings and the package installer closes the obvious bypasses: disabling the
     * accessibility service, revoking permissions, deactivating device admin, uninstalling.
     */
    fun setSystemSettingsLocked(locked: Boolean) = runAsync {
        SELF_PROTECTION_PACKAGES.forEach { lockedAppsRepository.setLocked(it, locked) }
    }

    fun deactivateDeviceAdmin() {
        context.getSystemService(DevicePolicyManager::class.java)
            .removeActiveAdmin(AppLockDeviceAdminReceiver.component(context))
        refresh()
    }

    private fun runAsync(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun readSystemStatus() = SystemStatus(
        detectionMode = PermissionStatus.detectionMode(context),
        biometric = biometricAvailability.status(),
        deviceAdminActive = PermissionStatus.isDeviceAdminActive(context),
    )

    private companion object {
        const val SETTINGS_PACKAGE = "com.android.settings"
        val SELF_PROTECTION_PACKAGES = listOf(
            SETTINGS_PACKAGE,
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
        )
    }
}
