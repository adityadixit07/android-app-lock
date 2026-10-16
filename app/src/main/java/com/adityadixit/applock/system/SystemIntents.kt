package com.adityadixit.applock.system

import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import com.adityadixit.applock.R
import com.adityadixit.applock.admin.AppLockDeviceAdminReceiver

/**
 * Deep links into the system screens where special permissions are granted. OEM Settings
 * apps vary wildly, so every intent falls back to the generic Settings screen.
 */
object SystemIntents {

    fun accessibilitySettings() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun usageAccessSettings() = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun overlaySettings(context: Context) = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    )

    /** Shows the one-tap system dialog. Requires REQUEST_IGNORE_BATTERY_OPTIMIZATIONS. */
    fun ignoreBatteryOptimizations(context: Context) = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}"),
    )

    /** App info page — needed on Android 13+ to "Allow restricted settings" for sideloaded APKs. */
    fun appDetails(context: Context) = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}"),
    )

    fun biometricEnrollment(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_BIOMETRIC_ENROLL).putExtra(
                Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                BiometricManager.Authenticators.BIOMETRIC_STRONG,
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }

    fun addDeviceAdmin(context: Context) = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, AppLockDeviceAdminReceiver.component(context))
        .putExtra(
            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            context.getString(R.string.device_admin_explanation),
        )

    /** Starts [intent], falling back to the main Settings screen if the OEM removed it. */
    fun launch(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (_: SecurityException) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}
