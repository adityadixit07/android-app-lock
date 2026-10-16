package com.adityadixit.applock.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adityadixit.applock.R
import com.adityadixit.applock.data.apps.AppIconLoader
import com.adityadixit.applock.system.DetectionMode
import com.adityadixit.applock.ui.components.AppIcon
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    onOpenPermissions: () -> Unit,
    viewModel: AppListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val iconLoader = rememberAppIconLoader()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "status") {
                ProtectionCard(state.detectionMode, state.lockedCount, onOpenPermissions)
            }
            item(key = "search") {
                SearchField(query = viewModel.query, onQueryChange = viewModel::onQueryChange)
            }
            item(key = "filters") {
                Row(Modifier.padding(horizontal = 16.dp)) {
                    FilterChip(
                        selected = state.showSystemApps,
                        onClick = { viewModel.onShowSystemAppsChange(!state.showSystemApps) },
                        label = { Text(stringResource(R.string.show_system_apps)) },
                    )
                }
            }
            when {
                state.loading -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.isEmpty -> item(key = "empty") {
                    Text(
                        stringResource(R.string.no_apps_found),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(48.dp),
                    )
                }
                else -> {
                    appSection(
                        key = "locked",
                        title = { stringResource(R.string.section_locked_apps, state.lockedRows.size) },
                        rows = state.lockedRows,
                        iconLoader = iconLoader,
                        onToggle = viewModel::setLocked,
                    )
                    appSection(
                        key = "other",
                        title = { stringResource(R.string.section_all_apps) },
                        rows = state.otherRows,
                        iconLoader = iconLoader,
                        onToggle = viewModel::setLocked,
                    )
                }
            }
        }
    }
}

private fun LazyListScope.appSection(
    key: String,
    title: @Composable () -> String,
    rows: List<AppRow>,
    iconLoader: AppIconLoader,
    onToggle: (packageName: String, locked: Boolean) -> Unit,
) {
    if (rows.isEmpty()) return
    item(key = "header_$key") {
        Text(
            title(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .animateItem()
                .padding(start = 28.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
        )
    }
    items(rows, key = { it.app.packageName }) { row ->
        AppRowItem(
            row = row,
            iconLoader = iconLoader,
            onToggle = { locked -> onToggle(row.app.packageName, locked) },
            modifier = Modifier.animateItem(),
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ProtectionCard(mode: DetectionMode, lockedCount: Int, onFix: () -> Unit) {
    val active = mode != DetectionMode.NONE
    val colors = MaterialTheme.colorScheme
    val gradient = if (active) {
        Brush.linearGradient(listOf(colors.primary, colors.tertiary))
    } else {
        Brush.linearGradient(listOf(colors.error, colors.error.copy(alpha = 0.85f)))
    }
    val onGradient = if (active) colors.onPrimary else colors.onError
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            Modifier
                .background(gradient)
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (active) Icons.Default.GppGood else Icons.Default.GppMaybe,
                    contentDescription = null,
                    tint = onGradient,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(if (active) R.string.protection_active_title else R.string.protection_inactive_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = onGradient,
                    )
                    Text(
                        stringResource(
                            when (mode) {
                                DetectionMode.ACCESSIBILITY -> R.string.protection_mode_instant
                                DetectionMode.USAGE_STATS -> R.string.protection_mode_usage
                                DetectionMode.NONE -> R.string.protection_inactive_body
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = onGradient.copy(alpha = 0.9f),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = onGradient, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    pluralStringResource(R.plurals.apps_locked_count, lockedCount, lockedCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = onGradient,
                    modifier = Modifier.weight(1f),
                )
                if (!active) {
                    Button(
                        onClick = onFix,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.onError, contentColor = colors.error),
                    ) {
                        Text(stringResource(R.string.action_fix))
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text(stringResource(R.string.search_apps)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.action_clear_search))
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    )
}

@Composable
private fun AppRowItem(
    row: AppRow,
    iconLoader: AppIconLoader,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lockIcon: (@Composable () -> Unit)? = if (row.locked) {
        { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
    } else {
        null
    }
    ListItem(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .toggleable(value = row.locked, role = Role.Switch, onValueChange = onToggle),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { AppIcon(row.app.packageName, iconLoader, size = 44.dp) },
        headlineContent = {
            Text(row.app.label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        },
        supportingContent = {
            Text(
                stringResource(if (row.locked) R.string.app_status_locked else R.string.app_status_unlocked),
                style = MaterialTheme.typography.bodySmall,
                color = if (row.locked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        // Null handler: the whole row is the toggle target (bigger tap area, one a11y node).
        trailingContent = { Switch(checked = row.locked, onCheckedChange = null, thumbContent = lockIcon) },
    )
}

/** Icon loading is a cross-cutting singleton, not screen state; fetch it via a Hilt entry point. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppIconLoaderEntryPoint {
    fun appIconLoader(): AppIconLoader
}

@Composable
fun rememberAppIconLoader(): AppIconLoader {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        EntryPointAccessors.fromApplication(context, AppIconLoaderEntryPoint::class.java).appIconLoader()
    }
}
