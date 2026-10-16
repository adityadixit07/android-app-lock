package com.adityadixit.applock.domain.auth

import com.adityadixit.applock.domain.model.AuthAttemptState
import com.adityadixit.applock.domain.model.VerifyResult
import com.adityadixit.applock.testing.FakeAuthAttemptRepository
import com.adityadixit.applock.testing.FakeCredentialRepository
import com.adityadixit.applock.testing.FakeTimeSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticatorTest {

    private val time = FakeTimeSource()
    private val credentials = FakeCredentialRepository(secret = "1234")
    private val attempts = FakeAuthAttemptRepository()
    private val authenticator = Authenticator(credentials, attempts, time)

    private suspend fun verify(pin: String) = authenticator.verify(pin.toCharArray())

    @Test
    fun `correct PIN succeeds and resets failures`() = runTest {
        verify("0000")
        assertEquals(VerifyResult.Success, verify("1234"))
        assertEquals(AuthAttemptState(), attempts.state)
    }

    @Test
    fun `wrong PIN reports remaining attempts`() = runTest {
        assertEquals(VerifyResult.Failure(attemptsBeforeLockout = 4), verify("0000"))
        assertEquals(VerifyResult.Failure(attemptsBeforeLockout = 3), verify("0000"))
    }

    @Test
    fun `fifth failure locks out and blocks further attempts`() = runTest {
        repeat(4) { verify("0000") }
        assertEquals(VerifyResult.LockedOut(30_000), verify("0000"))

        val callsBefore = credentials.verifyCalls
        // Even the correct PIN is refused during lockout, without touching the credential.
        assertTrue(verify("1234") is VerifyResult.LockedOut)
        assertEquals(callsBefore, credentials.verifyCalls)
    }

    @Test
    fun `attempts resume after lockout expires`() = runTest {
        repeat(5) { verify("0000") }
        time.advance(30_001)
        assertEquals(VerifyResult.Success, verify("1234"))
    }

    @Test
    fun `lockout escalates on continued failures`() = runTest {
        repeat(5) { verify("0000") }
        time.advance(30_001)
        assertEquals(VerifyResult.LockedOut(60_000), verify("0000"))
    }

    @Test
    fun `clock moved backwards cannot extend lockout beyond the cap`() = runTest {
        repeat(5) { verify("0000") }
        time.wallClock -= 365L * 24 * 60 * 60_000
        val result = authenticator.lockoutRemainingMillis()
        assertEquals(LockoutPolicy.MAX_LOCKOUT_MILLIS, result)
    }

    @Test
    fun `no credential is reported`() = runTest {
        val auth = Authenticator(FakeCredentialRepository(secret = null), attempts, time)
        assertEquals(VerifyResult.NoCredential, auth.verify("1234".toCharArray()))
    }
}
