package io.github.yasarmahomedabbas.paperlesscompanion.domain.repository

import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ApkFile
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.AppRelease
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Document
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentFile
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentFileKind
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentPage
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentQuery
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentSource
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.InstallEvent
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Label
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerConfig
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.StoredSignIn
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Tag
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.TaskInfo
import kotlinx.coroutines.flow.StateFlow
import java.io.File

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

/** The documents already on the server, and the labels they carry. Implementations throw ServerException on any failure. */
interface DocumentLibrary {
    suspend fun documents(config: ServerConfig, query: DocumentQuery): DocumentPage

    /** One document with everything known about it, its text included. A gone document is NOT_FOUND. */
    suspend fun document(config: ServerConfig, id: Int): Document

    suspend fun tags(config: ServerConfig): List<Tag>
    suspend fun correspondents(config: ServerConfig): List<Label>
    suspend fun documentTypes(config: ServerConfig): List<Label>
    suspend fun storagePaths(config: ServerConfig): List<Label>

    /** Fetches one of a document's files into the app's cache, replacing the previous copy of the same document and kind. */
    suspend fun file(config: ServerConfig, id: Int, kind: DocumentFileKind): DocumentFile
}

/** Turns a file the user picked, shot, scanned or shared (by its location, e.g. a content URI string) into a DocumentSource. */
interface DocumentFiles {
    fun from(location: String, fallbackName: String): DocumentSource
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

/** Where the app's own releases are published. */
interface ReleaseSource {
    /**
     * The newest release, or null when there's none usable. Throws UpdateException
     * (OFFLINE, or SERVER with the status).
     */
    suspend fun latest(): AppRelease?
}

/** What the update check found and has told the user about, kept between launches. */
interface UpdateStore {
    /** The newer release on offer, or null. */
    val offer: StateFlow<AppRelease?>

    fun saveOffer(release: AppRelease?)

    /** The last version code the user was told about ([Long.MIN_VALUE] before any). */
    fun notifiedVersionCode(): Long

    fun markNotified(versionCode: Long)

    fun askedForNotifications(): Boolean

    fun markAskedForNotifications()
}

/** Downloads a release's file and checks it is the file the release describes. */
interface ApkDownloader {
    /**
     * The verified file, reporting bytes as they arrive. Earlier downloads are removed first.
     * Throws UpdateException (OFFLINE, SERVER, CHECKSUM, STORAGE); nothing is left behind then.
     */
    suspend fun download(apk: ApkFile, onProgress: (done: Long, total: Long) -> Unit): File

    /** Removes every download. */
    fun clear()
}

/** Installs a downloaded update of this app. */
interface AppInstaller {
    fun installedVersionCode(): Long

    /** Whether the system lets this app install updates now. */
    fun canInstall(): Boolean

    /**
     * Hands [apk] to the system, which asks the user to confirm; the outcome arrives on [events].
     * Throws UpdateException: NEEDS_PERMISSION (the file is kept), or NOT_OURS, STORAGE, REFUSED
     * (the file is deleted, as it is after a hand-over).
     */
    suspend fun install(apk: File)

    /** The latest install's outcome, or null. */
    val events: StateFlow<InstallEvent?>

    /** Forgets the latest outcome, so an old one never answers a new install. */
    fun clearEvents()
}
