package io.github.yasarmahomedabbas.paperlesscompanion.ui.lock

import androidx.lifecycle.ViewModel
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerConfig
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.StoredSignIn
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.AppLockRepository
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.SettingsRepository
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.AppLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Which of sign-in, the lock screen or home the app shows. */
sealed interface GateState {
    data class SignIn(val notice: String? = null) : GateState

    /** Ask for the unlock. [overlay]: Home stays composed underneath (a re-lock), [protect]: sealing a fresh sign-in. */
    data class Locked(val overlay: Boolean = false, val protect: Boolean = false, val message: String? = null) : GateState

    data class Home(val config: ServerConfig, val lockAvailable: Boolean, val lockEnabled: Boolean) : GateState
}

class GateViewModel(
    private val settings: SettingsRepository,
    private val lock: AppLockRepository,
    private val appLock: AppLock,
) : ViewModel() {
    private val _state = MutableStateFlow(
        when (val stored = settings.read()) {
            StoredSignIn.None -> GateState.SignIn()
            StoredSignIn.Voided -> GateState.SignIn(VOIDED_NOTICE)
            StoredSignIn.Locked -> GateState.Locked()
            is StoredSignIn.Ready -> home(stored.config)
        },
    )
    val state: StateFlow<GateState> = _state.asStateFlow()

    /** The phone's prompt succeeded. */
    fun onUnlocked() {
        val locked = _state.value as? GateState.Locked ?: return
        _state.value = when (val stored = lock.unlock()) {
            is StoredSignIn.Ready -> home(stored.config)
            // The prompt passed but the key still refuses, e.g. a weak face unlock on older Android.
            StoredSignIn.Locked -> locked.copy(message = UNLOCK_WITH_CREDENTIAL)
            StoredSignIn.Voided -> GateState.SignIn(VOIDED_NOTICE)
            StoredSignIn.None -> GateState.SignIn()
        }
    }

    /** The prompt was cancelled ([message] null) or gave up with [message]. */
    fun onUnlockFailed(message: String?) {
        _state.update { if (it is GateState.Locked) it.copy(message = message) else it }
    }

    fun onSignedIn(config: ServerConfig) {
        _state.value = if (appLock.isActive && lock.hasUnsavedSignIn()) GateState.Locked(protect = true) else home(config)
    }

    fun onBackground() = appLock.onBackground()

    fun onForeground() {
        if (appLock.onForeground()) {
            _state.update { if (it is GateState.Home) GateState.Locked(overlay = true) else it }
        }
    }

    /** Call after a successful prompt. Leaves the switch as it was if the phone still refuses. */
    fun setLockEnabled(enabled: Boolean) {
        val home = _state.value as? GateState.Home ?: return
        try {
            lock.setEnabled(enabled)
        } catch (e: IllegalStateException) {
            // The key refused despite the prompt; the switch shows the unchanged setting.
        }
        _state.value = home(home.config)
    }

    fun onSignedOut() {
        _state.value = GateState.SignIn()
    }

    private fun home(config: ServerConfig) = GateState.Home(config, lock.canLock(), lock.isEnabled())

    companion object {
        const val VOIDED_NOTICE = "Your fingerprints or screen lock changed, so sign in again."
        const val UNLOCK_WITH_CREDENTIAL = "Unlock with your PIN or pattern"
    }
}
