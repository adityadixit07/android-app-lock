package com.adityadixit.applock.di

import com.adityadixit.applock.data.apps.PackageManagerAppsRepository
import com.adityadixit.applock.data.prefs.DataStoreAuthAttemptRepository
import com.adityadixit.applock.data.prefs.DataStoreLockedAppsRepository
import com.adityadixit.applock.data.prefs.DataStoreSettingsRepository
import com.adityadixit.applock.data.prefs.EncryptedCredentialRepository
import com.adityadixit.applock.domain.repository.AuthAttemptRepository
import com.adityadixit.applock.domain.repository.CredentialRepository
import com.adityadixit.applock.domain.repository.InstalledAppsRepository
import com.adityadixit.applock.domain.repository.LockedAppsRepository
import com.adityadixit.applock.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindLockedApps(impl: DataStoreLockedAppsRepository): LockedAppsRepository

    @Binds
    abstract fun bindSettings(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    abstract fun bindCredentials(impl: EncryptedCredentialRepository): CredentialRepository

    @Binds
    abstract fun bindAuthAttempts(impl: DataStoreAuthAttemptRepository): AuthAttemptRepository

    @Binds
    abstract fun bindInstalledApps(impl: PackageManagerAppsRepository): InstalledAppsRepository
}
