package com.adityadixit.applock.di

import android.content.Context
import android.os.SystemClock
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.adityadixit.applock.core.ApplicationScope
import com.adityadixit.applock.core.DefaultDispatcher
import com.adityadixit.applock.core.IoDispatcher
import com.adityadixit.applock.core.TimeSource
import com.adityadixit.applock.core.TransientPackages
import com.adityadixit.applock.data.prefs.AppPreferences
import com.adityadixit.applock.data.prefs.CredentialPreferences
import com.adityadixit.applock.data.security.CredentialHasher
import com.adityadixit.applock.data.security.KeystoreCipher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    fun provideTimeSource(): TimeSource = object : TimeSource {
        override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
        override fun currentTimeMillis(): Long = System.currentTimeMillis()
    }

    @Provides
    @Singleton
    @TransientPackages
    fun provideTransientPackages(@ApplicationContext context: Context): Set<String> = setOf(
        context.packageName,
        "android", // share sheet / resolver
        "com.android.systemui", // notification shade, volume dialog
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
    )

    @Provides
    @Singleton
    @AppPreferences
    fun provideAppDataStore(
        @ApplicationContext context: Context,
        @IoDispatcher io: CoroutineDispatcher,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = CoroutineScope(SupervisorJob() + io),
        produceFile = { context.preferencesDataStoreFile("app_lock_settings") },
    )

    @Provides
    @Singleton
    @CredentialPreferences
    fun provideCredentialDataStore(
        @ApplicationContext context: Context,
        @IoDispatcher io: CoroutineDispatcher,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = CoroutineScope(SupervisorJob() + io),
        produceFile = { context.preferencesDataStoreFile("app_lock_credential") },
    )

    @Provides
    @Singleton
    fun provideCredentialHasher(): CredentialHasher = CredentialHasher()

    @Provides
    @Singleton
    fun provideKeystoreCipher(): KeystoreCipher = KeystoreCipher()
}
