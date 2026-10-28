package io.github.yasarmahomedabbas.paperlesscompanion.data

import io.github.yasarmahomedabbas.paperlesscompanion.CONFIG
import io.github.yasarmahomedabbas.paperlesscompanion.document
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentSource
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.LabelKind
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException.Kind
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerConfig
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.TaskInfo
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.TaskStatus
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.FileNotFoundException
import java.net.SocketTimeoutException

class PaperlessApiTest {
    private val server = MockWebServer()
    private val api = PaperlessApi()
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

    private suspend fun expectError(kind: Kind, block: suspend () -> Unit) {
        try {
            block()
            fail("expected ServerException($kind)")
        } catch (e: ServerException) {
            assertEquals(kind, e.kind)
        }
    }

    @Test
    fun `documentUrl points at the document's details page`() {
        assertEquals("http://paperless:8000/documents/5/details", api.documentUrl(CONFIG, 5))
    }

    @Test
    fun `labelsUrl points at each label kind's page in the web UI`() {
        assertEquals("http://paperless:8000/tags", api.labelsUrl(CONFIG, LabelKind.TAG))
        assertEquals("http://paperless:8000/correspondents", api.labelsUrl(CONFIG, LabelKind.CORRESPONDENT))
        assertEquals("http://paperless:8000/documenttypes", api.labelsUrl(CONFIG, LabelKind.DOCUMENT_TYPE))
        assertEquals("http://paperless:8000/storagepaths", api.labelsUrl(CONFIG, LabelKind.STORAGE_PATH))
    }

    @Test
    fun `login posts the credentials as a form and returns the token`() = runTest {
        respond(body = """{"token":"abc"}""")

        assertEquals("abc", api.login(config.baseUrl, "anna", "p&w"))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/token/", request.path)
        assertEquals("username=anna&password=p%26w", request.body.readUtf8())
    }

    @Test
    fun `login with wrong credentials is unauthorized`() = runTest {
        respond(400, """{"non_field_errors":["Unable to log in"]}""")
        expectError(Kind.UNAUTHORIZED) { api.login(config.baseUrl, "anna", "nope") }
    }

    @Test
    fun `login without a token in the answer is a server error`() = runTest {
        respond(body = """{"something":"else"}""")
        expectError(Kind.SERVER) { api.login(config.baseUrl, "anna", "pw") }
    }

    @Test
    fun `upload sends the file with the token and returns the task id`() = runTest {
        respond(body = "\"7f3c-task\"")

        val taskId = api.upload(config, document("bill.pdf", "PDFBYTES".toByteArray()))

        assertEquals("7f3c-task", taskId)
        val request = server.takeRequest()
        assertEquals("/api/documents/post_document/", request.path)
        assertEquals("Token tok", request.getHeader("Authorization"))
        // Paperless reads a chunked upload as empty ("No file was submitted"), so the length must be sent.
        assertEquals(null, request.getHeader("Transfer-Encoding"))
        assertEquals(request.bodySize.toString(), request.getHeader("Content-Length"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("""name="document"; filename="bill.pdf""""))
        assertTrue(body.contains("Content-Type: application/pdf"))
        assertTrue(body.contains("PDFBYTES"))
    }

    @Test
    fun `upload of an unreadable file is reported as unreadable, not as a network error`() = runTest {
        val missing = DocumentSource("gone.pdf", "application/pdf") { throw FileNotFoundException("gone") }
        expectError(Kind.UNREADABLE) { api.upload(config, missing) }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `an expired token is unauthorized`() = runTest {
        respond(401, """{"detail":"Invalid token."}""")
        expectError(Kind.UNAUTHORIZED) { api.upload(config, document()) }
    }

    @Test
    fun `a server error carries the status code`() = runTest {
        respond(500, "kaputt")
        try {
            api.upload(config, document())
            fail()
        } catch (e: ServerException) {
            assertEquals(Kind.SERVER, e.kind)
            assertTrue(e.message!!.contains("500"))
        }
    }

    @Test
    fun `an upload answer without a task id is a server error`() = runTest {
        respond(body = "{}")
        expectError(Kind.SERVER) { api.upload(config, document()) }
    }

    @Test
    fun `task reads status, result and the new document id`() = runTest {
        respond(body = """[{"task_id":"t1","status":"SUCCESS","result":"Success. New document id 12 created","related_document":"12"}]""")

        val task = api.task(config, "t1")

        assertEquals(TaskInfo(TaskStatus.SUCCESS, "Success. New document id 12 created", 12), task)
        assertEquals("/api/tasks/?task_id=t1", server.takeRequest().path)
    }

    @Test
    fun `task reads the paginated Paperless 3 answer`() = runTest {
        respond(
            body = """{"count":1,"next":null,"previous":null,"results":[{"task_id":"t1","status":"success",""" +
                """"result_data":{"document_id":98},"related_document_ids":[98]}]}""",
        )

        assertEquals(TaskInfo(TaskStatus.SUCCESS, "Success. New document id 98 created", 98), api.task(config, "t1"))
    }

    @Test
    fun `task reads a Paperless 3 duplicate refusal`() = runTest {
        respond(body = """{"results":[{"status":"failure","result_data":{"duplicate_of":97},"related_document_ids":[]}]}""")

        assertEquals(
            TaskInfo(TaskStatus.FAILURE, "Not consuming: It is a duplicate of document #97", null),
            api.task(config, "t"),
        )
    }

    @Test
    fun `task reads a Paperless 3 error message`() = runTest {
        respond(body = """{"results":[{"status":"failure","result_data":{"error_message":"Unsupported type"}}]}""")
        assertEquals(TaskInfo(TaskStatus.FAILURE, "Unsupported type", null), api.task(config, "t"))
    }

    @Test
    fun `an unknown task in the paginated answer is still pending`() = runTest {
        respond(body = """{"count":0,"results":[]}""")
        assertEquals(TaskInfo(TaskStatus.PENDING, null, null), api.task(config, "t"))
    }

    @Test
    fun `task maps every Paperless status`() = runTest {
        val expected = mapOf(
            "PENDING" to TaskStatus.PENDING,
            "STARTED" to TaskStatus.RUNNING,
            "RETRY" to TaskStatus.RUNNING,
            "SUCCESS" to TaskStatus.SUCCESS,
            "FAILURE" to TaskStatus.FAILURE,
            "REVOKED" to TaskStatus.FAILURE,
        )
        for ((paperlessStatus, status) in expected) {
            respond(body = """[{"status":"$paperlessStatus","result":null,"related_document":null}]""")
            assertEquals(TaskInfo(status, null, null), api.task(config, "t"))
            // Paperless 3 sends the same statuses in lower case.
            respond(body = """{"results":[{"status":"${paperlessStatus.lowercase()}"}]}""")
            assertEquals(TaskInfo(status, null, null), api.task(config, "t"))
        }
    }

    @Test
    fun `an unknown task is still pending`() = runTest {
        respond(body = "[]")
        assertEquals(TaskInfo(TaskStatus.PENDING, null, null), api.task(config, "t"))
    }

    @Test
    fun `an unreachable server is reported as unreachable`() = runTest {
        server.shutdown()
        expectError(Kind.UNREACHABLE) { api.task(config, "t") }
    }

    @Test
    fun `an unreachable server says why`() = runTest {
        server.shutdown()
        try {
            api.task(config, "t")
            fail()
        } catch (e: ServerException) {
            assertTrue(e.message!!.startsWith("can't connect to ${server.hostName}: "))
        }
    }

    @Test
    fun `a status check that gets no answer is a no-answer error, not unreachable`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val quick = PaperlessApi(pollTimeoutMillis = 200)

        try {
            quick.task(config, "t")
            fail()
        } catch (e: ServerException) {
            assertEquals(Kind.NO_ANSWER, e.kind)
            assertTrue(e.cause is SocketTimeoutException)
            assertTrue(e.message!!.startsWith("no answer from ${server.hostName}: "))
        }
    }

    @Test
    fun `a timeout is sorted by where it happened, whatever its wording`() = runTest {
        val cases = listOf(
            "timeout" to Kind.NO_ANSWER,
            "Read timed out" to Kind.NO_ANSWER,
            "Connect timed out" to Kind.UNREACHABLE,
            "connect timed out" to Kind.UNREACHABLE,
        )
        for ((message, kind) in cases) {
            val client = OkHttpClient.Builder().addInterceptor { throw SocketTimeoutException(message) }.build()
            try {
                PaperlessApi(client = client).task(config, "t")
                fail("\"$message\": expected ServerException($kind)")
            } catch (e: ServerException) {
                assertEquals("\"$message\"", kind, e.kind)
                assertTrue("\"$message\": cause", e.cause is SocketTimeoutException)
                assertTrue("\"$message\": ${e.message}", e.message!!.endsWith(": $message"))
            }
        }
    }

    @Test
    fun `an invalid server address is reported as unreachable`() = runTest {
        expectError(Kind.UNREACHABLE) { api.login("not a url", "a", "b") }
    }
}
