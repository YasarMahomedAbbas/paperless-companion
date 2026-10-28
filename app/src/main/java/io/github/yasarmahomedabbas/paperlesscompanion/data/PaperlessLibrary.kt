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
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Note
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.SavedView
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.SavedViews
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerConfig
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException.Kind
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Tag
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.DocumentLibrary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.ConcurrentHashMap

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

    /**
     * One page of documents. With [DocumentQuery.view], the saved view's own parameters (as the web interface
     * sends them) are the start, and the query narrows them: its tags are added to the view's, its
     * correspondent and type replace the view's, and typed text replaces the view's own search. The view's
     * order applies with [DocumentQuery.viewOrder] and no text; otherwise the app's sort does.
     */
    override suspend fun documents(config: ServerConfig, query: DocumentQuery): DocumentPage {
        val view = query.view?.let { viewFilter(config, it) }
        val text = query.text?.trim()?.takeIf { it.isNotEmpty() }
        val params = buildMap {
            view?.let { putAll(it.params) }
            put("page", query.page.toString())
            put("page_size", query.pageSize.toString())
            // A list needs no document text, which can be megabytes per document.
            put("fields", LIST_FIELDS)
            when {
                query.similarTo != null -> {
                    // "More like this" answers in relevance order and can't be combined with a text search.
                    SEARCH_PARAMS.forEach { remove(it) }
                    put("more_like_id", query.similarTo.toString())
                }
                text != null -> {
                    // Full-text search answers in relevance order; an ordering would override it.
                    SEARCH_PARAMS.forEach { remove(it) }
                    put("query", text)
                }
                view != null && query.viewOrder -> put("ordering", view.ordering)
                else -> put("ordering", (if (query.descending) "-" else "") + query.sort.field)
            }
            if (query.tagIds.isNotEmpty()) {
                val viewTags = this["tags__id__all"]?.split(",")?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()
                put("tags__id__all", (query.tagIds + viewTags).sorted().joinToString(","))
            }
            query.correspondentId?.let { put("correspondent__id", it.toString()) }
            query.documentTypeId?.let { put("document_type__id", it.toString()) }
        }
        return page(http.json(config, "GET", "api/documents/", params))
    }

    /** Each saved view's filter by server address and id, so listing a view's pages needs no extra call. */
    private val viewFilters = ConcurrentHashMap<Pair<String, Int>, ViewFilter>()

    private suspend fun viewFilter(config: ServerConfig, id: Int): ViewFilter =
        viewFilters[config.baseUrl to id]
            ?: obj(http.json(config, "GET", "api/saved_views/$id/")).toViewFilter()
                .also { viewFilters[config.baseUrl to id] = it }

    /**
     * The saved views. API v9 (Paperless 2) puts `show_on_dashboard` / `show_in_sidebar` on each view; v10
     * (Paperless 3) drops them for the web interface's settings (`saved_views.dashboard_views_visible_ids` /
     * `sidebar_views_visible_ids`). Both keep the order in `dashboard_views_sort_order` / `sidebar_views_sort_order`.
     */
    override suspend fun savedViews(config: ServerConfig): SavedViews = coroutineScope {
        val settings = async {
            try {
                Result.success(uiSettings(config))
            } catch (e: ServerException) {
                Result.failure(e)
            }
        }
        val json = http.pages(config, "api/saved_views/", mapOf("page_size" to "100"))
        val allFlagged = json.all { "show_on_dashboard" in it && "show_in_sidebar" in it }
        val ui = settings.await().getOrElse { if (allFlagged) JsonObject(emptyMap()) else throw it }
        val saved = ui["saved_views"] as? JsonObject ?: JsonObject(emptyMap())
        val homeIds = saved.intList("dashboard_views_visible_ids").toSet()
        val quickIds = saved.intList("sidebar_views_visible_ids").toSet()
        val views = json.map { view ->
            val id = view.int("id") ?: throw ServerException(Kind.SERVER, "Paperless sent a saved view without an id")
            viewFilters[config.baseUrl to id] = view.toViewFilter()
            SavedView(
                id = id,
                name = view.string("name").orEmpty(),
                onHome = view.flag("show_on_dashboard") ?: (id in homeIds),
                quickFilter = view.flag("show_in_sidebar") ?: (id in quickIds),
            )
        }
        val byName = compareBy<SavedView, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
        fun ordered(subset: List<SavedView>, order: List<Int>): List<SavedView> {
            val (known, rest) = subset.partition { it.id in order }
            return known.sortedBy { order.indexOf(it.id) } + rest.sortedWith(byName)
        }
        val quick = ordered(views.filter { it.quickFilter }, saved.intList("sidebar_views_sort_order"))
        SavedViews(
            home = ordered(views.filter { it.onHome }, saved.intList("dashboard_views_sort_order")),
            all = quick + views.filterNot { it.quickFilter }.sortedWith(byName),
        )
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

    override suspend fun file(
        config: ServerConfig,
        id: Int,
        kind: DocumentFileKind,
        onProgress: (done: Long, total: Long?) -> Unit,
    ): DocumentFile {
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
        val (disposition, type) = http.download(http.authed(config, url).get().build(), download, onProgress)
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
     * method. Paperless 2 has no such endpoint, so it gets the `bulk_edit` call. Its router reads
     * `documents/delete/` as the document with id "delete", which takes no POST: it answers 405, not 404
     * (404 is kept for a proxy that filters unknown paths).
     */
    private suspend fun bulkDelete(config: ServerConfig, ids: List<Int>) {
        val body = buildJsonObject { ids(ids) }
        http.jsonUnless(config, "POST", "api/documents/delete/", NO_SUCH_ENDPOINT, body = body)
            ?: bulkEditCall(config, ids, "delete", JsonObject(emptyMap()))
    }

    private suspend fun bulkEditCall(config: ServerConfig, ids: List<Int>, method: String, parameters: JsonObject) {
        http.json(config, "POST", "api/documents/bulk_edit/", body = buildJsonObject {
            ids(ids)
            put("method", method)
            put("parameters", parameters)
        })
    }

    /**
     * Paperless answers a failure inside its notes view with HTTP 200 and `{"error": "…"}`, so only a list
     * counts as an answer. A user without the note permissions gets 403 (FORBIDDEN).
     */
    override suspend fun notes(config: ServerConfig, documentId: Int): List<Note> =
        notesFrom(http.json(config, "GET", "api/documents/$documentId/notes/"), "Paperless couldn't load the notes")

    override suspend fun addNote(config: ServerConfig, documentId: Int, text: String): List<Note> =
        notesFrom(
            http.json(config, "POST", "api/documents/$documentId/notes/", body = buildJsonObject { put("note", text) }),
            "Paperless couldn't save the note",
        )

    /** The web interface's "enable notes" setting; on unless it says false. */
    override suspend fun notesEnabled(config: ServerConfig): Boolean =
        (uiSettings(config)["notes_enabled"] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.booleanOrNull ?: true

    /** The signed-in user's web interface settings (`settings` of `api/ui_settings/`), or empty. */
    private suspend fun uiSettings(config: ServerConfig): JsonObject =
        (http.json(config, "GET", "api/ui_settings/") as? JsonObject)?.get("settings") as? JsonObject ?: JsonObject(emptyMap())

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

        /** What Paperless 2 answers for an endpoint only Paperless 3 has (see [bulkDelete]). */
        val NO_SUCH_ENDPOINT = setOf(404, 405)

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

        /** The parameters that search: typed text or "more like this" replaces every one a view brings. */
        val SEARCH_PARAMS = listOf("text", "title_search", "query", "more_like_id")

        /** A v9 saved view's flag, or null when the view doesn't carry it (v10). */
        fun JsonObject.flag(key: String): Boolean? =
            (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.booleanOrNull

        fun notesFrom(json: JsonElement, failed: String): List<Note> = when {
            json is JsonArray -> json.filterIsInstance<JsonObject>().map { it.toNote() }
            json is JsonObject && "error" in json -> throw ServerException(Kind.SERVER, failed)
            else -> throw ServerException(Kind.SERVER, "Paperless sent no notes")
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
