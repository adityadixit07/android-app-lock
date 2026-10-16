package com.adityadixit.applock.ui.credential

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adityadixit.applock.R
import com.adityadixit.applock.domain.auth.CredentialRules
import com.adityadixit.applock.domain.model.CredentialType
import com.adityadixit.applock.ui.lock.PatternLock
import com.adityadixit.applock.ui.lock.PinDots
import com.adityadixit.applock.ui.lock.PinPad

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCredentialScreen(
    onDone: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: CreateCredentialViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.create_credential_title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.step == CredentialStep.ENTER) {
                TypeSelector(selected = state.type, onSelect = viewModel::selectType)
            }
            Text(
                text = instruction(state),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            state.error?.let { error ->
                Text(
                    text = when (error) {
                        CreateCredentialError.PATTERN_TOO_SHORT ->
                            stringResource(R.string.pattern_too_short, CredentialRules.MIN_PATTERN_CELLS)
                        CreateCredentialError.MISMATCH -> stringResource(R.string.credential_mismatch)
                        CreateCredentialError.SAVE_FAILED -> stringResource(R.string.credential_save_failed)
                    },
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(8.dp))
            if (state.saving) {
                CircularProgressIndicator()
            } else {
                when (state.type) {
                    CredentialType.PIN -> {
                        PinDots(length = state.pinLength, isError = false)
                        PinPad(
                            enabled = true,
                            onDigit = viewModel::onDigit,
                            onBackspace = viewModel::onBackspace,
                            onSubmit = viewModel::onSubmitPin,
                            submitEnabled = state.pinLength >= CredentialRules.MIN_PIN_LENGTH,
                            submitLabel = stringResource(
                                if (state.step == CredentialStep.ENTER) R.string.action_continue else R.string.action_confirm,
                            ),
                        )
                    }
                    CredentialType.PATTERN -> PatternLock(
                        enabled = true,
                        isError = state.error != null,
                        resetToken = state.patternResetToken,
                        onPatternComplete = viewModel::onPatternDrawn,
                    )
                }
            }
        }
    }
}

@Composable
private fun instruction(state: CreateCredentialUiState): String = when (state.type) {
    CredentialType.PIN -> if (state.step == CredentialStep.ENTER) {
        stringResource(R.string.create_pin_instruction, CredentialRules.MIN_PIN_LENGTH, CredentialRules.MAX_PIN_LENGTH)
    } else {
        stringResource(R.string.confirm_pin_instruction)
    }
    CredentialType.PATTERN -> if (state.step == CredentialStep.ENTER) {
        stringResource(R.string.create_pattern_instruction, CredentialRules.MIN_PATTERN_CELLS)
    } else {
        stringResource(R.string.confirm_pattern_instruction)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeSelector(selected: CredentialType, onSelect: (CredentialType) -> Unit) {
    val options = listOf(
        CredentialType.PIN to R.string.credential_type_pin,
        CredentialType.PATTERN to R.string.credential_type_pattern,
    )
    SingleChoiceSegmentedButtonRow {
        options.forEachIndexed { index, (type, label) ->
            SegmentedButton(
                selected = selected == type,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(stringResource(label))
            }
        }
    }
}
