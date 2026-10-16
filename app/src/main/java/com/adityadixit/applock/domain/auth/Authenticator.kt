package com.adityadixit.applock.domain.auth

import com.adityadixit.applock.core.TimeSource
import com.adityadixit.applock.domain.model.AuthAttemptState
import com.adityadixit.applock.domain.model.CredentialType
import com.adityadixit.applock.domain.model.VerifyResult
import com.adityadixit.applock.domain.repository.AuthAttemptRepository
import com.adityadixit.applock.domain.repository.CredentialRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verifies the PIN/pattern and enforces [LockoutPolicy]. Every unlock surface (overlay,
 * in-app gate) goes through here, so brute-force protection cannot be bypassed by
 * switching between surfaces.
 */
@Singleton
class Authenticator @Inject constructor(
    private val credentials: CredentialRepository,
    private val attempts: AuthAttemptRepository,
    private val time: TimeSource,
) {
    val credentialType: Flow<CredentialType?> = credentials.credentialType

    // Serialises verifications so parallel attempts can't race the failure counter.
    private val mutex = Mutex()

    /** Remaining lockout in ms, or 0 when input is allowed. */
    suspend fun lockoutRemainingMillis(): Long = remaining(attempts.get())

    suspend fun verify(secret: CharArray): VerifyResult = mutex.withLock {
        val state = attempts.get()
        val remaining = remaining(state)
        if (remaining > 0) return VerifyResult.LockedOut(remaining)

        val matches = credentials.verify(secret) ?: return VerifyResult.NoCredential
        if (matches) {
            if (state.failedAttempts != 0) attempts.set(AuthAttemptState())
            return VerifyResult.Success
        }

        val failed = state.failedAttempts + 1
        val lockout = LockoutPolicy.lockoutDurationFor(failed)
        val deadline = if (lockout > 0) time.currentTimeMillis() + lockout else 0L
        attempts.set(AuthAttemptState(failedAttempts = failed, lockoutUntilMillis = deadline))
        if (lockout > 0) {
            VerifyResult.LockedOut(lockout)
        } else {
            VerifyResult.Failure(LockoutPolicy.attemptsBeforeLockout(failed))
        }
    }

    /** Successful biometric auth also clears the failure counter. */
    suspend fun onBiometricSuccess() = mutex.withLock {
        if (attempts.get().failedAttempts != 0) attempts.set(AuthAttemptState())
    }

    private fun remaining(state: AuthAttemptState): Long {
        if (state.lockoutUntilMillis == 0L) return 0L
        val left = state.lockoutUntilMillis - time.currentTimeMillis()
        // Cap protects against the wall clock being moved backwards to extend a lockout forever.
        return left.coerceIn(0L, LockoutPolicy.MAX_LOCKOUT_MILLIS)
    }
}
