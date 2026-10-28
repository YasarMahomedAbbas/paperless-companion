package io.github.yasarmahomedabbas.paperlesscompanion

import android.app.Application
import android.content.Context
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.yasarmahomedabbas.paperlesscompanion.data.KeystoreSettingsRepository
import io.github.yasarmahomedabbas.paperlesscompanion.data.PaperlessApi
import io.github.yasarmahomedabbas.paperlesscompanion.data.UriDocuments
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.DocumentFiles
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.DocumentServer
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.SettingsRepository
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.DocumentLinkUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.LoginUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.UploadDocumentUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.ui.home.HomeViewModel
import io.github.yasarmahomedabbas.paperlesscompanion.ui.login.LoginViewModel

/** Wires the data layer into the domain use cases, and those into the ViewModels. */
class AppContainer(context: Context) {
    val settings: SettingsRepository = KeystoreSettingsRepository(context)
    val documents: DocumentFiles = UriDocuments(context.contentResolver)
    private val server: DocumentServer = PaperlessApi()

    val loginViewModelFactory = viewModelFactory {
        initializer { LoginViewModel(LoginUseCase(server, settings)) }
    }
    val homeViewModelFactory = viewModelFactory {
        initializer { HomeViewModel(UploadDocumentUseCase(server, settings), DocumentLinkUseCase(server, settings), settings) }
    }
}

class PaperlessApp : Application() {
    val container by lazy { AppContainer(this) }
}
