package com.adityadixit.applock.core

import javax.inject.Qualifier

/** Process-lifetime scope for state that must outlive any screen or service. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * Packages whose windows must not count as "the user left the locked app": this app itself,
 * System UI (notification shade), the share sheet and permission dialogs.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TransientPackages

/** Dispatcher for blocking I/O (DataStore, PackageManager, UsageStatsManager). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** Dispatcher for CPU-bound work (PBKDF2 key derivation). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher
