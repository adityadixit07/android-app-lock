package com.adityadixit.applock.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.adityadixit.applock.R
import com.adityadixit.applock.service.LockServiceLauncher
import com.adityadixit.applock.ui.apps.AppListScreen
import com.adityadixit.applock.ui.credential.CreateCredentialScreen
import com.adityadixit.applock.ui.gate.GateScreen
import com.adityadixit.applock.ui.onboarding.OnboardingScreen
import com.adityadixit.applock.ui.permissions.PermissionsScreen
import com.adityadixit.applock.ui.settings.SettingsScreen

@Composable
fun AppRoot(viewModel: MainViewModel) {
    val state by viewModel.rootState.collectAsStateWithLifecycle()
    when (val current = state) {
        RootState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        RootState.NeedsSetup -> OnboardingScreen(onFinished = viewModel::onOnboardingFinished)
        is RootState.Locked -> GateScreen(
            controller = viewModel.gateController,
            biometricEnabled = current.biometricEnabled,
            onBiometricSuccess = viewModel::onBiometricSuccess,
        )
        RootState.Ready -> MainScaffold()
    }
}

private object Routes {
    const val APPS = "apps"
    const val SETTINGS = "settings"
    const val PERMISSIONS = "permissions"
    const val CHANGE_CREDENTIAL = "change_credential"
}

private data class TopLevelDestination(val route: String, val labelRes: Int, val icon: ImageVector)

private val topLevelDestinations = listOf(
    TopLevelDestination(Routes.APPS, R.string.nav_apps, Icons.Default.Apps),
    TopLevelDestination(Routes.SETTINGS, R.string.nav_settings, Icons.Default.Settings),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MainScaffold() {
    val context = LocalContext.current
    // Keep the right detection host running whenever the user is in the app.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { LockServiceLauncher.ensureRunning(context) }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        // Each screen draws its own top app bar and handles the status-bar inset itself.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (topLevelDestinations.any { it.route == currentRoute }) {
                NavigationBar {
                    topLevelDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.APPS,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Routes.APPS) {
                AppListScreen(onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) },
                    onChangeCredential = { navController.navigate(Routes.CHANGE_CREDENTIAL) },
                )
            }
            composable(Routes.PERMISSIONS) {
                PermissionsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.CHANGE_CREDENTIAL) {
                CreateCredentialScreen(
                    onDone = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
