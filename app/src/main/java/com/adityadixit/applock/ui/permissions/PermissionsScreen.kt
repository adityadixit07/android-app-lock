package com.adityadixit.applock.ui.permissions

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import com.adityadixit.applock.ui.components.IconBadge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adityadixit.applock.BuildConfig
import com.adityadixit.applock.R
import com.adityadixit.applock.system.BiometricStatus
import com.adityadixit.applock.system.SystemIntents
import com.adityadixit.applock.ui.LocalSystemScreenLauncher

/**
 * Permission onboarding checklist. Used inside onboarding ([onContinue] set) and from
 * Settings ([onBack] set). Status is re-read every time the user returns from Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    onBack: (() -> Unit)? = null,
    onContinue: (() -> Unit)? = null,
    viewModel: PermissionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.permissions_title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (onContinue != null) {
                Button(
                    onClick = onContinue,
                    enabled = state.permissions.canProtect,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(stringResource(R.string.action_finish_setup))
                }
            }
        },
    ) { padding ->
        PermissionChecklist(
            state = state,
            onBiometricToggle = viewModel::setBiometricEnabled,
            contentPadding = padding,
        )
    }
}

@Composable
private fun PermissionChecklist(
    state: PermissionsUiState,
    onBiometricToggle: (Boolean) -> Unit,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    val systemScreens = LocalSystemScreenLauncher.current
    val perms = state.permissions
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* refreshed on resume */ }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SetupProgress(perms)
        Text(
            stringResource(R.string.permissions_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionTitle(
            stringResource(
                if (BuildConfig.ACCESSIBILITY_DETECTION) {
                    R.string.permissions_section_detection
                } else {
                    R.string.permissions_section_detection_single
                },
            ),
        )
        if (BuildConfig.ACCESSIBILITY_DETECTION) {
            PermissionCard(
                icon = Icons.Default.Accessibility,
                title = stringResource(R.string.perm_accessibility_title),
                description = stringResource(R.string.perm_accessibility_description),
                granted = perms.accessibility,
                badge = stringResource(R.string.badge_recommended),
                onAction = { systemScreens.launch(SystemIntents.accessibilitySettings()) },
                footer = {
                    // Android 13+ greys out the switch for sideloaded APKs until "restricted settings"
                    // are allowed from the app info screen.
                    if (!perms.accessibility && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Text(
                            stringResource(R.string.perm_restricted_settings_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { systemScreens.launch(SystemIntents.appDetails(context)) }) {
                            Text(stringResource(R.string.action_open_app_info))
                        }
                    }
                },
            )
        }
        PermissionCard(
            icon = Icons.Default.DataUsage,
            title = stringResource(R.string.perm_usage_title),
            description = stringResource(R.string.perm_usage_description),
            granted = perms.usageAccess,
            badge = stringResource(
                if (BuildConfig.ACCESSIBILITY_DETECTION) R.string.badge_alternative else R.string.badge_required,
            ),
            onAction = { systemScreens.launch(SystemIntents.usageAccessSettings()) },
        )

        SectionTitle(stringResource(R.string.permissions_section_display))
        PermissionCard(
            icon = Icons.Default.Layers,
            title = stringResource(R.string.perm_overlay_title),
            description = stringResource(R.string.perm_overlay_description),
            granted = perms.overlay,
            onAction = { systemScreens.launch(SystemIntents.overlaySettings(context)) },
        )

        SectionTitle(stringResource(R.string.permissions_section_reliability))
        PermissionCard(
            icon = Icons.Default.BatteryFull,
            title = stringResource(R.string.perm_battery_title),
            description = stringResource(R.string.perm_battery_description),
            granted = perms.batteryUnrestricted,
            onAction = { systemScreens.launch(SystemIntents.ignoreBatteryOptimizations(context)) },
        )
        if (perms.notificationsRequired) {
            PermissionCard(
                icon = Icons.Default.Notifications,
                title = stringResource(R.string.perm_notifications_title),
                description = stringResource(R.string.perm_notifications_description),
                granted = perms.notifications,
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
        }

        SectionTitle(stringResource(R.string.permissions_section_biometric))
        BiometricCard(
            status = perms.biometric,
            enabled = state.biometricEnabled,
            onToggle = onBiometricToggle,
            onEnroll = { systemScreens.launch(SystemIntents.biometricEnrollment()) },
        )
        Spacer(Modifier.size(8.dp))
    }
}

@Composable
private fun SetupProgress(perms: PermissionSnapshot) {
    val steps = buildList {
        add(perms.canProtect)
        add(perms.overlay || perms.detectionMode == com.adityadixit.applock.system.DetectionMode.ACCESSIBILITY)
        add(perms.batteryUnrestricted)
        if (perms.notificationsRequired) add(perms.notifications)
    }
    val done = steps.count { it }
    val progress by animateFloatAsState(done.toFloat() / steps.size, label = "setupProgress")
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (perms.canProtect) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(
                    if (perms.canProtect) R.string.setup_ready_title else R.string.setup_needed_title,
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                strokeCap = StrokeCap.Round,
            )
            Text(
                stringResource(R.string.setup_progress, done, steps.size),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    onAction: () -> Unit,
    badge: String? = null,
    footer: @Composable () -> Unit = {},
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (granted) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (badge != null) {
                        Text(badge, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
                StatusIcon(granted)
            }
            Text(description, style = MaterialTheme.typography.bodyMedium)
            if (!granted) {
                FilledTonalButton(onClick = onAction) { Text(stringResource(R.string.action_grant)) }
            }
            footer()
        }
    }
}

@Composable
private fun BiometricCard(
    status: BiometricStatus,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onEnroll: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Default.Fingerprint)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.perm_biometric_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = when (status) {
                            BiometricStatus.AVAILABLE -> stringResource(R.string.biometric_status_available)
                            BiometricStatus.NOT_ENROLLED -> stringResource(R.string.biometric_status_not_enrolled)
                            BiometricStatus.UNAVAILABLE -> stringResource(R.string.biometric_status_unavailable)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (status == BiometricStatus.AVAILABLE) {
                    Switch(checked = enabled, onCheckedChange = onToggle)
                }
            }
            if (status == BiometricStatus.NOT_ENROLLED) {
                FilledTonalButton(onClick = onEnroll) { Text(stringResource(R.string.action_enroll_biometric)) }
            }
        }
    }
}

@Composable
private fun StatusIcon(granted: Boolean) {
    if (granted) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = stringResource(R.string.status_granted),
            tint = MaterialTheme.colorScheme.primary,
        )
    } else {
        Icon(
            Icons.Outlined.Circle,
            contentDescription = stringResource(R.string.status_not_granted),
            tint = MaterialTheme.colorScheme.outline,
        )
    }
}
