package io.github.yasarmahomedabbas.paperlesscompanion.domain.model

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
) {
    val thumbnail get() = DocumentThumbnail(id, modified)
}

/** Why a document matched a search: [excerpt] is plain text, [matches] the highlighted ranges in it. */
data class SearchHit(val score: Double?, val excerpt: String, val matches: List<IntRange>)

/** One page of documents out of [total]; [hasMore] when a later page exists. */
data class DocumentPage(val total: Int, val documents: List<Document>, val hasMore: Boolean)

enum class DocumentSort { CREATED, ADDED, MODIFIED, TITLE, CORRESPONDENT, DOCUMENT_TYPE, ARCHIVE_SERIAL_NUMBER }

/**
 * Which documents to list. A blank [text] counts as none. With [text], results come in relevance order
 * and [sort] is ignored. A document must carry every one of [tagIds].
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

data class Labels(
    val tags: List<Tag>,
    val correspondents: List<Label>,
    val documentTypes: List<Label>,
    val storagePaths: List<Label>,
)

/** What the UI hands the image loader to show a document's thumbnail; [version] changes when the document does. */
data class DocumentThumbnail(val documentId: Int, val version: Instant?)
