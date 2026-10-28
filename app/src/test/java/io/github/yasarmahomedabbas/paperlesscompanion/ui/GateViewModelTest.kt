package io.github.yasarmahomedabbas.paperlesscompanion.ui

import io.github.yasarmahomedabbas.paperlesscompanion.CONFIG
import io.github.yasarmahomedabbas.paperlesscompanion.FakeLock
import io.github.yasarmahomedabbas.paperlesscompanion.FakeSettings
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.StoredSignIn
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.AppLock
import io.github.yasarmahomedabbas.paperlesscompanion.ui.lock.GateState
import io.github.yasarmahomedabbas.paperlesscompanion.ui.lock.GateViewModel
import io.github.yasarmahomedabbas.paperlesscompanion.ui.lock.GateViewModel.Companion.UNLOCK_WITH_CREDENTIAL
import io.github.yasarmahomedabbas.paperlesscompanion.ui.lock.GateViewModel.Companion.VOIDED_NOTICE
import org.junit.Assert.assertEquals
import org.junit.Test

class GateViewModelTest {
    private val settings = FakeSettings()
    private val lock = FakeLock()
    private var now = 0L

    private fun gate() = GateViewModel(settings, lock, AppLock(lock, { now }))

    private fun GateViewModel.away(millis: Long) {
        onBackground()
        now += millis
        onForeground()
    }

    private val home = GateState.Home(CONFIG, lockAvailable = true, lockEnabled = true)

    @Test
    fun `starts from what the saved sign-in is`() {
        assertEquals(GateState.SignIn(), gate().state.value)
        settings.stored = StoredSignIn.Voided
        assertEquals(GateState.SignIn(VOIDED_NOTICE), gate().state.value)
        settings.stored = StoredSignIn.Locked
        assertEquals(GateState.Locked(), gate().state.value)
        settings.stored = StoredSignIn.Ready(CONFIG)
        assertEquals(home, gate().state.value)
    }

    @Test
    fun `a successful unlock opens home`() {
        settings.stored = StoredSignIn.Locked
        val gate = gate()

        gate.onUnlocked()

        assertEquals(home, gate.state.value)
    }

    @Test
    fun `an unlock the key still refuses asks for the PIN`() {
        settings.stored = StoredSignIn.Locked
        lock.unlockResult = StoredSignIn.Locked
        val gate = gate()

        gate.onUnlocked()

        assertEquals(GateState.Locked(message = UNLOCK_WITH_CREDENTIAL), gate.state.value)
    }

    @Test
    fun `a voided key goes back to sign-in with the notice`() {
        settings.stored = StoredSignIn.Locked
        lock.unlockResult = StoredSignIn.Voided
        val gate = gate()

        gate.onUnlocked()

        assertEquals(GateState.SignIn(VOIDED_NOTICE), gate.state.value)
    }

    @Test
    fun `an unlock with nothing stored goes to sign-in`() {
        settings.stored = StoredSignIn.Locked
        lock.unlockResult = StoredSignIn.None
        val gate = gate()

        gate.onUnlocked()

        assertEquals(GateState.SignIn(), gate.state.value)
    }

    @Test
    fun `a failed prompt stays locked and shows why`() {
        settings.stored = StoredSignIn.Locked
        val gate = gate()

        gate.onUnlockFailed("Too many attempts")
        assertEquals(GateState.Locked(message = "Too many attempts"), gate.state.value)

        gate.onUnlockFailed(null)
        assertEquals(GateState.Locked(), gate.state.value)
    }

    @Test
    fun `unlock calls outside the lock screen are ignored`() {
        val gate = gate()

        gate.onUnlocked()
        gate.onUnlockFailed("x")

        assertEquals(GateState.SignIn(), gate.state.value)
        assertEquals(0, lock.unlocks)
    }

    @Test
    fun `a sign-in waiting to be sealed asks to confirm, then opens home`() {
        val gate = gate()
        lock.unsaved = true

        gate.onSignedIn(CONFIG)
        assertEquals(GateState.Locked(protect = true), gate.state.value)

        gate.onUnlocked()
        assertEquals(home, gate.state.value)
    }

    @Test
    fun `a sign-in already sealed opens home`() {
        val gate = gate()

        gate.onSignedIn(CONFIG)

        assertEquals(home, gate.state.value)
    }

    @Test
    fun `without the lock a sign-in opens home directly`() {
        lock.canLock = false
        lock.unsaved = true
        val gate = gate()

        gate.onSignedIn(CONFIG)

        assertEquals(GateState.Home(CONFIG, lockAvailable = false, lockEnabled = true), gate.state.value)
    }

    @Test
    fun `a minute away re-locks over home`() {
        settings.stored = StoredSignIn.Ready(CONFIG)
        val gate = gate()

        gate.away(30_000)
        assertEquals(home, gate.state.value)

        gate.away(60_000)
        assertEquals(GateState.Locked(overlay = true), gate.state.value)
        assertEquals(1, lock.forgets)

        gate.onUnlocked()
        assertEquals(home, gate.state.value)
    }

    @Test
    fun `time away on the lock screen keeps it as it is`() {
        settings.stored = StoredSignIn.Locked
        val gate = gate()

        gate.away(120_000)

        assertEquals(GateState.Locked(), gate.state.value)
    }

    @Test
    fun `the lock switch turns off and on`() {
        settings.stored = StoredSignIn.Ready(CONFIG)
        val gate = gate()

        gate.setLockEnabled(false)
        assertEquals(home.copy(lockEnabled = false), gate.state.value)

        gate.setLockEnabled(true)
        assertEquals(home, gate.state.value)
    }

    @Test
    fun `a switch the key refuses stays as it was`() {
        settings.stored = StoredSignIn.Ready(CONFIG)
        lock.refuseSwitch = true
        val gate = gate()

        gate.setLockEnabled(false)

        assertEquals(home, gate.state.value)
    }

    @Test
    fun `signing out goes to sign-in`() {
        settings.stored = StoredSignIn.Ready(CONFIG)
        val gate = gate()

        gate.onSignedOut()

        assertEquals(GateState.SignIn(), gate.state.value)
    }
}
