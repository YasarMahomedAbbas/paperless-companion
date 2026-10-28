package io.github.yasarmahomedabbas.paperlesscompanion.data

import android.app.KeyguardManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-GCM with keys in the Android Keystore. The locked key needs the user to have unlocked
 * (fingerprint, PIN or pattern) within the last [UNLOCK_SECONDS].
 */
class KeystoreTokenVault(private val keyguard: KeyguardManager) : TokenVault {
    fun isDeviceSecure(): Boolean = keyguard.isDeviceSecure

    override fun seal(plain: String, locked: Boolean): String {
        val cipher = try {
            encryptCipher(locked)
        } catch (e: KeyPermanentlyInvalidatedException) {
            // Nothing sealed with the old key is needed to seal anew: start over with a fresh key.
            delete(locked)
            encryptCipher(locked)
        }
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    override fun open(sealed: String, locked: Boolean): String {
        val bytes = Base64.decode(sealed, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        try {
            cipher.init(Cipher.DECRYPT_MODE, key(locked), GCMParameterSpec(128, bytes, 0, IV_SIZE))
        } catch (e: UserNotAuthenticatedException) {
            throw VaultLockedException()
        } catch (e: KeyPermanentlyInvalidatedException) {
            delete(locked)
            throw VaultVoidedException()
        }
        return String(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE))
    }

    override fun delete(locked: Boolean) {
        keyStore().deleteEntry(alias(locked))
    }

    private fun encryptCipher(locked: Boolean): Cipher = Cipher.getInstance(TRANSFORMATION).apply {
        try {
            init(Cipher.ENCRYPT_MODE, key(locked))
        } catch (e: UserNotAuthenticatedException) {
            throw VaultLockedException()
        }
    }

    private fun keyStore() = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun alias(locked: Boolean) = if (locked) LOCKED_ALIAS else PLAIN_ALIAS

    private fun key(locked: Boolean): SecretKey {
        val alias = alias(locked)
        (keyStore().getKey(alias, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .apply { if (locked) requireUnlock() }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec) }.generateKey()
    }

    private fun KeyGenParameterSpec.Builder.requireUnlock() {
        setUserAuthenticationRequired(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setUserAuthenticationParameters(
                UNLOCK_SECONDS,
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
            )
        } else {
            // The only way to bind a key to a recent unlock below API 30 (deprecated from 30 on).
            setUserAuthenticationValidityDurationSeconds(UNLOCK_SECONDS)
        }
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        /** Unchanged from before the lock, so existing installs still open their token. */
        const val PLAIN_ALIAS = "paperless-token"
        const val LOCKED_ALIAS = "paperless-token-locked"
        const val UNLOCK_SECONDS = 10
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}
