package io.github.yasarmahomedabbas.paperlesscompanion.domain.model

import java.io.File
import java.time.Instant
import java.time.LocalDate

/** A document on the server. [content] (its text) is only filled in when one document is loaded, not in lists. */
data class Document(
    val id: Int,
    val title: String,
    val created: LocalDate?,
    val added: Instant?,
    val modified: Instant?,
    val correspondentId: Int?,
    val documentTypeId: Int?,
    val storagePathId: Int?,
    val tagIds: List<Int>,
    val archiveSerialNumber: Int?,
    val originalFileName: String?,
    val pageCount: Int?,
    val mimeType: String?,
    val content: String? = null,
    /** Set when the document came from a text search. */
    val searchHit: SearchHit? = null,
    /** How many notes it has; only known when one document is loaded. */
    val noteCount: Int? = null,
) {
    val thumbnail get() = DocumentThumbnail(id, modified)
}

/** Why a document matched a search: [excerpt] is plain text, [matches] the highlighted ranges in it. */
data class SearchHit(val score: Double?, val excerpt: String, val matches: List<IntRange>)

/** A note someone left on a document; [author] is null when the server doesn't say who. */
data class Note(val id: Int, val text: String, val created: Instant?, val author: String?)

/** One page of documents out of [total]; [hasMore] when a later page exists. */
data class DocumentPage(val total: Int, val documents: List<Document>, val hasMore: Boolean)

enum class DocumentSort { CREATED, ADDED, MODIFIED, TITLE, CORRESPONDENT, DOCUMENT_TYPE, ARCHIVE_SERIAL_NUMBER }

/**
 * Which documents to list. A blank [text] counts as none. With [text], results come in relevance order
 * and [sort] is ignored. With [similarTo], the documents most like that one come first, and [text] and
 * [sort] are ignored. A document must carry every one of [tagIds].
 */
data class DocumentQuery(
    val text: String? = null,
    val tagIds: Set<Int> = emptySet(),
    val correspondentId: Int? = null,
    val documentTypeId: Int? = null,
    val sort: DocumentSort = DocumentSort.CREATED,
    val descending: Boolean = true,
    val page: Int = 1,
    val pageSize: Int = 25,
    /** Documents like this one, most alike first; [text] and [sort] are ignored. */
    val similarTo: Int? = null,
) {
    init {
        require(page >= 1) { "page starts at 1" }
        require(pageSize in 1..100) { "pageSize is 1 to 100" }
    }
}

data class Tag(
    val id: Int,
    val name: String,
    val color: String?,
    val textColor: String?,
    val isInbox: Boolean,
    val documentCount: Int?,
)

/** A correspondent, document type or storage path. */
data class Label(val id: Int, val name: String, val documentCount: Int?)

/** The four kinds of label a document can carry. */
enum class LabelKind { TAG, CORRESPONDENT, DOCUMENT_TYPE, STORAGE_PATH }

data class Labels(
    val tags: List<Tag>,
    val correspondents: List<Label>,
    val documentTypes: List<Label>,
    val storagePaths: List<Label>,
)

/** What the UI hands the image loader to show a document's thumbnail; [version] changes when the document does. */
data class DocumentThumbnail(val documentId: Int, val version: Instant?)

/** Which file of a document: the preview the server shows, the archived (OCR'd) PDF, or the file as uploaded. */
enum class DocumentFileKind { PREVIEW, ARCHIVED, ORIGINAL }

/** A document's file, fetched into the app's cache as [file]; [name] is the name to show or save it under. */
data class DocumentFile(val name: String, val mimeType: String?, val file: File)

/** One field of an edit: [Keep] leaves it as it is, [To] sets it (`To(null)` clears a field that may be empty). */
sealed interface Change<out T> {
    data object Keep : Change<Nothing>
    data class To<T>(val value: T) : Change<T>
}

/** What to change on one document; every field is kept unless given. */
data class DocumentChanges(
    val title: Change<String> = Change.Keep,
    val created: Change<LocalDate> = Change.Keep,
    val correspondentId: Change<Int?> = Change.Keep,
    val documentTypeId: Change<Int?> = Change.Keep,
    val storagePathId: Change<Int?> = Change.Keep,
    val tagIds: Change<List<Int>> = Change.Keep,
    val archiveSerialNumber: Change<Int?> = Change.Keep,
) {
    val isEmpty: Boolean
        get() = listOf(title, created, correspondentId, documentTypeId, storagePathId, tagIds, archiveSerialNumber)
            .all { it == Change.Keep }
}

/** One change applied to many documents at once. A null id clears that field. */
sealed interface BulkEdit {
    data class ModifyTags(val add: Set<Int>, val remove: Set<Int>) : BulkEdit
    data class SetCorrespondent(val id: Int?) : BulkEdit
    data class SetDocumentType(val id: Int?) : BulkEdit
    data class SetStoragePath(val id: Int?) : BulkEdit
    /** Moves them to the trash. */
    data object Delete : BulkEdit
}
