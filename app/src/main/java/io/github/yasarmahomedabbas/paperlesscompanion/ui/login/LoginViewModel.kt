package io.github.yasarmahomedabbas.paperlesscompanion.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.PaperlessException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerConfig
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.LoginUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.normalizeServerUrl
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LoginField { SERVER, USERNAME, PASSWORD }

/**
 * [errorField] is the field the [error] is about, so the screen can mark it; [unreachable] says
 * the phone couldn't reach the server, which the screen shows as a network problem, not a refusal.
 */
data class LoginUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val errorField: LoginField? = null,
    val unreachable: Boolean = false,
    val signedIn: ServerConfig? = null,
)

class LoginViewModel(private val login: LoginUseCase) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onServerUrl(value: String) = _state.update { it.clearError().copy(serverUrl = value) }
    fun onUsername(value: String) = _state.update { it.clearError().copy(username = value) }
    fun onPassword(value: String) = _state.update { it.clearError().copy(password = value) }

    fun submit() {
        val current = _state.value
        if (current.busy) return
        _state.update { it.clearError().copy(busy = true) }
        viewModelScope.launch {
            login(current.serverUrl, current.username, current.password)
                .onSuccess { config -> _state.update { it.copy(busy = false, password = "", signedIn = config) } }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            busy = false,
                            error = e.loginMessage(),
                            errorField = e.field(current),
                            unreachable = e is PaperlessException && e.isNetwork,
                        )
                    }
                }
        }
    }
}

private fun LoginUiState.clearError() = copy(error = null, errorField = null, unreachable = false)

private val PaperlessException.isNetwork
    get() = kind == PaperlessException.Kind.UNREACHABLE || kind == PaperlessException.Kind.NO_ANSWER

/** Which field a failed sign-in is about, if any. Input problems are checked in the use case's order. */
private fun Throwable.field(input: LoginUiState): LoginField? = when {
    this is PaperlessException && isNetwork -> LoginField.SERVER
    this is PaperlessException && kind == PaperlessException.Kind.UNAUTHORIZED -> LoginField.PASSWORD
    this is PaperlessException -> null
    normalizeServerUrl(input.serverUrl) == null -> LoginField.SERVER
    input.username.isBlank() -> LoginField.USERNAME
    else -> LoginField.PASSWORD
}

private fun Throwable.loginMessage(): String = when {
    this is PaperlessException && kind == PaperlessException.Kind.UNAUTHORIZED -> "Wrong username or password"
    this is PaperlessException -> userMessage()
    else -> message ?: "Sign-in failed"
}
