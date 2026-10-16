package com.adityadixit.applock.ui.permissions

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

data class PermissionSnapshot(
    val accessibility: Boolean = false,
    val usageAccess: Boolean = false,
    val overlay: Boolean = false,
    val batteryUnrestricted: Boolean = false,
    val notifications: Boolean = false,
    val notificationsRequired: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
    val biometric: BiometricStatus = BiometricStatus.UNAVAILABLE,
    val detectionMode: DetectionMode = DetectionMode.NONE,
) {
    /** Locking works with either Accessibility, or Usage Access + overlay. */
    val canProtect: Boolean get() = detectionMode != DetectionMode.NONE
}

data class PermissionsUiState(
    val permissions: PermissionSnapshot = PermissionSnapshot(),
    val biometricEnabled: Boolean = false,
)

@HiltViewModel
class PermissionsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val biometricAvailability: BiometricAvailability,
) : ViewModel() {

    private val snapshot = MutableStateFlow(read())

    val state: StateFlow<PermissionsUiState> = combine(snapshot, settingsRepository.settings) { perms, settings ->
        PermissionsUiState(perms, settings.biometricEnabled && perms.biometric == BiometricStatus.AVAILABLE)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PermissionsUiState(snapshot.value))

    /** Permissions are granted in other apps, so re-read on every resume. */
    fun refresh() {
        snapshot.value = read()
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setBiometricEnabled(enabled) }
    }

    private fun read() = PermissionSnapshot(
        accessibility = PermissionStatus.isAccessibilityServiceEnabled(context),
        usageAccess = PermissionStatus.hasUsageAccess(context),
        overlay = PermissionStatus.canDrawOverlays(context),
        batteryUnrestricted = PermissionStatus.isIgnoringBatteryOptimizations(context),
        notifications = PermissionStatus.areNotificationsEnabled(context),
        biometric = biometricAvailability.status(),
        detectionMode = PermissionStatus.detectionMode(context),
    )
}
