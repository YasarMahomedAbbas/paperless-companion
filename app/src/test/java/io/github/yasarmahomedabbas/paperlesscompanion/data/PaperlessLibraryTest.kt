package io.github.yasarmahomedabbas.paperlesscompanion.data

import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Document
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentFileKind
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentQuery
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentSort
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Label
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerConfig
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException.Kind
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Tag
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import java.time.LocalDate

class PaperlessLibraryTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var library: PaperlessLibrary
    private lateinit var config: ServerConfig
    private lateinit var files: File

    @Before
    fun setUp() {
        server.start()
        config = ServerConfig(server.url("/").toString().trimEnd('/'), "tok")
        files = temp.newFolder("documents")
        library = PaperlessLibrary(files = files)
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

    private fun RecordedRequest.query(name: String): String? = requestUrl!!.queryParameter(name)

    private val listDoc = """{"id":7,"title":"Power bill","created":"2024-03-01","added":"2024-03-02T10:00:00Z",""" +
        """"modified":"2024-03-03T11:30:00.123456+01:00","correspondent":2,"document_type":3,"storage_path":null,""" +
        """"tags":[1,5],"archive_serial_number":12,"original_file_name":"scan.pdf","page_count":2,"mime_type":"application/pdf"}"""

    private val expectedListDoc = Document(
        id = 7, title = "Power bill", created = LocalDate.of(2024, 3, 1),
        added = Instant.parse("2024-03-02T10:00:00Z"), modified = Instant.parse("2024-03-03T10:30:00.123456Z"),
        correspondentId = 2, documentTypeId = 3, storagePathId = null, tagIds = listOf(1, 5),
        archiveSerialNumber = 12, originalFileName = "scan.pdf", pageCount = 2, mimeType = "application/pdf",
    )

    // --- the document list ---

    @Test
    fun `documents reads a Paperless 2 page (API v9, with all)`() = runTest {
        respond(body = """{"count":30,"next":"http://x/api/documents/?page=2","previous":null,"all":[7,8,9],"results":[$listDoc]}""")

        val page = library.documents(config, DocumentQuery())

        assertEquals(30, page.total)
        assertTrue(page.hasMore)
        assertEquals(listOf(expectedListDoc), page.documents)
    }

    @Test
    fun `documents reads a Paperless 3 page (API v10, without all)`() = runTest {
        respond(body = """{"count":1,"next":null,"previous":null,"results":[$listDoc]}""")

        val page = library.documents(config, DocumentQuery())

        assertEquals(1, page.total)
        assertFalse(page.hasMore)
        assertEquals(listOf(expectedListDoc), page.documents)
    }

    @Test
    fun `an empty page`() = runTest {
        respond(body = """{"count":0,"next":null,"previous":null,"results":[]}""")
        val page = library.documents(config, DocumentQuery())
        assertEquals(0, page.total)
        assertTrue(page.documents.isEmpty())
    }

    @Test
    fun `documents asks for a page without the document text, newest first by default`() = runTest {
        respond(body = """{"count":0,"results":[]}""")

        library.documents(config, DocumentQuery())

        val request = server.takeRequest()
        assertEquals("/api/documents/", request.requestUrl!!.encodedPath)
        assertEquals("Token tok", request.getHeader("Authorization"))
        assertEquals("1", request.query("page"))
        assertEquals("25", request.query("page_size"))
        assertEquals("-created", request.query("ordering"))
        val fields = request.query("fields")!!.split(",")
        assertFalse("content" in fields)
        assertTrue(fields.containsAll(listOf("id", "title", "modified", "tags", "mime_type")))
        assertNull(request.query("query"))
        assertNull(request.query("tags__id__all"))
    }

    @Test
    fun `each sort maps to its ordering, ascending or descending`() = runTest {
        val expected = mapOf(
            DocumentSort.CREATED to "created",
            DocumentSort.ADDED to "added",
            DocumentSort.MODIFIED to "modified",
            DocumentSort.TITLE to "title",
            DocumentSort.CORRESPONDENT to "correspondent__name",
            DocumentSort.DOCUMENT_TYPE to "document_type__name",
            DocumentSort.ARCHIVE_SERIAL_NUMBER to "archive_serial_number",
        )
        for ((sort, ordering) in expected) {
            respond(body = """{"count":0,"results":[]}""")
            library.documents(config, DocumentQuery(sort = sort, descending = false))
            assertEquals(ordering, server.takeRequest().query("ordering"))
            respond(body = """{"count":0,"results":[]}""")
            library.documents(config, DocumentQuery(sort = sort, descending = true))
            assertEquals("-$ordering", server.takeRequest().query("ordering"))
        }
    }

    @Test
    fun `filters and paging go in the query`() = runTest {
        respond(body = """{"count":0,"results":[]}""")

        library.documents(
            config,
            DocumentQuery(tagIds = setOf(5, 1), correspondentId = 2, documentTypeId = 3, page = 3, pageSize = 50),
        )

        val request = server.takeRequest()
        assertEquals("1,5", request.query("tags__id__all"))
        assertEquals("2", request.query("correspondent__id"))
        assertEquals("3", request.query("document_type__id"))
        assertEquals("3", request.query("page"))
        assertEquals("50", request.query("page_size"))
    }

    @Test
    fun `a text search sends the query and no ordering, and reads the hits`() = runTest {
        val hit = listDoc.dropLast(1) +
            ""","__search_hit__":{"score":3.5,"highlights":"the <span class=\"match\">power</span> bill","rank":0}}"""
        respond(body = """{"count":1,"next":null,"results":[$hit]}""")

        val page = library.documents(config, DocumentQuery(text = "  power  ", sort = DocumentSort.TITLE))

        val request = server.takeRequest()
        assertEquals("power", request.query("query"))
        assertNull(request.query("ordering"))
        val searchHit = page.documents.single().searchHit!!
        assertEquals(3.5, searchHit.score!!, 0.0)
        assertEquals("the power bill", searchHit.excerpt)
        assertEquals(listOf(4..8), searchHit.matches)
    }

    @Test
    fun `a blank search is no search`() = runTest {
        respond(body = """{"count":0,"results":[]}""")
        library.documents(config, DocumentQuery(text = "   "))
        val request = server.takeRequest()
        assertNull(request.query("query"))
        assertEquals("-created", request.query("ordering"))
    }

    @Test
    fun `a list without results is a server error`() = runTest {
        respond(body = """{"detail":"odd"}""")
        expectError(Kind.SERVER) { library.documents(config, DocumentQuery()) }
    }

    @Test
    fun `an expired token is unauthorized`() = runTest {
        respond(401, """{"detail":"Invalid token."}""")
        expectError(Kind.UNAUTHORIZED) { library.documents(config, DocumentQuery()) }
    }

    // --- one document ---

    @Test
    fun `document loads every field, its text included`() = runTest {
        respond(body = listDoc.dropLast(1) + ""","content":"Dear customer","notes":[],"deleted_at":null}""")

        val document = library.document(config, 7)

        assertEquals(expectedListDoc.copy(content = "Dear customer"), document)
        val request = server.takeRequest()
        assertEquals("/api/documents/7/", request.path)
        assertEquals("GET", request.method)
    }

    @Test
    fun `document reads nulls as missing`() = runTest {
        respond(
            body = """{"id":8,"title":"x","created":null,"added":null,"modified":null,"correspondent":null,""" +
                """"document_type":null,"storage_path":null,"tags":[],"archive_serial_number":null,""" +
                """"original_file_name":null,"page_count":null,"mime_type":null,"content":null}""",
        )

        val document = library.document(config, 8)

        assertEquals(Document(8, "x", null, null, null, null, null, null, emptyList(), null, null, null, null), document)
    }

    @Test
    fun `a gone document is not found`() = runTest {
        respond(404, """{"detail":"No Document matches the given query."}""")
        expectError(Kind.NOT_FOUND) { library.document(config, 99) }
    }

    // --- labels ---

    @Test
    fun `tags are read across pages in the Paperless 2 shape`() = runTest {
        respond(
            body = """{"count":2,"next":"${config.baseUrl}/api/tags/?ordering=name&page=2&page_size=100","all":[1,2],""" +
                """"results":[{"id":1,"name":"Inbox","color":"#ff0000","text_color":"#ffffff","is_inbox_tag":true,"document_count":3}]}""",
        )
        respond(body = """{"count":2,"next":null,"all":[1,2],"results":[{"id":2,"name":"Tax","color":"#00ff00","text_color":"#000000","is_inbox_tag":false,"document_count":0}]}""")

        val tags = library.tags(config)

        assertEquals(
            listOf(Tag(1, "Inbox", "#ff0000", "#ffffff", true, 3), Tag(2, "Tax", "#00ff00", "#000000", false, 0)),
            tags,
        )
        val first = server.takeRequest()
        assertEquals("/api/tags/", first.requestUrl!!.encodedPath)
        assertEquals("100", first.query("page_size"))
        assertEquals("name", first.query("ordering"))
        assertEquals("2", server.takeRequest().query("page"))
    }

    @Test
    fun `tags are read across pages in the Paperless 3 shape`() = runTest {
        respond(body = """{"count":2,"next":"${config.baseUrl}/api/tags/?page=2","results":[{"id":1,"name":"Inbox","is_inbox_tag":true}]}""")
        respond(body = """{"count":2,"next":null,"results":[{"id":2,"name":"Tax"}]}""")

        assertEquals(
            listOf(Tag(1, "Inbox", null, null, true, null), Tag(2, "Tax", null, null, false, null)),
            library.tags(config),
        )
    }

    @Test
    fun `correspondents, document types and storage paths each read their list`() = runTest {
        val cases = listOf<Pair<String, suspend () -> List<Label>>>(
            "/api/correspondents/" to { library.correspondents(config) },
            "/api/document_types/" to { library.documentTypes(config) },
            "/api/storage_paths/" to { library.storagePaths(config) },
        )
        for ((path, load) in cases) {
            respond(body = """{"count":1,"next":null,"all":[4],"results":[{"id":4,"name":"Power company","document_count":9}]}""")
            assertEquals(listOf(Label(4, "Power company", 9)), load())
            assertEquals(path, server.takeRequest().requestUrl!!.encodedPath)
            // Paperless 3: no `all`, and a label may come without its count.
            respond(body = """{"count":1,"next":null,"results":[{"id":4,"name":"Power company"}]}""")
            assertEquals(listOf(Label(4, "Power company", null)), load())
            server.takeRequest()
        }
    }

    // --- files ---

    private fun respondFile(body: String, disposition: String?, type: String? = "application/pdf") {
        val response = MockResponse().setBody(body)
        disposition?.let { response.setHeader("Content-Disposition", it) }
        type?.let { response.setHeader("Content-Type", it) }
        server.enqueue(response)
    }

    @Test
    fun `each kind of file comes from its own endpoint`() = runTest {
        val expected = mapOf(
            DocumentFileKind.PREVIEW to "/api/documents/7/preview/",
            DocumentFileKind.ARCHIVED to "/api/documents/7/download/",
            DocumentFileKind.ORIGINAL to "/api/documents/7/download/?original=true",
        )
        for ((kind, path) in expected) {
            respondFile("PDF-$kind", """attachment; filename="bill.pdf"""")

            val file = library.file(config, 7, kind)

            val request = server.takeRequest()
            assertEquals(path, request.path)
            assertEquals("GET", request.method)
            assertEquals("Token tok", request.getHeader("Authorization"))
            assertEquals("bill.pdf", file.name)
            assertEquals("application/pdf", file.mimeType)
            assertEquals("PDF-$kind", file.file.readText())
            assertEquals(File(files, "7-${kind.name.lowercase()}/bill.pdf"), file.file)
        }
    }

    @Test
    fun `a second fetch replaces the first copy`() = runTest {
        respondFile("old", """inline; filename="old.pdf"""")
        respondFile("new", """inline; filename="new.pdf"""")

        library.file(config, 7, DocumentFileKind.PREVIEW)
        val second = library.file(config, 7, DocumentFileKind.PREVIEW)

        assertEquals(listOf("new.pdf"), File(files, "7-preview").list()!!.toList())
        assertEquals("new", second.file.readText())
    }

    @Test
    fun `without a name the file is named after the document`() = runTest {
        respondFile("x", null, "application/pdf; charset=binary")
        val pdf = library.file(config, 7, DocumentFileKind.ARCHIVED)
        assertEquals("document-7.pdf", pdf.name)
        assertEquals("application/pdf", pdf.mimeType)

        respondFile("x", null, "image/png")
        assertEquals("document-7", library.file(config, 7, DocumentFileKind.ORIGINAL).name)

        respondFile("x", null, null)
        val untyped = library.file(config, 7, DocumentFileKind.ORIGINAL)
        assertEquals("document-7", untyped.name)
        assertNull(untyped.mimeType)
    }

    @Test
    fun `a name from the server never leaves the document's folder`() = runTest {
        respondFile("x", """attachment; filename="../../escape.pdf"""")

        val file = library.file(config, 7, DocumentFileKind.ORIGINAL)

        assertEquals(File(files, "7-original"), file.file.parentFile)
    }

    @Test
    fun `a gone document's file is not found and leaves nothing behind`() = runTest {
        respond(404, """{"detail":"Not found."}""")

        expectError(Kind.NOT_FOUND) { library.file(config, 99, DocumentFileKind.PREVIEW) }

        assertTrue(File(files, "99-preview").list()!!.isEmpty())
    }
}
