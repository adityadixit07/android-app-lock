package com.adityadixit.applock.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.adityadixit.applock.R
import com.adityadixit.applock.ui.credential.CreateCredentialScreen
import com.adityadixit.applock.ui.permissions.PermissionsScreen

private enum class OnboardingStep { WELCOME, CREDENTIAL, PERMISSIONS }

/**
 * First-run flow. Order matters: the credential is created before any permission is granted,
 * so the moment detection turns on there is already a way to unlock.
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    var step by rememberSaveable { mutableStateOf(OnboardingStep.WELCOME) }

    // Once the credential is saved there is no going back: it is already active.
    BackHandler(enabled = step == OnboardingStep.CREDENTIAL) {
        step = OnboardingStep.WELCOME
    }

    when (step) {
        OnboardingStep.WELCOME -> WelcomeStep(onStart = { step = OnboardingStep.CREDENTIAL })
        OnboardingStep.CREDENTIAL -> CreateCredentialScreen(
            onDone = { step = OnboardingStep.PERMISSIONS },
            onBack = { step = OnboardingStep.WELCOME },
        )
        OnboardingStep.PERMISSIONS -> PermissionsScreen(onContinue = onFinished)
    }
}

@Composable
private fun WelcomeStep(onStart: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(48.dp))
            Icon(
                Icons.Default.Shield,
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.welcome_body),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            listOf(
                R.string.welcome_point_no_ads,
                R.string.welcome_point_offline,
                R.string.welcome_point_private,
            ).forEach { point ->
                Text(
                    "•  " + stringResource(point),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_get_started))
            }
        }
    }
}
