package io.github.yasarmahomedabbas.paperlesscompanion.data

import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentSource
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException.Kind
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerConfig
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.TaskInfo
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.TaskStatus
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.DocumentServer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** DocumentServer over the paperless-ngx REST API (token auth). */
class PaperlessApi(
    private val client: OkHttpClient = defaultClient(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
    pollTimeoutMillis: Long = 15_000,
) : DocumentServer {
    private val http = PaperlessHttp(client, io)

    // A status check is small and repeated every few seconds, so a stuck one is dropped quickly and retried.
    private val pollClient = client.newBuilder().readTimeout(pollTimeoutMillis, TimeUnit.MILLISECONDS).build()

    override suspend fun login(baseUrl: String, username: String, password: String): String {
        val body = FormBody.Builder().add("username", username).add("password", password).build()
        val request = Request.Builder().url(url(baseUrl, "api/token/")).post(body).build()
        val json = call(request, signIn = true)
        return (json as? JsonObject)?.string("token")
            ?: throw ServerException(Kind.SERVER, "Paperless sent no token")
    }

    override suspend fun upload(config: ServerConfig, document: DocumentSource): String {
        // Read the file first, so a missing file isn't reported as an unreachable server. It is read whole
        // because the body needs a Content-Length: Paperless (Django under ASGI) sees a chunked upload as empty.
        val bytes = withContext(io) {
            try {
                document.open().use { it.readBytes() }
            } catch (e: Exception) {
                throw ServerException(Kind.UNREADABLE, "Can't read ${document.name}", e)
            }
        }
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("document", document.name, bytes.toRequestBody(document.mimeType.toMediaTypeOrNull()))
            .build()
        val request = authed(config, "api/documents/post_document/").post(body).build()
        // The response is the task id as a bare JSON string.
        return (call(request) as? JsonPrimitive)?.contentOrNull
            ?: throw ServerException(Kind.SERVER, "Paperless sent no task id")
    }

    override suspend fun task(config: ServerConfig, taskId: String): TaskInfo {
        val url = url(config.baseUrl, "api/tasks/").newBuilder().addQueryParameter("task_id", taskId).build()
        val request = authed(config, url).get().build()
        // Paperless 2.x (API v9) answers with a bare list; 3.x (API v10) with a paginated object.
        val json = call(request, pollClient)
        val tasks = json as? JsonArray ?: (json as? JsonObject)?.get("results") as? JsonArray
        val task = tasks?.firstOrNull()?.jsonObject
            ?: return TaskInfo(TaskStatus.PENDING, null, null)
        val status = when (task.string("status")?.uppercase()) {
            "SUCCESS" -> TaskStatus.SUCCESS
            "FAILURE", "REVOKED" -> TaskStatus.FAILURE
            "STARTED", "RETRY" -> TaskStatus.RUNNING
            else -> TaskStatus.PENDING
        }
        val data = task["result_data"] as? JsonObject
        val message = task.string("result") ?: data?.let { d ->
            d.string("document_id")?.let { "Success. New document id $it created" }
                ?: d.string("reason")
                ?: d.string("duplicate_of")?.let { "Not consuming: It is a duplicate of document #$it" }
                ?: d.string("error_message")
        }
        val documentId = task.string("related_document")?.toIntOrNull()
            ?: (task["related_document_ids"] as? JsonArray)?.firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull?.toIntOrNull() }
            ?: data?.string("document_id")?.toIntOrNull()
        return TaskInfo(status, message, documentId)
    }

    /** The document's details page in the Paperless web UI (not an API endpoint). */
    override fun documentUrl(config: ServerConfig, documentId: Int) = "${config.baseUrl}/documents/$documentId/details"

    private fun url(baseUrl: String, path: String) = PaperlessHttp.url(baseUrl, path)

    private fun authed(config: ServerConfig, path: String) = http.authed(config, path)

    private fun authed(config: ServerConfig, url: HttpUrl) = http.authed(config, url)

    private suspend fun call(
        request: Request,
        client: OkHttpClient = this.client,
        signIn: Boolean = false,
    ): JsonElement = http.call(request, client, signIn)

    companion object {
        fun defaultClient(): OkHttpClient = PaperlessHttp.defaultClient()
    }
}
