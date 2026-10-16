package com.adityadixit.applock.ui.lock

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adityadixit.applock.R
import com.adityadixit.applock.domain.auth.CredentialRules

/**
 * PIN progress: at least [CredentialRules.MIN_PIN_LENGTH] outlined slots that fill with a
 * springy animation as digits are typed (never showing the digits themselves).
 */
@Composable
fun PinDots(length: Int, isError: Boolean, modifier: Modifier = Modifier) {
    val active = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val outline = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
    val description = stringResource(R.string.pin_digits_entered, length)
    val slots = maxOf(CredentialRules.MIN_PIN_LENGTH, length)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(slots) { index ->
            val filled = index < length
            val dotSize by animateDpAsState(
                targetValue = if (filled) 16.dp else 12.dp,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "pinDotSize",
            )
            val fill by animateColorAsState(if (filled) active else Color.Transparent, label = "pinDotFill")
            Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(dotSize)
                        .clip(CircleShape)
                        .background(fill)
                        .border(1.5.dp, if (filled) active else outline, CircleShape),
                )
            }
        }
    }
}

/**
 * Dialer-style numeric keypad. Digits are emitted one at a time so callers never hold the
 * PIN as a String. [bottomStart] hosts an optional action (e.g. the biometric button).
 */
@Composable
fun PinPad(
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    submitEnabled: Boolean,
    modifier: Modifier = Modifier,
    submitLabel: String = stringResource(R.string.action_unlock),
    bottomStart: @Composable () -> Unit = {},
) {
    val haptics = LocalHapticFeedback.current
    val press: (Char) -> Unit = { digit ->
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onDigit(digit)
    }
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { digit -> DigitKey(digit, enabled) { press(digit) } }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(KeySize), contentAlignment = Alignment.Center) { bottomStart() }
            DigitKey('0', enabled) { press('0') }
            Box(Modifier.size(KeySize), contentAlignment = Alignment.Center) {
                IconButton(onClick = onBackspace, enabled = enabled, modifier = Modifier.size(KeySize)) {
                    Icon(
                        Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = stringResource(R.string.action_delete_digit),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        val canSubmit = enabled && submitEnabled
        Surface(
            onClick = onSubmit,
            enabled = canSubmit,
            shape = CircleShape,
            color = if (canSubmit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = if (canSubmit) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            shadowElevation = if (canSubmit) 4.dp else 0.dp,
            modifier = Modifier.size(width = KeySize * 3 + 48.dp, height = 56.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    submitLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.size(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
    }
}

@Composable
private fun DigitKey(digit: Char, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
        contentColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
        modifier = Modifier.size(KeySize),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(digit.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
            val letters = KeyLetters[digit]
            if (letters != null) {
                Text(
                    letters,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val KeySize = 76.dp

private val KeyLetters = mapOf(
    '2' to "ABC", '3' to "DEF", '4' to "GHI", '5' to "JKL",
    '6' to "MNO", '7' to "PQRS", '8' to "TUV", '9' to "WXYZ",
)
