package io.github.yasarmahomedabbas.paperlesscompanion

import android.app.Application
import android.content.Context
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.yasarmahomedabbas.paperlesscompanion.data.KeystoreSettingsRepository
import io.github.yasarmahomedabbas.paperlesscompanion.data.PaperlessApi
import io.github.yasarmahomedabbas.paperlesscompanion.data.UriDocuments
import io.github.yasarmahomedabbas.paperlesscompanion.data.update.GitHubReleases
import io.github.yasarmahomedabbas.paperlesscompanion.data.update.HttpApkDownloader
import io.github.yasarmahomedabbas.paperlesscompanion.data.update.PackageInstallerUpdates
import io.github.yasarmahomedabbas.paperlesscompanion.data.update.PrefsUpdateStore
import io.github.yasarmahomedabbas.paperlesscompanion.data.update.UpdateNotifier
import io.github.yasarmahomedabbas.paperlesscompanion.data.update.UpdateSchedule
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.ApkDownloader
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.DocumentFiles
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.DocumentServer
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.ReleaseSource
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.SettingsRepository
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.UpdateStore
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.CheckForUpdateUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.DocumentLinkUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.LoginUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.UploadDocumentUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.ui.home.HomeViewModel
import io.github.yasarmahomedabbas.paperlesscompanion.ui.login.LoginViewModel
import io.github.yasarmahomedabbas.paperlesscompanion.ui.updates.UpdatesViewModel
import java.io.File

/** Wires the data layer into the domain use cases, and those into the ViewModels. */
class AppContainer(private val context: Context) {
    val settings: SettingsRepository = KeystoreSettingsRepository(context)
    val documents: DocumentFiles = UriDocuments(context.contentResolver)
    private val server: DocumentServer = PaperlessApi()

    // Self-update from GitHub Releases.
    val updateStore: UpdateStore = PrefsUpdateStore(context)
    private val releases: ReleaseSource = GitHubReleases()
    private val downloader: ApkDownloader = HttpApkDownloader(File(context.cacheDir, "updates"))
    val installer = PackageInstallerUpdates(context, updateStore)
    val notifier = UpdateNotifier(context, MainActivity::class.java)
    val checkForUpdate = CheckForUpdateUseCase(releases, updateStore, installer::installedVersionCode, notifier::announce)

    /** One update check as soon as there's a network (the activity asks on each fresh launch). */
    fun checkForUpdatesNow() = UpdateSchedule.now(context)

    val loginViewModelFactory = viewModelFactory {
        initializer { LoginViewModel(LoginUseCase(server, settings)) }
    }
    val homeViewModelFactory = viewModelFactory {
        initializer { HomeViewModel(UploadDocumentUseCase(server, settings), DocumentLinkUseCase(server, settings), settings) }
    }
    val updatesViewModelFactory = viewModelFactory {
        initializer {
            val installed = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
            UpdatesViewModel(installed, updateStore, downloader, installer, checkForUpdate)
        }
    }

    companion object {
        /** On the activity's intent: open the Updates screen (the "Update ready" notification's tap). */
        const val EXTRA_OPEN_UPDATES = UpdateNotifier.EXTRA_OPEN_UPDATES
    }
}

class PaperlessApp : Application() {
    val container by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        container.notifier.createChannel()
        // Debug builds aren't signed with the release key, so a release could never replace them.
        if (!BuildConfig.DEBUG) UpdateSchedule.daily(this)
    }
}
