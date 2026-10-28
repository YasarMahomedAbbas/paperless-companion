package io.github.yasarmahomedabbas.paperlesscompanion.ui

import io.github.yasarmahomedabbas.paperlesscompanion.FakeServer
import io.github.yasarmahomedabbas.paperlesscompanion.FakeSettings
import io.github.yasarmahomedabbas.paperlesscompanion.MainDispatcherRule
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException.Kind
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerConfig
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.LoginUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.ui.login.LoginField
import io.github.yasarmahomedabbas.paperlesscompanion.ui.login.LoginViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val server = FakeServer()
    private val viewModel = LoginViewModel(LoginUseCase(server, FakeSettings()))

    private fun fillIn() {
        viewModel.onServerUrl("paperless:8000")
        viewModel.onUsername("anna")
        viewModel.onPassword("pw")
    }

    @Test
    fun `a successful sign-in clears the password and exposes the config`() = runTest(main.dispatcher) {
        fillIn()
        viewModel.submit()
        assertTrue(viewModel.state.value.busy)

        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.busy)
        assertEquals("", state.password)
        assertEquals(ServerConfig("http://paperless:8000", "token-123"), state.signedIn)
    }

    @Test
    fun `wrong credentials show a friendly error`() = runTest(main.dispatcher) {
        server.loginResult = { throw ServerException(Kind.UNAUTHORIZED, "401") }
        fillIn()

        viewModel.submit()
        advanceUntilIdle()

        assertEquals("Wrong username or password", viewModel.state.value.error)
        assertEquals(LoginField.PASSWORD, viewModel.state.value.errorField)
        assertFalse(viewModel.state.value.unreachable)
        assertNull(viewModel.state.value.signedIn)
    }

    @Test
    fun `an unreachable server hints at the VPN`() = runTest(main.dispatcher) {
        server.loginResult = { throw ServerException(Kind.UNREACHABLE, "timeout") }
        fillIn()

        viewModel.submit()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.error!!.contains("VPN"))
        assertEquals(LoginField.SERVER, viewModel.state.value.errorField)
        assertTrue(viewModel.state.value.unreachable)
    }

    @Test
    fun `a server error marks no field and is not a network problem`() = runTest(main.dispatcher) {
        server.loginResult = { throw ServerException(Kind.SERVER, "Paperless returned 500") }
        fillIn()

        viewModel.submit()
        advanceUntilIdle()

        assertNull(viewModel.state.value.errorField)
        assertFalse(viewModel.state.value.unreachable)
    }

    @Test
    fun `missing input marks the first field that needs it`() = runTest(main.dispatcher) {
        viewModel.onServerUrl("paperless:8000")
        viewModel.submit()
        advanceUntilIdle()
        assertEquals(LoginField.USERNAME, viewModel.state.value.errorField)

        viewModel.onUsername("anna")
        viewModel.submit()
        advanceUntilIdle()
        assertEquals(LoginField.PASSWORD, viewModel.state.value.errorField)
    }

    @Test
    fun `missing input shows the validation message`() = runTest(main.dispatcher) {
        viewModel.submit()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.error!!.contains("server address"))
        assertEquals(LoginField.SERVER, viewModel.state.value.errorField)
        assertTrue(server.logins.isEmpty())
    }

    @Test
    fun `typing clears the error`() = runTest(main.dispatcher) {
        viewModel.submit()
        advanceUntilIdle()

        viewModel.onUsername("a")

        assertNull(viewModel.state.value.error)
        assertNull(viewModel.state.value.errorField)
    }

    @Test
    fun `a second submit while busy is ignored`() = runTest(main.dispatcher) {
        fillIn()
        viewModel.submit()
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(1, server.logins.size)
    }
}
