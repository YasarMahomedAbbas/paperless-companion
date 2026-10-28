package io.github.yasarmahomedabbas.paperlesscompanion.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerConfig
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.LoginUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val signedIn: ServerConfig? = null,
)

class LoginViewModel(private val login: LoginUseCase) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onServerUrl(value: String) = _state.update { it.copy(serverUrl = value, error = null) }
    fun onUsername(value: String) = _state.update { it.copy(username = value, error = null) }
    fun onPassword(value: String) = _state.update { it.copy(password = value, error = null) }

    /**
     * The screen calls this once it has passed [LoginUiState.signedIn] on: this ViewModel outlives the
     * screen (activity scope) and would otherwise sign in again when it is shown after Sign out.
     */
    fun onSignedInHandled() = _state.update { it.copy(signedIn = null) }

    fun submit() {
        val current = _state.value
        if (current.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            login(current.serverUrl, current.username, current.password)
                .onSuccess { config -> _state.update { it.copy(busy = false, password = "", signedIn = config) } }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.loginMessage()) } }
        }
    }
}

private fun Throwable.loginMessage(): String = when {
    this is ServerException && kind == ServerException.Kind.UNAUTHORIZED -> "Wrong username or password"
    this is ServerException -> userMessage()
    else -> message ?: "Sign-in failed"
}
