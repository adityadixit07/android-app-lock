package com.adityadixit.applock.domain.repository

import com.adityadixit.applock.domain.model.AuthAttemptState
import com.adityadixit.applock.domain.model.CredentialType
import com.adityadixit.applock.domain.model.InstalledApp
import com.adityadixit.applock.domain.model.LockSettings
import com.adityadixit.applock.domain.model.RelockPolicy
import kotlinx.coroutines.flow.Flow

interface LockedAppsRepository {
    val lockedPackages: Flow<Set<String>>
    suspend fun setLocked(packageName: String, locked: Boolean)
}

interface SettingsRepository {
    val settings: Flow<LockSettings>
    suspend fun setLockingEnabled(enabled: Boolean)
    suspend fun setRelockPolicy(policy: RelockPolicy)
    suspend fun setBiometricEnabled(enabled: Boolean)
    suspend fun setOnboardingCompleted(completed: Boolean)
}

/**
 * Stores the user's PIN/pattern. Implementations must never persist the secret itself,
 * only a salted, slow hash of it.
 */
interface CredentialRepository {
    /** Emits null when no credential is configured. */
    val credentialType: Flow<CredentialType?>

    /** Hashes and stores [secret]. The caller owns [secret] and should wipe it afterwards. */
    suspend fun save(type: CredentialType, secret: CharArray)

    /** @return true/false for a match, or null when no usable credential exists. */
    suspend fun verify(secret: CharArray): Boolean?
}

interface AuthAttemptRepository {
    suspend fun get(): AuthAttemptState
    suspend fun set(state: AuthAttemptState)
}

interface InstalledAppsRepository {
    /** Apps with a launcher entry, excluding this app. Sorted by label. */
    suspend fun getLaunchableApps(): List<InstalledApp>
}
