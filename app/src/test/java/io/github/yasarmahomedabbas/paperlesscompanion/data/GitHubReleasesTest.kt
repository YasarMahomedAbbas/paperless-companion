package io.github.yasarmahomedabbas.paperlesscompanion.data

import io.github.yasarmahomedabbas.paperlesscompanion.data.update.GitHubReleases
import io.github.yasarmahomedabbas.paperlesscompanion.data.update.versionCodeOf
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ApkFile
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.AppRelease
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.UpdateException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.UpdateException.Kind
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class GitHubReleasesTest {
    private val server = MockWebServer()
    private lateinit var releases: GitHubReleases

    @Before
    fun setUp() {
        server.start()
        releases = GitHubReleases(url = server.url("/repos/o/r/releases/latest").toString())
    }

    @After
    fun tearDown() = server.shutdown()

    private fun respond(code: Int = 200, body: String = "") =
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))

    private suspend fun expectError(kind: Kind, status: Int? = null) {
        try {
            releases.latest()
            fail("expected UpdateException($kind)")
        } catch (e: UpdateException) {
            assertEquals(kind, e.kind)
            assertEquals(status, e.status)
        }
    }

    @Test
    fun `the latest release is read from GitHub's answer`() = runTest {
        respond(body = release())

        assertEquals(
            AppRelease(
                version = "0.1.4",
                versionCode = 104,
                notes = "**Full Changelog**: https://github.com/YasarMahomedAbbas/paperless-companion/compare/v0.1.3...v0.1.4",
                apk = ApkFile(
                    name = "paperless-scan-0.1.4.apk",
                    size = 8570413,
                    sha256 = SHA,
                    location = "https://github.com/YasarMahomedAbbas/paperless-companion/releases/download/v0.1.4/paperless-scan-0.1.4.apk",
                ),
            ),
            releases.latest(),
        )
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/repos/o/r/releases/latest", request.path)
        assertEquals("application/vnd.github+json", request.getHeader("Accept"))
        assertEquals("2022-11-28", request.getHeader("X-GitHub-Api-Version"))
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun `an upper-case digest is read in lower case`() = runTest {
        respond(body = release(digest = "sha256:${SHA.uppercase()}"))
        assertEquals(SHA, releases.latest()?.apk?.sha256)
    }

    @Test
    fun `the asset is found among others`() = runTest {
        respond(body = release(extraAsset = """{"name":"notes.txt","size":3,"digest":"sha256:$SHA","browser_download_url":"https://x/notes.txt"},"""))
        assertEquals("paperless-scan-0.1.4.apk", releases.latest()?.apk?.name)
    }

    @Test
    fun `no release yet is nothing to offer`() = runTest {
        respond(404, """{"message":"Not Found"}""")
        assertNull(releases.latest())
    }

    @Test
    fun `a release without the app's asset is nothing to offer`() = runTest {
        respond(body = release(assetName = "paperless-scan-0.1.3.apk"))
        assertNull(releases.latest())
    }

    @Test
    fun `an asset without a digest is nothing to offer`() = runTest {
        respond(body = release(digest = null))
        assertNull(releases.latest())
    }

    @Test
    fun `a digest that isn't sha256 is nothing to offer`() = runTest {
        respond(body = release(digest = "sha1:da39a3ee5e6b4b0d3255bfef95601890afd80709"))
        assertNull(releases.latest())
    }

    @Test
    fun `a short sha256 digest is nothing to offer`() = runTest {
        respond(body = release(digest = "sha256:27b2"))
        assertNull(releases.latest())
    }

    @Test
    fun `a tag that isn't a version is nothing to offer`() = runTest {
        respond(body = release(tag = "nightly"))
        assertNull(releases.latest())
    }

    @Test
    fun `malformed JSON is nothing to offer`() = runTest {
        respond(body = "{not json")
        assertNull(releases.latest())
    }

    @Test
    fun `a rate limit is a server refusal with its status`() = runTest {
        respond(403, """{"message":"API rate limit exceeded"}""")
        expectError(Kind.SERVER, 403)
    }

    @Test
    fun `a server error carries its status`() = runTest {
        respond(500)
        expectError(Kind.SERVER, 500)
    }

    @Test
    fun `a dropped connection is offline`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        expectError(Kind.OFFLINE)
    }

    @Test
    fun `version codes follow the Release workflow`() {
        assertEquals(10203L, versionCodeOf("v1.2.3"))
        assertEquals(10203L, versionCodeOf("1.2.3"))
        assertEquals(104L, versionCodeOf("v0.1.4"))
        assertEquals(9999L, versionCodeOf("v0.99.99"))
        assertNull(versionCodeOf("v1.2"))
        assertNull(versionCodeOf("v1.2.3.4"))
        assertNull(versionCodeOf("v1.2.3-beta"))
        assertNull(versionCodeOf("release"))
        assertNull(versionCodeOf(""))
        assertNull(versionCodeOf("v-1.2.3"))
        assertNull(versionCodeOf("v1.100.0"))
        assertNull(versionCodeOf("v1.0.100"))
        assertNull(versionCodeOf("v999999999999.0.0"))
    }

    private companion object {
        const val SHA = "27b2fa4e022b081c476acd460082e47c9cf2161c53ca7dc44fec11dbadc836a3"

        /** Shaped like GitHub's answer for v0.1.4 (2026-10-28), trimmed to the fields around the ones read. */
        fun release(
            tag: String = "v0.1.4",
            assetName: String = "paperless-scan-0.1.4.apk",
            digest: String? = "sha256:$SHA",
            extraAsset: String = "",
        ) = """
            {
              "url": "https://api.github.com/repos/YasarMahomedAbbas/paperless-companion/releases/1",
              "tag_name": "$tag",
              "name": "$tag",
              "draft": false,
              "prerelease": false,
              "published_at": "2026-10-20T10:00:00Z",
              "assets": [
                $extraAsset
                {
                  "name": "$assetName",
                  "content_type": "application/vnd.android.package-archive",
                  "state": "uploaded",
                  "size": 8570413,
                  "digest": ${digest?.let { "\"$it\"" } ?: "null"},
                  "browser_download_url": "https://github.com/YasarMahomedAbbas/paperless-companion/releases/download/v0.1.4/paperless-scan-0.1.4.apk"
                }
              ],
              "body": "**Full Changelog**: https://github.com/YasarMahomedAbbas/paperless-companion/compare/v0.1.3...v0.1.4"
            }
        """.trimIndent()
    }
}
