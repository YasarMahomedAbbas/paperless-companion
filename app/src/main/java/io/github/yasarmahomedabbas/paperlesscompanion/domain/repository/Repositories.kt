package io.github.yasarmahomedabbas.paperlesscompanion.domain.repository

import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentSource
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerConfig
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.TaskInfo

/** The document server the app signs in and uploads to. Implementations throw ServerException on any failure. */
interface DocumentServer {
    /** Exchanges a username and password for an API token. */
    suspend fun login(baseUrl: String, username: String, password: String): String

    /** Sends a document to the consume queue and returns the task id. */
    suspend fun upload(config: ServerConfig, document: DocumentSource): String

    suspend fun task(config: ServerConfig, taskId: String): TaskInfo

    /** The address of a document's page in the server's own web interface. */
    fun documentUrl(config: ServerConfig, documentId: Int): String
}

/** Turns a file the user picked, shot, scanned or shared (by its location, e.g. a content URI string) into a DocumentSource. */
interface DocumentFiles {
    fun from(location: String, fallbackName: String): DocumentSource
}

/** Where the signed-in server is remembered between launches. */
interface SettingsRepository {
    fun load(): ServerConfig?
    fun save(config: ServerConfig)
    fun clear()
}
