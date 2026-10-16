package com.adityadixit.applock.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
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
import com.adityadixit.applock.ui.components.IconBadge
import com.adityadixit.applock.ui.components.SettingsGroup

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_settings), fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsGroup(stringResource(R.string.settings_section_protection)) {
                SwitchRow(
                    icon = Icons.Default.Shield,
                    title = stringResource(R.string.settings_locking_enabled),
                    subtitle = stringResource(R.string.settings_locking_enabled_summary),
                    checked = state.settings.lockingEnabled,
                    onCheckedChange = viewModel::setLockingEnabled,
                )
                RowDivider()
                val detectionActive = state.system.detectionMode != DetectionMode.NONE
                NavigationRow(
                    icon = Icons.Default.Radar,
                    title = stringResource(R.string.settings_detection),
                    subtitle = stringResource(
                        when (state.system.detectionMode) {
                            DetectionMode.ACCESSIBILITY -> R.string.detection_mode_accessibility
                            DetectionMode.USAGE_STATS -> R.string.detection_mode_usage
                            DetectionMode.NONE -> R.string.detection_mode_none
                        },
                    ),
                    warning = !detectionActive,
                    onClick = onOpenPermissions,
                )
            }

            SettingsGroup(stringResource(R.string.settings_section_relock)) {
                Column(Modifier.selectableGroup()) {
                    RelockPolicy.entries.forEachIndexed { index, policy ->
                        if (index > 0) RowDivider()
                        RadioRow(
                            title = stringResource(policy.labelRes()),
                            selected = state.settings.relockPolicy == policy,
                            onClick = { viewModel.setRelockPolicy(policy) },
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.settings_relock_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp),
            )

            SettingsGroup(stringResource(R.string.settings_section_unlock)) {
                NavigationRow(
                    icon = Icons.Default.Password,
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
                RowDivider()
                val biometricAvailable = state.system.biometric == BiometricStatus.AVAILABLE
                SwitchRow(
                    icon = Icons.Default.Fingerprint,
                    title = stringResource(R.string.settings_biometric),
                    subtitle = stringResource(
                        if (biometricAvailable) R.string.settings_biometric_summary else R.string.biometric_status_unavailable,
                    ),
                    checked = state.settings.biometricEnabled && biometricAvailable,
                    enabled = biometricAvailable,
                    onCheckedChange = viewModel::setBiometricEnabled,
                )
            }

            SettingsGroup(stringResource(R.string.settings_section_self_protection)) {
                SwitchRow(
                    icon = Icons.Default.Security,
                    title = stringResource(R.string.settings_lock_system_settings),
                    subtitle = stringResource(R.string.settings_lock_system_settings_summary),
                    checked = state.systemSettingsLocked,
                    onCheckedChange = viewModel::setSystemSettingsLocked,
                )
                RowDivider()
                SwitchRow(
                    icon = Icons.Default.AdminPanelSettings,
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
            }

            SettingsGroup(stringResource(R.string.settings_section_about)) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { IconBadge(Icons.Default.Info) },
                    headlineContent = { Text(stringResource(R.string.app_name)) },
                    supportingContent = { Text(stringResource(R.string.settings_about_summary)) },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun RelockPolicy.labelRes(): Int = when (this) {
    RelockPolicy.IMMEDIATELY -> R.string.relock_immediately
    RelockPolicy.AFTER_1_MINUTE -> R.string.relock_after_1_minute
    RelockPolicy.AFTER_5_MINUTES -> R.string.relock_after_5_minutes
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
    )
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
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
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { IconBadge(icon) },
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
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { IconBadge(Icons.Default.Timer) },
        headlineContent = { Text(title) },
        trailingContent = { RadioButton(selected = selected, onClick = null) },
    )
}

@Composable
private fun NavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    warning: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    ListItem(
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            if (warning) {
                IconBadge(icon, container = colors.errorContainer, content = colors.onErrorContainer)
            } else {
                IconBadge(icon)
            }
        },
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) {
            { Text(subtitle, color = if (warning) colors.error else colors.onSurfaceVariant) }
        } else {
            null
        },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
    )
}
