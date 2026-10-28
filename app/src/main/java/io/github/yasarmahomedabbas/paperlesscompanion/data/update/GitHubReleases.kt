package io.github.yasarmahomedabbas.paperlesscompanion.data.update

import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ApkFile
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.AppRelease
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.UpdateException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.UpdateException.Kind
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.ReleaseSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * [ReleaseSource] over GitHub's "latest release" (REST API, anonymous: 60 requests an hour), which
 * leaves out drafts and prereleases. A release counts only when its tag is a version
 * (`v<major>.<minor>.<patch>`) and it has the Release workflow's `paperless-scan-<version>.apk`
 * with a sha256 `digest`; otherwise there's nothing to offer. No token is sent.
 */
class GitHubReleases(
    private val client: OkHttpClient = OkHttpClient(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val url: String = LATEST,
) : ReleaseSource {
    override suspend fun latest(): AppRelease? = withContext(io) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .get()
            .build()
        val (code, text) = try {
            client.newCall(request).execute().use { it.code to it.body?.string().orEmpty() }
        } catch (e: IOException) {
            throw UpdateException(Kind.OFFLINE, "can't reach GitHub: ${e.message}", e)
        }
        when {
            code == 404 -> null // no release yet
            code !in 200..299 -> throw UpdateException(Kind.SERVER, "GitHub returned HTTP $code", status = code)
            else -> parse(text)
        }
    }

    private fun parse(text: String): AppRelease? {
        val json = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
        val tag = json.string("tag_name") ?: return null
        val versionCode = versionCodeOf(tag) ?: return null
        val version = tag.removePrefix("v")
        val asset = (json["assets"] as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            ?.firstOrNull { it.string("name") == "paperless-scan-$version.apk" }
            ?: return null
        val size = (asset["size"] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 } ?: return null
        val sha256 = asset.string("digest")?.lowercase()
            ?.takeIf { it.startsWith("sha256:") }
            ?.removePrefix("sha256:")
            ?.takeIf { SHA256.matches(it) }
            ?: return null
        val location = asset.string("browser_download_url") ?: return null
        val apk = ApkFile("paperless-scan-$version.apk", size, sha256, location)
        return AppRelease(version, versionCode, json.string("body").orEmpty(), apk)
    }

    companion object {
        const val LATEST = "https://api.github.com/repos/YasarMahomedAbbas/paperless-companion/releases/latest"

        private val SHA256 = Regex("^[0-9a-f]{64}$")

        private fun JsonObject.string(key: String): String? =
            (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull
    }
}

private val VERSION = Regex("^v?([0-9]+)\\.([0-9]+)\\.([0-9]+)$")

/**
 * The version code the Release workflow gives a tag: `major*10000 + minor*100 + patch`, so minor
 * and patch stay below 100. Null for anything else.
 */
internal fun versionCodeOf(tag: String): Long? {
    val (major, minor, patch) = VERSION.matchEntire(tag)?.destructured ?: return null
    val parts = listOf(major, minor, patch).map { it.toLongOrNull() ?: return null }
    if (parts[1] >= 100 || parts[2] >= 100 || parts[0] > Int.MAX_VALUE / 10000) return null
    return parts[0] * 10000 + parts[1] * 100 + parts[2]
}
