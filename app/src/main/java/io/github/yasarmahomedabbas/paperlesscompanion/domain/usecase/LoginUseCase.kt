package io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase

import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerConfig
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.DocumentServer
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.SettingsRepository

class LoginUseCase(
    private val server: DocumentServer,
    private val settings: SettingsRepository,
) {
    suspend operator fun invoke(serverUrl: String, username: String, password: String): Result<ServerConfig> {
        val baseUrl = normalizeServerUrl(serverUrl)
            ?: return Result.failure(IllegalArgumentException("Enter the server address, e.g. http://paperless:8000"))
        if (username.isBlank() || password.isEmpty()) {
            return Result.failure(IllegalArgumentException("Enter your username and password"))
        }
        return try {
            val token = server.login(baseUrl, username.trim(), password)
            ServerConfig(baseUrl, token).also(settings::save).let { Result.success(it) }
        } catch (e: ServerException) {
            Result.failure(e)
        }
    }
}

/**
 * Turns what people type into a base URL: adds http:// when no scheme is given, and drops
 * trailing slashes and a trailing /api. Returns null when nothing usable is left.
 */
fun normalizeServerUrl(input: String): String? {
    var url = input.trim()
    if (url.isEmpty()) return null
    if (!url.contains("://")) url = "http://$url"
    val host = url.substringAfter("://").substringBefore('/').substringBefore(':')
    if (host.isEmpty() || url.contains(' ')) return null
    url = url.trimEnd('/')
    if (url.endsWith("/api")) url = url.removeSuffix("/api").trimEnd('/')
    return url
}
