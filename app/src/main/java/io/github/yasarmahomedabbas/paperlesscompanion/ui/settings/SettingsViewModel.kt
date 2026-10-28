package io.github.yasarmahomedabbas.paperlesscompanion.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.SettingsRepository
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.UpdateStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Settings: the server the app is signed in to, whether a newer release waits (the Updates row's
 * "0.4.0 ready" pill) and Sign out. The fingerprint switch stays with the gate, which owns the lock.
 */
class SettingsViewModel(
    private val settings: SettingsRepository,
    updates: UpdateStore,
    installedVersionCode: () -> Long,
) : ViewModel() {
    val serverUrl: String = settings.load()?.baseUrl.orEmpty()

    /** The version of a release newer than the installed one, or null. */
    val updateReady: StateFlow<String?> = updates.offer
        .map { offer -> offer?.takeIf { it.isNewerThan(installedVersionCode()) }?.version }
        .stateIn(viewModelScope, SharingStarted.Eagerly, updates.offer.value?.takeIf { it.isNewerThan(installedVersionCode()) }?.version)

    /** Forgets the server and its sign-in. */
    fun signOut() = settings.clear()
}
