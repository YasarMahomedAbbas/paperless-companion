package io.github.yasarmahomedabbas.paperlesscompanion

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentSource
import io.github.yasarmahomedabbas.paperlesscompanion.ui.home.HomeScreen
import io.github.yasarmahomedabbas.paperlesscompanion.ui.home.HomeViewModel
import io.github.yasarmahomedabbas.paperlesscompanion.ui.login.LoginScreen
import io.github.yasarmahomedabbas.paperlesscompanion.ui.login.LoginViewModel
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.PaperlessTheme
import io.github.yasarmahomedabbas.paperlesscompanion.ui.updates.UpdatesScreen
import io.github.yasarmahomedabbas.paperlesscompanion.ui.updates.UpdatesViewModel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val container get() = (application as PaperlessApp).container

    /** Files shared into the app, waiting for the home screen (and a sign-in, if needed). */
    private var shared by mutableStateOf<List<DocumentSource>>(emptyList())

    /** The Updates screen is open (from the menu or the "Update ready" notification). */
    private var showUpdates by mutableStateOf(false)

    // "Update ready" can't be posted without it; whatever the answer, it's only asked once.
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) receive(intent)
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
                var config by remember { mutableStateOf(container.settings.load()) }
                val signedIn = config
                if (signedIn == null) {
                    LoginScreen(
                        viewModel = viewModel<LoginViewModel>(factory = container.loginViewModelFactory),
                        onSignedIn = { config = it },
                    )
                } else if (showUpdates) {
                    BackHandler { showUpdates = false }
                    UpdatesScreen(
                        viewModel = viewModel<UpdatesViewModel>(factory = container.updatesViewModelFactory),
                        onAllow = { startActivity(container.installer.permissionIntent()) },
                        onBack = { showUpdates = false },
                    )
                } else {
                    HomeScreen(
                        viewModel = viewModel<HomeViewModel>(key = signedIn.baseUrl, factory = container.homeViewModelFactory),
                        documents = container.documents,
                        shared = shared,
                        onSharedConsumed = { shared = emptyList() },
                        onSignedOut = { config = null },
                        onOpenUpdates = { showUpdates = true },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receive(intent)
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
        if (uris.isNotEmpty()) shared = shared + uris.map { container.documents.from(it.toString(), "Shared document") }
    }
}
