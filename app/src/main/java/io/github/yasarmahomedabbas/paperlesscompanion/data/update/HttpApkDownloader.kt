package io.github.yasarmahomedabbas.paperlesscompanion.data.update

import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ApkFile
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.UpdateException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.UpdateException.Kind
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.ApkDownloader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * [ApkDownloader] over HTTPS into [dir] (`cacheDir/updates/`), hashing the file as it streams.
 *
 * GitHub's download address answers with a redirect to its file host, so redirects are followed
 * (never from https to http): the size and sha256 the release states, and the signer check before
 * the install, are the guard, not the host. The file goes to `<name>.part`, renamed to `<name>`
 * only when both match. Any failure or a cancel deletes it: there's no resuming. The name must be
 * the app's own APK name, so nothing from the network chooses a path. Nothing here logs.
 */
class HttpApkDownloader(
    private val dir: File,
    client: OkHttpClient = OkHttpClient(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /** Tests only: MockWebServer speaks plain http. */
    private val allowHttp: Boolean = false,
) : ApkDownloader {
    private val client = client.newBuilder()
        .followRedirects(true)
        .followSslRedirects(false)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun download(apk: ApkFile, onProgress: (done: Long, total: Long) -> Unit): File {
        if (!NAME.matches(apk.name)) throw UpdateException(Kind.CHECKSUM, "unexpected file name")
        val secure = apk.location.startsWith("https://") || (allowHttp && apk.location.startsWith("http://"))
        if (!secure) throw UpdateException(Kind.SERVER, "not an https address")
        val request = try {
            Request.Builder().url(apk.location).get().build()
        } catch (e: IllegalArgumentException) {
            throw UpdateException(Kind.SERVER, "not an address", e)
        }
        return withContext(io) {
            storage {
                // An earlier download (installed, or left by a crash) is never kept.
                clear()
                if (!dir.isDirectory && !dir.mkdirs()) throw IOException("mkdirs")
            }
            val part = File(dir, "${apk.name}.part")
            val file = File(dir, apk.name)
            try {
                val sha = fetch(request, part, apk.size, onProgress)
                if (part.length() != apk.size || sha != apk.sha256.lowercase()) {
                    throw UpdateException(Kind.CHECKSUM, "download doesn't match the release")
                }
                storage { if (!part.renameTo(file)) throw IOException("rename") }
                file
            } catch (e: Throwable) {
                part.delete()
                file.delete()
                throw e
            }
        }
    }

    override fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private suspend fun fetch(request: Request, part: File, total: Long, onProgress: (Long, Long) -> Unit): String {
        val digest = MessageDigest.getInstance("SHA-256")
        execute(request) { response ->
            if (response.code != 200) {
                throw UpdateException(Kind.SERVER, "HTTP ${response.code}", status = response.code)
            }
            copy(response.body.byteStream(), part, digest, total, onProgress)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun copy(
        input: InputStream,
        part: File,
        digest: MessageDigest,
        total: Long,
        onProgress: (Long, Long) -> Unit,
    ) = coroutineScope {
        storage { FileOutputStream(part) }.use { out ->
            val buffer = ByteArray(BUFFER)
            var done = 0L
            var reported = -1L
            while (true) {
                ensureActive()
                val read = network { input.read(buffer) }
                if (read < 0) break
                storage { out.write(buffer, 0, read) }
                digest.update(buffer, 0, read)
                done += read
                // More than the release announced can't match; stop reading it.
                if (done > total) throw UpdateException(Kind.CHECKSUM, "longer than the release")
                if (done - reported >= REPORT_EVERY || done == total) {
                    onProgress(done, total)
                    reported = done
                }
            }
        }
    }

    private suspend fun execute(request: Request, handle: suspend (Response) -> Unit) = coroutineScope {
        val call: Call = client.newCall(request)
        // Blocking reads don't see cancellation; cancelling the call ends them.
        val canceller = launch {
            try {
                awaitCancellation()
            } finally {
                call.cancel()
            }
        }
        try {
            network { call.execute() }.use { handle(it) }
        } finally {
            canceller.cancel()
        }
    }

    private companion object {
        val NAME = Regex("^paperless-scan-[0-9.]+\\.apk$")
        const val BUFFER = 64 * 1024
        const val REPORT_EVERY = 256 * 1024L

        inline fun <T> network(block: () -> T): T = try {
            block()
        } catch (e: IOException) {
            throw UpdateException(Kind.OFFLINE, "download failed: ${e.message}", e)
        }

        inline fun <T> storage(block: () -> T): T = try {
            block()
        } catch (e: IOException) {
            throw UpdateException(Kind.STORAGE, "can't write the download: ${e.message}", e)
        }
    }
}
