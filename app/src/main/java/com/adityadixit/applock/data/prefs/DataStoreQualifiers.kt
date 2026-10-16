package com.adityadixit.applock.data.prefs

import javax.inject.Qualifier

/** General settings, locked-app list and lockout counters. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppPreferences

/** Isolated file holding only the encrypted credential record. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CredentialPreferences
