package com.adityadixit.applock.system

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class BiometricStatus { AVAILABLE, NOT_ENROLLED, UNAVAILABLE }

/**
 * Only Class 3 (BIOMETRIC_STRONG) sensors are accepted: weak face unlock that can be fooled
 * by a photo is not good enough to guard private apps.
 */
@Singleton
class BiometricAvailability @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun status(): BiometricStatus =
        when (BiometricManager.from(context).canAuthenticate(AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NOT_ENROLLED
            else -> BiometricStatus.UNAVAILABLE
        }

    fun isAvailable(): Boolean = status() == BiometricStatus.AVAILABLE

    companion object {
        const val AUTHENTICATORS = BIOMETRIC_STRONG
    }
}
