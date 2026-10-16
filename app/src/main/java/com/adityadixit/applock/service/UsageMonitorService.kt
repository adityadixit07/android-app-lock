package com.adityadixit.applock.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.adityadixit.applock.R
import com.adityadixit.applock.core.IoDispatcher
import com.adityadixit.applock.data.apps.AppIconLoader
import com.adityadixit.applock.domain.auth.Authenticator
import com.adityadixit.applock.domain.lock.LockEngine
import com.adityadixit.applock.domain.repository.SettingsRepository
import com.adityadixit.applock.system.BiometricAvailability
import com.adityadixit.applock.system.DetectionMode
import com.adityadixit.applock.system.PermissionStatus
import com.adityadixit.applock.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Fallback detection host for users who don't want to grant Accessibility (and for Play Store
 * builds, where Accessibility for app locking is not an accepted use case).
 *
 * Battery strategy: poll UsageEvents incrementally every [POLL_INTERVAL_MS] **only while the
 * screen is on**; polling stops completely on SCREEN_OFF. Runs as a `specialUse` foreground
 * service so Android 14/15 background limits don't kill it.
 */
@AndroidEntryPoint
class UsageMonitorService : Service() {

    @Inject lateinit var engine: LockEngine
    @Inject lateinit var authenticator: Authenticator
    @Inject lateinit var iconLoader: AppIconLoader
    @Inject lateinit var biometricAvailability: BiometricAvailability
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject @field:IoDispatcher lateinit var ioDispatcher: CoroutineDispatcher

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var coordinator: LockCoordinator? = null
    private var pollJob: Job? = null
    private lateinit var poller: UsageEventsPoller

    override fun onCreate() {
        super.onCreate()
        // Must be called promptly after startForegroundService(), before any other work.
        startInForeground()
        poller = UsageEventsPoller(getSystemService(UsageStatsManager::class.java))
        coordinator = LockCoordinator(
            context = this,
            windowType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            engine = engine,
            authenticator = authenticator,
            iconLoader = iconLoader,
            biometricAvailability = biometricAvailability,
            settingsRepository = settingsRepository,
            leaveLockedApp = ::goHome,
            onScreenInteractiveChanged = { interactive ->
                if (interactive) startPolling() else stopPolling()
            },
        ).also { it.start() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (PermissionStatus.detectionMode(this) != DetectionMode.USAGE_STATS) {
            stopSelf()
            return START_NOT_STICKY
        }
        scope.launch {
            if (!settingsRepository.settings.first().onboardingCompleted) stopSelf()
        }
        if (getSystemService(PowerManager::class.java).isInteractive) startPolling()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        coordinator?.stop()
        coordinator = null
        super.onDestroy()
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                val packageName = withContext(ioDispatcher) { poller.latestForegroundPackage() }
                if (packageName != null) coordinator?.onForegroundPackage(packageName)
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun goHome() {
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(home) }
    }

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_protection),
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = getString(R.string.notification_channel_protection_description)
                setShowBadge(false)
            },
        )
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lock)
            .setContentTitle(getString(R.string.notification_protection_title))
            .setContentText(getString(R.string.notification_protection_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private companion object {
        const val CHANNEL_ID = "protection"
        const val NOTIFICATION_ID = 1001
        /** ~350 ms feels instant to users while keeping IPC cost negligible. */
        const val POLL_INTERVAL_MS = 350L
    }
}
