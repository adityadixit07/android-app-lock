package com.adityadixit.applock.data.prefs

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.adityadixit.applock.core.DefaultDispatcher
import com.adityadixit.applock.data.security.CredentialHasher
import com.adityadixit.applock.data.security.HashedCredential
import com.adityadixit.applock.data.security.KeystoreCipher
import com.adityadixit.applock.domain.model.CredentialType
import com.adityadixit.applock.domain.repository.CredentialRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Defence in depth for the PIN/pattern:
 *  1. Salted PBKDF2 hash — the secret itself is never stored.
 *  2. The hash record is AES-GCM encrypted with a hardware-backed Keystore key.
 *  3. Stored in a dedicated DataStore file excluded from backup and device transfer.
 */
@Singleton
class EncryptedCredentialRepository @Inject constructor(
    @CredentialPreferences private val dataStore: DataStore<Preferences>,
    private val hasher: CredentialHasher,
    private val cipher: KeystoreCipher,
    @DefaultDispatcher private val cpuDispatcher: CoroutineDispatcher,
) : CredentialRepository {

    override val credentialType: Flow<CredentialType?> = dataStore.data
        .map { prefs ->
            prefs[KEY_TYPE]?.let { name -> CredentialType.entries.firstOrNull { it.name == name } }
                ?.takeIf { prefs[KEY_RECORD] != null }
        }
        .distinctUntilChanged()

    override suspend fun save(type: CredentialType, secret: CharArray) {
        val record = withContext(cpuDispatcher) {
            val encoded = hasher.hash(secret).encode().toByteArray(Charsets.UTF_8)
            Base64.getEncoder().encodeToString(cipher.encrypt(encoded))
        }
        dataStore.edit {
            it[KEY_TYPE] = type.name
            it[KEY_RECORD] = record
        }
    }

    override suspend fun verify(secret: CharArray): Boolean? {
        val record = dataStore.data.first()[KEY_RECORD] ?: return null
        val stored = withContext(cpuDispatcher) {
            try {
                val plain = cipher.decrypt(Base64.getDecoder().decode(record))
                HashedCredential.decode(String(plain, Charsets.UTF_8))
            } catch (e: Exception) {
                Log.e(TAG, "Credential record unreadable", e)
                null
            }
        }
        if (stored == null) {
            // Keystore key lost (e.g. data restored onto another device). Unrecoverable, so
            // discard the record: the engine stops enforcing and the app routes to setup,
            // instead of locking the owner out of their own apps forever.
            dataStore.edit {
                it.remove(KEY_RECORD)
                it.remove(KEY_TYPE)
            }
            return null
        }
        return withContext(cpuDispatcher) { hasher.verify(secret, stored) }
    }

    private companion object {
        const val TAG = "CredentialRepository"
        val KEY_TYPE = stringPreferencesKey("credential_type")
        val KEY_RECORD = stringPreferencesKey("credential_record")
    }
}
