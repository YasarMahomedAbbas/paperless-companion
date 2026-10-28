package io.github.yasarmahomedabbas.paperlesscompanion

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import io.github.yasarmahomedabbas.paperlesscompanion.ui.viewer.ViewerEvent
import io.github.yasarmahomedabbas.paperlesscompanion.ui.viewer.ViewerScreen
import io.github.yasarmahomedabbas.paperlesscompanion.ui.viewer.ViewerViewModel
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.yasarmahomedabbas.paperlesscompanion.ui.documents.DocumentsScreen
import io.github.yasarmahomedabbas.paperlesscompanion.ui.documents.DocumentsViewModel
import io.github.yasarmahomedabbas.paperlesscompanion.ui.documents.UploadsViewModel
import io.github.yasarmahomedabbas.paperlesscompanion.ui.lock.GateState
import io.github.yasarmahomedabbas.paperlesscompanion.ui.lock.GateViewModel
import io.github.yasarmahomedabbas.paperlesscompanion.ui.lock.LockScreen
import io.github.yasarmahomedabbas.paperlesscompanion.ui.lock.askToUnlock
import io.github.yasarmahomedabbas.paperlesscompanion.ui.login.LoginScreen
import io.github.yasarmahomedabbas.paperlesscompanion.ui.login.LoginViewModel
import io.github.yasarmahomedabbas.paperlesscompanion.ui.settings.SettingsScreen
import io.github.yasarmahomedabbas.paperlesscompanion.ui.settings.SettingsViewModel
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.PaperlessTheme
import io.github.yasarmahomedabbas.paperlesscompanion.ui.updates.UpdatesScreen
import io.github.yasarmahomedabbas.paperlesscompanion.ui.updates.UpdatesViewModel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/** A FragmentActivity because the biometric prompt is a fragment. */
class MainActivity : FragmentActivity() {
    private val container get() = (application as PaperlessApp).container

    private val gate by lazy { ViewModelProvider(this, container.gateViewModelFactory)[GateViewModel::class.java] }

    /** The last unlocked home, kept composed under the lock screen when the app locks again. */
    private var lastHome: GateState.Home? = null

    /** The screens behind the gate: Documents → Settings → Updates (layouts.md § Navigation). */
    private enum class Screen { DOCUMENTS, SETTINGS, UPDATES }

    /** Which one is open; the "Update ready" notification opens Updates directly. */
    private var screen by mutableStateOf(Screen.DOCUMENTS)

    /** Counts sign-outs, so signing in again (even to the same server) starts the screens afresh. */
    private var session by mutableIntStateOf(0)

    // "Update ready" can't be posted without it; whatever the answer, it's only asked once.
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let {
            session = it.getInt(KEY_SESSION)
            screen = Screen.entries[it.getInt(KEY_SCREEN)]
        }
        // One dark theme, so the system bars always get light icons, whatever the system setting.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        if (savedInstanceState == null) receive(intent)
        // Leaving for the PIN screen or the scanner and coming back within the timeout doesn't re-lock.
        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> gate.onBackground()
                    Lifecycle.Event.ON_START -> gate.onForeground()
                    else -> Unit
                }
            },
        )
        // Debug builds aren't signed with the release key, so a release could never replace them.
        if (!BuildConfig.DEBUG && savedInstanceState == null) {
            container.checkForUpdatesNow()
            askForNotificationsOnce()
        }
        // Android's install confirmation, started from here (it needs an activity). Only while the
        // app is on screen: Android blocks an activity start from the background, so one that
        // arrives then waits until the user comes back instead of being lost.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.installer.confirmation.filterNotNull().collect {
                    container.installer.consumeConfirmation(it)
                    startActivity(it)
                }
            }
        }
        setContent {
            PaperlessTheme {
                val state by gate.state.collectAsState()
                val pending by gate.pending.collectAsState()
                val current = state
                if (current is GateState.Home) lastHome = current
                val locked = current as? GateState.Locked
                val home = current as? GateState.Home ?: lastHome.takeIf { locked?.overlay == true }
                // The document open in the viewer, kept across rotation; signing out closes it.
                var openDocumentId by rememberSaveable { mutableStateOf<Int?>(null) }
                // Counts each opening, so reopening a document gets a fresh viewer rather than the last one's
                // state (a stale failure, or another sign-in's document under the same id).
                var viewerOpens by rememberSaveable { mutableIntStateOf(0) }
                LaunchedEffect(current is GateState.SignIn) { if (current is GateState.SignIn) openDocumentId = null }

                Box(Modifier.fillMaxSize()) {
                    if (home != null) {
                        // Hidden from TalkBack too while the lock screen covers it.
                        Box(if (locked != null) Modifier.clearAndSetSemantics {} else Modifier) {
                            val key = "$session ${home.config.baseUrl}"
                            val uploads = viewModel<UploadsViewModel>(key = "uploads $key", factory = container.uploadsViewModelFactory)
                            // Keeps the list's scroll and sheets while the viewer covers it.
                            val saved = rememberSaveableStateHolder()
                            val documentId = openDocumentId
                            BackHandler(enabled = locked == null && screen != Screen.DOCUMENTS) {
                                screen = if (screen == Screen.UPDATES) Screen.SETTINGS else Screen.DOCUMENTS
                            }
                            when {
                                screen == Screen.DOCUMENTS && documentId != null -> {
                                    BackHandler(enabled = locked == null) { openDocumentId = null }
                                    val viewer = viewModel<ViewerViewModel>(key = "viewer-$documentId-$viewerOpens", factory = container.viewerViewModelFactory(documentId))
                                    val saveAs = rememberLauncherForActivityResult(SaveAs()) { uri -> viewer.saveTo(uri?.toString()) }
                                    ViewerScreen(
                                        viewModel = viewer,
                                        onBack = { openDocumentId = null },
                                        onShare = ::share,
                                        onPickSaveLocation = { saveAs.launch(it) },
                                        onOpenUrl = ::openUrl,
                                    )
                                }
                                screen == Screen.DOCUMENTS -> saved.SaveableStateProvider("documents $key") {
                                    DocumentsScreen(
                                        documents = viewModel<DocumentsViewModel>(key = "documents $key", factory = container.documentsViewModelFactory),
                                        uploads = uploads,
                                        files = container.documents,
                                        pending = if (locked == null) pending else emptyList(),
                                        onPendingConsumed = gate::onPendingConsumed,
                                        onDocuments = gate::addPending,
                                        onOpenSettings = { screen = Screen.SETTINGS },
                                        onOpenDocument = { viewerOpens++; openDocumentId = it },
                                    )
                                }
                                screen == Screen.SETTINGS -> {
                                    val settings = viewModel<SettingsViewModel>(key = "settings $key", factory = container.settingsViewModelFactory)
                                    SettingsScreen(
                                        viewModel = settings,
                                        lockAvailable = home.lockAvailable,
                                        lockEnabled = home.lockEnabled,
                                        onLockChange = ::changeLock,
                                        onOpenUpdates = { screen = Screen.UPDATES },
                                        onSignOut = {
                                            settings.signOut()
                                            uploads.clear()
                                            openDocumentId = null
                                            screen = Screen.DOCUMENTS
                                            session++
                                            gate.onSignedOut()
                                        },
                                        onBack = { screen = Screen.DOCUMENTS },
                                    )
                                }
                                else -> UpdatesScreen(
                                    viewModel = viewModel<UpdatesViewModel>(factory = container.updatesViewModelFactory),
                                    onAllow = { startActivity(container.installer.permissionIntent()) },
                                    onBack = { screen = Screen.SETTINGS },
                                )
                            }
                        }
                    }
                    if (locked != null) {
                        LockScreen(protect = locked.protect, message = locked.message, onUnlock = ::unlock)
                    }
                    if (current is GateState.SignIn) {
                        LoginScreen(
                            viewModel = viewModel<LoginViewModel>(factory = container.loginViewModelFactory),
                            notice = current.notice,
                            onSignedIn = gate::onSignedIn,
                        )
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // The ViewModels outlive a rotation, so the keys that find them must too.
        outState.putInt(KEY_SESSION, session)
        outState.putInt(KEY_SCREEN, screen.ordinal)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receive(intent)
    }

    private fun unlock() {
        askToUnlock("Unlock Paperless Scan", onSuccess = gate::onUnlocked, onFailure = gate::onUnlockFailed)
    }

    private fun changeLock(enabled: Boolean) {
        askToUnlock(
            if (enabled) "Lock with fingerprint" else "Stop locking with fingerprint",
            onSuccess = { gate.setLockEnabled(enabled) },
            onFailure = {},
        )
    }

    /** Hands a document file to the app the user picks, with read access to that one file. */
    private fun share(event: ViewerEvent.Share) {
        val uri = Uri.parse(event.location)
        val send = Intent(Intent.ACTION_SEND)
            .setType(event.mimeType ?: "application/octet-stream")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(event.name, uri)
        startActivity(Intent.createChooser(send, null))
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            // No browser on the phone: nothing to open it in.
        }
    }

    private fun askForNotificationsOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || container.updateStore.askedForNotifications()) return
        container.updateStore.markAskedForNotifications()
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun receive(intent: Intent) {
        // Shown only once signed in: the login screen ignores it.
        if (intent.getBooleanExtra(AppContainer.EXTRA_OPEN_UPDATES, false)) screen = Screen.UPDATES
        val uris = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }
        gate.addPending(uris.map { container.documents.from(it.toString(), "Shared document") })
    }

    private companion object {
        const val KEY_SESSION = "session"
        const val KEY_SCREEN = "screen"
    }
}

/** The system's "Save as" for a file whose name and type are known only at launch. */
private class SaveAs : ActivityResultContract<ViewerEvent.PickSaveLocation, Uri?>() {
    override fun createIntent(context: Context, input: ViewerEvent.PickSaveLocation): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.mimeType ?: "application/octet-stream")
            .putExtra(Intent.EXTRA_TITLE, input.name)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent?.data.takeIf { resultCode == Activity.RESULT_OK }
}
