package com.adityadixit.applock.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.adityadixit.applock.R

/** Row of dots showing how many digits were typed (never the digits themselves). */
@Composable
fun PinDots(length: Int, isError: Boolean, modifier: Modifier = Modifier) {
    val filled = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val description = stringResource(R.string.pin_digits_entered, length)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(length) {
            Box(Modifier.size(14.dp).background(filled, CircleShape))
        }
    }
}

/**
 * Numeric keypad. Digits are emitted one at a time so callers never hold the PIN as a String.
 * [bottomStart] hosts an optional extra action (e.g. biometric button).
 */
@Composable
fun PinPad(
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    submitEnabled: Boolean,
    modifier: Modifier = Modifier,
    bottomStart: @Composable () -> Unit = { Spacer(Modifier.size(KeySize)) },
) {
    val haptics = LocalHapticFeedback.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { digit ->
                    DigitKey(digit, enabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDigit(digit)
                    }
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(KeySize), contentAlignment = Alignment.Center) { bottomStart() }
            DigitKey('0', enabled) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onDigit('0')
            }
            Box(Modifier.size(KeySize), contentAlignment = Alignment.Center) {
                IconButton(onClick = onBackspace, enabled = enabled) {
                    Icon(
                        Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = stringResource(R.string.action_delete_digit),
                    )
                }
            }
        }
        Row(Modifier.width(KeySize * 3 + 40.dp), horizontalArrangement = Arrangement.Center) {
            Surface(
                onClick = onSubmit,
                enabled = enabled && submitEnabled,
                shape = CircleShape,
                color = if (enabled && submitEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.size(KeySize),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = stringResource(R.string.action_confirm),
                        tint = if (enabled && submitEnabled) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
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
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (enabled) MaterialTheme.colorScheme.onSurface else Color.Gray,
        modifier = Modifier.size(KeySize),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(digit.toString(), style = MaterialTheme.typography.headlineMedium)
        }
    }
}

private val KeySize = 72.dp
