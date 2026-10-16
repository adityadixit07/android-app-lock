package com.adityadixit.applock.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.adityadixit.applock.R
import com.adityadixit.applock.data.apps.AppIconLoader
import com.adityadixit.applock.domain.auth.Authenticator
import com.adityadixit.applock.ui.lock.LockScreen
import com.adityadixit.applock.ui.lock.LockScreenController
import com.adityadixit.applock.ui.theme.AppLockTheme

/** What the overlay is currently guarding. */
data class OverlayTarget(
    val packageName: String,
    val autoPromptBiometric: Boolean,
    val canUseBiometric: Boolean,
)

/**
 * Owns the full-screen lock window. The window type depends on the host:
 *  - TYPE_ACCESSIBILITY_OVERLAY from the AccessibilityService (no extra permission needed)
 *  - TYPE_APPLICATION_OVERLAY from the foreground service (needs SYSTEM_ALERT_WINDOW)
 *
 * The window is opaque, focusable (so it receives BACK) and swallows BACK, which sends the
 * user home instead of revealing the app. Must be used from the main thread.
 */
class LockOverlayController(
    context: Context,
    private val windowType: Int,
    private val authenticator: Authenticator,
    private val iconLoader: AppIconLoader,
    private val onUnlocked: (packageName: String) -> Unit,
    private val onBiometricRequested: (packageName: String) -> Unit,
    private val onLeaveRequested: () -> Unit,
) {
    private val themedContext = ContextThemeWrapper(context, R.style.Theme_AppLock)
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val target = mutableStateOf<OverlayTarget?>(null)
    private var root: View? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    val isShowing: Boolean get() = root != null

    /** @return false if the window could not be added (permission revoked, etc.). */
    fun show(newTarget: OverlayTarget): Boolean {
        target.value = newTarget
        if (root != null) return true

        val owner = OverlayLifecycleOwner().apply { create() }
        val view = BackInterceptingLayout(themedContext, onLeaveRequested).apply {
            addView(
                ComposeView(themedContext).apply {
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                    setContent { AppLockTheme { OverlayContent() } }
                },
            )
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
        }
        return try {
            windowManager.addView(view, layoutParams())
            owner.resume()
            root = view
            lifecycleOwner = owner
            true
        } catch (e: RuntimeException) {
            // BadTokenException / SecurityException when the permission was revoked.
            Log.e(TAG, "Unable to add lock overlay", e)
            owner.destroy()
            target.value = null
            false
        }
    }

    fun hide() {
        val view = root ?: return
        root = null
        target.value = null
        runCatching { windowManager.removeViewImmediate(view) }
        lifecycleOwner?.destroy()
        lifecycleOwner = null
    }

    @Composable
    private fun OverlayContent() {
        val current = target.value ?: return
        key(current.packageName) {
            val pkg = current.packageName
            val scope = rememberCoroutineScope()
            val controller = remember {
                LockScreenController(authenticator, scope) { onUnlocked(pkg) }
            }
            val icon by produceState<ImageBitmap?>(null, pkg) { value = iconLoader.load(pkg) }
            val label by produceState<String?>(null, pkg) { value = iconLoader.loadLabel(pkg) }

            LaunchedEffect(current.autoPromptBiometric) {
                if (current.autoPromptBiometric && current.canUseBiometric) onBiometricRequested(pkg)
            }

            LockScreen(
                state = controller.state,
                title = label?.let { stringResource(R.string.unlock_app_title, it) }
                    ?: stringResource(R.string.app_name),
                appIcon = icon,
                showBiometric = current.canUseBiometric,
                onBiometric = { onBiometricRequested(pkg) },
                onDigit = controller::onDigit,
                onBackspace = controller::onBackspace,
                onSubmitPin = controller::onSubmitPin,
                onPatternDrawn = controller::onPatternDrawn,
                onClose = onLeaveRequested,
            )
        }
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        windowType,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.OPAQUE,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        title = "AppLockOverlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Draw behind system bars so no sliver of the locked app is visible; Compose pads
            // the content with WindowInsets.safeDrawing.
            fitInsetsTypes = 0
        }
    }

    /** Root view that turns BACK into "leave the locked app" instead of dismissing anything. */
    @SuppressLint("ViewConstructor")
    private class BackInterceptingLayout(
        context: Context,
        private val onBack: () -> Unit,
    ) : FrameLayout(context) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) onBack()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }

    private companion object {
        const val TAG = "LockOverlay"
    }
}
