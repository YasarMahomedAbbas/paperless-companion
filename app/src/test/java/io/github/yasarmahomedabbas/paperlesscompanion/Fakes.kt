package io.github.yasarmahomedabbas.paperlesscompanion

import io.github.yasarmahomedabbas.paperlesscompanion.data.KeyValueStore
import io.github.yasarmahomedabbas.paperlesscompanion.data.TokenVault
import io.github.yasarmahomedabbas.paperlesscompanion.data.VaultLockedException
import io.github.yasarmahomedabbas.paperlesscompanion.data.VaultVoidedException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentSource
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerConfig
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.StoredSignIn
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.TaskInfo
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.TaskStatus
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.PaperlessRepository
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    override fun read() = config?.let { StoredSignIn.Ready(it) } ?: StoredSignIn.None
    override fun save(config: ServerConfig) { this.config = config }
    override fun clear() { config = null }
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

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = StandardTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
