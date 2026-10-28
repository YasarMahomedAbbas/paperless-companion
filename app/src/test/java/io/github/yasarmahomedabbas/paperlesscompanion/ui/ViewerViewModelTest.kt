package io.github.yasarmahomedabbas.paperlesscompanion.ui

import io.github.yasarmahomedabbas.paperlesscompanion.CONFIG
import io.github.yasarmahomedabbas.paperlesscompanion.FakeExports
import io.github.yasarmahomedabbas.paperlesscompanion.FakeLibrary
import io.github.yasarmahomedabbas.paperlesscompanion.FakeServer
import io.github.yasarmahomedabbas.paperlesscompanion.FakeSettings
import io.github.yasarmahomedabbas.paperlesscompanion.MainDispatcherRule
import io.github.yasarmahomedabbas.paperlesscompanion.doc
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentFile
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentFileKind
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException.Kind
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Tag
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.DocumentLinkUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.ExportDocumentUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.GetDocumentUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.LoadLabelsUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.OpenDocumentFileUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.ui.viewer.DetailsState
import io.github.yasarmahomedabbas.paperlesscompanion.ui.viewer.DocumentDetails
import io.github.yasarmahomedabbas.paperlesscompanion.ui.viewer.FileState
import io.github.yasarmahomedabbas.paperlesscompanion.ui.viewer.ViewerEvent
import io.github.yasarmahomedabbas.paperlesscompanion.ui.viewer.ViewerViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ViewerViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val library = FakeLibrary()
    private val settings = FakeSettings(CONFIG)
    private val exports = FakeExports()

    private val bill = doc(7, "Power bill").copy(
        created = LocalDate.of(2024, 3, 1),
        added = Instant.parse("2024-03-02T10:00:00Z"),
        correspondentId = 2,
        documentTypeId = 3,
        storagePathId = 4,
        tagIds = listOf(9, 1, 8),
        mimeType = "application/pdf",
    )
    private val photo = DocumentFile("IMG_1.jpg", "image/jpeg", File("IMG_1.jpg"))
    private val text = DocumentFile("note.txt", "text/plain", File("note.txt"))

    init {
        library.document = bill
        library.tags = listOf(library.tags.first(), Tag(8, "Paid", null, null, false, 1))
    }

    private fun viewModel() = ViewerViewModel(
        7,
        GetDocumentUseCase(library, settings),
        LoadLabelsUseCase(library, settings),
        OpenDocumentFileUseCase(library, settings),
        DocumentLinkUseCase(FakeServer(), settings),
        ExportDocumentUseCase(exports),
    )

    /** Collects every event the view model sends from now on, as it is sent. */
    private fun TestScope.events(vm: ViewerViewModel): MutableList<ViewerEvent> {
        val events = mutableListOf<ViewerEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.toList(events) }
        return events
    }

    private fun ViewerViewModel.details() = (state.value.details as DetailsState.Ready).details

    // --- loading ---

    @Test
    fun `loads the document, its labels and its preview side by side`() = runTest(main.dispatcher) {
        library.fileGate = CompletableDeferred()
        val vm = viewModel()
        assertEquals(DetailsState.Loading, vm.state.value.details)
        runCurrent()

        // The file is still on its way while the details are already shown.
        assertTrue(vm.state.value.file is FileState.Loading)
        assertEquals(
            DocumentDetails(
                title = "Power bill",
                created = LocalDate.of(2024, 3, 1),
                added = Instant.parse("2024-03-02T10:00:00Z"),
                correspondent = "Power company",
                documentType = "Invoice",
                storagePath = "Bills",
                tags = listOf("Inbox", "Paid"),
                isPhotoOriginal = false,
                originalIsPdf = true,
            ),
            vm.details(),
        )
        library.fileGate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(FileState.Ready(library.file), vm.state.value.file)
        assertTrue("file 7 PREVIEW" in library.calls)
        assertTrue("document 7" in library.calls)
        assertTrue("tags" in library.calls)
    }

    @Test
    fun `tag names keep the document's order and unknown ids are left out`() = runTest(main.dispatcher) {
        library.document = bill.copy(tagIds = listOf(8, 1, 99), correspondentId = 99, documentTypeId = null)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(listOf("Paid", "Inbox"), vm.details().tags)
        assertNull(vm.details().correspondent)
        assertNull(vm.details().documentType)
        assertEquals("Bills", vm.details().storagePath)
    }

    @Test
    fun `a label failure leaves the names out but shows the details`() = runTest(main.dispatcher) {
        library.labelFailure = ServerException(Kind.SERVER, "Paperless returned HTTP 500: x")
        val vm = viewModel()
        advanceUntilIdle()

        val details = vm.details()
        assertEquals("Power bill", details.title)
        assertNull(details.correspondent)
        assertNull(details.documentType)
        assertNull(details.storagePath)
        assertEquals(emptyList<String>(), details.tags)
    }

    @Test
    fun `a gone document fails the details, and retry reloads it`() = runTest(main.dispatcher) {
        library.documentFailure = ServerException(Kind.NOT_FOUND, "Paperless returned HTTP 404: gone")
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(DetailsState.Failed("Paperless returned HTTP 404: gone", unreachable = false), vm.state.value.details)
        assertTrue("the file loads regardless", vm.state.value.file is FileState.Ready)

        library.documentFailure = null
        library.calls.clear()
        vm.retry()
        advanceUntilIdle()

        assertEquals("Power bill", vm.details().title)
        assertFalse("a loaded file isn't fetched again", library.calls.any { it.startsWith("file") })
    }

    @Test
    fun `an unreachable server fails the details in the unreachable tone`() = runTest(main.dispatcher) {
        library.documentFailure = ServerException(Kind.UNREACHABLE, "can't connect to paperless")
        val vm = viewModel()
        advanceUntilIdle()

        val failed = vm.state.value.details as DetailsState.Failed
        assertTrue(failed.unreachable)
        assertTrue(failed.message.startsWith("Can't reach Paperless"))
    }

    // --- the file ---

    @Test
    fun `the file reports its progress`() = runTest(main.dispatcher) {
        library.progress = listOf(0L to 2_000_000L, 1_000_000L to 2_000_000L)
        library.fileGate = CompletableDeferred()
        val vm = viewModel()
        runCurrent()

        assertEquals(FileState.Loading(1_000_000L, 2_000_000L), vm.state.value.file)
    }

    @Test
    fun `a file of unknown size reports no total`() = runTest(main.dispatcher) {
        library.progress = listOf(0L to null, 500L to null)
        library.fileGate = CompletableDeferred()
        val vm = viewModel()
        runCurrent()

        assertEquals(FileState.Loading(500L, null), vm.state.value.file)
    }

    @Test
    fun `a preview that isn't a PDF can't be viewed`() = runTest(main.dispatcher) {
        library.file = text
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(FileState.NotViewable(text), vm.state.value.file)
    }

    @Test
    fun `a pdf is known by its name when the type is missing`() = runTest(main.dispatcher) {
        val untyped = DocumentFile("bill.PDF", null, File("bill.PDF"))
        library.file = untyped
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(FileState.Ready(untyped), vm.state.value.file)
    }

    @Test
    fun `a failed file is shown in the unreachable tone, and retry fetches it again`() = runTest(main.dispatcher) {
        library.fileFailure = ServerException(Kind.UNREACHABLE, "can't connect to paperless")
        val vm = viewModel()
        advanceUntilIdle()

        val failed = vm.state.value.file as FileState.Failed
        assertTrue(failed.unreachable)
        assertTrue("the details load regardless", vm.state.value.details is DetailsState.Ready)

        library.fileFailure = null
        library.calls.clear()
        vm.retry()
        advanceUntilIdle()

        assertEquals(FileState.Ready(library.file), vm.state.value.file)
        assertFalse("loaded details aren't fetched again", "document 7" in library.calls)
    }

    @Test
    fun `a server error on the file isn't the unreachable tone`() = runTest(main.dispatcher) {
        library.fileFailure = ServerException(Kind.SERVER, "Paperless returned HTTP 500: x")
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(FileState.Failed("Paperless returned HTTP 500: x", unreachable = false), vm.state.value.file)
    }

    // --- share ---

    @Test
    fun `share hands the file's location, name and type on`() = runTest(main.dispatcher) {
        val vm = viewModel()
        val events = events(vm)
        advanceUntilIdle()

        vm.share()
        advanceUntilIdle()

        assertEquals(listOf(ViewerEvent.Share("content://test/bill.pdf", "bill.pdf", "application/pdf")), events)
    }

    @Test
    fun `a file that can't be viewed can still be shared`() = runTest(main.dispatcher) {
        library.file = text
        val vm = viewModel()
        val events = events(vm)
        advanceUntilIdle()

        vm.share()
        advanceUntilIdle()

        assertEquals(listOf(ViewerEvent.Share("content://test/note.txt", "note.txt", "text/plain")), events)
    }

    @Test
    fun `share does nothing until the file is there`() = runTest(main.dispatcher) {
        library.fileGate = CompletableDeferred()
        val vm = viewModel()
        val events = events(vm)
        runCurrent()

        vm.share()
        vm.download()
        advanceUntilIdle()

        assertTrue(events.isEmpty())
        assertTrue(exports.calls.isEmpty())
        assertFalse(vm.state.value.saving)
    }

    // --- save ---

    @Test
    fun `download saves to Downloads in one go`() = runTest(main.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.download()
        assertTrue(vm.state.value.saving)
        advanceUntilIdle()

        assertEquals(listOf("downloads bill.pdf"), exports.calls)
        assertFalse(vm.state.value.saving)
        assertEquals("Saved to Downloads", vm.state.value.message)

        vm.messageShown()
        assertNull(vm.state.value.message)
    }

    @Test
    fun `without direct saving, download asks where and copies there`() = runTest(main.dispatcher) {
        exports.canSaveDirectly = false
        val vm = viewModel()
        val events = events(vm)
        advanceUntilIdle()

        vm.download()
        advanceUntilIdle()

        assertEquals(listOf(ViewerEvent.PickSaveLocation("bill.pdf", "application/pdf")), events)
        assertTrue("still saving while the picker is open", vm.state.value.saving)

        vm.saveTo("content://picked/1")
        advanceUntilIdle()

        assertEquals(listOf("saveTo bill.pdf content://picked/1"), exports.calls)
        assertFalse(vm.state.value.saving)
        assertEquals("Saved", vm.state.value.message)
    }

    @Test
    fun `cancelling the picker saves nothing`() = runTest(main.dispatcher) {
        exports.canSaveDirectly = false
        val vm = viewModel()
        advanceUntilIdle()

        vm.download()
        advanceUntilIdle()
        vm.saveTo(null)
        advanceUntilIdle()

        assertTrue(exports.calls.isEmpty())
        assertFalse(vm.state.value.saving)
        assertNull(vm.state.value.message)

        vm.saveTo("content://picked/late")
        advanceUntilIdle()
        assertTrue("nothing is pending any more", exports.calls.isEmpty())
    }

    @Test
    fun `a failed save says which file`() = runTest(main.dispatcher) {
        exports.failure = ServerException(Kind.UNREADABLE, "Couldn't save bill.pdf")
        val vm = viewModel()
        advanceUntilIdle()

        vm.download()
        advanceUntilIdle()

        assertEquals("Couldn't save bill.pdf", vm.state.value.message)
        assertFalse(vm.state.value.saving)
    }

    @Test
    fun `a failed copy to a picked place says which file`() = runTest(main.dispatcher) {
        exports.canSaveDirectly = false
        val vm = viewModel()
        advanceUntilIdle()
        vm.download()
        advanceUntilIdle()

        exports.failure = ServerException(Kind.UNREADABLE, "Couldn't save bill.pdf")
        vm.saveTo("content://picked/1")
        advanceUntilIdle()

        assertEquals("Couldn't save bill.pdf", vm.state.value.message)
        assertFalse(vm.state.value.saving)
    }

    @Test
    fun `get original fetches the original, then saves it`() = runTest(main.dispatcher) {
        library.document = bill.copy(mimeType = "image/jpeg")
        library.files = mapOf(DocumentFileKind.ORIGINAL to photo)
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.details().isPhotoOriginal)
        assertFalse(vm.details().originalIsPdf)

        vm.downloadOriginal()
        advanceUntilIdle()

        assertTrue("file 7 ORIGINAL" in library.calls)
        assertEquals(listOf("downloads IMG_1.jpg"), exports.calls)
        assertEquals("Saved to Downloads", vm.state.value.message)
        assertEquals("the preview stays on show", FileState.Ready(library.file), vm.state.value.file)
    }

    @Test
    fun `a failed fetch of the original is said, and nothing is saved`() = runTest(main.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        library.fileFailure = ServerException(Kind.NO_ANSWER, "no answer from paperless")
        vm.downloadOriginal()
        advanceUntilIdle()

        assertEquals("Paperless didn't answer (no answer from paperless).", vm.state.value.message)
        assertTrue(exports.calls.isEmpty())
        assertFalse(vm.state.value.saving)
    }

    @Test
    fun `a second save while one runs is ignored`() = runTest(main.dispatcher) {
        exports.canSaveDirectly = false
        val vm = viewModel()
        val events = events(vm)
        advanceUntilIdle()

        vm.download()
        vm.download()
        vm.downloadOriginal()
        advanceUntilIdle()

        assertEquals(1, events.size)
    }

    @Test
    fun `a non-image original isn't a photo`() = runTest(main.dispatcher) {
        library.document = bill.copy(mimeType = "application/vnd.oasis.opendocument.text")
        val vm = viewModel()
        advanceUntilIdle()

        assertFalse(vm.details().isPhotoOriginal)
        assertFalse(vm.details().originalIsPdf)
    }

    // --- open in Paperless ---

    @Test
    fun `open in Paperless sends the web address`() = runTest(main.dispatcher) {
        val vm = viewModel()
        val events = events(vm)
        advanceUntilIdle()

        vm.openInPaperless()
        advanceUntilIdle()

        assertEquals(listOf(ViewerEvent.OpenUrl("http://paperless:8000/documents/7")), events)
    }

    @Test
    fun `signed out, open in Paperless does nothing`() = runTest(main.dispatcher) {
        val vm = viewModel()
        val events = events(vm)
        advanceUntilIdle()
        settings.clear()

        vm.openInPaperless()
        advanceUntilIdle()

        assertTrue(events.isEmpty())
    }
}
