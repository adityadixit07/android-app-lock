package com.adityadixit.applock.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class LockoutPolicyTest {

    @Test
    fun `first attempts are free`() {
        (0..LockoutPolicy.FREE_ATTEMPTS).forEach { assertEquals(0L, LockoutPolicy.lockoutDurationFor(it)) }
    }

    @Test
    fun `lockout doubles after free attempts`() {
        assertEquals(30_000L, LockoutPolicy.lockoutDurationFor(5))
        assertEquals(60_000L, LockoutPolicy.lockoutDurationFor(6))
        assertEquals(120_000L, LockoutPolicy.lockoutDurationFor(7))
    }

    @Test
    fun `lockout is capped`() {
        assertEquals(LockoutPolicy.MAX_LOCKOUT_MILLIS, LockoutPolicy.lockoutDurationFor(20))
        assertEquals(LockoutPolicy.MAX_LOCKOUT_MILLIS, LockoutPolicy.lockoutDurationFor(Int.MAX_VALUE))
    }

    @Test
    fun `remaining attempts count down to zero`() {
        assertEquals(4, LockoutPolicy.attemptsBeforeLockout(1))
        assertEquals(1, LockoutPolicy.attemptsBeforeLockout(4))
        assertEquals(0, LockoutPolicy.attemptsBeforeLockout(9))
    }

    @Test
    fun `pattern codec and rules`() {
        assertEquals("0124", String(PatternCodec.encode(listOf(0, 1, 2, 4))))
        assertEquals(false, CredentialRules.isValidPattern(listOf(0, 1, 2)))
        assertEquals(false, CredentialRules.isValidPattern(listOf(0, 1, 1, 2)))
        assertEquals(false, CredentialRules.isValidPattern(listOf(0, 1, 2, 9)))
        assertEquals(true, CredentialRules.isValidPattern(listOf(6, 4, 2, 5)))
        assertEquals(true, CredentialRules.isValidPin("1234".toCharArray()))
        assertEquals(false, CredentialRules.isValidPin("12a4".toCharArray()))
        assertEquals(false, CredentialRules.isValidPin("123".toCharArray()))
    }
}
