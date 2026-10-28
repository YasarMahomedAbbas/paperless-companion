package io.github.yasarmahomedabbas.paperlesscompanion

import android.app.Application
import android.app.KeyguardManager
import android.content.Context
import android.os.SystemClock
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.yasarmahomedabbas.paperlesscompanion.data.KeystoreTokenVault
import io.github.yasarmahomedabbas.paperlesscompanion.data.PaperlessApi
import io.github.yasarmahomedabbas.paperlesscompanion.data.PaperlessLibrary
import io.github.yasarmahomedabbas.paperlesscompanion.data.PrefsStore
import io.github.yasarmahomedabbas.paperlesscompanion.data.SealedSettingsRepository
import io.github.yasarmahomedabbas.paperlesscompanion.data.UriDocuments
import io.github.yasarmahomedabbas.paperlesscompanion.data.thumbnailImageLoader
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.AppLockRepository
import io.github.yasarmahomedabbas.paperlesscompanion.data.update.GitHubReleases
import io.github.yasarmahomedabbas.paperlesscompanion.data.update.HttpApkDownloader
import io.github.yasarmahomedabbas.paperlesscompanion.data.update.PackageInstallerUpdates
import io.github.yasarmahomedabbas.paperlesscompanion.data.update.PrefsUpdateStore
import io.github.yasarmahomedabbas.paperlesscompanion.data.update.UpdateNotifier
import io.github.yasarmahomedabbas.paperlesscompanion.data.update.UpdateSchedule
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.ApkDownloader
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.DocumentFiles
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.DocumentLibrary
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.DocumentServer
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.ReleaseSource
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.SettingsRepository
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.AppLock
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.BrowseDocumentsUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.LoadLabelsUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.UpdateStore
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.CheckForUpdateUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.DocumentLinkUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.LoginUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.UploadDocumentUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.ui.documents.DocumentsViewModel
import io.github.yasarmahomedabbas.paperlesscompanion.ui.documents.UploadsViewModel
import io.github.yasarmahomedabbas.paperlesscompanion.ui.lock.GateViewModel
import io.github.yasarmahomedabbas.paperlesscompanion.ui.login.LoginViewModel
import io.github.yasarmahomedabbas.paperlesscompanion.ui.settings.SettingsViewModel
import io.github.yasarmahomedabbas.paperlesscompanion.ui.updates.UpdatesViewModel
import java.io.File

/** Wires the data layer into the domain use cases, and those into the ViewModels. */
class AppContainer(private val context: Context) {
    private val vault = KeystoreTokenVault(context.getSystemService(KeyguardManager::class.java))
    private val sealed = SealedSettingsRepository(
        PrefsStore(context.getSharedPreferences("server", Context.MODE_PRIVATE)),
        vault,
        vault::isDeviceSecure,
    )
    val settings: SettingsRepository = sealed
    val lock: AppLockRepository = sealed
    val documents: DocumentFiles = UriDocuments(context.contentResolver)
    private val server: DocumentServer = PaperlessApi()
    // The documents already on the server. The document screens (#10, #11) build their use cases from it too.
    private val library: DocumentLibrary = PaperlessLibrary(files = File(context.cacheDir, "documents"))
    /** Coil's loader for the whole app, so a screen only writes `AsyncImage(model = document.thumbnail, ...)`. */
    val imageLoader by lazy { thumbnailImageLoader(context, settings, PaperlessApi.defaultClient()) }

    val gateViewModelFactory = viewModelFactory {
        initializer { GateViewModel(settings, lock, AppLock(lock, SystemClock::elapsedRealtime)) }
    }
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
    val documentsViewModelFactory = viewModelFactory {
        initializer { DocumentsViewModel(BrowseDocumentsUseCase(library, settings), LoadLabelsUseCase(library, settings)) }
    }
    val uploadsViewModelFactory = viewModelFactory {
        initializer { UploadsViewModel(UploadDocumentUseCase(server, settings), DocumentLinkUseCase(server, settings)) }
    }
    val settingsViewModelFactory = viewModelFactory {
        initializer { SettingsViewModel(settings, updateStore, installer::installedVersionCode) }
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

class PaperlessApp : Application(), SingletonImageLoader.Factory {
    val container by lazy { AppContainer(this) }

    override fun newImageLoader(context: PlatformContext): ImageLoader = container.imageLoader

    override fun onCreate() {
        super.onCreate()
        container.notifier.createChannel()
        // Debug builds aren't signed with the release key, so a release could never replace them.
        if (!BuildConfig.DEBUG) UpdateSchedule.daily(this)
    }
}
