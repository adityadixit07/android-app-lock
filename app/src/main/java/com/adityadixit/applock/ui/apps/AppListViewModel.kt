package com.adityadixit.applock.ui.apps

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adityadixit.applock.core.DefaultDispatcher
import com.adityadixit.applock.domain.model.InstalledApp
import com.adityadixit.applock.domain.repository.InstalledAppsRepository
import com.adityadixit.applock.domain.repository.LockedAppsRepository
import com.adityadixit.applock.system.DetectionMode
import com.adityadixit.applock.system.PermissionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppRow(val app: InstalledApp, val locked: Boolean)

data class AppListUiState(
    val loading: Boolean = true,
    val showSystemApps: Boolean = false,
    val rows: List<AppRow> = emptyList(),
    val lockedCount: Int = 0,
    val protectionActive: Boolean = true,
)

@HiltViewModel
class AppListViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val installedAppsRepository: InstalledAppsRepository,
    private val lockedAppsRepository: LockedAppsRepository,
    @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val installedApps = MutableStateFlow<List<InstalledApp>?>(null)
    /**
     * Held as synchronous Compose state (not a flow hop) so the TextField never loses keystrokes;
     * the filtering pipeline observes it through snapshotFlow.
     */
    var query by mutableStateOf("")
        private set
    private val showSystemApps = MutableStateFlow(false)
    private val protectionActive = MutableStateFlow(true)

    private data class Filters(val query: String, val showSystem: Boolean, val protectionActive: Boolean)

    val state: StateFlow<AppListUiState> = combine(
        installedApps,
        lockedAppsRepository.lockedPackages,
        combine(snapshotFlow { query }, showSystemApps, protectionActive) { q, system, active ->
            Filters(q, system, active)
        },
    ) { apps, locked, filters ->
        val needle = filters.query.trim()
        val rows = apps.orEmpty()
            .asSequence()
            // Locked system apps stay visible so they can always be unlocked again.
            .filter { filters.showSystem || !it.isSystemApp || it.packageName in locked }
            .filter {
                needle.isEmpty() ||
                    it.label.contains(needle, ignoreCase = true) ||
                    it.packageName.contains(needle, ignoreCase = true)
            }
            .map { AppRow(it, it.packageName in locked) }
            .toList()
        AppListUiState(
            loading = apps == null,
            showSystemApps = filters.showSystem,
            rows = rows,
            lockedCount = locked.size,
            protectionActive = filters.protectionActive,
        )
    }
        .flowOn(defaultDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppListUiState())

    /** Reloads installed apps and protection status; called on every resume. */
    fun refresh() {
        protectionActive.value = PermissionStatus.detectionMode(context) != DetectionMode.NONE
        viewModelScope.launch { installedApps.value = installedAppsRepository.getLaunchableApps() }
    }

    fun onQueryChange(value: String) {
        query = value
    }

    fun onShowSystemAppsChange(show: Boolean) {
        showSystemApps.value = show
    }

    fun setLocked(packageName: String, locked: Boolean) {
        viewModelScope.launch { lockedAppsRepository.setLocked(packageName, locked) }
    }
}
