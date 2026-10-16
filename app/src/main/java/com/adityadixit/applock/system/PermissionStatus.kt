package com.adityadixit.applock.system

import android.Manifest
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.adityadixit.applock.BuildConfig
import com.adityadixit.applock.admin.AppLockDeviceAdminReceiver
import com.adityadixit.applock.service.AppLockAccessibilityService

/** Which detection host can run, in order of preference. */
enum class DetectionMode { ACCESSIBILITY, USAGE_STATS, NONE }

/** Read-only checks of the special permissions App Lock depends on. Cheap enough for onResume. */
object PermissionStatus {

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        // The "standard" flavor doesn't declare the service at all.
        if (!BuildConfig.ACCESSIBILITY_DETECTION) return false
        val expected = ComponentName(context, AppLockAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)

    fun areNotificationsEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun isDeviceAdminActive(context: Context): Boolean =
        context.getSystemService(DevicePolicyManager::class.java)
            .isAdminActive(AppLockDeviceAdminReceiver.component(context))

    /**
     * Accessibility is preferred (event-driven, no polling, works without the overlay
     * permission). Usage Access needs the overlay permission to draw the lock screen.
     */
    fun detectionMode(context: Context): DetectionMode = when {
        isAccessibilityServiceEnabled(context) -> DetectionMode.ACCESSIBILITY
        hasUsageAccess(context) && canDrawOverlays(context) -> DetectionMode.USAGE_STATS
        else -> DetectionMode.NONE
    }
}
