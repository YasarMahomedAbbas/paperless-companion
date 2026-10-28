package io.github.yasarmahomedabbas.paperlesscompanion.domain.repository

import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentSource
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerConfig
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.StoredSignIn
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
    /** Reads the saved sign-in without asking for the phone's lock. */
    fun read(): StoredSignIn

    /** The saved sign-in, when it can be read right now. */
    fun load(): ServerConfig? = (read() as? StoredSignIn.Ready)?.config

    /**
     * Remembers the sign-in. With the lock active and no recent unlock, the token is held in
     * memory only until `AppLockRepository.unlock()` seals it.
     */
    fun save(config: ServerConfig)
    fun clear()
}

/** The phone's own lock (fingerprint, PIN, pattern) guarding the saved sign-in. */
interface AppLockRepository {
    /** True when the phone has a screen lock at all, so the app can be locked. */
    fun canLock(): Boolean

    /** Whether the user wants the app locked. True until turned off. */
    fun isEnabled(): Boolean

    /** Call right after the user unlocked: reads (and if needed seals or re-seals) the sign-in. */
    fun unlock(): StoredSignIn

    /** Call right after the user unlocked: turns the lock on or off and re-seals the token to match. */
    fun setEnabled(enabled: Boolean)

    /** True while a fresh sign-in is held in memory only, waiting for unlock() to seal it. */
    fun hasUnsavedSignIn(): Boolean

    /** Drops the token held in memory, so the next read() is Locked again. */
    fun forget()
}
