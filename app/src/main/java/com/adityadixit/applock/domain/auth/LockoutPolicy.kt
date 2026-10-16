package com.adityadixit.applock.domain.auth

/**
 * Brute-force protection: a few free attempts, then exponentially growing lockouts.
 *
 * Attempts 1–4 fail freely. Attempt 5 locks for 30 s, 6 for 60 s, 7 for 2 min … capped at 15 min.
 * With a 4-digit PIN this pushes exhaustive search from minutes to many months.
 */
object LockoutPolicy {
    const val FREE_ATTEMPTS = 4
    const val BASE_LOCKOUT_MILLIS = 30_000L
    const val MAX_LOCKOUT_MILLIS = 15 * 60_000L

    fun lockoutDurationFor(failedAttempts: Int): Long {
        if (failedAttempts <= FREE_ATTEMPTS) return 0L
        val exponent = (failedAttempts - FREE_ATTEMPTS - 1).coerceAtMost(16)
        return (BASE_LOCKOUT_MILLIS shl exponent).coerceAtMost(MAX_LOCKOUT_MILLIS)
    }

    fun attemptsBeforeLockout(failedAttempts: Int): Int =
        (FREE_ATTEMPTS + 1 - failedAttempts).coerceAtLeast(0)
}
