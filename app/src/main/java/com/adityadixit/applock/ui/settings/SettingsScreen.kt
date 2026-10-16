package com.adityadixit.applock.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adityadixit.applock.R
import com.adityadixit.applock.domain.model.CredentialType
import com.adityadixit.applock.domain.model.RelockPolicy
import com.adityadixit.applock.system.BiometricStatus
import com.adityadixit.applock.system.DetectionMode
import com.adityadixit.applock.system.SystemIntents
import com.adityadixit.applock.ui.LocalSystemScreenLauncher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenPermissions: () -> Unit,
    onChangeCredential: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val context = LocalContext.current
    val systemScreens = LocalSystemScreenLauncher.current

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_settings)) }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(R.string.settings_section_protection)
            SwitchRow(
                title = stringResource(R.string.settings_locking_enabled),
                subtitle = stringResource(R.string.settings_locking_enabled_summary),
                checked = state.settings.lockingEnabled,
                onCheckedChange = viewModel::setLockingEnabled,
            )
            NavigationRow(
                title = stringResource(R.string.settings_detection),
                subtitle = stringResource(
                    when (state.system.detectionMode) {
                        DetectionMode.ACCESSIBILITY -> R.string.detection_mode_accessibility
                        DetectionMode.USAGE_STATS -> R.string.detection_mode_usage
                        DetectionMode.NONE -> R.string.detection_mode_none
                    },
                ),
                onClick = onOpenPermissions,
            )

            SectionHeader(R.string.settings_section_relock)
            Column(Modifier.selectableGroup()) {
                RelockPolicy.entries.forEach { policy ->
                    RadioRow(
                        title = stringResource(policy.labelRes()),
                        selected = state.settings.relockPolicy == policy,
                        onClick = { viewModel.setRelockPolicy(policy) },
                    )
                }
            }
            Text(
                stringResource(R.string.settings_relock_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            SectionHeader(R.string.settings_section_unlock)
            NavigationRow(
                title = stringResource(
                    if (state.credentialType == CredentialType.PATTERN) {
                        R.string.settings_change_pattern
                    } else {
                        R.string.settings_change_pin
                    },
                ),
                subtitle = null,
                onClick = onChangeCredential,
            )
            val biometricAvailable = state.system.biometric == BiometricStatus.AVAILABLE
            SwitchRow(
                title = stringResource(R.string.settings_biometric),
                subtitle = stringResource(
                    if (biometricAvailable) R.string.settings_biometric_summary else R.string.biometric_status_unavailable,
                ),
                checked = state.settings.biometricEnabled && biometricAvailable,
                enabled = biometricAvailable,
                onCheckedChange = viewModel::setBiometricEnabled,
            )

            SectionHeader(R.string.settings_section_self_protection)
            SwitchRow(
                title = stringResource(R.string.settings_lock_system_settings),
                subtitle = stringResource(R.string.settings_lock_system_settings_summary),
                checked = state.systemSettingsLocked,
                onCheckedChange = viewModel::setSystemSettingsLocked,
            )
            SwitchRow(
                title = stringResource(R.string.settings_prevent_uninstall),
                subtitle = stringResource(R.string.settings_prevent_uninstall_summary),
                checked = state.system.deviceAdminActive,
                onCheckedChange = { enable ->
                    if (enable) {
                        systemScreens.launch(SystemIntents.addDeviceAdmin(context))
                    } else {
                        viewModel.deactivateDeviceAdmin()
                    }
                },
            )

            SectionHeader(R.string.settings_section_about)
            ListItem(
                headlineContent = { Text(stringResource(R.string.app_name)) },
                supportingContent = { Text(stringResource(R.string.settings_about_summary)) },
            )
        }
    }
}

private fun RelockPolicy.labelRes(): Int = when (this) {
    RelockPolicy.IMMEDIATELY -> R.string.relock_immediately
    RelockPolicy.AFTER_1_MINUTE -> R.string.relock_after_1_minute
    RelockPolicy.AFTER_5_MINUTES -> R.string.relock_after_5_minutes
}

@Composable
private fun SectionHeader(titleRes: Int) {
    HorizontalDivider(Modifier.padding(top = 8.dp))
    Text(
        stringResource(titleRes),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    ListItem(
        modifier = Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) {
            { Text(subtitle) }
        } else {
            null
        },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
    )
}

@Composable
private fun RadioRow(title: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        headlineContent = { Text(title) },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
    )
}

@Composable
private fun NavigationRow(title: String, subtitle: String?, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) {
            { Text(subtitle) }
        } else {
            null
        },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
    )
}
