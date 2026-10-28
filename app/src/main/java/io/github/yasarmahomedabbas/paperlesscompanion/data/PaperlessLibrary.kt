package io.github.yasarmahomedabbas.paperlesscompanion.data

import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.BulkEdit
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Change
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Document
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentChanges
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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
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

    override suspend fun update(config: ServerConfig, id: Int, changes: DocumentChanges): Document {
        // A partial update: only the fields that change are sent, and null clears one.
        val body = buildJsonObject {
            set("title", changes.title) { JsonPrimitive(it) }
            set("created", changes.created) { JsonPrimitive(it.toString()) }
            set("correspondent", changes.correspondentId) { JsonPrimitive(it) }
            set("document_type", changes.documentTypeId) { JsonPrimitive(it) }
            set("storage_path", changes.storagePathId) { JsonPrimitive(it) }
            set("tags", changes.tagIds) { ids -> buildJsonArray { ids.forEach { add(JsonPrimitive(it)) } } }
            set("archive_serial_number", changes.archiveSerialNumber) { JsonPrimitive(it) }
        }
        return obj(http.json(config, "PATCH", "api/documents/$id/", body = body)).toDocument()
    }

    override suspend fun delete(config: ServerConfig, id: Int) {
        // Paperless moves it to the trash (both 2.x and 3.x).
        http.json(config, "DELETE", "api/documents/$id/")
    }

    override suspend fun trash(config: ServerConfig, page: Int, pageSize: Int): DocumentPage =
        page(http.json(config, "GET", "api/trash/", mapOf("page" to page.toString(), "page_size" to pageSize.toString())))

    override suspend fun restore(config: ServerConfig, ids: List<Int>) {
        if (ids.isEmpty()) return
        http.json(config, "POST", "api/trash/", body = buildJsonObject {
            ids(ids)
            put("action", "restore")
        })
    }

    override suspend fun bulkEdit(config: ServerConfig, ids: List<Int>, edit: BulkEdit) {
        if (ids.isEmpty()) return
        val (method, parameters) = when (edit) {
            is BulkEdit.ModifyTags -> "modify_tags" to buildJsonObject {
                // Paperless requires both lists, even when one is empty.
                putJsonArray("add_tags") { edit.add.sorted().forEach { add(JsonPrimitive(it)) } }
                putJsonArray("remove_tags") { edit.remove.sorted().forEach { add(JsonPrimitive(it)) } }
            }
            is BulkEdit.SetCorrespondent -> "set_correspondent" to buildJsonObject { put("correspondent", edit.id) }
            is BulkEdit.SetDocumentType -> "set_document_type" to buildJsonObject { put("document_type", edit.id) }
            is BulkEdit.SetStoragePath -> "set_storage_path" to buildJsonObject { put("storage_path", edit.id) }
            BulkEdit.Delete -> return bulkDelete(config, ids)
        }
        bulkEditCall(config, ids, method, parameters)
    }

    /**
     * Paperless 3 (API v10) deletes through its own endpoint and keeps `bulk_edit`'s `delete` only as a legacy
     * method; Paperless 2 has no such endpoint and answers 404, so it gets the `bulk_edit` call.
     */
    private suspend fun bulkDelete(config: ServerConfig, ids: List<Int>) {
        try {
            http.json(config, "POST", "api/documents/delete/", body = buildJsonObject { ids(ids) })
        } catch (e: ServerException) {
            if (e.kind != Kind.NOT_FOUND) throw e
            bulkEditCall(config, ids, "delete", JsonObject(emptyMap()))
        }
    }

    private suspend fun bulkEditCall(config: ServerConfig, ids: List<Int>, method: String, parameters: JsonObject) {
        http.json(config, "POST", "api/documents/bulk_edit/", body = buildJsonObject {
            ids(ids)
            put("method", method)
            put("parameters", parameters)
        })
    }

    private suspend fun labels(config: ServerConfig, path: String) =
        http.pages(config, path, mapOf("page_size" to "100", "ordering" to "name"))

    private companion object {
        fun JsonObjectBuilder.ids(ids: List<Int>) = putJsonArray("documents") { ids.forEach { add(JsonPrimitive(it)) } }

        /** Puts [key] unless the change keeps it; a null value is sent as JSON null, which clears the field. */
        fun <T> JsonObjectBuilder.set(key: String, change: Change<T>, value: (T & Any) -> JsonElement) {
            if (change is Change.To) put(key, change.value?.let(value) ?: JsonNull)
        }

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
