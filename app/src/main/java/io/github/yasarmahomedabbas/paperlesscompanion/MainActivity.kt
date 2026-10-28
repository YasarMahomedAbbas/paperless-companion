package io.github.yasarmahomedabbas.paperlesscompanion

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentSource
import io.github.yasarmahomedabbas.paperlesscompanion.ui.home.HomeScreen
import io.github.yasarmahomedabbas.paperlesscompanion.ui.home.HomeViewModel
import io.github.yasarmahomedabbas.paperlesscompanion.ui.lock.GateState
import io.github.yasarmahomedabbas.paperlesscompanion.ui.lock.GateViewModel
import io.github.yasarmahomedabbas.paperlesscompanion.ui.lock.LockScreen
import io.github.yasarmahomedabbas.paperlesscompanion.ui.lock.askToUnlock
import io.github.yasarmahomedabbas.paperlesscompanion.ui.login.LoginScreen
import io.github.yasarmahomedabbas.paperlesscompanion.ui.login.LoginViewModel
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.PaperlessTheme

/** A FragmentActivity because the biometric prompt is a fragment. */
class MainActivity : FragmentActivity() {
    private val container get() = (application as PaperlessApp).container

    private val gate by lazy { ViewModelProvider(this, container.gateViewModelFactory)[GateViewModel::class.java] }

    /** Files shared, scanned, photographed or picked, waiting for an unlocked home screen (and a sign-in, if needed). */
    private var pending by mutableStateOf<List<DocumentSource>>(emptyList())

    /** The last unlocked home, kept composed under the lock screen when the app locks again. */
    private var lastHome: GateState.Home? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
        setContent {
            PaperlessTheme {
                val state by gate.state.collectAsState()
                val current = state
                if (current is GateState.Home) lastHome = current
                val locked = current as? GateState.Locked
                val home = current as? GateState.Home ?: lastHome.takeIf { locked?.overlay == true }

                Box(Modifier.fillMaxSize()) {
                    if (home != null) {
                        // Hidden from TalkBack too while the lock screen covers it.
                        Box(if (locked != null) Modifier.clearAndSetSemantics {} else Modifier) {
                            HomeScreen(
                                viewModel = viewModel<HomeViewModel>(key = home.config.baseUrl, factory = container.homeViewModelFactory),
                                documents = container.documents,
                                pending = if (locked == null) pending else emptyList(),
                                onPendingConsumed = { pending = emptyList() },
                                onDocuments = { pending = pending + it },
                                lockAvailable = home.lockAvailable,
                                lockEnabled = home.lockEnabled,
                                onLockChange = ::changeLock,
                                onSignedOut = gate::onSignedOut,
                            )
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

    private fun receive(intent: Intent) {
        val uris = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }
        if (uris.isNotEmpty()) pending = pending + uris.map { container.documents.from(it, "Shared document") }
    }
}
