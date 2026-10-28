package io.github.yasarmahomedabbas.paperlesscompanion.data

import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerConfig
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException.Kind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PaperlessHttpTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val server = MockWebServer()
    private val http = PaperlessHttp(PaperlessHttp.defaultClient(), Dispatchers.IO)
    private lateinit var config: ServerConfig

    @Before
    fun setUp() {
        server.start()
        config = ServerConfig(server.url("/").toString().trimEnd('/'), "tok")
    }

    @After
    fun tearDown() = server.shutdown()

    private fun respond(code: Int = 200, body: String = "") =
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))

    private suspend fun expectError(kind: Kind, block: suspend () -> Unit): ServerException {
        try {
            block()
        } catch (e: ServerException) {
            assertEquals(kind, e.kind)
            return e
        }
        fail("expected ServerException($kind)")
        throw AssertionError()
    }

    @Test
    fun `a 404 is not found, with the usual message`() = runTest {
        respond(404, """{"detail":"No Document matches the given query."}""")

        val e = expectError(Kind.NOT_FOUND) { http.json(config, "GET", "api/documents/9/") }

        assertTrue(e.message!!.startsWith("Paperless returned HTTP 404: "))
    }

    @Test
    fun `json sends the token, the query and a JSON body`() = runTest {
        respond(body = """{"ok":true}""")

        val answer = http.json(
            config, "PATCH", "api/documents/3/",
            query = mapOf("full_perms" to "true"),
            body = buildJsonObject { put("title", "Bill") },
        )

        assertEquals("true", (answer as JsonObject)["ok"].toString())
        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/documents/3/?full_perms=true", request.path)
        assertEquals("Token tok", request.getHeader("Authorization"))
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))
        assertEquals("""{"title":"Bill"}""", request.body.readUtf8())
    }

    @Test
    fun `an empty answer comes back as an empty string`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        assertEquals(JsonPrimitive(""), http.json(config, "DELETE", "api/documents/3/"))
    }

    @Test
    fun `download writes the body and returns the headers`() = runTest {
        server.enqueue(
            MockResponse().setBody("PDFBYTES")
                .setHeader("Content-Disposition", """attachment; filename="bill.pdf"""")
                .setHeader("Content-Type", "application/pdf"),
        )
        val into = File(temp.root, "bill.pdf")

        val (disposition, type) = http.download(http.authed(config, "api/documents/3/download/").build(), into)

        assertEquals("""attachment; filename="bill.pdf"""", disposition)
        assertEquals("application/pdf", type)
        assertEquals("PDFBYTES", into.readText())
        assertFalse(File(temp.root, "bill.pdf.part").exists())
        assertEquals("Token tok", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `a failed download leaves no file`() = runTest {
        respond(500, "kaputt")
        val into = File(temp.root, "bill.pdf")

        expectError(Kind.SERVER) { http.download(http.authed(config, "x/").build(), into) }

        assertTrue(temp.root.listFiles()!!.isEmpty())
    }

    @Test
    fun `a dropped download leaves no file`() = runTest {
        server.enqueue(
            MockResponse().setBody("x".repeat(100_000)).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
        )
        val into = File(temp.root, "bill.pdf")

        expectError(Kind.NO_ANSWER) { http.download(http.authed(config, "x/").build(), into) }

        assertTrue(temp.root.listFiles()!!.isEmpty())
    }

    @Test
    fun `pages follows next and only ever asks the signed-in server`() = runTest {
        // `next` names another host, as a misconfigured proxy would: only its query is used.
        respond(body = """{"count":3,"next":"http://inner:8000/api/tags/?page=2&page_size=2","all":[1,2,3],"results":[{"id":1},{"id":2}]}""")
        respond(body = """{"count":3,"next":null,"results":[{"id":3}]}""")

        val items = http.pages(config, "api/tags/", mapOf("page_size" to "2"))

        assertEquals(listOf(1, 2, 3), items.map { it.int("id") })
        assertEquals("/api/tags/?page_size=2", server.takeRequest().path)
        val second = server.takeRequest()
        assertEquals("/api/tags/?page=2&page_size=2", second.path)
        assertEquals("Token tok", second.getHeader("Authorization"))
    }

    @Test
    fun `pages accepts a bare array as one page`() = runTest {
        respond(body = """[{"id":4},{"id":5}]""")
        assertEquals(listOf(4, 5), http.pages(config, "api/tags/", emptyMap()).map { it.int("id") })
    }

    @Test
    fun `pages without a list is a server error`() = runTest {
        respond(body = """{"detail":"odd"}""")
        expectError(Kind.SERVER) { http.pages(config, "api/tags/", emptyMap()) }
    }

    @Test
    fun `json helpers read numbers, strings and lists, nulls as missing`() {
        val json = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"a":3,"b":"4","c":null,"d":[1,null,2],"e":"x"}""",
        ) as JsonObject
        assertEquals(3, json.int("a"))
        assertEquals(4, json.int("b"))
        assertEquals(null, json.int("c"))
        assertEquals(null, json.int("e"))
        assertEquals(null, json.string("c"))
        assertEquals(listOf(1, 2), json.intList("d"))
        assertEquals(emptyList<Int>(), json.intList("missing"))
    }
}
