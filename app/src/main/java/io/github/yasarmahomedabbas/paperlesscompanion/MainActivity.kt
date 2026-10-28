package io.github.yasarmahomedabbas.paperlesscompanion

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentSource
import io.github.yasarmahomedabbas.paperlesscompanion.ui.home.HomeScreen
import io.github.yasarmahomedabbas.paperlesscompanion.ui.home.HomeViewModel
import io.github.yasarmahomedabbas.paperlesscompanion.ui.login.LoginScreen
import io.github.yasarmahomedabbas.paperlesscompanion.ui.login.LoginViewModel
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.PaperlessTheme

class MainActivity : ComponentActivity() {
    private val container get() = (application as PaperlessApp).container

    /** Files shared into the app, waiting for the home screen (and a sign-in, if needed). */
    private var shared by mutableStateOf<List<DocumentSource>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) receive(intent)
        setContent {
            PaperlessTheme {
                var config by remember { mutableStateOf(container.settings.load()) }
                val signedIn = config
                if (signedIn == null) {
                    LoginScreen(
                        viewModel = viewModel<LoginViewModel>(factory = container.loginViewModelFactory),
                        onSignedIn = { config = it },
                    )
                } else {
                    HomeScreen(
                        viewModel = viewModel<HomeViewModel>(key = signedIn.baseUrl, factory = container.homeViewModelFactory),
                        documents = container.documents,
                        shared = shared,
                        onSharedConsumed = { shared = emptyList() },
                        onSignedOut = { config = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receive(intent)
    }

    private fun receive(intent: Intent) {
        val uris = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }
        if (uris.isNotEmpty()) shared = shared + uris.map { container.documents.from(it.toString(), "Shared document") }
    }
}
