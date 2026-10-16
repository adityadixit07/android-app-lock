package com.adityadixit.applock.ui.biometric

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.adityadixit.applock.R
import com.adityadixit.applock.core.ApplicationScope
import com.adityadixit.applock.data.apps.AppIconLoader
import com.adityadixit.applock.domain.auth.Authenticator
import com.adityadixit.applock.domain.lock.LockEngine
import com.adityadixit.applock.system.BiometricAvailability
import com.adityadixit.applock.ui.components.rememberLoaded
import com.adityadixit.applock.ui.theme.AppLockTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * BiometricPrompt needs a FragmentActivity, so biometric unlock of another app runs here.
 * The activity is opaque and shows the target app's icon, so the protected content never
 * shows through. Every exit path reports back to [LockEngine] exactly once.
 */
@AndroidEntryPoint
class BiometricUnlockActivity : FragmentActivity() {

    @Inject lateinit var engine: LockEngine
    @Inject lateinit var authenticator: Authenticator
    @Inject lateinit var iconLoader: AppIconLoader
    @Inject @field:ApplicationScope lateinit var appScope: CoroutineScope

    private lateinit var targetPackage: String
    private var resultDelivered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetPackage = intent.getStringExtra(EXTRA_PACKAGE) ?: run {
            finish()
            return
        }

        setContent {
            AppLockTheme {
                val icon = rememberLoaded(targetPackage) { iconLoader.load(targetPackage) }
                val label = rememberLoaded(targetPackage) { iconLoader.loadLabel(targetPackage) }.orEmpty()
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(72.dp)) }
                        Spacer(Modifier.height(16.dp))
                        Text(label, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }

        // Created in onCreate so the prompt survives configuration changes.
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), callback)
        if (savedInstanceState == null) {
            // Post: the opaque window is on screen before the overlay underneath is removed.
            window.decorView.post {
                if (isFinishing) return@post
                engine.onBiometricPromptVisible(targetPackage)
                prompt.authenticate(promptInfo())
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Home/recents while prompting: treat as cancelled and fall back to the PIN overlay.
        if (!isChangingConfigurations && !resultDelivered) {
            deliver { engine.onBiometricDismissed(targetPackage) }
            finish()
        }
    }

    private val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            deliver {
                // App scope: the write must outlive this activity, which finishes immediately.
                appScope.launch { authenticator.onBiometricSuccess() }
                engine.onUnlockSucceeded(targetPackage)
            }
            finish()
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            // Covers "Use PIN" (negative button), user cancel, sensor lockout, timeouts.
            deliver { engine.onBiometricDismissed(targetPackage) }
            finish()
        }
    }

    private fun promptInfo(): BiometricPrompt.PromptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(getString(R.string.biometric_prompt_title))
        .setSubtitle(getString(R.string.biometric_prompt_subtitle))
        .setNegativeButtonText(getString(R.string.biometric_use_credential))
        .setAllowedAuthenticators(BiometricAvailability.AUTHENTICATORS)
        .setConfirmationRequired(false)
        .build()

    private inline fun deliver(action: () -> Unit) {
        if (resultDelivered) return
        resultDelivered = true
        action()
    }

    companion object {
        private const val EXTRA_PACKAGE = "extra_package"

        fun intent(context: Context, packageName: String): Intent =
            Intent(context, BiometricUnlockActivity::class.java)
                .putExtra(EXTRA_PACKAGE, packageName)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
                )
    }
}
