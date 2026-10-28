package io.github.yasarmahomedabbas.paperlesscompanion.ui.documents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Document
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentQuery
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

/**
 * The Documents list: pages of documents with their labels, searched as you type, filtered and
 * sorted. Paging is by hand: [loadMore] fetches the next page while the last one said there is
 * more. Every change to the text, filters or sort reloads from page 1, and a newer load cancels
 * the older one, so a slow search never overwrites a newer one.
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
    private var loadedPage = 0
    private var hasMore = false

    /** The search text the shown list was loaded with. */
    private var loadedText: String? = null

    /** The documents behind [DocumentsUiState.rows], in the same order. */
    private var loaded: List<Document> = emptyList()

    init {
        refresh()
    }

    /** Reloads the labels and page 1. Rows already on screen stay there while it runs. */
    fun refresh() = loadFirstPage(withLabels = true, keepRows = _state.value.rows.isNotEmpty())

    fun loadMore() {
        val s = _state.value
        val labels = s.labels ?: return
        if (s.loadingMore || s.refreshing || s.status != ListStatus.Loaded || !hasMore) return
        val query = query(page = loadedPage + 1)
        _state.update { it.copy(loadingMore = true, loadMoreFailed = null) }
        loadJob = viewModelScope.launch {
            try {
                val page = browse(query)
                loadedPage = query.page
                hasMore = page.hasMore
                loaded = loaded + page.documents
                _state.update {
                    it.copy(rows = it.rows + page.documents.map { d -> d.toRow(labels) }, total = page.total, loadingMore = false)
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
     * document that isn't loaded is left alone. The search match it was found by stays.
     */
    fun patch(document: Document) {
        val index = loaded.indexOfFirst { it.id == document.id }
        if (index < 0) return
        loaded = loaded.toMutableList().also { it[index] = document.copy(searchHit = loaded[index].searchHit) }
        showLoaded()
    }

    /** Re-draws the rows from [loaded], keeping everything else. */
    private fun showLoaded() {
        val labels = _state.value.labels ?: return
        _state.update { it.copy(rows = loaded.map { d -> d.toRow(labels) }) }
    }

    /** Page 1 again after the text, filters or sort changed; the labels too if they never loaded. */
    private fun reload() = loadFirstPage(withLabels = _state.value.labels == null, keepRows = false)

    private fun loadFirstPage(withLabels: Boolean, keepRows: Boolean) {
        typingJob?.cancel()
        loadJob?.cancel()
        _state.update {
            if (keepRows) {
                it.copy(refreshing = true, loadingMore = false, loadMoreFailed = null)
            } else {
                it.copy(status = ListStatus.Loading, rows = emptyList(), total = null, refreshing = false, loadingMore = false, loadMoreFailed = null)
            }
        }
        if (!keepRows) loaded = emptyList()
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
                loadedPage = 1
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
