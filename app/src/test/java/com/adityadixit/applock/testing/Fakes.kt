package com.adityadixit.applock.testing

import com.adityadixit.applock.core.TimeSource
import com.adityadixit.applock.domain.model.AuthAttemptState
import com.adityadixit.applock.domain.model.CredentialType
import com.adityadixit.applock.domain.model.LockSettings
import com.adityadixit.applock.domain.model.RelockPolicy
import com.adityadixit.applock.domain.repository.AuthAttemptRepository
import com.adityadixit.applock.domain.repository.CredentialRepository
import com.adityadixit.applock.domain.repository.LockedAppsRepository
import com.adityadixit.applock.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeTimeSource(var elapsed: Long = 0L, var wallClock: Long = 1_700_000_000_000L) : TimeSource {
    override fun elapsedRealtime() = elapsed
    override fun currentTimeMillis() = wallClock
    fun advance(millis: Long) {
        elapsed += millis
        wallClock += millis
    }
}

class FakeLockedAppsRepository(initial: Set<String> = emptySet()) : LockedAppsRepository {
    val flow = MutableStateFlow(initial)
    override val lockedPackages: Flow<Set<String>> = flow
    override suspend fun setLocked(packageName: String, locked: Boolean) {
        flow.update { if (locked) it + packageName else it - packageName }
    }
}

class FakeSettingsRepository(initial: LockSettings = LockSettings()) : SettingsRepository {
    val flow = MutableStateFlow(initial)
    override val settings: Flow<LockSettings> = flow
    override suspend fun setLockingEnabled(enabled: Boolean) = flow.update { it.copy(lockingEnabled = enabled) }
    override suspend fun setRelockPolicy(policy: RelockPolicy) = flow.update { it.copy(relockPolicy = policy) }
    override suspend fun setBiometricEnabled(enabled: Boolean) = flow.update { it.copy(biometricEnabled = enabled) }
    override suspend fun setOnboardingCompleted(completed: Boolean) =
        flow.update { it.copy(onboardingCompleted = completed) }
}

/** Stores the secret in plain text — acceptable only in a test double. */
class FakeCredentialRepository(secret: String? = "1234") : CredentialRepository {
    private var stored: String? = secret
    val typeFlow = MutableStateFlow(secret?.let { CredentialType.PIN })
    var verifyCalls = 0
        private set

    override val credentialType: Flow<CredentialType?> = typeFlow

    override suspend fun save(type: CredentialType, secret: CharArray) {
        stored = String(secret)
        typeFlow.value = type
    }

    override suspend fun verify(secret: CharArray): Boolean? {
        verifyCalls++
        return stored?.let { it == String(secret) }
    }
}

class FakeAuthAttemptRepository : AuthAttemptRepository {
    var state = AuthAttemptState()
    override suspend fun get() = state
    override suspend fun set(state: AuthAttemptState) {
        this.state = state
    }
}
