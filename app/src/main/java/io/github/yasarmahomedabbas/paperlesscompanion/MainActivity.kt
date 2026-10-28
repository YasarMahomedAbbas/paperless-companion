package io.github.yasarmahomedabbas.paperlesscompanion

import android.Manifest
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

    /** The Updates screen is open (from the menu or the "Update ready" notification). */
    private var showUpdates by mutableStateOf(false)

    // "Update ready" can't be posted without it; whatever the answer, it's only asked once.
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

                Box(Modifier.fillMaxSize()) {
                    if (home != null) {
                        // Hidden from TalkBack too while the lock screen covers it.
                        Box(if (locked != null) Modifier.clearAndSetSemantics {} else Modifier) {
                            if (showUpdates) {
                                BackHandler(enabled = locked == null) { showUpdates = false }
                                UpdatesScreen(
                                    viewModel = viewModel<UpdatesViewModel>(factory = container.updatesViewModelFactory),
                                    onAllow = { startActivity(container.installer.permissionIntent()) },
                                    onBack = { showUpdates = false },
                                )
                            } else {
                                DocumentsScreen(
                                    documents = viewModel<DocumentsViewModel>(
                                        key = "documents ${home.config.baseUrl}",
                                        factory = container.documentsViewModelFactory,
                                    ),
                                    uploads = viewModel<UploadsViewModel>(
                                        key = "uploads ${home.config.baseUrl}",
                                        factory = container.uploadsViewModelFactory,
                                    ),
                                    files = container.documents,
                                    pending = if (locked == null) pending else emptyList(),
                                    onPendingConsumed = gate::onPendingConsumed,
                                    onDocuments = gate::addPending,
                                    // Until Settings lands (checkpoint 5), the gear opens Updates.
                                    onOpenSettings = { showUpdates = true },
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

    private fun askForNotificationsOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || container.updateStore.askedForNotifications()) return
        container.updateStore.markAskedForNotifications()
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun receive(intent: Intent) {
        // Shown only once signed in: the login screen ignores it.
        if (intent.getBooleanExtra(AppContainer.EXTRA_OPEN_UPDATES, false)) showUpdates = true
        val uris = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }
        gate.addPending(uris.map { container.documents.from(it.toString(), "Shared document") })
    }
}
