package io.github.yasarmahomedabbas.paperlesscompanion.ui.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.AppRelease
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.InstallEvent
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.UpdateException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.UpdateException.Kind
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.ApkDownloader
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.AppInstaller
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.UpdateStore
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.CheckForUpdateUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/** Where an update stands on the Updates screen. */
sealed interface Phase {
    data object Idle : Phase

    data class Downloading(val done: Long, val total: Long) : Phase

    /** Handed to Android: its confirmation, then the install itself. */
    data object Installing : Phase

    /** Installing updates isn't allowed for the app yet; the download is kept for the retry. */
    data object NeedsPermission : Phase

    /** Why it didn't work, in words. */
    data class Failed(val reason: String) : Phase
}

data class UpdatesUiState(
    val installedVersion: String,
    /** A release newer than the installed one, or null: "Up to date". */
    val offer: AppRelease?,
    val phase: Phase = Phase.Idle,
    /** *Check now* is on its way. */
    val checking: Boolean = false,
    /** The last *Check now* couldn't reach GitHub. */
    val checkFailed: Boolean = false,
) {
    /** Downloading or installing: keep the screen on, and no second *Install*. */
    val busy: Boolean get() = phase is Phase.Downloading || phase == Phase.Installing
}

/**
 * The Updates screen: the installed version, the newer one the check found with *Install*, the
 * download's progress with *Stop*, and why it didn't work in words. *Install* downloads and
 * verifies the file, then hands it to the installer (the signer check and Android's confirmation);
 * the outcome comes back on the installer's events.
 */
class UpdatesViewModel(
    installedVersion: String,
    store: UpdateStore,
    private val downloader: ApkDownloader,
    private val installer: AppInstaller,
    private val check: CheckForUpdateUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(UpdatesUiState(installedVersion, offer = null))
    val state: StateFlow<UpdatesUiState> = _state.asStateFlow()

    private var installed = installer.installedVersionCode()
    private var found: AppRelease? = store.offer.value
    private var job: Job? = null

    /** The verified download waiting for the permission. */
    private var kept: File? = null

    init {
        rebuild()
        viewModelScope.launch {
            store.offer.collect {
                found = it
                rebuild()
            }
        }
        viewModelScope.launch { installer.events.collect(::onEvent) }
    }

    /** Downloads, verifies and installs the offer. */
    fun install() {
        val offer = _state.value.offer ?: return
        if (_state.value.busy) return
        // The download clears every earlier one, so a kept file is gone either way.
        kept = null
        job = viewModelScope.launch {
            setPhase(Phase.Downloading(0, offer.apk.size))
            val apk = try {
                downloader.download(offer.apk) { done, total ->
                    // A late report after *Stop* never brings the progress back.
                    if (isActive) setPhase(Phase.Downloading(done, total))
                }
            } catch (e: UpdateException) {
                setPhase(Phase.Failed(reason(e)))
                return@launch
            }
            commit(apk)
        }
    }

    /** Stops a download; the part file goes with it. */
    fun stop() {
        if (_state.value.phase !is Phase.Downloading) return
        job?.cancel()
        setPhase(Phase.Idle)
    }

    /** Back on the screen (from the settings screen, say): installing may be allowed now. */
    fun resumed() {
        installed = installer.installedVersionCode()
        rebuild()
        val apk = kept ?: return
        if (_state.value.phase != Phase.NeedsPermission || !installer.canInstall()) return
        kept = null
        job = viewModelScope.launch { commit(apk) }
    }

    fun checkNow() {
        if (_state.value.checking) return
        _state.update { it.copy(checking = true, checkFailed = false) }
        viewModelScope.launch {
            val outcome = check.run()
            installed = installer.installedVersionCode()
            _state.update {
                it.copy(checking = false, checkFailed = outcome == CheckForUpdateUseCase.Outcome.TryAgain)
            }
            rebuild()
        }
    }

    private suspend fun commit(apk: File) {
        installer.clearEvents()
        setPhase(Phase.Installing)
        try {
            installer.install(apk)
        } catch (e: UpdateException) {
            if (e.kind == Kind.NEEDS_PERMISSION) {
                kept = apk
                setPhase(Phase.NeedsPermission)
            } else {
                setPhase(Phase.Failed(reason(e)))
            }
        }
    }

    private fun onEvent(event: InstallEvent?) {
        if (event == null || _state.value.phase != Phase.Installing) return
        when (event) {
            InstallEvent.Installed -> {
                installed = installer.installedVersionCode()
                setPhase(Phase.Idle)
            }
            is InstallEvent.Failed -> setPhase(Phase.Failed(refused(event.detail)))
        }
    }

    private fun setPhase(phase: Phase) {
        _state.update { it.copy(phase = phase) }
        rebuild()
    }

    /**
     * The offer counts only while it's newer than what's installed now (the store may still hold
     * one just installed). The phase stays while there's an offer, or while Android is installing.
     */
    private fun rebuild() {
        _state.update { ui ->
            val offer = found?.takeIf { it.isNewerThan(installed) }
            val phase = ui.phase.takeIf { offer != null || it == Phase.Installing } ?: Phase.Idle
            ui.copy(offer = offer, phase = phase)
        }
    }

    private fun reason(e: UpdateException): String = when (e.kind) {
        Kind.CHECKSUM -> "The download didn't match the release, so it wasn't installed."
        Kind.NOT_OURS -> "This update isn't signed with the app's key, so it wasn't installed."
        Kind.OFFLINE -> "Couldn't reach GitHub. Check the connection and try again."
        Kind.STORAGE -> "Not enough space to download the update."
        Kind.SERVER -> "GitHub didn't send the update. Try again later."
        Kind.REFUSED -> refused(e.message.orEmpty())
        Kind.NEEDS_PERMISSION -> "Allow this app to install updates, then try again."
    }

    private fun refused(detail: String): String =
        if (detail.isBlank()) "Android didn't install the update." else "Android didn't install the update: $detail"
}
