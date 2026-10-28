package io.github.yasarmahomedabbas.paperlesscompanion.ui

import io.github.yasarmahomedabbas.paperlesscompanion.CONFIG
import io.github.yasarmahomedabbas.paperlesscompanion.FakeLibrary
import io.github.yasarmahomedabbas.paperlesscompanion.FakeSettings
import io.github.yasarmahomedabbas.paperlesscompanion.MainDispatcherRule
import io.github.yasarmahomedabbas.paperlesscompanion.doc
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentPage
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentQuery
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentSort
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Label
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Labels
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.SearchHit
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException.Kind
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Tag
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.BrowseDocumentsUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.LoadLabelsUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.userMessage
import io.github.yasarmahomedabbas.paperlesscompanion.ui.documents.BEST_MATCH
import io.github.yasarmahomedabbas.paperlesscompanion.ui.documents.DocumentFilters
import io.github.yasarmahomedabbas.paperlesscompanion.ui.documents.DocumentsViewModel
import io.github.yasarmahomedabbas.paperlesscompanion.ui.documents.ListStatus
import io.github.yasarmahomedabbas.paperlesscompanion.ui.documents.SortOption
import io.github.yasarmahomedabbas.paperlesscompanion.ui.documents.toRow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val library = FakeLibrary()
    private val settings = FakeSettings(CONFIG)
    private val queries get() = library.queries

    /** Three pages of 25, 60 documents in all, ids running on from page to page; a search titles them after its text. */
    private var answer: (DocumentQuery) -> DocumentPage = { q ->
        DocumentPage(60, (1..25).map { doc((q.page - 1) * 25 + it, q.text ?: "Bill") }, hasMore = q.page < 3)
    }

    init {
        library.pages = { q -> answer(q) }
    }

    private fun viewModel() = DocumentsViewModel(BrowseDocumentsUseCase(library, settings), LoadLabelsUseCase(library, settings))

    private fun TestScope.loaded(): DocumentsViewModel = viewModel().also { advanceUntilIdle() }

    private val DocumentsViewModel.s get() = state.value

    // First load

    @Test
    fun `the first load brings the labels and page 1`() = runTest(main.dispatcher) {
        val vm = viewModel()
        assertEquals(ListStatus.Loading, vm.s.status)

        advanceUntilIdle()

        assertEquals(ListStatus.Loaded, vm.s.status)
        assertEquals(25, vm.s.rows.size)
        assertEquals(60, vm.s.total)
        assertEquals(listOf("Inbox"), vm.s.labels?.tags?.map { it.name })
        assertEquals(DocumentQuery(page = 1, pageSize = 25), queries.single())
        assertTrue(library.calls.containsAll(listOf("tags", "correspondents", "documentTypes", "storagePaths")))
    }

    @Test
    fun `no documents is empty`() = runTest(main.dispatcher) {
        answer = { DocumentPage(0, emptyList(), hasMore = false) }
        val vm = loaded()

        assertEquals(ListStatus.Empty, vm.s.status)
        assertEquals(0, vm.s.total)
    }

    @Test
    fun `an unreachable server fails as the network`() = runTest(main.dispatcher) {
        val e = ServerException(Kind.UNREACHABLE, "timeout")
        library.failure = e
        val vm = loaded()

        assertEquals(ListStatus.Failed(e.userMessage(), unreachable = true), vm.s.status)
    }

    @Test
    fun `no answer counts as the network too`() = runTest(main.dispatcher) {
        library.failure = ServerException(Kind.NO_ANSWER, "reset")
        val vm = loaded()

        assertTrue((vm.s.status as ListStatus.Failed).unreachable)
    }

    @Test
    fun `a refusal fails with the server's words`() = runTest(main.dispatcher) {
        library.failure = ServerException(Kind.SERVER, "Paperless returned 500")
        val vm = loaded()

        assertEquals(ListStatus.Failed("Paperless returned 500", unreachable = false), vm.s.status)
    }

    @Test
    fun `labels that fail to load fail the list`() = runTest(main.dispatcher) {
        library.labelsFailure = ServerException(Kind.UNAUTHORIZED, "401")
        val vm = loaded()

        assertFalse((vm.s.status as ListStatus.Failed).unreachable)
        assertTrue(vm.s.rows.isEmpty())
    }

    @Test
    fun `an unexpected error fails with a plain message instead of a crash`() = runTest(main.dispatcher) {
        answer = { error("surprise") }
        val vm = loaded()

        assertEquals(ListStatus.Failed("Couldn't load the documents", unreachable = false), vm.s.status)
    }

    // Load more

    @Test
    fun `load more appends the next page`() = runTest(main.dispatcher) {
        val vm = loaded()

        vm.loadMore()
        assertTrue(vm.s.loadingMore)
        advanceUntilIdle()

        assertFalse(vm.s.loadingMore)
        assertEquals((1..50).toList(), vm.s.rows.map { it.id })
        assertEquals(listOf(1, 2), queries.map { it.page })
    }

    @Test
    fun `load more is ignored while one is running`() = runTest(main.dispatcher) {
        val vm = loaded()
        library.documentsGate = CompletableDeferred()

        vm.loadMore()
        runCurrent()
        vm.loadMore()
        library.documentsGate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(1, 2), queries.map { it.page })
    }

    @Test
    fun `load more stops after the last page`() = runTest(main.dispatcher) {
        val vm = loaded()
        repeat(4) {
            vm.loadMore()
            advanceUntilIdle()
        }

        assertEquals(listOf(1, 2, 3), queries.map { it.page })
        assertEquals(75, vm.s.rows.size)
    }

    @Test
    fun `load more does nothing before the list has loaded`() = runTest(main.dispatcher) {
        library.failure = ServerException(Kind.UNREACHABLE, "down")
        val vm = loaded()

        vm.loadMore()
        advanceUntilIdle()

        assertEquals(1, queries.size)
    }

    @Test
    fun `a failed load more keeps the rows and retries`() = runTest(main.dispatcher) {
        val vm = loaded()
        val e = ServerException(Kind.UNREACHABLE, "down")
        library.failure = e

        vm.loadMore()
        advanceUntilIdle()

        assertEquals(25, vm.s.rows.size)
        assertEquals(ListStatus.Loaded, vm.s.status)
        assertEquals(e.userMessage(), vm.s.loadMoreFailed)

        library.failure = null
        vm.loadMore()
        advanceUntilIdle()

        assertNull(vm.s.loadMoreFailed)
        assertEquals(50, vm.s.rows.size)
        assertEquals(listOf(1, 2, 2), queries.map { it.page })
    }

    // Refresh

    @Test
    fun `refresh keeps the rows on screen and reloads page 1 and the labels`() = runTest(main.dispatcher) {
        val vm = loaded()
        vm.loadMore()
        advanceUntilIdle()
        library.tags = listOf(Tag(9, "New", null, null, false, 1))
        library.documentsGate = CompletableDeferred()

        vm.refresh()
        runCurrent()

        assertTrue(vm.s.refreshing)
        assertEquals(50, vm.s.rows.size)
        assertEquals(ListStatus.Loaded, vm.s.status)

        library.documentsGate!!.complete(Unit)
        advanceUntilIdle()

        assertFalse(vm.s.refreshing)
        assertEquals(25, vm.s.rows.size)
        assertEquals(listOf(1, 2, 1), queries.map { it.page })
        assertEquals(listOf("New"), vm.s.labels?.tags?.map { it.name })
    }

    @Test
    fun `a failed refresh shows the load failure`() = runTest(main.dispatcher) {
        val vm = loaded()
        library.failure = ServerException(Kind.UNREACHABLE, "down")

        vm.refresh()
        advanceUntilIdle()

        assertTrue(vm.s.status is ListStatus.Failed)
        assertFalse(vm.s.refreshing)
        assertTrue(vm.s.rows.isEmpty())
    }

    @Test
    fun `try again after a failure loads from scratch`() = runTest(main.dispatcher) {
        library.failure = ServerException(Kind.UNREACHABLE, "down")
        val vm = loaded()
        library.failure = null

        vm.refresh()
        assertEquals(ListStatus.Loading, vm.s.status)
        assertFalse(vm.s.refreshing)
        advanceUntilIdle()

        assertEquals(ListStatus.Loaded, vm.s.status)
    }

    // Search

    @Test
    fun `typing waits for the debounce before searching`() = runTest(main.dispatcher) {
        val vm = loaded()

        vm.onTextChange("inv")
        vm.onTextChange("invoice")
        advanceTimeBy(399)
        runCurrent()
        assertEquals(1, queries.size)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(null, "invoice"), queries.map { it.text })
    }

    @Test
    fun `the search key searches at once`() = runTest(main.dispatcher) {
        val vm = loaded()

        vm.onTextChange("invoice")
        vm.search()
        runCurrent()
        assertEquals("invoice", queries.last().text)

        advanceUntilIdle()
        assertEquals(2, queries.size)
    }

    @Test
    fun `a newer search cancels the older one`() = runTest(main.dispatcher) {
        val vm = loaded()
        library.documentsGate = CompletableDeferred()

        vm.onTextChange("old")
        vm.search()
        runCurrent()
        vm.onTextChange("new")
        vm.search()
        runCurrent()
        library.documentsGate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(null, "old", "new"), queries.map { it.text })
        assertTrue(vm.s.rows.all { it.title == "new" })
        assertEquals(ListStatus.Loaded, vm.s.status)
    }

    @Test
    fun `clearing the text goes back to browsing in the chosen sort`() = runTest(main.dispatcher) {
        val vm = loaded()
        vm.setSort(SortOption.TITLE)
        vm.onTextChange("invoice")
        vm.search()
        advanceUntilIdle()

        vm.onTextChange("")
        advanceUntilIdle()

        val last = queries.last()
        assertNull(last.text)
        assertEquals(DocumentSort.TITLE, last.sort)
        assertFalse(last.descending)
        assertEquals(SortOption.TITLE.label, vm.s.sortLabel)
    }

    @Test
    fun `searching sorts by best match and rows carry their snippet`() = runTest(main.dispatcher) {
        val hit = SearchHit(1.0, "your invoice for March", listOf(5..11))
        answer = { q -> DocumentPage(1, listOf(doc(7).copy(searchHit = hit.takeIf { q.text != null })), hasMore = false) }
        val vm = loaded()

        vm.onTextChange("invoice")
        vm.search()
        advanceUntilIdle()

        assertEquals(BEST_MATCH, vm.s.sortLabel)
        assertTrue(vm.s.showResultLine)
        assertEquals(hit, vm.s.rows.single().snippet)
    }

    @Test
    fun `start and stop searching, stopping clears the text and browses again`() = runTest(main.dispatcher) {
        val vm = loaded()

        vm.startSearch()
        assertTrue(vm.s.searching)
        vm.onTextChange("invoice")
        vm.search()
        advanceUntilIdle()

        vm.stopSearch()
        advanceUntilIdle()

        assertFalse(vm.s.searching)
        assertEquals("", vm.s.text)
        assertEquals(listOf(null, "invoice", null), queries.map { it.text })
    }

    @Test
    fun `stopping a search that never ran loads nothing`() = runTest(main.dispatcher) {
        val vm = loaded()

        vm.startSearch()
        vm.stopSearch()
        advanceUntilIdle()

        assertEquals(1, queries.size)
    }

    @Test
    fun `stopping while the text was being cleared still goes back to browsing`() = runTest(main.dispatcher) {
        val vm = loaded()
        vm.onTextChange("invoice")
        vm.search()
        advanceUntilIdle()

        vm.onTextChange("")
        vm.stopSearch()
        advanceUntilIdle()

        assertEquals(listOf(null, "invoice", null), queries.map { it.text })
    }

    // Filters and sort

    @Test
    fun `filters map to the query`() = runTest(main.dispatcher) {
        val vm = loaded()

        vm.setFilters(DocumentFilters(tagIds = setOf(1, 5), correspondentId = 2, documentTypeId = 3))
        advanceUntilIdle()

        val last = queries.last()
        assertEquals(setOf(1, 5), last.tagIds)
        assertEquals(2, last.correspondentId)
        assertEquals(3, last.documentTypeId)
        assertEquals(1, last.page)
        assertEquals(4, vm.s.filters.count)
        assertTrue(vm.s.showResultLine)
    }

    @Test
    fun `each sort option maps to the query`() = runTest(main.dispatcher) {
        val vm = loaded()

        for (option in SortOption.entries) {
            vm.setSort(option)
            advanceUntilIdle()
            assertEquals(option.sort to option.descending, queries.last().let { it.sort to it.descending })
            assertEquals(option, vm.s.sort)
        }
    }

    @Test
    fun `a filter change shows the loading state and starts again from page 1`() = runTest(main.dispatcher) {
        val vm = loaded()
        vm.loadMore()
        advanceUntilIdle()

        vm.setFilters(DocumentFilters(tagIds = setOf(1)))
        assertEquals(ListStatus.Loading, vm.s.status)
        assertTrue(vm.s.rows.isEmpty())
        advanceUntilIdle()

        assertEquals(listOf(1, 2, 1), queries.map { it.page })
        assertEquals(25, vm.s.rows.size)
    }

    @Test
    fun `removing one filter keeps the others`() = runTest(main.dispatcher) {
        val vm = loaded()
        vm.setFilters(DocumentFilters(tagIds = setOf(1, 5), correspondentId = 2, documentTypeId = 3))

        vm.removeTag(5)
        assertEquals(DocumentFilters(setOf(1), 2, 3), vm.s.filters)
        vm.removeCorrespondent()
        assertEquals(DocumentFilters(setOf(1), null, 3), vm.s.filters)
        vm.removeDocumentType()
        assertEquals(DocumentFilters(setOf(1)), vm.s.filters)
        advanceUntilIdle()

        assertEquals(DocumentQuery(tagIds = setOf(1)), queries.last())
    }

    @Test
    fun `clear filters drops the filters and the search text`() = runTest(main.dispatcher) {
        val vm = loaded()
        vm.setFilters(DocumentFilters(tagIds = setOf(1)))
        vm.onTextChange("invoice")
        vm.search()
        advanceUntilIdle()

        vm.clearFilters()
        advanceUntilIdle()

        assertTrue(vm.s.filters.isEmpty)
        assertEquals("", vm.s.text)
        assertFalse(vm.s.showResultLine)
        assertEquals(DocumentQuery(), queries.last())
    }

    @Test
    fun `a filter change while the labels never loaded loads them too`() = runTest(main.dispatcher) {
        library.labelsFailure = ServerException(Kind.UNREACHABLE, "down")
        val vm = loaded()
        library.labelsFailure = null

        vm.setSort(SortOption.OLDEST)
        advanceUntilIdle()

        assertEquals(ListStatus.Loaded, vm.s.status)
        assertEquals(2, library.calls.count { it == "tags" })
    }

    @Test
    fun `a filter change keeps the labels it has`() = runTest(main.dispatcher) {
        val vm = loaded()

        vm.setSort(SortOption.OLDEST)
        advanceUntilIdle()

        assertEquals(1, library.calls.count { it == "tags" })
    }

    // Filter preview

    @Test
    fun `the filter preview counts the draft after the debounce with one row`() = runTest(main.dispatcher) {
        answer = { q -> DocumentPage(if (q.tagIds.isEmpty()) 60 else 12, listOf(doc()), hasMore = false) }
        val vm = loaded()

        vm.previewFilters(DocumentFilters(tagIds = setOf(1)))
        vm.previewFilters(DocumentFilters(tagIds = setOf(1, 2)))
        advanceTimeBy(399)
        runCurrent()
        assertEquals(1, queries.size)
        assertNull(vm.s.filterPreviewCount)

        advanceTimeBy(1)
        runCurrent()

        assertEquals(DocumentQuery(tagIds = setOf(1, 2), pageSize = 1), queries.last())
        assertEquals(2, queries.size)
        assertEquals(12, vm.s.filterPreviewCount)
        assertTrue(vm.s.filters.isEmpty)
        assertEquals(60, vm.s.total)
    }

    @Test
    fun `a failed preview shows no count`() = runTest(main.dispatcher) {
        val vm = loaded()
        library.failure = ServerException(Kind.UNREACHABLE, "down")

        vm.previewFilters(DocumentFilters(tagIds = setOf(1)))
        advanceUntilIdle()

        assertNull(vm.s.filterPreviewCount)
        assertEquals(ListStatus.Loaded, vm.s.status)
    }

    @Test
    fun `stopping the preview cancels it and clears the count`() = runTest(main.dispatcher) {
        val vm = loaded()
        vm.previewFilters(DocumentFilters(tagIds = setOf(1)))
        advanceUntilIdle()
        assertEquals(60, vm.s.filterPreviewCount)

        vm.previewFilters(DocumentFilters(tagIds = setOf(2)))
        vm.stopPreview()
        advanceUntilIdle()

        assertNull(vm.s.filterPreviewCount)
        assertEquals(2, queries.size)
    }

    @Test
    fun `applying the filters ends the preview`() = runTest(main.dispatcher) {
        val vm = loaded()
        vm.previewFilters(DocumentFilters(tagIds = setOf(1)))
        advanceUntilIdle()

        vm.setFilters(DocumentFilters(tagIds = setOf(1)))

        assertNull(vm.s.filterPreviewCount)
    }

    // Rows

    private val labels = Labels(
        tags = listOf(
            Tag(1, "Inbox", null, null, true, null),
            Tag(2, "Bills", null, null, false, null),
            Tag(3, "Tax", null, null, false, null),
            Tag(4, "2025", null, null, false, null),
        ),
        correspondents = listOf(Label(2, "Power company", null)),
        documentTypes = emptyList(),
        storagePaths = emptyList(),
    )

    @Test
    fun `a row shows the correspondent and the date`() {
        val row = doc(1).copy(correspondentId = 2, created = LocalDate.of(2025, 3, 4)).toRow(labels)

        assertEquals("Power company · 4 Mar 2025", row.meta)
        assertEquals("Bill 1", row.title)
        assertEquals(doc(1).thumbnail, row.thumbnail)
    }

    @Test
    fun `a row shows the date or the correspondent alone`() {
        assertEquals("4 Mar 2025", doc().copy(created = LocalDate.of(2025, 3, 4)).toRow(labels).meta)
        assertEquals("Power company", doc().copy(correspondentId = 2).toRow(labels).meta)
        assertEquals("", doc().toRow(labels).meta)
    }

    @Test
    fun `a correspondent the server no longer has is skipped`() {
        assertEquals("4 Mar 2025", doc().copy(correspondentId = 99, created = LocalDate.of(2025, 3, 4)).toRow(labels).meta)
    }

    @Test
    fun `a row shows two tags in the document's order and counts the rest`() {
        val row = doc().copy(tagIds = listOf(3, 1, 2, 4)).toRow(labels)

        assertEquals(listOf("Tax", "Inbox"), row.tags)
        assertEquals(2, row.moreTags)
    }

    @Test
    fun `a gone tag is skipped and not counted`() {
        val row = doc().copy(tagIds = listOf(99, 2)).toRow(labels)

        assertEquals(listOf("Bills"), row.tags)
        assertEquals(0, row.moreTags)
    }

    @Test
    fun `an untitled document says so`() {
        assertEquals("Untitled", doc(title = " ").toRow(labels).title)
    }

    @Test
    fun `filters count each tag once`() {
        assertEquals(0, DocumentFilters().count)
        assertTrue(DocumentFilters().isEmpty)
        assertEquals(3, DocumentFilters(tagIds = setOf(1, 2), documentTypeId = 3).count)
    }
}
