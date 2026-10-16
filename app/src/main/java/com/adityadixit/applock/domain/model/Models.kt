package com.adityadixit.applock.domain.model

/** An app the user can choose to lock. */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean,
)

enum class CredentialType { PIN, PATTERN }

/** How long an unlocked app stays unlocked after the user leaves it. */
enum class RelockPolicy(val graceMillis: Long) {
    IMMEDIATELY(0L),
    AFTER_1_MINUTE(60_000L),
    AFTER_5_MINUTES(5 * 60_000L),
}

data class LockSettings(
    /** Master switch. When false nothing is locked, but the configuration is kept. */
    val lockingEnabled: Boolean = true,
    val relockPolicy: RelockPolicy = RelockPolicy.IMMEDIATELY,
    val biometricEnabled: Boolean = false,
    val onboardingCompleted: Boolean = false,
)

/** Persisted brute-force protection state. */
data class AuthAttemptState(
    val failedAttempts: Int = 0,
    /** Wall-clock deadline (ms); 0 when not locked out. */
    val lockoutUntilMillis: Long = 0L,
)

sealed interface VerifyResult {
    data object Success : VerifyResult
    data class Failure(val attemptsBeforeLockout: Int) : VerifyResult
    data class LockedOut(val remainingMillis: Long) : VerifyResult
    /** No credential has been set up (or it could not be decrypted). */
    data object NoCredential : VerifyResult
}
