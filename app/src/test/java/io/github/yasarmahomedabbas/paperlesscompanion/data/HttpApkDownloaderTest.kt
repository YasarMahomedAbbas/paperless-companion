package io.github.yasarmahomedabbas.paperlesscompanion.data

import io.github.yasarmahomedabbas.paperlesscompanion.data.update.HttpApkDownloader
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ApkFile
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.UpdateException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.UpdateException.Kind
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class HttpApkDownloaderTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var dir: File
    private lateinit var downloader: HttpApkDownloader

    /** Large enough for more than one progress report (every 256 KiB). */
    private val bytes = ByteArray(600 * 1024) { (it % 251).toByte() }

    @Before
    fun setUp() {
        server.start()
        dir = File(temp.root, "updates")
        downloader = HttpApkDownloader(dir, allowHttp = true)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun apk(
        size: Long = bytes.size.toLong(),
        sha256: String = sha(bytes),
        path: String = "/download/paperless-scan-0.2.0.apk",
        name: String = "paperless-scan-0.2.0.apk",
    ) = ApkFile(name, size, sha256, server.url(path).toString())

    private fun serve(body: ByteArray = bytes) =
        server.enqueue(MockResponse().setBody(Buffer().write(body)))

    private suspend fun expectError(kind: Kind, apk: ApkFile = apk()): UpdateException {
        try {
            downloader.download(apk) { _, _ -> }
            fail("expected UpdateException($kind)")
            throw AssertionError()
        } catch (e: UpdateException) {
            assertEquals(kind, e.kind)
            assertTrue("nothing left behind: ${dir.list()?.toList()}", dir.list().isNullOrEmpty())
            return e
        }
    }

    @Test
    fun `a matching file is downloaded with its progress`() = runTest {
        serve()
        val progress = mutableListOf<Pair<Long, Long>>()

        val file = downloader.download(apk()) { done, total -> progress += done to total }

        assertEquals(File(dir, "paperless-scan-0.2.0.apk"), file)
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(listOf("paperless-scan-0.2.0.apk"), dir.list()?.toList())
        assertTrue(progress.size >= 2)
        assertEquals(bytes.size.toLong() to bytes.size.toLong(), progress.last())
        assertEquals("/download/paperless-scan-0.2.0.apk", server.takeRequest().path)
    }

    @Test
    fun `an upper-case sha256 still matches`() = runTest {
        serve()
        downloader.download(apk(sha256 = sha(bytes).uppercase())) { _, _ -> }
    }

    @Test
    fun `a redirect to the file host is followed`() = runTest {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/objects/blob?sig=1"))
        serve()

        val file = downloader.download(apk()) { _, _ -> }

        assertArrayEquals(bytes, file.readBytes())
        assertEquals("/download/paperless-scan-0.2.0.apk", server.takeRequest().path)
        assertEquals("/objects/blob?sig=1", server.takeRequest().path)
    }

    @Test
    fun `a shorter file than the release states is refused`() = runTest {
        serve(bytes.copyOf(bytes.size - 10))
        expectError(Kind.CHECKSUM)
    }

    @Test
    fun `a tampered file of the same size is refused`() = runTest {
        val tampered = bytes.copyOf().also { it[1000] = (it[1000] + 1).toByte() }
        serve(tampered)
        expectError(Kind.CHECKSUM)
    }

    @Test
    fun `a wrong sha256 is refused`() = runTest {
        serve()
        expectError(Kind.CHECKSUM, apk(sha256 = "00".repeat(32)))
    }

    @Test
    fun `more bytes than announced stops the download`() = runTest {
        serve()
        expectError(Kind.CHECKSUM, apk(size = 1000))
    }

    @Test
    fun `a missing file is a server refusal with its status`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(404, expectError(Kind.SERVER).status)
    }

    @Test
    fun `a dropped connection is offline`() = runTest {
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
        expectError(Kind.OFFLINE)
    }

    @Test
    fun `no connection at all is offline`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        expectError(Kind.OFFLINE)
    }

    @Test
    fun `a name that isn't the app's APK is never written`() = runTest {
        expectError(Kind.CHECKSUM, apk(name = "../evil.apk"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `plain http is refused outside tests`() = runTest {
        downloader = HttpApkDownloader(dir)
        expectError(Kind.SERVER)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `earlier downloads are cleared first`() = runTest {
        dir.mkdirs()
        File(dir, "paperless-scan-0.1.0.apk").writeText("old")
        File(dir, "paperless-scan-0.1.5.apk.part").writeText("crash")
        serve()

        downloader.download(apk()) { _, _ -> }

        assertEquals(listOf("paperless-scan-0.2.0.apk"), dir.list()?.toList())
    }

    @Test
    fun `clear removes every download`() = runTest {
        serve()
        downloader.download(apk()) { _, _ -> }

        downloader.clear()

        assertTrue(dir.list().isNullOrEmpty())
    }

    private fun sha(data: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
}
