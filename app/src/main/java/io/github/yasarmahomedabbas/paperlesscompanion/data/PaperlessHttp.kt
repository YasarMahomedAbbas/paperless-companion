package io.github.yasarmahomedabbas.paperlesscompanion.data

import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerConfig
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException.Kind
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * The HTTP plumbing every Paperless call shares: addresses, the token header, and turning every
 * failure into a ServerException. The token only ever goes in the Authorization header.
 */
internal class PaperlessHttp(val client: OkHttpClient, val io: CoroutineDispatcher) {

    fun authed(config: ServerConfig, path: String) = authed(config, url(config.baseUrl, path))

    fun authed(config: ServerConfig, url: HttpUrl): Request.Builder =
        Request.Builder().url(url).header("Authorization", "Token ${config.token}")

    /**
     * Sends [request] and parses the answer as JSON (or a bare string when it isn't JSON).
     * [signIn]: the request is a sign-in, so 400 and 403 mean wrong credentials.
     */
    suspend fun call(
        request: Request,
        client: OkHttpClient = this.client,
        signIn: Boolean = false,
    ): JsonElement = callUnless(request, emptySet(), client, signIn)!!

    /** Like [call], but answers null instead of throwing when the server answers one of the [absent] codes. */
    private suspend fun callUnless(
        request: Request,
        absent: Set<Int>,
        client: OkHttpClient = this.client,
        signIn: Boolean = false,
    ): JsonElement? = withContext(io) {
        val (code, text) = try {
            client.newCall(request).execute().use { it.code to it.body?.string().orEmpty() }
        } catch (e: IOException) {
            throw networkError(request.url.host, e)
        }
        if (code in absent) return@withContext null
        if (code !in 200..299) throw httpError(code, text, signIn)
        runCatching { Json.parseToJsonElement(text) }.getOrElse { JsonPrimitive(text) }
    }

    /** An authed JSON call: [body], when given, is sent as `application/json`. */
    suspend fun json(
        config: ServerConfig,
        method: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        body: JsonElement? = null,
    ): JsonElement = jsonUnless(config, method, path, emptySet(), query, body)!!

    /**
     * Like [json], but answers null when the server answers one of the [absent] codes: how a caller tells
     * "this server has no such endpoint" apart from a real failure.
     */
    suspend fun jsonUnless(
        config: ServerConfig,
        method: String,
        path: String,
        absent: Set<Int>,
        query: Map<String, String> = emptyMap(),
        body: JsonElement? = null,
    ): JsonElement? {
        val url = url(config.baseUrl, path).newBuilder()
            .apply { query.forEach { (key, value) -> addQueryParameter(key, value) } }
            .build()
        val requestBody = body?.toString()?.toRequestBody(JSON)
        return callUnless(authed(config, url).method(method, requestBody).build(), absent)
    }

    /**
     * Streams [request]'s answer into [into] (through `<into>.part`, renamed on success; nothing is left
     * on any failure) and returns its Content-Disposition and Content-Type. [onProgress] hears the bytes
     * written so far: first (0, total), then every [PROGRESS_STEP] bytes, and once at the end. The total
     * comes from Content-Length and is null when the server doesn't send one.
     */
    suspend fun download(
        request: Request,
        into: File,
        onProgress: (done: Long, total: Long?) -> Unit = { _, _ -> },
    ): Pair<String?, String?> = withContext(io) {
        val part = File(into.parentFile, "${into.name}.part")
        try {
            val response: Response = try {
                client.newCall(request).execute()
            } catch (e: IOException) {
                throw networkError(request.url.host, e)
            }
            response.use {
                if (it.code !in 200..299) throw httpError(it.code, it.body?.string().orEmpty(), signIn = false)
                val body = it.body ?: throw ServerException(Kind.SERVER, "Paperless sent no file")
                val total = body.contentLength().takeIf { length -> length >= 0 }
                try {
                    body.byteStream().use { input -> part.outputStream().use { out -> copy(input, out, total, onProgress) } }
                } catch (e: IOException) {
                    throw networkError(request.url.host, e)
                }
                into.delete()
                if (!part.renameTo(into)) throw ServerException(Kind.UNREADABLE, "Can't save ${into.name}")
                it.header("Content-Disposition") to it.header("Content-Type")
            }
        } catch (e: Throwable) {
            part.delete()
            into.delete()
            throw e
        }
    }

    /**
     * Every item of a paginated list, following `next` until there is none. A bare array is one page.
     * Only `next`'s query is used, on [path] at the signed-in address: behind a proxy Paperless may
     * build `next` with an inner host or scheme, and the token never goes anywhere else.
     */
    suspend fun pages(config: ServerConfig, path: String, query: Map<String, String>): List<JsonObject> {
        val items = mutableListOf<JsonObject>()
        var url: HttpUrl? = url(config.baseUrl, path).newBuilder()
            .apply { query.forEach { (key, value) -> addQueryParameter(key, value) } }
            .build()
        var seen = 0
        while (url != null) {
            if (++seen > MAX_PAGES) throw ServerException(Kind.SERVER, "Paperless sent too many pages")
            val json = call(authed(config, url).get().build())
            val results = json as? JsonArray ?: (json as? JsonObject)?.get("results") as? JsonArray
                ?: throw ServerException(Kind.SERVER, "Paperless sent no list")
            results.filterIsInstance<JsonObject>().forEach { items += it }
            url = (json as? JsonObject)?.string("next")?.toHttpUrlOrNull()?.let { next ->
                url(config.baseUrl, path).newBuilder().encodedQuery(next.encodedQuery).build()
            }
        }
        return items
    }

    companion object {
        private val JSON = "application/json".toMediaType()
        private const val PROGRESS_STEP = 64 * 1024L

        private fun copy(input: InputStream, out: OutputStream, total: Long?, onProgress: (Long, Long?) -> Unit) {
            val buffer = ByteArray(8 * 1024)
            var done = 0L
            var reported = 0L
            onProgress(0, total)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                done += read
                if (done - reported >= PROGRESS_STEP) {
                    reported = done
                    onProgress(done, total)
                }
            }
            if (reported != done) onProgress(done, total)
        }
        private const val MAX_PAGES = 1000

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(2, TimeUnit.MINUTES)
            .readTimeout(1, TimeUnit.MINUTES)
            .build()

        fun url(baseUrl: String, path: String): HttpUrl =
            "${baseUrl.trimEnd('/')}/$path".toHttpUrlOrNull()
                ?: throw ServerException(Kind.UNREACHABLE, "Invalid server address: $baseUrl")

        /**
         * 401 is a login the server doesn't accept. On a signed-in call 403 is a working login the server
         * refused this action, so signing in again can't help; on a sign-in it means wrong credentials.
         */
        private fun httpError(code: Int, text: String, signIn: Boolean) = when {
            code == 401 || (signIn && (code == 400 || code == 403)) ->
                ServerException(Kind.UNAUTHORIZED, "Wrong username or password")
            code == 403 -> ServerException(Kind.FORBIDDEN, "Paperless returned HTTP $code: ${text.take(200)}")
            code == 404 -> ServerException(Kind.NOT_FOUND, "Paperless returned HTTP $code: ${text.take(200)}")
            else -> ServerException(Kind.SERVER, "Paperless returned HTTP $code: ${text.take(200)}")
        }

        /** Tells "couldn't connect" (wrong network, server down) apart from "connected, but no complete answer". */
        fun networkError(host: String, e: IOException): ServerException {
            val detail = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
            val connecting = e is ConnectException || e is UnknownHostException || e is NoRouteToHostException ||
                (e is SocketTimeoutException && detail.startsWith("connect", ignoreCase = true))
            return if (connecting) {
                ServerException(Kind.UNREACHABLE, "can't connect to $host: $detail", e)
            } else {
                ServerException(Kind.NO_ANSWER, "no answer from $host: $detail", e)
            }
        }
    }
}

internal fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull

internal fun JsonObject.int(key: String): Int? =
    (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }

internal fun JsonObject.intList(key: String): List<Int> =
    (this[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.takeUnless { p -> p is JsonNull }?.intOrNull }
