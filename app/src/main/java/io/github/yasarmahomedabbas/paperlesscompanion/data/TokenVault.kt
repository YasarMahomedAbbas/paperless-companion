package io.github.yasarmahomedabbas.paperlesscompanion.data

/**
 * Seals and opens the API token with one of two keys: a plain one, or a [locked] one that only
 * works for a few seconds after the user unlocked the phone's lock in the app.
 */
interface TokenVault {
    /** Throws [VaultLockedException] when [locked] and the user hasn't unlocked recently. */
    fun seal(plain: String, locked: Boolean): String

    /** Throws [VaultLockedException] as [seal] does, and [VaultVoidedException] when the key is gone for good. */
    fun open(sealed: String, locked: Boolean): String

    fun delete(locked: Boolean)
}

/** The user hasn't unlocked recently, so the locked key refuses. */
class VaultLockedException : Exception()

/** The phone voided the key (the screen lock was removed or the biometrics changed); what it sealed is lost. */
class VaultVoidedException : Exception()
