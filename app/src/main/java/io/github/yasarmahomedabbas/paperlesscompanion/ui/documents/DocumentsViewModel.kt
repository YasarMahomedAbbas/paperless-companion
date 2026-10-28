package io.github.yasarmahomedabbas.paperlesscompanion.ui.documents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.BulkEdit
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Document
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentPage
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.with
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentQuery
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentSort
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException.Kind
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.BrowseDocumentsUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.LoadLabelsUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 25

/** The page size the shown window is asked again in after a change that moves documents. */
private const val WINDOW_PAGE_SIZE = 100

/**
 * The Documents list: pages of documents with their labels, searched as you type, filtered and
 * sorted. Paging is by hand: [loadMore] fetches the next page while the last one said there is
 * more. Every change to the text, filters or sort reloads from page 1, and a newer load cancels
 * the older one, so a slow search never overwrites a newer one. An edit or bulk change patches the
 * rows in place; when it touches what the list is filtered or sorted by, or a search is on, it may
 * move documents across the server's pages, so the rows shown so far are asked for again.
 */
class DocumentsViewModel(
    private val browse: BrowseDocumentsUseCase,
    private val loadLabels: LoadLabelsUseCase,
    private val debounceMillis: Long = 400,
) : ViewModel() {
    private val _state = MutableStateFlow(DocumentsUiState())
    val state: StateFlow<DocumentsUiState> = _state.asStateFlow()

    /** Page loads, first and later ones: a new first page cancels whatever runs. */
    private var loadJob: Job? = null
    private var typingJob: Job? = null
    private var previewJob: Job? = null
    /**
     * How many of the server's documents, in list order, come before the next one not shown yet. It
     * follows trash and Undo, which move the server's pages under the list, so [loadMore] neither
     * skips a document nor shows one twice. An edit that moves documents can't be followed this way:
     * [refetchWindow] asks for the first [consumed] positions again and sets it from the answer.
     */
    private var consumed = 0
    private var hasMore = false

    /** The shown rows are out of step with the server's pages until [refetchWindow] succeeds. */
    private var stale = false

    /** The search text the shown list was loaded with. */
    private var loadedText: String? = null

    /** The documents behind [DocumentsUiState.rows], in the same order. */
    private var loaded: List<Document> = emptyList()

    /** Rows taken out by a trash, by id, with the index each had, until Undo puts them back. */
    private val removed = mutableMapOf<Int, IndexedValue<Document>>()

    init {
        refresh()
    }

    /** Reloads the labels and page 1. Rows already on screen stay there while it runs. */
    fun refresh() = loadFirstPage(withLabels = true, keepRows = _state.value.rows.isNotEmpty())

    fun loadMore() {
        val s = _state.value
        val labels = s.labels ?: return
        if (s.loadingMore || s.refreshing || s.status != ListStatus.Loaded) return
        // Paging on from an out-of-step window would skip documents: catch up first (Retry lands here too).
        if (stale) return refetchWindow()
        if (!hasMore) return
        // The page holding the next unseen document; the ones before it on that page are shown already.
        val query = query(page = consumed / PAGE_SIZE + 1)
        val seen = consumed % PAGE_SIZE
        _state.update { it.copy(loadingMore = true, loadMoreFailed = null) }
        loadJob = viewModelScope.launch {
            try {
                var page = browse(query)
                var next = page.documents.drop(seen)
                // Part of that page was shown already: the one after it too, so a scroll brings a full page.
                if (seen > 0 && page.hasMore) {
                    page = browse(query.copy(page = query.page + 1))
                    next = next + page.documents
                }
                consumed += next.size
                // Nothing new would ask for the same page again for ever.
                hasMore = page.hasMore && next.isNotEmpty()
                // A document a change elsewhere moved further down is shown once, where it was first.
                val shown = loaded.mapTo(HashSet()) { it.id }
                val fresh = next.filter { it.id !in shown }
                loaded = loaded + fresh
                _state.update {
                    it.copy(rows = it.rows + fresh.map { d -> d.toRow(labels) }, total = page.total, loadingMore = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loadingMore = false, loadMoreFailed = e.failure().message) }
            }
        }
    }

    /** Typing: the list follows once the text has been still for the debounce. */
    fun onTextChange(text: String) {
        if (text == _state.value.text) return
        _state.update { it.copy(text = text) }
        typingJob?.cancel()
        typingJob = viewModelScope.launch {
            delay(debounceMillis)
            reload()
        }
    }

    /** The keyboard's Search key: no waiting for the debounce. */
    fun search() = reload()

    fun startSearch() = _state.update { it.copy(searching = true) }

    /** Leaves searching and goes back to browsing in the chosen sort. */
    fun stopSearch() {
        _state.update { it.copy(searching = false, text = "") }
        if (loadedText != null || typingJob?.isActive == true) reload()
    }

    fun setSort(option: SortOption) {
        _state.update { it.copy(sort = option) }
        reload()
    }

    fun setFilters(filters: DocumentFilters) {
        stopPreview()
        _state.update { it.copy(filters = filters) }
        reload()
    }

    fun removeTag(id: Int) = setFilters(_state.value.filters.let { it.copy(tagIds = it.tagIds - id) })

    fun removeCorrespondent() = setFilters(_state.value.filters.copy(correspondentId = null))

    fun removeDocumentType() = setFilters(_state.value.filters.copy(documentTypeId = null))

    /** "No documents match" → Clear filters: drops the filters and the search text. */
    fun clearFilters() {
        _state.update { it.copy(filters = DocumentFilters(), text = "") }
        reload()
    }

    /** Counts what the filters sheet's [draft] would show, once the draft has been still for the debounce. */
    fun previewFilters(draft: DocumentFilters) {
        previewJob?.cancel()
        _state.update { it.copy(filterPreviewCount = null) }
        val query = query(page = 1, filters = draft).copy(pageSize = 1)
        previewJob = viewModelScope.launch {
            delay(debounceMillis)
            val count = try {
                browse(query).total
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            _state.update { it.copy(filterPreviewCount = count) }
        }
    }

    fun stopPreview() {
        previewJob?.cancel()
        previewJob = null
        _state.update { it.copy(filterPreviewCount = null) }
    }

    /**
     * Edit details saved [document]: its row shows the change where it is, without a reload. A
     * document that isn't loaded is left alone. The search match it was found by stays. A change
     * that may move it in the list asks for the shown rows again.
     */
    fun patch(document: Document) {
        val index = loaded.indexOfFirst { it.id == document.id }
        if (index < 0) return
        val old = loaded[index]
        loaded = loaded.toMutableList().also { it[index] = document.copy(searchHit = old.searchHit) }
        showLoaded()
        if (moves(old, document)) refetchWindow()
    }

    /**
     * A bulk change went through for [ids]: the loaded ones show it, as the server now has them. A
     * change that may move them in the list asks for the shown rows again.
     */
    fun apply(ids: Collection<Int>, edit: BulkEdit) {
        val wanted = ids.toSet()
        if (loaded.none { it.id in wanted }) return
        val moved = loaded.any { it.id in wanted && moves(it, it.with(edit)) }
        loaded = loaded.map { if (it.id in wanted) it.with(edit) else it }
        showLoaded()
        if (moved) refetchWindow()
    }

    /** Whether going from [before] to [after] may change where the document stands in the list, or whether it is in it. */
    private fun moves(before: Document, after: Document): Boolean {
        // The search index covers the title, tags, correspondent and type, and ranks by them.
        if (loadedText != null) return before.copy(searchHit = null) != after.copy(searchHit = null)
        val s = _state.value
        val tags = s.filters.tagIds
        if (tags.isNotEmpty() && before.tagIds.toSet().intersect(tags) != after.tagIds.toSet().intersect(tags)) return true
        if (s.filters.correspondentId != null && before.correspondentId != after.correspondentId) return true
        if (s.filters.documentTypeId != null && before.documentTypeId != after.documentTypeId) return true
        return when (s.sort.sort) {
            DocumentSort.CREATED -> before.created != after.created
            DocumentSort.TITLE -> before.title != after.title
            DocumentSort.CORRESPONDENT -> before.correspondentId != after.correspondentId
            // No edit changes when a document was added.
            DocumentSort.ADDED -> false
            DocumentSort.DOCUMENT_TYPE -> before.documentTypeId != after.documentTypeId
            DocumentSort.ARCHIVE_SERIAL_NUMBER -> before.archiveSerialNumber != after.archiveSerialNumber
            // Every edit changes it.
            DocumentSort.MODIFIED -> true
        }
    }

    /**
     * Asks the server again for the first [consumed] positions, in pages of [WINDOW_PAGE_SIZE], and
     * shows its answer in place of the rows, keyed by id so the scroll holds. A failure keeps the
     * patched rows and offers Retry on the load-more footer, which comes back here.
     */
    private fun refetchWindow() {
        val labels = _state.value.labels ?: return
        if (consumed == 0) return
        // A refresh is bringing page 1 fresh already.
        if (_state.value.refreshing && !stale) return
        stale = true
        loadJob?.cancel()
        _state.update { it.copy(refreshing = true, loadingMore = false, loadMoreFailed = null) }
        val count = consumed
        loadJob = viewModelScope.launch {
            try {
                val collected = mutableListOf<Document>()
                var page = 1
                var last: DocumentPage
                do {
                    last = browse(query(page = page++).copy(pageSize = WINDOW_PAGE_SIZE))
                    collected += last.documents
                } while (collected.size < count && last.hasMore && last.documents.isNotEmpty())
                val taken = collected.take(count)
                consumed = taken.size
                hasMore = last.hasMore || collected.size > taken.size
                loaded = taken.distinctBy { it.id }
                stale = false
                _state.update {
                    it.copy(
                        rows = loaded.map { d -> d.toRow(labels) },
                        total = last.total,
                        status = if (loaded.isEmpty()) ListStatus.Empty else ListStatus.Loaded,
                        refreshing = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(refreshing = false, loadMoreFailed = e.failure().message) }
            }
        }
    }

    /** The loaded documents among [ids], in list order (the bulk Tags sheet marks their tags). */
    fun documents(ids: Collection<Int>): List<Document> {
        val wanted = ids.toSet()
        return loaded.filter { it.id in wanted }
    }

    /**
     * [ids] went to the trash: their rows go, and each is remembered with where it stood so
     * [putBack] can return it there.
     */
    fun remove(ids: Collection<Int>) {
        val wanted = ids.toSet()
        val gone = loaded.withIndex().filter { it.value.id in wanted }
        if (gone.isEmpty()) return
        gone.forEach { removed[it.value.id] = it }
        loaded = loaded.filter { it.id !in wanted }
        consumed = (consumed - gone.size).coerceAtLeast(0)
        showLoaded(totalChange = -gone.size)
    }

    /** Undo brought [ids] back from the trash: each remembered one returns to its old place. */
    fun putBack(ids: Collection<Int>) {
        val back = ids.mapNotNull { removed.remove(it) }.filter { r -> loaded.none { it.id == r.value.id } }.sortedBy { it.index }
        if (back.isEmpty()) return
        val list = loaded.toMutableList()
        back.forEach { list.add(it.index.coerceAtMost(list.size), it.value) }
        loaded = list
        consumed += back.size
        showLoaded(totalChange = back.size)
    }

    /** Re-draws the rows from [loaded], keeping everything else; [totalChange] moves the count. */
    private fun showLoaded(totalChange: Int = 0) {
        val labels = _state.value.labels ?: return
        _state.update {
            it.copy(
                rows = loaded.map { d -> d.toRow(labels) },
                total = it.total?.let { t -> (t + totalChange).coerceAtLeast(0) },
                status = when {
                    it.status != ListStatus.Loaded && it.status != ListStatus.Empty -> it.status
                    loaded.isEmpty() -> ListStatus.Empty
                    else -> ListStatus.Loaded
                },
            )
        }
    }

    /** Page 1 again after the text, filters or sort changed; the labels too if they never loaded. */
    private fun reload() = loadFirstPage(withLabels = _state.value.labels == null, keepRows = false)

    private fun loadFirstPage(withLabels: Boolean, keepRows: Boolean) {
        typingJob?.cancel()
        loadJob?.cancel()
        // A fresh page 1 is in step with the server.
        stale = false
        _state.update {
            if (keepRows) {
                it.copy(refreshing = true, loadingMore = false, loadMoreFailed = null)
            } else {
                it.copy(status = ListStatus.Loading, rows = emptyList(), total = null, refreshing = false, loadingMore = false, loadMoreFailed = null)
            }
        }
        if (!keepRows) {
            loaded = emptyList()
            // Another search, filter or sort: a trashed row may not belong in the new list.
            removed.clear()
        }
        val query = query(page = 1)
        loadedText = query.text
        val known = _state.value.labels
        loadJob = viewModelScope.launch {
            try {
                val (labels, page) = coroutineScope {
                    val labels = if (withLabels || known == null) async { loadLabels() } else null
                    val page = async { browse(query) }
                    (labels?.await() ?: checkNotNull(known)) to page.await()
                }
                consumed = page.documents.size
                hasMore = page.hasMore
                loaded = page.documents
                _state.update {
                    it.copy(
                        labels = labels,
                        rows = page.documents.map { d -> d.toRow(labels) },
                        total = page.total,
                        status = if (page.documents.isEmpty()) ListStatus.Empty else ListStatus.Loaded,
                        refreshing = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                hasMore = false
                loaded = emptyList()
                _state.update { it.copy(status = e.failure(), rows = emptyList(), total = null, refreshing = false) }
            }
        }
    }

    private fun query(page: Int, filters: DocumentFilters = _state.value.filters): DocumentQuery {
        val s = _state.value
        return DocumentQuery(
            text = s.text.takeIf { it.isNotBlank() },
            tagIds = filters.tagIds,
            correspondentId = filters.correspondentId,
            documentTypeId = filters.documentTypeId,
            sort = s.sort.sort,
            descending = s.sort.descending,
            page = page,
            pageSize = PAGE_SIZE,
        )
    }
}

private fun Exception.failure(): ListStatus.Failed = when (this) {
    is ServerException -> ListStatus.Failed(userMessage(), unreachable = kind == Kind.UNREACHABLE || kind == Kind.NO_ANSWER)
    else -> ListStatus.Failed("Couldn't load the documents", unreachable = false)
}
