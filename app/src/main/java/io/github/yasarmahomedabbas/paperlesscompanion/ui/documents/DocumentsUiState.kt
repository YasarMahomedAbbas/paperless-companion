package io.github.yasarmahomedabbas.paperlesscompanion.ui.documents

import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Document
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentThumbnail
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Labels
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.SearchHit
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The filters applied to the list. A document must carry every one of [tagIds]. */
data class DocumentFilters(
    val tagIds: Set<Int> = emptySet(),
    val correspondentId: Int? = null,
    val documentTypeId: Int? = null,
) {
    /** How many filters are on; each tag counts once. */
    val count: Int get() = tagIds.size + (if (correspondentId != null) 1 else 0) + (if (documentTypeId != null) 1 else 0)
    val isEmpty: Boolean get() = count == 0
}

/** One document as its row shows it. [moreTags] is how many tags didn't fit in [tags]. */
data class DocumentRow(
    val id: Int,
    val title: String,
    val meta: String,
    val tags: List<String>,
    val moreTags: Int,
    val thumbnail: DocumentThumbnail,
    val snippet: SearchHit?,
)

private val DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
private const val SHOWN_TAGS = 2

/** "<correspondent> · <date>", or whichever of the two is known. A label id the server no longer has is skipped. */
internal fun Document.toRow(labels: Labels): DocumentRow {
    val correspondent = correspondentId?.let { id -> labels.correspondents.firstOrNull { it.id == id }?.name }
    val tagNames = tagIds.mapNotNull { id -> labels.tags.firstOrNull { it.id == id }?.name }
    return DocumentRow(
        id = id,
        title = title.ifBlank { "Untitled" },
        meta = listOfNotNull(correspondent, created?.format(DATE)).joinToString(" · "),
        tags = tagNames.take(SHOWN_TAGS),
        moreTags = (tagNames.size - SHOWN_TAGS).coerceAtLeast(0),
        thumbnail = thumbnail,
        snippet = searchHit,
    )
}

/** Where the list stands. [Failed.unreachable] says the phone couldn't reach the server, as opposed to a refusal. */
sealed interface ListStatus {
    data object Loading : ListStatus
    data object Loaded : ListStatus
    data object Empty : ListStatus
    data class Failed(val message: String, val unreachable: Boolean) : ListStatus
}

data class DocumentsUiState(
    val text: String = "",
    val searching: Boolean = false,
    val filters: DocumentFilters = DocumentFilters(),
    val sort: SortOption = SortOption.NEWEST,
    val labels: Labels? = null,
    val rows: List<DocumentRow> = emptyList(),
    val total: Int? = null,
    val status: ListStatus = ListStatus.Loading,
    /** A pull to refresh is running; the rows stay on screen. */
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    /** Why the next page didn't load; the footer offers a retry. */
    val loadMoreFailed: String? = null,
    /** How many documents the filters sheet's draft would show, or null while unknown. */
    val filterPreviewCount: Int? = null,
) {
    val sortLabel: String get() = if (text.isNotBlank()) BEST_MATCH else sort.label

    /** "23 documents" shows only when searching or filtering. */
    val showResultLine: Boolean get() = text.isNotBlank() || !filters.isEmpty
}
