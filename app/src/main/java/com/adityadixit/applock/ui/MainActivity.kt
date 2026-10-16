package com.adityadixit.applock.ui

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.fragment.app.FragmentActivity
import com.adityadixit.applock.system.SystemIntents
import com.adityadixit.applock.ui.theme.AppLockTheme
import dagger.hilt.android.AndroidEntryPoint

/** Single activity hosting onboarding, the PIN gate and the management screens. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // The list of locked apps is private: keep it out of screenshots and the recents thumbnail.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        setContent {
            AppLockTheme {
                val systemScreens = remember {
                    SystemScreenLauncher { intent: Intent ->
                        viewModel.markExternalNavigation()
                        SystemIntents.launch(this, intent)
                    }
                }
                CompositionLocalProvider(LocalSystemScreenLauncher provides systemScreens) {
                    AppRoot(viewModel)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppStarted()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) viewModel.onAppStopped()
    }
}
