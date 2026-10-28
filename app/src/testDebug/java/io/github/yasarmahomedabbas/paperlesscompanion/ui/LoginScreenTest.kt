package io.github.yasarmahomedabbas.paperlesscompanion.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.yasarmahomedabbas.paperlesscompanion.FakeServer
import io.github.yasarmahomedabbas.paperlesscompanion.FakeSettings
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerConfig
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.LoginUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.ui.login.LoginScreen
import io.github.yasarmahomedabbas.paperlesscompanion.ui.login.LoginViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Runs the real sign-in screen on the JVM. It lives in testDebug because the ComponentActivity
// the compose rule launches is only in the debug manifest (ui-test-manifest). A plain
// Application, so the test doesn't start the app's own wiring (Keystore, WorkManager). No
// MainDispatcherRule: under Robolectric the ViewModel's scope runs on the main looper, which the
// compose rule drives while it waits.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LoginScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val viewModel = LoginViewModel(LoginUseCase(FakeServer(), FakeSettings()))

    @Test
    fun `the sign-in screen shows its fields and the Sign in button`() {
        compose.setContent { LoginScreen(viewModel, notice = null, onSignedIn = {}) }

        compose.onNodeWithText("Server address").assertExists()
        compose.onNodeWithText("Username").assertExists()
        compose.onNodeWithText("Password").assertExists()
        compose.onNodeWithText("Sign in").assertIsDisplayed()
    }

    // The ViewModel outlives the screen (activity scope in MainActivity), so the test keeps one
    // and shows the screen, hides it (Home) and shows it again (after Sign out) around it.
    private var showLogin by mutableStateOf(true)
    private val signedIn = mutableListOf<ServerConfig>()

    private fun showSignIn() = compose.setContent {
        if (showLogin) LoginScreen(viewModel, notice = null) { signedIn += it }
    }

    private fun signIn(withAddress: Boolean = true) {
        if (withAddress) {
            compose.onNodeWithText("Server address").performTextInput("paperless:8000")
            compose.onNodeWithText("Username").performTextInput("anna")
        }
        compose.onNodeWithText("Password").performTextInput("pw")
        compose.onNodeWithText("Sign in").performClick()
        compose.waitForIdle()
    }

    private fun signInThenSignOut() {
        showSignIn()
        signIn()
        assertEquals(listOf(ServerConfig("http://paperless:8000", "token-123")), signedIn)
        showLogin = false
        compose.waitForIdle()
        showLogin = true
        compose.waitForIdle()
    }

    @Test
    fun `a second sign-in screen after sign-out does not sign in on its own`() {
        signInThenSignOut()

        assertEquals(1, signedIn.size)
        compose.onNodeWithText("Sign in").assertIsDisplayed()
        compose.onNodeWithText("Server address").assertTextContains("paperless:8000")
        compose.onNodeWithText("Username").assertTextContains("anna")
    }

    @Test
    fun `signing in again after sign-out signs in once more`() {
        signInThenSignOut()
        assertEquals(1, signedIn.size)

        signIn(withAddress = false)

        assertEquals(2, signedIn.size)
    }
}
