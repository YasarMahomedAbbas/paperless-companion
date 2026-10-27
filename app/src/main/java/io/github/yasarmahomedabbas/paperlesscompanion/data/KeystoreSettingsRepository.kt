package io.github.yasarmahomedabbas.paperlesscompanion.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerConfig
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.SettingsRepository
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Keeps the server URL in private prefs and the API token encrypted with a key in the Android Keystore. */
class KeystoreSettingsRepository(context: Context) : SettingsRepository {
    private val prefs = context.getSharedPreferences("server", Context.MODE_PRIVATE)

    override fun load(): ServerConfig? {
        val url = prefs.getString(KEY_URL, null) ?: return null
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        return runCatching { ServerConfig(url, decrypt(token)) }.getOrNull()
    }

    override fun save(config: ServerConfig) {
        prefs.edit().putString(KEY_URL, config.baseUrl).putString(KEY_TOKEN, encrypt(config.token)).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec) }.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val sealed = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, sealed, 0, IV_SIZE))
        }
        return String(cipher.doFinal(sealed, IV_SIZE, sealed.size - IV_SIZE))
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "paperless-token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val KEY_URL = "url"
        const val KEY_TOKEN = "token"
    }
}
