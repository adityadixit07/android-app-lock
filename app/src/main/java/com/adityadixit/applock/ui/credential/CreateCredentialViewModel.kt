package com.adityadixit.applock.ui.credential

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adityadixit.applock.domain.auth.CredentialRules
import com.adityadixit.applock.domain.auth.PatternCodec
import com.adityadixit.applock.domain.model.CredentialType
import com.adityadixit.applock.domain.repository.CredentialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CredentialStep { ENTER, CONFIRM }

enum class CreateCredentialError { PATTERN_TOO_SHORT, MISMATCH, SAVE_FAILED }

data class CreateCredentialUiState(
    val type: CredentialType = CredentialType.PIN,
    val step: CredentialStep = CredentialStep.ENTER,
    val pinLength: Int = 0,
    val error: CreateCredentialError? = null,
    val saving: Boolean = false,
    val patternResetToken: Int = 0,
    val saved: Boolean = false,
)

/** Two-step (enter + confirm) PIN/pattern creation. Secrets live only in wiped CharArrays. */
@HiltViewModel
class CreateCredentialViewModel @Inject constructor(
    private val credentialRepository: CredentialRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateCredentialUiState())
    val state: StateFlow<CreateCredentialUiState> = _state.asStateFlow()

    private val pinBuffer = CharArray(CredentialRules.MAX_PIN_LENGTH)
    private var pinLength = 0
    private var firstEntry: CharArray? = null

    fun selectType(type: CredentialType) {
        if (_state.value.step != CredentialStep.ENTER || _state.value.saving) return
        clearSecrets()
        _state.value = CreateCredentialUiState(type = type)
    }

    fun onDigit(digit: Char) {
        if (_state.value.saving || pinLength >= pinBuffer.size) return
        pinBuffer[pinLength++] = digit
        _state.update { it.copy(pinLength = pinLength, error = null) }
    }

    fun onBackspace() {
        if (pinLength == 0) return
        pinBuffer[--pinLength] = '\u0000'
        _state.update { it.copy(pinLength = pinLength) }
    }

    fun onSubmitPin() {
        if (_state.value.saving || pinLength < CredentialRules.MIN_PIN_LENGTH) return
        val entry = pinBuffer.copyOf(pinLength)
        pinBuffer.fill('\u0000')
        pinLength = 0
        _state.update { it.copy(pinLength = 0) }
        handleEntry(entry)
    }

    fun onPatternDrawn(cells: List<Int>) {
        if (_state.value.saving) return
        if (!CredentialRules.isValidPattern(cells)) {
            _state.update {
                it.copy(
                    error = CreateCredentialError.PATTERN_TOO_SHORT,
                    patternResetToken = it.patternResetToken + 1,
                )
            }
            return
        }
        _state.update { it.copy(patternResetToken = it.patternResetToken + 1, error = null) }
        handleEntry(PatternCodec.encode(cells))
    }

    private fun handleEntry(entry: CharArray) {
        val first = firstEntry
        if (first == null) {
            firstEntry = entry
            _state.update { it.copy(step = CredentialStep.CONFIRM, error = null) }
            return
        }
        if (first.contentEquals(entry)) {
            save(entry)
        } else {
            entry.fill('\u0000')
            clearSecrets()
            _state.update { it.copy(step = CredentialStep.ENTER, error = CreateCredentialError.MISMATCH) }
        }
    }

    private fun save(entry: CharArray) {
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                credentialRepository.save(_state.value.type, entry)
                _state.update { it.copy(saving = false, saved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Keystore can fail on some devices; let the user retry instead of crashing.
                _state.update {
                    it.copy(saving = false, step = CredentialStep.ENTER, error = CreateCredentialError.SAVE_FAILED)
                }
            } finally {
                entry.fill('\u0000')
                clearSecrets()
            }
        }
    }

    private fun clearSecrets() {
        firstEntry?.fill('\u0000')
        firstEntry = null
        pinBuffer.fill('\u0000')
        pinLength = 0
    }

    override fun onCleared() = clearSecrets()
}
