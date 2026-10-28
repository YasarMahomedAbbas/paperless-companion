package io.github.yasarmahomedabbas.paperlesscompanion.data

import io.github.yasarmahomedabbas.paperlesscompanion.CONFIG
import io.github.yasarmahomedabbas.paperlesscompanion.FakeVault
import io.github.yasarmahomedabbas.paperlesscompanion.MapStore
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.StoredSignIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SealedSettingsRepositoryTest {
    private val store = MapStore()
    private val vault = FakeVault()
    private var deviceSecure = true
    private val repo = SealedSettingsRepository(store, vault) { deviceSecure }

    private val ready = StoredSignIn.Ready(CONFIG)

    /** A second repository on the same store and vault: what the app sees after a restart. */
    private fun restarted() = SealedSettingsRepository(store, vault) { deviceSecure }

    private fun storedBy(locked: Boolean) {
        store.values += mapOf("url" to CONFIG.baseUrl, "token" to "${if (locked) "locked" else "plain"}:${CONFIG.token}")
        if (locked) store.values["token_locked"] = true
    }

    @Test
    fun `nothing saved reads as none`() {
        assertEquals(StoredSignIn.None, repo.read())
        assertEquals(StoredSignIn.None, repo.unlock())
        assertNull(repo.load())
    }

    @Test
    fun `with the lock on a saved token reads as locked without touching the vault`() {
        storedBy(locked = true)

        assertEquals(StoredSignIn.Locked, repo.read())
        assertEquals(0, vault.opens)
    }

    @Test
    fun `an old plain token reads as locked once the phone can lock`() {
        storedBy(locked = false)

        assertEquals(StoredSignIn.Locked, repo.read())
        assertEquals(0, vault.opens)
    }

    @Test
    fun `unlock opens the token and keeps it in memory`() {
        storedBy(locked = true)

        assertEquals(ready, repo.unlock())
        assertEquals(ready, repo.read())
        assertEquals(1, vault.opens)
    }

    @Test
    fun `unlock without a recent unlock stays locked`() {
        storedBy(locked = true)
        vault.lockedUntilUnlock = true

        assertEquals(StoredSignIn.Locked, repo.unlock())
        assertEquals(StoredSignIn.Locked, repo.read())
    }

    @Test
    fun `a voided key clears the sign-in`() {
        storedBy(locked = true)
        vault.voided = true

        assertEquals(StoredSignIn.Voided, repo.unlock())
        assertTrue(store.values.isEmpty())
        assertEquals(StoredSignIn.None, repo.read())
    }

    @Test
    fun `a locked token on a phone that lost its screen lock is voided`() {
        storedBy(locked = true)
        deviceSecure = false

        assertEquals(StoredSignIn.Voided, repo.read())
        assertTrue(store.values.isEmpty())
    }

    @Test
    fun `unlock moves a token from before the lock to the locked key`() {
        storedBy(locked = false)

        assertEquals(ready, repo.unlock())

        assertEquals("locked:${CONFIG.token}", store.values["token"])
        assertEquals(true, store.values["token_locked"])
        assertEquals(listOf(false), vault.deleted)
    }

    @Test
    fun `a failed move keeps the old seal`() {
        storedBy(locked = false)
        vault.lockedUntilUnlock = true

        assertEquals(ready, repo.unlock())

        assertEquals("plain:${CONFIG.token}", store.values["token"])
        assertTrue(vault.deleted.isEmpty())
    }

    @Test
    fun `save with the lock on and a recent unlock seals with the locked key`() {
        repo.save(CONFIG)

        assertEquals("locked:${CONFIG.token}", store.values["token"])
        assertEquals(true, store.values["token_locked"])
        assertFalse(repo.hasUnsavedSignIn())
    }

    @Test
    fun `save without a recent unlock keeps the token in memory until unlock seals it`() {
        vault.lockedUntilUnlock = true
        repo.save(CONFIG)

        assertEquals(ready, repo.read())
        assertTrue(repo.hasUnsavedSignIn())
        assertNull(store.values["token"])
        assertEquals(CONFIG.baseUrl, store.values["url"])

        repo.forget()
        assertEquals(ready, repo.read())

        vault.lockedUntilUnlock = false
        assertEquals(ready, repo.unlock())
        assertFalse(repo.hasUnsavedSignIn())
        assertEquals("locked:${CONFIG.token}", store.values["token"])
    }

    @Test
    fun `an unsaved sign-in stays unsaved while the unlock still refuses`() {
        vault.lockedUntilUnlock = true
        repo.save(CONFIG)

        assertEquals(StoredSignIn.Locked, repo.unlock())
        assertTrue(repo.hasUnsavedSignIn())
    }

    @Test
    fun `turning the lock off re-seals with the plain key`() {
        repo.save(CONFIG)

        repo.setEnabled(false)

        assertFalse(repo.isEnabled())
        assertEquals("plain:${CONFIG.token}", store.values["token"])
        assertEquals(false, store.values["token_locked"])
        assertEquals(listOf(true), vault.deleted)
        assertEquals(ready, restarted().read())
    }

    @Test
    fun `turning the lock on again re-seals with the locked key`() {
        repo.save(CONFIG)
        repo.setEnabled(false)

        repo.setEnabled(true)

        assertTrue(repo.isEnabled())
        assertEquals("locked:${CONFIG.token}", store.values["token"])
        assertEquals(listOf(true, false), vault.deleted)
        assertEquals(StoredSignIn.Locked, restarted().read())
    }

    @Test
    fun `switching without a recent unlock refuses and keeps the old seal`() {
        repo.save(CONFIG)
        repo.setEnabled(false)
        vault.lockedUntilUnlock = true

        assertThrows(IllegalStateException::class.java) { repo.setEnabled(true) }

        assertFalse(repo.isEnabled())
        assertEquals("plain:${CONFIG.token}", store.values["token"])
    }

    @Test
    fun `switching while a sign-in waits to be sealed only records the choice`() {
        vault.lockedUntilUnlock = true
        repo.save(CONFIG)

        repo.setEnabled(false)

        assertFalse(repo.isEnabled())
        assertTrue(repo.hasUnsavedSignIn())
        assertEquals(ready, repo.unlock())
        assertEquals("plain:${CONFIG.token}", store.values["token"])
    }

    @Test
    fun `without a screen lock the plain key is used whatever the switch says`() {
        deviceSecure = false
        assertFalse(repo.canLock())

        repo.save(CONFIG)
        assertEquals("plain:${CONFIG.token}", store.values["token"])

        repo.setEnabled(true)
        assertEquals("plain:${CONFIG.token}", store.values["token"])

        repo.forget()
        assertEquals(ready, restarted().read())
    }

    @Test
    fun `without the lock an unreadable token is voided`() {
        deviceSecure = false
        storedBy(locked = false)
        vault.voided = true

        assertEquals(StoredSignIn.Voided, repo.read())
        assertTrue(store.values.isEmpty())
    }

    @Test
    fun `forget drops the token while the lock is active`() {
        storedBy(locked = true)
        repo.unlock()

        repo.forget()

        assertEquals(StoredSignIn.Locked, repo.read())
    }

    @Test
    fun `clear wipes the store, the memory and the locked key`() {
        repo.save(CONFIG)
        repo.setEnabled(false)
        vault.deleted.clear()

        repo.clear()

        assertTrue(store.values.isEmpty())
        assertTrue(repo.isEnabled())
        assertEquals(StoredSignIn.None, repo.read())
        assertEquals(listOf(true), vault.deleted)
    }
}
