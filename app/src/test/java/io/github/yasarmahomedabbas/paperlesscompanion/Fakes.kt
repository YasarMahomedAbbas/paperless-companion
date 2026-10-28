package io.github.yasarmahomedabbas.paperlesscompanion

import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ApkFile
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.AppRelease
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentSource
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerConfig
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.TaskInfo
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.TaskStatus
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.PaperlessRepository
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.ReleaseSource
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.SettingsRepository
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.UpdateStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

val CONFIG = ServerConfig("http://paperless:8000", "secret-token")

fun document(name: String = "scan.pdf", bytes: ByteArray = "%PDF-1.7".toByteArray()) =
    DocumentSource(name, "application/pdf") { bytes.inputStream() }

class FakeSettings(var config: ServerConfig? = null) : SettingsRepository {
    override fun load() = config
    override fun save(config: ServerConfig) { this.config = config }
    override fun clear() { config = null }
}

/** Scripted Paperless: login and upload results are fixed, task() replays [tasks] then repeats the last one. */
class FakePaperless(
    var loginResult: () -> String = { "token-123" },
    var uploadResult: () -> String = { "task-1" },
    val tasks: MutableList<TaskInfo> = mutableListOf(TaskInfo(TaskStatus.SUCCESS, "ok", 42)),
) : PaperlessRepository {
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

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = StandardTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
