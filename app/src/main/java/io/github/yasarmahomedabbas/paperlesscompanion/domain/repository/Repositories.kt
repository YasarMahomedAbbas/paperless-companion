package io.github.yasarmahomedabbas.paperlesscompanion.domain.repository

import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentSource
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerConfig
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.TaskInfo

/** The Paperless REST API. Implementations throw PaperlessException on any failure. */
interface PaperlessRepository {
    /** Exchanges a username and password for an API token. */
    suspend fun login(baseUrl: String, username: String, password: String): String

    /** Sends a document to the consume queue and returns the task id. */
    suspend fun upload(config: ServerConfig, document: DocumentSource): String

    suspend fun task(config: ServerConfig, taskId: String): TaskInfo
}

/** Where the signed-in server is remembered between launches. */
interface SettingsRepository {
    fun load(): ServerConfig?
    fun save(config: ServerConfig)
    fun clear()
}
