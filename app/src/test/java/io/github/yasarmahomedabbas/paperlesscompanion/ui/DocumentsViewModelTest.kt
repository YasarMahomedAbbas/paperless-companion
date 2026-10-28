package io.github.yasarmahomedabbas.paperlesscompanion.ui

import io.github.yasarmahomedabbas.paperlesscompanion.CONFIG
import io.github.yasarmahomedabbas.paperlesscompanion.FakeLibrary
import io.github.yasarmahomedabbas.paperlesscompanion.FakeSettings
import io.github.yasarmahomedabbas.paperlesscompanion.MainDispatcherRule
import io.github.yasarmahomedabbas.paperlesscompanion.doc
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.BulkEdit
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Document
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentPage
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentQuery
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentSort
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Label
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Labels
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.SearchHit
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException.Kind
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Tag
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.with
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
        library.labelFailure = ServerException(Kind.UNAUTHORIZED, "401")
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
        library.labelFailure = ServerException(Kind.UNREACHABLE, "down")
        val vm = loaded()
        library.labelFailure = null

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

    // Changes made elsewhere (#11)

    @Test
    fun `an edited document replaces its row in place and keeps the others and the page`() = runTest(main.dispatcher) {
        val vm = loaded()
        vm.loadMore()
        advanceUntilIdle()
        val before = vm.s.rows
        val labels = vm.s.labels!!

        val edited = doc(30, "Renamed").copy(correspondentId = 2)
        vm.patch(edited)

        assertEquals(50, vm.s.rows.size)
        assertEquals(edited.toRow(labels), vm.s.rows[29])
        assertEquals(before.filterIndexed { i, _ -> i != 29 }, vm.s.rows.filterIndexed { i, _ -> i != 29 })
        assertEquals(60, vm.s.total)
        // Paging carries on from page 2.
        vm.loadMore()
        advanceUntilIdle()
        assertEquals(3, queries.last().page)
    }

    @Test
    fun `an edit of a document not loaded changes nothing`() = runTest(main.dispatcher) {
        val vm = loaded()
        val before = vm.s

        vm.patch(doc(99, "Elsewhere"))

        assertEquals(before, vm.s)
    }

    @Test
    fun `an edited search result keeps its snippet`() = runTest(main.dispatcher) {
        val hit = SearchHit(1.0, "rates due", listOf(0..4))
        answer = { DocumentPage(1, listOf(doc(1, "Rates").copy(searchHit = hit)), hasMore = false) }
        val vm = loaded()

        vm.patch(doc(1, "Rates — August"))

        assertEquals("Rates — August", vm.s.rows.single().title)
        assertEquals(hit, vm.s.rows.single().snippet)
    }

    @Test
    fun `a bulk change patches every loaded document it names`() = runTest(main.dispatcher) {
        val vm = loaded()
        val labels = vm.s.labels!!

        vm.apply(listOf(2, 4, 99), BulkEdit.SetCorrespondent(2))

        assertEquals(doc(2, "Bill").copy(correspondentId = 2).toRow(labels), vm.s.rows[1])
        assertEquals(doc(4, "Bill").copy(correspondentId = 2).toRow(labels), vm.s.rows[3])
        assertEquals(doc(3, "Bill").toRow(labels), vm.s.rows[2])
        assertEquals(listOf(2, 4), vm.documents(listOf(4, 2, 99)).map { it.id })
        assertEquals(2, vm.documents(listOf(2)).single().correspondentId)
    }

    @Test
    fun `trashed rows go and come back where they were`() = runTest(main.dispatcher) {
        val vm = loaded()
        val before = vm.s.rows

        vm.remove(listOf(1, 5, 99))

        assertEquals(23, vm.s.rows.size)
        assertEquals(58, vm.s.total)
        assertFalse(vm.s.rows.any { it.id == 1 || it.id == 5 })

        vm.putBack(listOf(5, 1))

        assertEquals(before, vm.s.rows)
        assertEquals(60, vm.s.total)
    }

    @Test
    fun `trashing every row is empty, and Undo fills it again`() = runTest(main.dispatcher) {
        answer = { DocumentPage(1, listOf(doc(1)), hasMore = false) }
        val vm = loaded()

        vm.remove(listOf(1))
        assertEquals(ListStatus.Empty, vm.s.status)
        assertEquals(0, vm.s.total)

        vm.putBack(listOf(1))
        assertEquals(ListStatus.Loaded, vm.s.status)
        assertEquals(listOf(1), vm.s.rows.map { it.id })
    }

    @Test
    fun `a row put back after the list shrank goes at the end`() = runTest(main.dispatcher) {
        answer = { DocumentPage(3, listOf(doc(1), doc(2), doc(3)), hasMore = false) }
        val vm = loaded()

        vm.remove(listOf(3))
        vm.remove(listOf(1, 2))
        vm.putBack(listOf(3))

        assertEquals(listOf(3), vm.s.rows.map { it.id })
    }

    @Test
    fun `putting back what wasn't removed, or is already back, changes nothing`() = runTest(main.dispatcher) {
        val vm = loaded()
        vm.remove(listOf(1))
        vm.putBack(listOf(1))
        val before = vm.s

        vm.putBack(listOf(1, 2))

        assertEquals(before, vm.s)
    }

    @Test
    fun `a new search forgets the trashed rows`() = runTest(main.dispatcher) {
        val vm = loaded()
        vm.remove(listOf(1))
        // The server no longer lists it.
        answer = { DocumentPage(59, (2..25).map { doc(it) }, hasMore = true) }
        vm.setSort(SortOption.entries.last())
        advanceUntilIdle()

        vm.putBack(listOf(1))

        assertEquals(24, vm.s.rows.size)
        assertEquals(59, vm.s.total)
    }

    /** A server holding [ids] in list order, paging them as Paperless does; trash takes them out of it. */
    private fun serving(ids: MutableList<Int>) {
        answer = { q ->
            val from = (q.page - 1) * q.pageSize
            DocumentPage(ids.size, ids.drop(from).take(q.pageSize).map { doc(it) }, hasMore = from + q.pageSize < ids.size)
        }
    }

    @Test
    fun `after a trash, loading more skips no document`() = runTest(main.dispatcher) {
        val server = (1..60).toMutableList()
        serving(server)
        val vm = loaded()

        server.remove(5)
        vm.remove(listOf(5))
        vm.loadMore()
        advanceUntilIdle()

        assertEquals((1..51).filter { it != 5 }, vm.s.rows.map { it.id })
    }

    @Test
    fun `a trash, more, Undo and more again shows every document once`() = runTest(main.dispatcher) {
        val server = (1..80).toMutableList()
        serving(server)
        val vm = loaded()

        server.remove(5)
        vm.remove(listOf(5))
        vm.loadMore()
        advanceUntilIdle()
        server.add(4, 5)
        vm.putBack(listOf(5))
        vm.loadMore()
        advanceUntilIdle()

        assertEquals((1..80).toList(), vm.s.rows.map { it.id })
    }

    @Test
    fun `a document that moved onto a later page is shown once`() = runTest(main.dispatcher) {
        val server = (1..60).toMutableList()
        serving(server)
        val vm = loaded()

        // Renamed elsewhere, it now sorts after the first page.
        server.remove(3)
        server.add(26, 3)
        vm.loadMore()
        advanceUntilIdle()

        val ids = vm.s.rows.map { it.id }
        assertEquals(ids.distinct(), ids)
        assertEquals(1, ids.count { it == 3 })
    }

    // A change that moves documents in the list (#32)

    /**
     * A server holding [docs] by id, as Paperless lists them: only those carrying every one of the
     * query's tags, a search matching titles that hold its text, ordered by the query's sort (ties by
     * id), then paged.
     */
    private fun serving(docs: MutableMap<Int, Document>) {
        answer = { q ->
            val matching = docs.values
                .filter { d -> d.tagIds.containsAll(q.tagIds) }
                .filter { d -> q.text == null || d.title.contains(q.text!!, ignoreCase = true) }
            val ordered = when {
                q.text != null -> matching.sortedBy { it.id }
                q.sort == DocumentSort.TITLE -> matching.sortedWith(compareBy<Document> { it.title }.thenBy { it.id })
                q.sort == DocumentSort.CREATED && q.descending ->
                    matching.sortedWith(compareByDescending<Document> { it.created }.thenBy { it.id })
                else -> matching.sortedBy { it.id }
            }
            val from = (q.page - 1) * q.pageSize
            DocumentPage(ordered.size, ordered.drop(from).take(q.pageSize), hasMore = from + q.pageSize < ordered.size)
        }
    }

    private fun documents(count: Int, made: (Int) -> Document = { doc(it) }) =
        (1..count).associateWithTo(mutableMapOf()) { made(it) }

    /** Scrolls to the end of the list. */
    private fun TestScope.loadAll(vm: DocumentsViewModel) {
        repeat(20) {
            vm.loadMore()
            advanceUntilIdle()
        }
    }

    @Test
    fun `taking the filtered tag off loaded documents skips none of the rest`() = runTest(main.dispatcher) {
        val server = documents(60) { doc(it).copy(tagIds = listOf(7)) }
        serving(server)
        val vm = loaded()
        vm.setFilters(DocumentFilters(tagIds = setOf(7)))
        advanceUntilIdle()

        val edit = BulkEdit.ModifyTags(add = emptySet(), remove = setOf(7))
        listOf(1, 2, 3).forEach { server[it] = server.getValue(it).with(edit) }
        vm.apply(listOf(1, 2, 3), edit)
        advanceUntilIdle()
        loadAll(vm)

        assertEquals((4..60).toList(), vm.s.rows.map { it.id })
        assertEquals(57, vm.s.total)
    }

    @Test
    fun `renaming a document in a title sort skips none and repeats none`() = runTest(main.dispatcher) {
        val server = documents(60) { doc(it, "A%02d".format(it)) }
        serving(server)
        val vm = loaded()
        vm.setSort(SortOption.TITLE)
        advanceUntilIdle()

        // It now sorts last, past page 1.
        val renamed = server.getValue(2).copy(title = "Z")
        server[2] = renamed
        vm.patch(renamed)
        advanceUntilIdle()
        loadAll(vm)

        assertEquals((1..60).filter { it != 2 } + 2, vm.s.rows.map { it.id })
    }

    @Test
    fun `changing the date in a date sort skips none and repeats none`() = runTest(main.dispatcher) {
        val day = LocalDate.of(2026, 1, 1)
        val server = documents(60) { doc(it).copy(created = day.minusDays(it.toLong())) }
        serving(server)
        val vm = loaded()

        val redated = server.getValue(2).copy(created = day.minusYears(5))
        server[2] = redated
        vm.patch(redated)
        advanceUntilIdle()
        loadAll(vm)

        assertEquals((1..60).filter { it != 2 } + 2, vm.s.rows.map { it.id })
    }

    @Test
    fun `a change the list neither filters nor sorts by asks the server nothing`() = runTest(main.dispatcher) {
        val server = documents(60) { doc(it).copy(tagIds = listOf(7)) }
        serving(server)
        val vm = loaded()
        vm.setFilters(DocumentFilters(tagIds = setOf(7)))
        advanceUntilIdle()
        val asked = queries.size

        vm.apply(listOf(1, 2), BulkEdit.SetStoragePath(4))
        vm.apply(listOf(3), BulkEdit.ModifyTags(add = setOf(1), remove = emptySet()))
        vm.patch(server.getValue(4).copy(title = "Renamed", correspondentId = 2))
        advanceUntilIdle()

        assertEquals(asked, queries.size)
        assertEquals(4, vm.documents(listOf(2)).single().storagePathId)
        assertEquals("Renamed", vm.s.rows[3].title)
    }

    @Test
    fun `a change during a search asks for the shown results again`() = runTest(main.dispatcher) {
        val server = documents(60) { doc(it, "Bill $it") }
        serving(server)
        val vm = loaded()
        vm.startSearch()
        vm.onTextChange("bill")
        vm.search()
        advanceUntilIdle()
        val asked = queries.size

        vm.apply(listOf(1), BulkEdit.SetCorrespondent(2))
        advanceUntilIdle()

        assertEquals(asked + 1, queries.size)
        assertEquals(DocumentQuery(text = "bill", page = 1, pageSize = 100), queries.last())
        assertEquals(25, vm.s.rows.size)
    }

    @Test
    fun `a window of more than 100 is asked in pages of 100, and paging carries on after it`() = runTest(main.dispatcher) {
        val server = documents(160) { doc(it, "A%03d".format(it)) }
        serving(server)
        val vm = loaded()
        vm.setSort(SortOption.TITLE)
        advanceUntilIdle()
        repeat(4) {
            vm.loadMore()
            advanceUntilIdle()
        }
        assertEquals(125, vm.s.rows.size)
        val asked = queries.size

        val renamed = server.getValue(2).copy(title = "Z")
        server[2] = renamed
        vm.patch(renamed)
        advanceUntilIdle()

        assertEquals(listOf(1 to 100, 2 to 100), queries.drop(asked).map { it.page to it.pageSize })
        assertEquals((1..126).filter { it != 2 }, vm.s.rows.map { it.id })
        assertFalse(vm.s.refreshing)

        vm.loadMore()
        advanceUntilIdle()
        assertEquals(6 to 25, queries.last().let { it.page to it.pageSize })
        loadAll(vm)
        assertEquals((1..160).filter { it != 2 } + 2, vm.s.rows.map { it.id })
    }

    @Test
    fun `a failed re-fetch keeps the patched rows, and Retry asks again before paging on`() = runTest(main.dispatcher) {
        val server = documents(60) { doc(it, "A%02d".format(it)) }
        serving(server)
        val vm = loaded()
        vm.setSort(SortOption.TITLE)
        advanceUntilIdle()
        val e = ServerException(Kind.UNREACHABLE, "down")
        library.failure = e

        val renamed = server.getValue(2).copy(title = "Z")
        server[2] = renamed
        vm.patch(renamed)
        advanceUntilIdle()

        assertEquals(25, vm.s.rows.size)
        assertEquals("Z", vm.s.rows[1].title)
        assertEquals(e.userMessage(), vm.s.loadMoreFailed)
        assertFalse(vm.s.refreshing)
        assertEquals(ListStatus.Loaded, vm.s.status)

        library.failure = null
        val asked = queries.size
        vm.loadMore()
        advanceUntilIdle()

        assertEquals(DocumentQuery(sort = DocumentSort.TITLE, descending = false, page = 1, pageSize = 100), queries.drop(asked).single())
        assertNull(vm.s.loadMoreFailed)
        assertEquals((1..26).filter { it != 2 }, vm.s.rows.map { it.id })

        loadAll(vm)
        assertEquals((1..60).filter { it != 2 } + 2, vm.s.rows.map { it.id })
    }

    @Test
    fun `a refresh while the re-fetch runs cancels it`() = runTest(main.dispatcher) {
        val server = documents(160) { doc(it, "A%03d".format(it)) }
        serving(server)
        val vm = loaded()
        vm.setSort(SortOption.TITLE)
        advanceUntilIdle()
        repeat(4) {
            vm.loadMore()
            advanceUntilIdle()
        }
        val renamed = server.getValue(2).copy(title = "Z")
        server[2] = renamed
        library.documentsGate = CompletableDeferred()
        vm.patch(renamed)
        runCurrent()
        assertTrue(vm.s.refreshing)
        val asked = queries.size

        vm.refresh()
        library.documentsGate!!.complete(Unit)
        advanceUntilIdle()

        // The re-fetch never asked for its second page, and the refresh's page 1 stands.
        assertEquals(listOf(1 to 25), queries.drop(asked).map { it.page to it.pageSize })
        assertEquals((1..26).filter { it != 2 }, vm.s.rows.map { it.id })
        assertFalse(vm.s.refreshing)
        vm.loadMore()
        advanceUntilIdle()
        assertEquals(2 to 25, queries.last().let { it.page to it.pageSize })
    }

    /**
     * Starts a re-fetch, by renaming document 2 in a title sort, and holds it in flight. The server
     * answers it as it stood when it was asked, unless a newer request has been sent since.
     */
    private fun TestScope.refetchInFlight(server: MutableMap<Int, Document>): DocumentsViewModel {
        serving(server)
        val vm = loaded()
        vm.setSort(SortOption.TITLE)
        advanceUntilIdle()
        val renamed = server.getValue(2).copy(title = "Z")
        server[2] = renamed
        serving(server.toMutableMap())
        val asAsked = answer
        serving(server)
        val now = answer
        library.documentsGate = CompletableDeferred()
        vm.patch(renamed)
        runCurrent()
        val sent = queries.size
        answer = { q -> if (queries.size == sent) asAsked(q) else now(q) }
        return vm
    }

    @Test
    fun `a trash while the re-fetch runs isn't undone by its answer`() = runTest(main.dispatcher) {
        val server = documents(60) { doc(it, "A%02d".format(it)) }
        val vm = refetchInFlight(server)

        server.remove(5)
        vm.remove(listOf(5))
        library.documentsGate!!.complete(Unit)
        advanceUntilIdle()

        assertFalse(5 in vm.s.rows.map { it.id })
        loadAll(vm)
        assertEquals((1..60).filter { it != 2 && it != 5 } + 2, vm.s.rows.map { it.id })
    }

    @Test
    fun `an Undo while the re-fetch runs isn't undone by its answer`() = runTest(main.dispatcher) {
        val server = documents(60) { doc(it, "A%02d".format(it)) }
        val vm = refetchInFlight(server)
        library.documentsGate!!.complete(Unit)
        advanceUntilIdle()
        val trashed = server.remove(5)!!
        vm.remove(listOf(5))
        // The next re-fetch is held, then the Undo lands while it waits.
        library.documentsGate = CompletableDeferred()
        vm.patch(server.getValue(10).copy(title = "Zy").also { server[10] = it })
        runCurrent()
        serving(server.toMutableMap())
        val asAsked = answer
        serving(server)
        val now = answer
        val sent = queries.size
        answer = { q -> if (queries.size == sent) asAsked(q) else now(q) }

        server[5] = trashed
        vm.putBack(listOf(5))
        library.documentsGate!!.complete(Unit)
        advanceUntilIdle()

        assertTrue(5 in vm.s.rows.map { it.id })
        loadAll(vm)
        assertEquals((1..60).filter { it != 2 && it != 10 } + listOf(2, 10), vm.s.rows.map { it.id })
    }

    @Test
    fun `an edit while the re-fetch runs isn't undone by its answer`() = runTest(main.dispatcher) {
        val server = documents(60) { doc(it, "A%02d".format(it)) }
        val vm = refetchInFlight(server)

        val edited = server.getValue(4).copy(storagePathId = 9)
        server[4] = edited
        vm.patch(edited)
        library.documentsGate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(9, vm.documents(listOf(4)).single().storagePathId)
    }
}
