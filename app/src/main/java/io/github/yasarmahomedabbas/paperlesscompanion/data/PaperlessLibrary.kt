package io.github.yasarmahomedabbas.paperlesscompanion.data

import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Document
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentFile
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentFileKind
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentPage
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentQuery
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentSort
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Label
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerConfig
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException.Kind
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Tag
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.DocumentLibrary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import java.io.File

/**
 * [DocumentLibrary] over the paperless-ngx REST API. No API version is asked for, so the server's default
 * is used; where API v9 (Paperless 2.x) and v10 (3.x) answer differently, both are read. Downloaded files
 * go under [files], which this class owns.
 */
class PaperlessLibrary(
    client: OkHttpClient = PaperlessApi.defaultClient(),
    io: CoroutineDispatcher = Dispatchers.IO,
    private val files: File,
) : DocumentLibrary {
    private val http = PaperlessHttp(client, io)

    override suspend fun documents(config: ServerConfig, query: DocumentQuery): DocumentPage {
        val params = buildMap {
            put("page", query.page.toString())
            put("page_size", query.pageSize.toString())
            // A list needs no document text, which can be megabytes per document.
            put("fields", LIST_FIELDS)
            val text = query.text?.trim()?.takeIf { it.isNotEmpty() }
            if (text != null) {
                // Full-text search answers in relevance order; an ordering would override it.
                put("query", text)
            } else {
                put("ordering", (if (query.descending) "-" else "") + query.sort.field)
            }
            if (query.tagIds.isNotEmpty()) put("tags__id__all", query.tagIds.sorted().joinToString(","))
            query.correspondentId?.let { put("correspondent__id", it.toString()) }
            query.documentTypeId?.let { put("document_type__id", it.toString()) }
        }
        return page(http.json(config, "GET", "api/documents/", params))
    }

    override suspend fun document(config: ServerConfig, id: Int): Document =
        obj(http.json(config, "GET", "api/documents/$id/")).toDocument()

    override suspend fun tags(config: ServerConfig): List<Tag> = labels(config, "api/tags/").map { it.toTag() }

    override suspend fun correspondents(config: ServerConfig): List<Label> =
        labels(config, "api/correspondents/").map { it.toLabel() }

    override suspend fun documentTypes(config: ServerConfig): List<Label> =
        labels(config, "api/document_types/").map { it.toLabel() }

    override suspend fun storagePaths(config: ServerConfig): List<Label> =
        labels(config, "api/storage_paths/").map { it.toLabel() }

    override suspend fun file(config: ServerConfig, id: Int, kind: DocumentFileKind): DocumentFile {
        val url = PaperlessHttp.url(config.baseUrl, "api/documents/$id/${if (kind == DocumentFileKind.PREVIEW) "preview" else "download"}/")
            .newBuilder()
            .apply { if (kind == DocumentFileKind.ORIGINAL) addQueryParameter("original", "true") }
            .build()
        // One folder per document and kind, emptied first: only the latest copy is kept, and the name the
        // server gives (known only once it answers) can't collide with another document's.
        val dir = File(files, "$id-${kind.name.lowercase()}")
        val download = File(dir, DOWNLOAD)
        withContext(http.io) {
            dir.deleteRecursively()
            if (!dir.mkdirs()) throw ServerException(Kind.UNREADABLE, "Can't make room for the file")
        }
        val (disposition, type) = http.download(http.authed(config, url).get().build(), download)
        return withContext(http.io) {
            val mimeType = type?.substringBefore(';')?.trim()?.takeIf { it.isNotEmpty() }
            val fallback = "document-$id" + if (mimeType == "application/pdf") ".pdf" else ""
            val name = fileName(disposition, fallback)
            val file = File(dir, name)
            if (!download.renameTo(file)) {
                download.delete()
                throw ServerException(Kind.UNREADABLE, "Can't save $name")
            }
            DocumentFile(name, mimeType, file)
        }
    }

    private suspend fun labels(config: ServerConfig, path: String) =
        http.pages(config, path, mapOf("page_size" to "100", "ordering" to "name"))

    private companion object {
        /** Where a file lands before its name is known. A server-given name never starts with a dot. */
        const val DOWNLOAD = ".download"

        val LIST_FIELDS = listOf(
            "id", "title", "created", "added", "modified", "correspondent", "document_type", "storage_path",
            "tags", "archive_serial_number", "original_file_name", "page_count", "mime_type",
        ).joinToString(",")

        val DocumentSort.field
            get() = when (this) {
                DocumentSort.CREATED -> "created"
                DocumentSort.ADDED -> "added"
                DocumentSort.MODIFIED -> "modified"
                DocumentSort.TITLE -> "title"
                DocumentSort.CORRESPONDENT -> "correspondent__name"
                DocumentSort.DOCUMENT_TYPE -> "document_type__name"
                DocumentSort.ARCHIVE_SERIAL_NUMBER -> "archive_serial_number"
            }

        fun obj(json: JsonElement) = json as? JsonObject ?: throw ServerException(Kind.SERVER, "Paperless sent no document")

        /** A paginated document list. API v9 also sends `all` (every matching id); v10 doesn't, and it isn't needed. */
        fun page(json: JsonElement): DocumentPage {
            val page = json as? JsonObject ?: throw ServerException(Kind.SERVER, "Paperless sent no list")
            val results = page["results"] as? JsonArray ?: throw ServerException(Kind.SERVER, "Paperless sent no list")
            val documents = results.filterIsInstance<JsonObject>().map { it.toDocument() }
            return DocumentPage(
                total = page.int("count") ?: documents.size,
                documents = documents,
                hasMore = page.string("next") != null,
            )
        }
    }
}
