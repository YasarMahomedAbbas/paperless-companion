package io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase

import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.AppLockRepository
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** When the app locks again: after [timeout] or longer in the background, while the lock is active. */
class AppLock(
    private val lock: AppLockRepository,
    /** Milliseconds on a clock that keeps running while the phone sleeps, e.g. SystemClock::elapsedRealtime. */
    private val now: () -> Long,
    private val timeout: Duration = 1.minutes,
) {
    private var backgroundSince: Long? = null

    val isActive: Boolean get() = lock.canLock() && lock.isEnabled()

    fun onBackground() {
        backgroundSince = now()
    }

    /** True (and the token forgotten) when the app was away for [timeout] or longer while the lock is active. */
    fun onForeground(): Boolean {
        val since = backgroundSince ?: return false
        backgroundSince = null
        if (!isActive || now() - since < timeout.inWholeMilliseconds) return false
        lock.forget()
        return true
    }
}
