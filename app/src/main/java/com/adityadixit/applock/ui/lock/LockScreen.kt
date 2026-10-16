package com.adityadixit.applock.ui.lock

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.adityadixit.applock.R
import com.adityadixit.applock.domain.auth.CredentialRules
import com.adityadixit.applock.domain.model.CredentialType
import kotlin.math.roundToInt

/**
 * Full-screen unlock UI shared by the system overlay and the in-app gate.
 * Stateless: everything comes from [state] and is reported through callbacks.
 *
 * Always painted on an opaque base layer first — the gradient on top is translucent and must
 * never let the protected app show through.
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
    val colors = MaterialTheme.colorScheme
    val shake = remember { Animatable(0f) }
    LaunchedEffect(state.errorToken) {
        if (state.errorToken == 0) return@LaunchedEffect
        listOf(-28f, 24f, -18f, 12f, -6f, 0f).forEach { shake.animateTo(it, tween(durationMillis = 45)) }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(colors.surface)
            .background(
                Brush.verticalGradient(
                    0f to colors.primaryContainer.copy(alpha = 0.65f),
                    0.45f to colors.surface,
                    1f to colors.surfaceContainer,
                ),
            ),
    ) {
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
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Header(title = title, appIcon = appIcon)
                Spacer(Modifier.height(12.dp))
                StatusLine(state)
                Spacer(Modifier.height(28.dp))

                val isError = state.message is LockMessage.WrongCredential || state.message == LockMessage.PatternTooShort
                Column(
                    Modifier.offset { IntOffset(shake.value.roundToInt(), 0) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (state.credentialType) {
                        CredentialType.PIN -> {
                            PinDots(length = state.pinLength, isError = isError)
                            Spacer(Modifier.height(32.dp))
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
                                Spacer(Modifier.height(24.dp))
                                BiometricButton(onBiometric)
                            }
                        }
                        null -> CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(title: String, appIcon: ImageBitmap?) {
    Box(contentAlignment = Alignment.BottomEnd) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = 8.dp,
            modifier = Modifier.size(96.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (appIcon != null) {
                    Image(bitmap = appIcon, contentDescription = null, modifier = Modifier.size(68.dp))
                } else {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        // Small lock badge on the icon corner.
        Box(
            Modifier
                .offset(x = 6.dp, y = 6.dp)
                .size(32.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .padding(7.dp),
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
    Spacer(Modifier.height(24.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
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
            val suffix = if (left in 1..2) " " + pluralStringResource(R.plurals.attempts_left, left, left) else ""
            base + suffix to true
        }
        state.message == LockMessage.PatternTooShort ->
            stringResource(R.string.pattern_too_short, CredentialRules.MIN_PATTERN_CELLS) to true
        state.message == LockMessage.NoCredential ->
            stringResource(R.string.no_credential_configured) to true
        state.credentialType == CredentialType.PATTERN -> stringResource(R.string.draw_pattern) to false
        else -> stringResource(R.string.enter_pin) to false
    }
    AnimatedContent(
        targetState = text to isError,
        transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
        label = "lockStatus",
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) { (message, error) ->
        if (error) {
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.errorContainer) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.lockoutSeconds > 0) {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun BiometricButton(onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(64.dp)) {
        Icon(
            Icons.Default.Fingerprint,
            contentDescription = stringResource(R.string.action_use_biometric),
            modifier = Modifier.size(34.dp),
        )
    }
}
