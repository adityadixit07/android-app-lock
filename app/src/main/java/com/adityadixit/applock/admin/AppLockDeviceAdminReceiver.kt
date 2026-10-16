package com.adityadixit.applock.admin

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.adityadixit.applock.R

/**
 * Optional uninstall protection. While this admin is active Android refuses to uninstall the
 * app; the user must first deactivate it in Settings › Security › Device admin apps.
 * Pair it with locking the Settings app to make that step require the App Lock PIN too.
 */
class AppLockDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        context.getString(R.string.device_admin_disable_warning)

    companion object {
        fun component(context: Context) =
            ComponentName(context, AppLockDeviceAdminReceiver::class.java)
    }
}
