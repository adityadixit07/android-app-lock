package com.adityadixit.applock.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import com.adityadixit.applock.ui.components.FeatureRow
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
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.surface)
            .background(
                Brush.verticalGradient(
                    0f to colors.primaryContainer,
                    0.5f to colors.surface,
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(40.dp))
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(168.dp)
                        .background(colors.primary.copy(alpha = 0.12f), CircleShape),
                )
                Box(
                    Modifier
                        .size(124.dp)
                        .background(
                            Brush.linearGradient(listOf(colors.primary, colors.tertiary)),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = colors.onPrimary,
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
            Text(
                stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.welcome_body),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                FeatureRow(
                    Icons.Default.Block,
                    stringResource(R.string.welcome_point_no_ads),
                    stringResource(R.string.welcome_point_no_ads_body),
                )
                FeatureRow(
                    Icons.Default.CloudOff,
                    stringResource(R.string.welcome_point_offline),
                    stringResource(R.string.welcome_point_offline_body),
                )
                FeatureRow(
                    Icons.Default.VisibilityOff,
                    stringResource(R.string.welcome_point_private),
                    stringResource(R.string.welcome_point_private_body),
                )
            }
            Spacer(Modifier.height(40.dp))
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(stringResource(R.string.action_get_started), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
