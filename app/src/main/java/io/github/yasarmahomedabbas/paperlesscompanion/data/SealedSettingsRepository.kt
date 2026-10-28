package io.github.yasarmahomedabbas.paperlesscompanion.data

import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerConfig
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.StoredSignIn
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.AppLockRepository
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.SettingsRepository

/**
 * Keeps the server URL in [store] and the API token sealed by [vault]: with the locked key while
 * the phone has a screen lock and the user wants the app locked, with the plain key otherwise.
 * After an unlock the token is held in memory until [forget].
 */
class SealedSettingsRepository(
    private val store: KeyValueStore,
    private val vault: TokenVault,
    private val deviceSecure: () -> Boolean,
) : SettingsRepository, AppLockRepository {
    private var unlocked: ServerConfig? = null

    /** True while [unlocked] is a fresh sign-in that couldn't be sealed yet (no recent unlock). */
    private var unsaved = false

    override fun canLock() = deviceSecure()

    override fun isEnabled() = store.getBoolean(KEY_LOCK_ENABLED) ?: true

    private fun active() = canLock() && isEnabled()

    /** Which key sealed the stored token. Absent on installs from before the lock: the plain one. */
    private fun sealedLocked() = store.getBoolean(KEY_TOKEN_LOCKED) ?: false

    override fun read(): StoredSignIn {
        unlocked?.let { return StoredSignIn.Ready(it) }
        val url = store.getString(KEY_URL) ?: return StoredSignIn.None
        val token = store.getString(KEY_TOKEN) ?: return StoredSignIn.None
        if (sealedLocked() && !canLock()) {
            // The screen lock was removed, which voids every key bound to it.
            clear()
            return StoredSignIn.Voided
        }
        // Never open the locked key here: a recent screen unlock would open it without the app's prompt.
        if (active() || sealedLocked()) return StoredSignIn.Locked
        return try {
            StoredSignIn.Ready(ServerConfig(url, vault.open(token, locked = false)).also { unlocked = it })
        } catch (e: Exception) {
            clear()
            StoredSignIn.Voided
        }
    }

    override fun unlock(): StoredSignIn {
        val held = unlocked
        if (held != null && unsaved) {
            return try {
                seal(held, active())
                unsaved = false
                StoredSignIn.Ready(held)
            } catch (e: VaultLockedException) {
                StoredSignIn.Locked
            }
        }
        if (held != null) return StoredSignIn.Ready(held)
        val url = store.getString(KEY_URL) ?: return StoredSignIn.None
        val token = store.getString(KEY_TOKEN) ?: return StoredSignIn.None
        val wasLocked = sealedLocked()
        val config = try {
            ServerConfig(url, vault.open(token, wasLocked))
        } catch (e: VaultLockedException) {
            return StoredSignIn.Locked
        } catch (e: VaultVoidedException) {
            clear()
            return StoredSignIn.Voided
        }
        val wanted = active()
        if (wasLocked != wanted) {
            // An install from before the lock, or the switch or screen lock changed since it was sealed.
            try {
                seal(config, wanted)
                vault.delete(wasLocked)
            } catch (e: VaultLockedException) {
                // Keep the old seal; the next unlock tries again.
            }
        }
        unlocked = config
        return StoredSignIn.Ready(config)
    }

    override fun save(config: ServerConfig) {
        unlocked = config
        try {
            seal(config, active())
            unsaved = false
        } catch (e: VaultLockedException) {
            store.edit {
                put(KEY_URL, config.baseUrl)
                put(KEY_TOKEN, null)
                put(KEY_TOKEN_LOCKED, null)
            }
            unsaved = true
        }
    }

    override fun setEnabled(enabled: Boolean) {
        val wanted = canLock() && enabled
        val wasLocked = sealedLocked()
        // A sign-in still waiting to be sealed is sealed by unlock() with whichever key is wanted then.
        val reseal = unlocked.takeUnless { unsaved }
        if (reseal != null) {
            try {
                seal(reseal, wanted)
            } catch (e: VaultLockedException) {
                throw IllegalStateException("Unlock first", e)
            }
        }
        store.edit { put(KEY_LOCK_ENABLED, enabled) }
        if (reseal != null && wasLocked != wanted) vault.delete(wasLocked)
    }

    override fun hasUnsavedSignIn() = unsaved

    override fun forget() {
        if (active() && !unsaved) unlocked = null
    }

    override fun clear() {
        store.clear()
        unlocked = null
        unsaved = false
        vault.delete(locked = true)
    }

    private fun seal(config: ServerConfig, locked: Boolean) {
        val sealed = vault.seal(config.token, locked)
        store.edit {
            put(KEY_URL, config.baseUrl)
            put(KEY_TOKEN, sealed)
            put(KEY_TOKEN_LOCKED, locked)
        }
    }

    private companion object {
        const val KEY_URL = "url"
        const val KEY_TOKEN = "token"
        const val KEY_TOKEN_LOCKED = "token_locked"
        const val KEY_LOCK_ENABLED = "lock_enabled"
    }
}
