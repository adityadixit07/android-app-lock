package com.adityadixit.applock.ui.lock

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.adityadixit.applock.R
import com.adityadixit.applock.domain.auth.CredentialRules
import com.adityadixit.applock.domain.model.CredentialType

/**
 * Full-screen, opaque unlock UI shared by the system overlay and the in-app gate.
 * Stateless: everything comes from [state] and is reported through callbacks.
 */
@Composable
fun LockScreen(
    state: LockUiState,
    title: String,
    appIcon: ImageBitmap?,
    showBiometric: Boolean,
    onBiometric: () -> Unit,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSubmitPin: () -> Unit,
    onPatternDrawn: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
            if (onClose != null) {
                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Header(title = title, appIcon = appIcon)
                Spacer(Modifier.height(16.dp))
                StatusLine(state)
                Spacer(Modifier.height(24.dp))

                val isError = state.message is LockMessage.WrongCredential
                when (state.credentialType) {
                    CredentialType.PIN -> {
                        PinDots(length = state.pinLength, isError = isError)
                        Spacer(Modifier.height(24.dp))
                        PinPad(
                            enabled = state.inputEnabled,
                            onDigit = onDigit,
                            onBackspace = onBackspace,
                            onSubmit = onSubmitPin,
                            submitEnabled = state.pinLength >= CredentialRules.MIN_PIN_LENGTH,
                            bottomStart = { if (showBiometric) BiometricButton(onBiometric) },
                        )
                    }
                    CredentialType.PATTERN -> {
                        PatternLock(
                            enabled = state.inputEnabled,
                            isError = isError,
                            resetToken = state.patternResetToken,
                            onPatternComplete = onPatternDrawn,
                        )
                        if (showBiometric) {
                            Spacer(Modifier.height(16.dp))
                            BiometricButton(onBiometric)
                        }
                    }
                    null -> CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun Header(title: String, appIcon: ImageBitmap?) {
    if (appIcon != null) {
        Image(bitmap = appIcon, contentDescription = null, modifier = Modifier.size(64.dp))
    } else {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
    Spacer(Modifier.height(16.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun StatusLine(state: LockUiState) {
    val (text, isError) = when {
        state.lockoutSeconds > 0 ->
            pluralStringResource(R.plurals.lockout_seconds, state.lockoutSeconds, state.lockoutSeconds) to true
        state.message is LockMessage.WrongCredential -> {
            val left = state.message.attemptsBeforeLockout
            val base = stringResource(R.string.wrong_credential)
            val suffix = if (left in 1..2) {
                " " + pluralStringResource(R.plurals.attempts_left, left, left)
            } else {
                ""
            }
            base + suffix to true
        }
        state.message == LockMessage.PatternTooShort ->
            stringResource(R.string.pattern_too_short, CredentialRules.MIN_PATTERN_CELLS) to true
        state.message == LockMessage.NoCredential ->
            stringResource(R.string.no_credential_configured) to true
        state.credentialType == CredentialType.PATTERN -> stringResource(R.string.draw_pattern) to false
        else -> stringResource(R.string.enter_pin) to false
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun BiometricButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(64.dp)) {
        Icon(
            Icons.Default.Fingerprint,
            contentDescription = stringResource(R.string.action_use_biometric),
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
