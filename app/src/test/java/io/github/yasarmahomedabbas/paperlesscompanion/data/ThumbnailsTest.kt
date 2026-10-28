package io.github.yasarmahomedabbas.paperlesscompanion.data

import io.github.yasarmahomedabbas.paperlesscompanion.FakeSettings
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentThumbnail
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant

class ThumbnailsTest {
    private val server = MockWebServer()
    private val other = MockWebServer()
    private val settings = FakeSettings()
    private val client = OkHttpClient.Builder().addInterceptor(ServerTokenInterceptor(settings)).build()
    private val thumb = DocumentThumbnail(7, Instant.ofEpochMilli(1_700_000_000_000))

    @Before
    fun setUp() {
        server.start()
        other.start()
        settings.config = ServerConfig(server.url("/").toString().trimEnd('/'), "tok")
    }

    @After
    fun tearDown() {
        server.shutdown()
        other.shutdown()
    }

    private fun fetch(url: String) {
        client.newCall(Request.Builder().url(url).build()).execute().close()
    }

    @Test
    fun `the url is the document's thumb endpoint`() {
        val config = ServerConfig("https://paper.example/", "tok")
        assertEquals("https://paper.example/api/documents/7/thumb/", thumbnailUrl(config, thumb))
    }

    @Test
    fun `the key changes with the document's version and the server`() {
        val config = ServerConfig("https://paper.example", "tok")
        val key = thumbnailCacheKey(config, thumb)

        assertEquals("thumb:https://paper.example:7:1700000000000", key)
        assertEquals(key, thumbnailCacheKey(config.copy(baseUrl = "https://paper.example/"), thumb))
        assertNotEquals(key, thumbnailCacheKey(config, thumb.copy(version = Instant.ofEpochMilli(1_700_000_000_001))))
        assertNotEquals(key, thumbnailCacheKey(ServerConfig("https://other.example", "tok"), thumb))
        assertEquals("thumb:https://paper.example:7:0", thumbnailCacheKey(config, thumb.copy(version = null)))
    }

    @Test
    fun `the signed-in server gets the token`() {
        server.enqueue(MockResponse())
        fetch(server.url("/api/documents/7/thumb/").toString())
        assertEquals("Token tok", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `another port gets no token`() {
        other.enqueue(MockResponse())
        fetch(other.url("/api/documents/7/thumb/").toString())
        assertNull(other.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `another host gets no token`() {
        // The same server under another name: 127.0.0.1 vs localhost.
        server.enqueue(MockResponse())
        val host = if (server.hostName == "localhost") "127.0.0.1" else "localhost"
        fetch("http://$host:${server.port}/x")
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `another scheme gets no token`() {
        settings.config = ServerConfig("https://${server.hostName}:${server.port}", "tok")
        server.enqueue(MockResponse())
        fetch(server.url("/x").toString())
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `signed out, nobody gets a token`() {
        settings.config = null
        server.enqueue(MockResponse())
        fetch(server.url("/x").toString())
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `an Authorization header already set is left alone`() {
        server.enqueue(MockResponse())
        client.newCall(Request.Builder().url(server.url("/x")).header("Authorization", "Basic abc").build()).execute().close()
        assertEquals("Basic abc", server.takeRequest().getHeader("Authorization"))
    }
}
