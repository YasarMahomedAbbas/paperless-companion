package io.github.yasarmahomedabbas.paperlesscompanion.data

import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentSource
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.PaperlessException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.PaperlessException.Kind
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerConfig
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.TaskInfo
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.TaskStatus
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.PaperlessRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** PaperlessRepository over the paperless-ngx REST API (token auth). */
class PaperlessApi(
    private val client: OkHttpClient = defaultClient(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : PaperlessRepository {

    override suspend fun login(baseUrl: String, username: String, password: String): String {
        val body = FormBody.Builder().add("username", username).add("password", password).build()
        val request = Request.Builder().url(url(baseUrl, "api/token/")).post(body).build()
        val json = call(request, badRequestIsAuth = true)
        return (json as? JsonObject)?.string("token")
            ?: throw PaperlessException(Kind.SERVER, "Paperless sent no token")
    }

    override suspend fun upload(config: ServerConfig, document: DocumentSource): String {
        // Read the file first, so a missing file isn't reported as an unreachable server. It is read whole
        // because the body needs a Content-Length: Paperless (Django under ASGI) sees a chunked upload as empty.
        val bytes = withContext(io) {
            try {
                document.open().use { it.readBytes() }
            } catch (e: Exception) {
                throw PaperlessException(Kind.UNREADABLE, "Can't read ${document.name}", e)
            }
        }
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("document", document.name, bytes.toRequestBody(document.mimeType.toMediaTypeOrNull()))
            .build()
        val request = authed(config, "api/documents/post_document/").post(body).build()
        // The response is the task id as a bare JSON string.
        return (call(request) as? JsonPrimitive)?.contentOrNull
            ?: throw PaperlessException(Kind.SERVER, "Paperless sent no task id")
    }

    override suspend fun task(config: ServerConfig, taskId: String): TaskInfo {
        val url = url(config.baseUrl, "api/tasks/").newBuilder().addQueryParameter("task_id", taskId).build()
        val request = authed(config, url).get().build()
        val task = (call(request) as? JsonArray)?.firstOrNull()?.jsonObject
            ?: return TaskInfo(TaskStatus.PENDING, null, null)
        val status = when (task.string("status")) {
            "SUCCESS" -> TaskStatus.SUCCESS
            "FAILURE", "REVOKED" -> TaskStatus.FAILURE
            "STARTED", "RETRY" -> TaskStatus.RUNNING
            else -> TaskStatus.PENDING
        }
        return TaskInfo(status, task.string("result"), task.string("related_document")?.toIntOrNull())
    }

    private fun authed(config: ServerConfig, path: String) = authed(config, url(config.baseUrl, path))

    private fun authed(config: ServerConfig, url: HttpUrl) =
        Request.Builder().url(url).header("Authorization", "Token ${config.token}")

    private suspend fun call(request: Request, badRequestIsAuth: Boolean = false): JsonElement = withContext(io) {
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw PaperlessException(Kind.UNREACHABLE, "Can't reach ${request.url.host}", e)
        }
        response.use {
            val text = it.body?.string().orEmpty()
            when {
                it.isSuccessful -> runCatching { Json.parseToJsonElement(text) }.getOrElse { JsonPrimitive(text) }
                it.code == 401 || it.code == 403 || (badRequestIsAuth && it.code == 400) ->
                    throw PaperlessException(Kind.UNAUTHORIZED, "Wrong username or password")
                else -> throw PaperlessException(Kind.SERVER, "Paperless returned HTTP ${it.code}: ${text.take(200)}")
            }
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(2, TimeUnit.MINUTES)
            .readTimeout(1, TimeUnit.MINUTES)
            .build()

        private fun url(baseUrl: String, path: String): HttpUrl =
            "${baseUrl.trimEnd('/')}/$path".toHttpUrlOrNull()
                ?: throw PaperlessException(Kind.UNREACHABLE, "Invalid server address: $baseUrl")

        private fun JsonObject.string(key: String): String? =
            (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull
    }
}
