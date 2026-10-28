package io.github.yasarmahomedabbas.paperlesscompanion

import android.app.Application
import android.app.KeyguardManager
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.yasarmahomedabbas.paperlesscompanion.data.KeystoreTokenVault
import io.github.yasarmahomedabbas.paperlesscompanion.data.PaperlessApi
import io.github.yasarmahomedabbas.paperlesscompanion.data.PrefsStore
import io.github.yasarmahomedabbas.paperlesscompanion.data.SealedSettingsRepository
import io.github.yasarmahomedabbas.paperlesscompanion.data.UriDocuments
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.AppLockRepository
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.PaperlessRepository
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.SettingsRepository
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.AppLock
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.LoginUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.UploadDocumentUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.ui.home.HomeViewModel
import io.github.yasarmahomedabbas.paperlesscompanion.ui.lock.GateViewModel
import io.github.yasarmahomedabbas.paperlesscompanion.ui.login.LoginViewModel

/** Wires the data layer into the domain use cases, and those into the ViewModels. */
class AppContainer(context: Context) {
    private val vault = KeystoreTokenVault(context.getSystemService(KeyguardManager::class.java))
    private val sealed = SealedSettingsRepository(
        PrefsStore(context.getSharedPreferences("server", Context.MODE_PRIVATE)),
        vault,
        vault::isDeviceSecure,
    )
    val settings: SettingsRepository = sealed
    val lock: AppLockRepository = sealed
    val documents = UriDocuments(context.contentResolver)
    private val paperless: PaperlessRepository = PaperlessApi()

    val gateViewModelFactory = viewModelFactory {
        initializer { GateViewModel(settings, lock, AppLock(lock, SystemClock::elapsedRealtime)) }
    }
    val loginViewModelFactory = viewModelFactory {
        initializer { LoginViewModel(LoginUseCase(paperless, settings)) }
    }
    val homeViewModelFactory = viewModelFactory {
        initializer { HomeViewModel(UploadDocumentUseCase(paperless, settings), settings) }
    }
}

class PaperlessApp : Application() {
    val container by lazy { AppContainer(this) }
}
