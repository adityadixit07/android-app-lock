package com.adityadixit.applock.ui.gate

import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.adityadixit.applock.R
import com.adityadixit.applock.system.BiometricAvailability
import com.adityadixit.applock.ui.lock.LockScreen
import com.adityadixit.applock.ui.lock.LockScreenController

/** PIN gate protecting App Lock's own settings. */
@Composable
fun GateScreen(
    controller: LockScreenController,
    biometricEnabled: Boolean,
    onBiometricSuccess: () -> Unit,
) {
    val promptBiometric = rememberBiometricPrompt(onSuccess = onBiometricSuccess)
    LaunchedEffect(biometricEnabled) {
        if (biometricEnabled) promptBiometric()
    }
    LockScreen(
        state = controller.state,
        title = stringResource(R.string.gate_title),
        appIcon = null,
        showBiometric = biometricEnabled,
        onBiometric = promptBiometric,
        onDigit = controller::onDigit,
        onBackspace = controller::onBackspace,
        onSubmitPin = controller::onSubmitPin,
        onPatternDrawn = controller::onPatternDrawn,
    )
}

/** BiometricPrompt bound to the hosting FragmentActivity. Returns a function that shows it. */
@Composable
fun rememberBiometricPrompt(onSuccess: () -> Unit): () -> Unit {
    val activity = LocalContext.current as FragmentActivity
    val currentOnSuccess by rememberUpdatedState(onSuccess)
    val title = stringResource(R.string.gate_biometric_title)
    val negative = stringResource(R.string.biometric_use_credential)
    return remember(activity) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    currentOnSuccess()
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setNegativeButtonText(negative)
            .setAllowedAuthenticators(BiometricAvailability.AUTHENTICATORS)
            .setConfirmationRequired(false)
            .build()
        return@remember { prompt.authenticate(info) }
    }
}
