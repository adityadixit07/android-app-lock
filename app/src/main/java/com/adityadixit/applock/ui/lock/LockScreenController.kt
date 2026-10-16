package com.adityadixit.applock.ui.lock

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.adityadixit.applock.domain.auth.Authenticator
import com.adityadixit.applock.domain.auth.CredentialRules
import com.adityadixit.applock.domain.auth.PatternCodec
import com.adityadixit.applock.domain.model.CredentialType
import com.adityadixit.applock.domain.model.VerifyResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface LockMessage {
    data class WrongCredential(val attemptsBeforeLockout: Int) : LockMessage
    data object PatternTooShort : LockMessage
    data object NoCredential : LockMessage
}

data class LockUiState(
    val credentialType: CredentialType? = null,
    val pinLength: Int = 0,
    val message: LockMessage? = null,
    val lockoutSeconds: Int = 0,
    val verifying: Boolean = false,
    /** Incremented to ask the pattern view to clear its drawing. */
    val patternResetToken: Int = 0,
    /** Incremented on every rejected attempt; drives the shake animation. */
    val errorToken: Int = 0,
) {
    val inputEnabled: Boolean get() = !verifying && lockoutSeconds == 0
}

/**
 * Platform-agnostic state holder for any unlock surface (system overlay or in-app gate).
 * Lives outside ViewModel because the overlay has no ViewModelStore; callers pass a
 * main-thread [scope] tied to the surface lifetime.
 *
 * The PIN digits are kept in a private CharArray, never in Compose state or a String, and
 * wiped right after verification.
 */
class LockScreenController(
    private val authenticator: Authenticator,
    private val scope: CoroutineScope,
    private val onUnlocked: () -> Unit,
) {
    var state by mutableStateOf(LockUiState())
        private set

    private val pinBuffer = CharArray(CredentialRules.MAX_PIN_LENGTH)
    private var pinLength = 0
    private var countdownJob: Job? = null

    init {
        scope.launch {
            authenticator.credentialType.collect { state = state.copy(credentialType = it) }
        }
        scope.launch { startCountdown(authenticator.lockoutRemainingMillis()) }
    }

    fun onDigit(digit: Char) {
        if (!state.inputEnabled || pinLength >= pinBuffer.size || digit !in '0'..'9') return
        pinBuffer[pinLength++] = digit
        state = state.copy(pinLength = pinLength, message = null)
    }

    fun onBackspace() {
        if (!state.inputEnabled || pinLength == 0) return
        pinBuffer[--pinLength] = '\u0000'
        state = state.copy(pinLength = pinLength)
    }

    fun onSubmitPin() {
        if (!state.inputEnabled || pinLength < CredentialRules.MIN_PIN_LENGTH) return
        val secret = pinBuffer.copyOf(pinLength)
        clearPin()
        verify(secret)
    }

    fun onPatternDrawn(cells: List<Int>) {
        if (!state.inputEnabled) return
        if (cells.size < CredentialRules.MIN_PATTERN_CELLS) {
            state = state.copy(
                message = LockMessage.PatternTooShort,
                patternResetToken = state.patternResetToken + 1,
                errorToken = state.errorToken + 1,
            )
            return
        }
        verify(PatternCodec.encode(cells))
    }

    private fun verify(secret: CharArray) {
        state = state.copy(verifying = true, message = null)
        scope.launch {
            val result = try {
                authenticator.verify(secret)
            } finally {
                secret.fill('\u0000')
            }
            state = state.copy(verifying = false, patternResetToken = state.patternResetToken + 1)
            when (result) {
                VerifyResult.Success -> onUnlocked()
                is VerifyResult.Failure -> state = state.copy(
                    message = LockMessage.WrongCredential(result.attemptsBeforeLockout),
                    errorToken = state.errorToken + 1,
                )
                is VerifyResult.LockedOut -> {
                    state = state.copy(errorToken = state.errorToken + 1)
                    startCountdown(result.remainingMillis)
                }
                VerifyResult.NoCredential -> state = state.copy(message = LockMessage.NoCredential)
            }
        }
    }

    private fun startCountdown(remainingMillis: Long) {
        countdownJob?.cancel()
        if (remainingMillis <= 0) return
        countdownJob = scope.launch {
            var seconds = ((remainingMillis + 999) / 1000).toInt()
            while (seconds > 0) {
                state = state.copy(lockoutSeconds = seconds, message = null)
                delay(1_000)
                seconds--
            }
            state = state.copy(lockoutSeconds = 0)
        }
    }

    private fun clearPin() {
        pinBuffer.fill('\u0000')
        pinLength = 0
        state = state.copy(pinLength = 0)
    }
}
