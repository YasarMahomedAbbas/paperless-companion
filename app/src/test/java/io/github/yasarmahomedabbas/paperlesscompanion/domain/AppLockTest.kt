package io.github.yasarmahomedabbas.paperlesscompanion.domain

import io.github.yasarmahomedabbas.paperlesscompanion.FakeLock
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.AppLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockTest {
    private val lock = FakeLock()
    private var now = 1_000L
    private val appLock = AppLock(lock, { now })

    private fun away(millis: Long): Boolean {
        appLock.onBackground()
        now += millis
        return appLock.onForeground()
    }

    @Test
    fun `active only with a screen lock and the switch on`() {
        assertTrue(appLock.isActive)
        lock.switchOn = false
        assertFalse(appLock.isActive)
        lock.switchOn = true
        lock.canLock = false
        assertFalse(appLock.isActive)
    }

    @Test
    fun `under a minute away does not lock`() {
        assertFalse(away(59_999))
        assertEquals(0, lock.forgets)
    }

    @Test
    fun `a minute or more away locks and forgets the token`() {
        assertTrue(away(60_000))
        assertEquals(1, lock.forgets)
    }

    @Test
    fun `coming back without having left does not lock`() {
        now += 120_000
        assertFalse(appLock.onForeground())
    }

    @Test
    fun `each time away counts on its own`() {
        assertTrue(away(60_000))
        now += 120_000
        assertFalse(appLock.onForeground())
        assertEquals(1, lock.forgets)
    }

    @Test
    fun `never locks when the lock is off or the phone has no screen lock`() {
        lock.switchOn = false
        assertFalse(away(600_000))
        lock.switchOn = true
        lock.canLock = false
        assertFalse(away(600_000))
        assertEquals(0, lock.forgets)
    }
}
