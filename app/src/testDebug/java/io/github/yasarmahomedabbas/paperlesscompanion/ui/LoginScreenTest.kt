package io.github.yasarmahomedabbas.paperlesscompanion.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.yasarmahomedabbas.paperlesscompanion.FakeServer
import io.github.yasarmahomedabbas.paperlesscompanion.FakeSettings
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.LoginUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.ui.login.LoginScreen
import io.github.yasarmahomedabbas.paperlesscompanion.ui.login.LoginViewModel
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
}
