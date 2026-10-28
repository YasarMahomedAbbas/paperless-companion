package io.github.yasarmahomedabbas.paperlesscompanion.data

import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentThumbnail
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerConfig
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.SettingsRepository
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/** Where a document's thumbnail is on the server (a small WebP image). */
internal fun thumbnailUrl(config: ServerConfig, thumb: DocumentThumbnail): String =
    PaperlessHttp.url(config.baseUrl, "api/documents/${thumb.documentId}/thumb/").toString()

/**
 * The image caches' key for a thumbnail. It names the server and the document's version (its modified
 * time), so an edit or a reprocess fetches a fresh image, while an unchanged one is read from the cache.
 */
internal fun thumbnailCacheKey(config: ServerConfig, thumb: DocumentThumbnail): String =
    "thumb:${config.baseUrl.trimEnd('/')}:${thumb.documentId}:${thumb.version?.toEpochMilli() ?: 0}"

/**
 * Adds the token to requests for the signed-in server (same scheme, host and port) and to nothing else,
 * so an image from anywhere else never carries it. An Authorization header already set is left alone.
 */
class ServerTokenInterceptor(private val settings: SettingsRepository) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val config = settings.load()
        val server = config?.baseUrl?.toHttpUrlOrNull()
        val url = request.url
        val ours = server != null && url.scheme == server.scheme && url.host == server.host && url.port == server.port
        if (!ours || request.header("Authorization") != null) return chain.proceed(request)
        return chain.proceed(request.newBuilder().header("Authorization", "Token ${config!!.token}").build())
    }
}
