package io.github.yasarmahomedabbas.paperlesscompanion

import io.github.yasarmahomedabbas.paperlesscompanion.data.KeyValueStore
import io.github.yasarmahomedabbas.paperlesscompanion.data.TokenVault
import io.github.yasarmahomedabbas.paperlesscompanion.data.VaultLockedException
import io.github.yasarmahomedabbas.paperlesscompanion.data.VaultVoidedException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ApkFile
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.AppRelease
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentSource
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.InstallEvent
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerConfig
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.StoredSignIn
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.TaskInfo
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.TaskStatus
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.AppLockRepository
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.ApkDownloader
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.AppInstaller
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.DocumentServer
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.ReleaseSource
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.SettingsRepository
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.UpdateStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.io.File
import org.junit.rules.TestWatcher
import org.junit.runner.Description

val CONFIG = ServerConfig("http://paperless:8000", "secret-token")

fun document(name: String = "scan.pdf", bytes: ByteArray = "%PDF-1.7".toByteArray()) =
    DocumentSource(name, "application/pdf") { bytes.inputStream() }

class FakeSettings(var config: ServerConfig? = null) : SettingsRepository {
    /** Overrides what read() finds, e.g. Locked or Voided. */
    var stored: StoredSignIn? = null
    override fun read() = stored ?: config?.let { StoredSignIn.Ready(it) } ?: StoredSignIn.None
    override fun save(config: ServerConfig) { this.config = config }
    override fun clear() { config = null }
}

/** A scripted phone lock. */
class FakeLock(
    var canLock: Boolean = true,
    var switchOn: Boolean = true,
    var unlockResult: StoredSignIn = StoredSignIn.Ready(CONFIG),
    var unsaved: Boolean = false,
) : AppLockRepository {
    var forgets = 0
    var unlocks = 0
    /** setEnabled() throws as if the key refused despite the prompt. */
    var refuseSwitch = false

    override fun canLock() = canLock
    override fun isEnabled() = switchOn
    override fun unlock(): StoredSignIn {
        unlocks++
        if (unlockResult is StoredSignIn.Ready) unsaved = false
        return unlockResult
    }
    override fun setEnabled(enabled: Boolean) {
        if (refuseSwitch) throw IllegalStateException("Unlock first")
        switchOn = enabled
    }
    override fun hasUnsavedSignIn() = unsaved
    override fun forget() {
        forgets++
    }
}

/** Seals as "<locked|plain>:<plain>", so a test can see which key sealed a token. */
class FakeVault : TokenVault {
    /** The locked key refuses to seal or open, as it does until the user unlocks. */
    var lockedUntilUnlock = false
    /** Opening throws as if the phone had voided the key. */
    var voided = false
    val deleted = mutableListOf<Boolean>()
    var opens = 0

    override fun seal(plain: String, locked: Boolean): String {
        if (locked && lockedUntilUnlock) throw VaultLockedException()
        return "${name(locked)}:$plain"
    }

    override fun open(sealed: String, locked: Boolean): String {
        opens++
        if (voided) throw VaultVoidedException()
        if (locked && lockedUntilUnlock) throw VaultLockedException()
        val prefix = "${name(locked)}:"
        check(sealed.startsWith(prefix)) { "$sealed was not sealed with the ${name(locked)} key" }
        return sealed.removePrefix(prefix)
    }

    override fun delete(locked: Boolean) {
        deleted += locked
    }

    private fun name(locked: Boolean) = if (locked) "locked" else "plain"
}

class MapStore(val values: MutableMap<String, Any?> = mutableMapOf()) : KeyValueStore {
    override fun getString(key: String) = values[key] as? String
    override fun getBoolean(key: String) = values[key] as? Boolean
    override fun edit(block: MutableMap<String, Any?>.() -> Unit) {
        mutableMapOf<String, Any?>().apply(block).forEach { (key, value) ->
            if (value == null) values.remove(key) else values[key] = value
        }
    }
    override fun clear() = values.clear()
}

/** Scripted server: login and upload results are fixed, task() replays [tasks] then repeats the last one. */
class FakeServer(
    var loginResult: () -> String = { "token-123" },
    var uploadResult: () -> String = { "task-1" },
    val tasks: MutableList<TaskInfo> = mutableListOf(TaskInfo(TaskStatus.SUCCESS, "ok", 42)),
) : DocumentServer {
    /** Thrown by task() before it replays [tasks]: the first ones once each, [taskAlwaysFails] every time. */
    val taskFailures = mutableListOf<Exception>()
    var taskAlwaysFails: Exception? = null
    val logins = mutableListOf<Triple<String, String, String>>()
    val uploads = mutableListOf<String>()
    var taskPolls = 0

    override suspend fun login(baseUrl: String, username: String, password: String): String {
        logins += Triple(baseUrl, username, password)
        return loginResult()
    }

    override suspend fun upload(config: ServerConfig, document: DocumentSource): String {
        uploads += document.name
        return uploadResult()
    }

    override suspend fun task(config: ServerConfig, taskId: String): TaskInfo {
        taskPolls++
        taskAlwaysFails?.let { throw it }
        if (taskFailures.isNotEmpty()) throw taskFailures.removeAt(0)
        return if (tasks.size > 1) tasks.removeAt(0) else tasks.first()
    }

    override fun documentUrl(config: ServerConfig, documentId: Int) = "${config.baseUrl}/documents/$documentId"
}

fun release(version: String = "0.2.0", versionCode: Long = 200, notes: String = "Fixes") =
    AppRelease(version, versionCode, notes, ApkFile("paperless-scan-$version.apk", 1234, "ab".repeat(32), "https://example.test/$version.apk"))

/** A release source that answers [latest] (or throws [failure]) and counts the calls. */
class FakeReleases(var latest: AppRelease? = null, var failure: Exception? = null) : ReleaseSource {
    var calls = 0

    override suspend fun latest(): AppRelease? {
        calls++
        failure?.let { throw it }
        return latest
    }
}

class FakeUpdateStore(offer: AppRelease? = null, var notified: Long = Long.MIN_VALUE) : UpdateStore {
    private val _offer = MutableStateFlow(offer)
    override val offer: StateFlow<AppRelease?> = _offer
    var asked = false

    override fun saveOffer(release: AppRelease?) { _offer.value = release }
    override fun notifiedVersionCode() = notified
    override fun markNotified(versionCode: Long) { notified = versionCode }
    override fun askedForNotifications() = asked
    override fun markAskedForNotifications() { asked = true }
}

/**
 * A download that reports [progress], then answers [file] or throws [failure]; with [gate] set it
 * waits on it in between, so a test can look at (or stop) a download in flight.
 */
class FakeDownloader(
    var file: File = File("paperless-scan-0.2.0.apk"),
    var failure: Exception? = null,
    var progress: List<Long> = emptyList(),
) : ApkDownloader {
    var gate: CompletableDeferred<Unit>? = null
    val downloads = mutableListOf<ApkFile>()
    var cleared = 0

    override suspend fun download(apk: ApkFile, onProgress: (done: Long, total: Long) -> Unit): File {
        downloads += apk
        progress.forEach { onProgress(it, apk.size) }
        gate?.await()
        failure?.let { throw it }
        return file
    }

    override fun clear() { cleared++ }
}

/** An installer whose [install] throws [failure] or records the file; [events] is driven by the test. */
class FakeInstaller(var versionCode: Long = 104, var allowed: Boolean = true) : AppInstaller {
    var failure: Exception? = null
    val installs = mutableListOf<File>()
    override val events = MutableStateFlow<InstallEvent?>(null)

    override fun installedVersionCode() = versionCode
    override fun canInstall() = allowed

    override suspend fun install(apk: File) {
        failure?.let { throw it }
        installs += apk
    }

    override fun clearEvents() { events.value = null }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = StandardTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
