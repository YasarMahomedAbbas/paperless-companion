package io.github.yasarmahomedabbas.paperlesscompanion.ui.lock

import android.os.Build
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Shows the phone's own unlock prompt: a fingerprint, with the PIN, pattern or password as the
 * fallback. [onFailure] gets null when the user cancelled, or the prompt's reason when it gave up.
 */
fun FragmentActivity.askToUnlock(title: String, onSuccess: () -> Unit, onFailure: (String?) -> Unit) {
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                errorCode == BiometricPrompt.ERROR_CANCELED ||
                errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
            onFailure(if (cancelled) null else errString.toString())
        }

        // A finger that didn't match: the prompt says so itself and keeps listening.
        override fun onAuthenticationFailed() = Unit
    }
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setAllowedAuthenticators(authenticators())
        .build()
    BiometricPrompt(this, ContextCompat.getMainExecutor(this), callback).authenticate(info)
}

/** Below API 30 androidx only offers the PIN fallback together with weak biometrics. */
private fun authenticators() =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_STRONG or DEVICE_CREDENTIAL
    else BIOMETRIC_WEAK or DEVICE_CREDENTIAL
