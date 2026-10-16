package com.adityadixit.applock.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.adityadixit.applock.domain.model.AuthAttemptState
import com.adityadixit.applock.domain.model.LockSettings
import com.adityadixit.applock.domain.model.RelockPolicy
import com.adityadixit.applock.domain.repository.AuthAttemptRepository
import com.adityadixit.applock.domain.repository.LockedAppsRepository
import com.adityadixit.applock.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private object Keys {
    val LOCKED_PACKAGES = stringSetPreferencesKey("locked_packages")
    val LOCKING_ENABLED = booleanPreferencesKey("locking_enabled")
    val RELOCK_POLICY = stringPreferencesKey("relock_policy")
    val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    val FAILED_ATTEMPTS = intPreferencesKey("failed_attempts")
    val LOCKOUT_UNTIL = longPreferencesKey("lockout_until")
}

@Singleton
class DataStoreLockedAppsRepository @Inject constructor(
    @AppPreferences private val dataStore: DataStore<Preferences>,
) : LockedAppsRepository {

    override val lockedPackages: Flow<Set<String>> = dataStore.data
        .map { it[Keys.LOCKED_PACKAGES].orEmpty() }
        .distinctUntilChanged()

    override suspend fun setLocked(packageName: String, locked: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.LOCKED_PACKAGES].orEmpty()
            prefs[Keys.LOCKED_PACKAGES] = if (locked) current + packageName else current - packageName
        }
    }
}

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @AppPreferences private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<LockSettings> = dataStore.data
        .map { prefs ->
            LockSettings(
                lockingEnabled = prefs[Keys.LOCKING_ENABLED] ?: true,
                relockPolicy = prefs[Keys.RELOCK_POLICY]
                    ?.let { name -> RelockPolicy.entries.firstOrNull { it.name == name } }
                    ?: RelockPolicy.IMMEDIATELY,
                biometricEnabled = prefs[Keys.BIOMETRIC_ENABLED] ?: false,
                onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            )
        }
        .distinctUntilChanged()

    override suspend fun setLockingEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.LOCKING_ENABLED] = enabled }
    }

    override suspend fun setRelockPolicy(policy: RelockPolicy) {
        dataStore.edit { it[Keys.RELOCK_POLICY] = policy.name }
    }

    override suspend fun setBiometricEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.BIOMETRIC_ENABLED] = enabled }
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }
}

@Singleton
class DataStoreAuthAttemptRepository @Inject constructor(
    @AppPreferences private val dataStore: DataStore<Preferences>,
) : AuthAttemptRepository {

    override suspend fun get(): AuthAttemptState {
        val prefs = dataStore.data.first()
        return AuthAttemptState(
            failedAttempts = prefs[Keys.FAILED_ATTEMPTS] ?: 0,
            lockoutUntilMillis = prefs[Keys.LOCKOUT_UNTIL] ?: 0L,
        )
    }

    override suspend fun set(state: AuthAttemptState) {
        dataStore.edit {
            it[Keys.FAILED_ATTEMPTS] = state.failedAttempts
            it[Keys.LOCKOUT_UNTIL] = state.lockoutUntilMillis
        }
    }
}
