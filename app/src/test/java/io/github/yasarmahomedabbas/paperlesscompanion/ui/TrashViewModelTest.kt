package io.github.yasarmahomedabbas.paperlesscompanion.ui

import io.github.yasarmahomedabbas.paperlesscompanion.CONFIG
import io.github.yasarmahomedabbas.paperlesscompanion.FakeLibrary
import io.github.yasarmahomedabbas.paperlesscompanion.FakeSettings
import io.github.yasarmahomedabbas.paperlesscompanion.MainDispatcherRule
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.BulkEdit
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException.Kind
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.BulkEditUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.DeleteDocumentUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.TrashUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.ui.documents.TrashEvent
import io.github.yasarmahomedabbas.paperlesscompanion.ui.documents.TrashViewModel
import io.github.yasarmahomedabbas.paperlesscompanion.ui.documents.Undoable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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

@OptIn(ExperimentalCoroutinesApi::class)
class TrashViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val library = FakeLibrary()
    private val settings = FakeSettings(CONFIG)
    private val viewModel = TrashViewModel(
        DeleteDocumentUseCase(library, settings),
        BulkEditUseCase(library, settings),
        TrashUseCase(library, settings),
    )

    private fun TestScope.events(): List<TrashEvent> {
        val seen = mutableListOf<TrashEvent>()
        backgroundScope.launch { viewModel.events.collect { seen += it } }
        runCurrent()
        return seen
    }

    @Test
    fun `one document goes through delete and offers Undo for 8 seconds`() = runTest(main.dispatcher) {
        val events = events()

        viewModel.trash(listOf(5))
        assertTrue(viewModel.state.value.working)
        runCurrent()

        assertEquals(listOf("delete 5"), library.calls)
        assertEquals(listOf(TrashEvent.Trashed(listOf(5))), events)
        assertFalse(viewModel.state.value.working)
        assertEquals(Undoable(listOf(5), "Moved to trash"), viewModel.state.value.undoable)

        advanceTimeBy(7_999)
        runCurrent()
        assertEquals(Undoable(listOf(5), "Moved to trash"), viewModel.state.value.undoable)

        advanceTimeBy(1)
        runCurrent()
        assertNull(viewModel.state.value.undoable)
    }

    @Test
    fun `several documents go through one bulk delete`() = runTest(main.dispatcher) {
        val events = events()

        viewModel.trash(listOf(1, 2, 3))
        runCurrent()

        assertEquals(listOf("bulkEdit [1, 2, 3] ${BulkEdit.Delete}"), library.calls)
        assertEquals(listOf(TrashEvent.Trashed(listOf(1, 2, 3))), events)
        assertEquals(Undoable(listOf(1, 2, 3), "3 moved to trash"), viewModel.state.value.undoable)
    }

    @Test
    fun `nothing to trash makes no call`() = runTest(main.dispatcher) {
        viewModel.trash(emptyList())
        advanceUntilIdle()

        assertTrue(library.calls.isEmpty())
    }

    @Test
    fun `undo restores the same ids and says so`() = runTest(main.dispatcher) {
        val events = events()
        viewModel.trash(listOf(1, 2))
        runCurrent()

        viewModel.undo()
        runCurrent()

        assertEquals("restore [1, 2]", library.calls.last())
        assertEquals(listOf(TrashEvent.Trashed(listOf(1, 2)), TrashEvent.Restored(listOf(1, 2))), events)
        assertNull(viewModel.state.value.undoable)
        assertFalse(viewModel.state.value.working)
    }

    @Test
    fun `a failed undo says it couldn't bring it back, and why`() = runTest(main.dispatcher) {
        val events = events()
        viewModel.trash(listOf(5))
        runCurrent()

        library.writeFailure = ServerException(Kind.SERVER, "Paperless returned 500")
        viewModel.undo()
        runCurrent()

        assertEquals("Couldn't bring it back: Paperless returned 500", viewModel.state.value.failure)
        assertEquals(listOf(TrashEvent.Trashed(listOf(5))), events)

        viewModel.failureShown()
        assertNull(viewModel.state.value.failure)
    }

    @Test
    fun `a failed trash shows the message and emits nothing`() = runTest(main.dispatcher) {
        val events = events()
        library.writeFailure = ServerException(Kind.UNREACHABLE, "timeout")

        viewModel.trash(listOf(5))
        runCurrent()

        assertTrue(viewModel.state.value.failure!!.contains("VPN"))
        assertTrue(events.isEmpty())
        assertNull(viewModel.state.value.undoable)
        assertFalse(viewModel.state.value.working)

        library.writeFailure = IllegalStateException("boom")
        viewModel.trash(listOf(5))
        runCurrent()
        assertEquals("Couldn't move it to the trash", viewModel.state.value.failure)
    }

    @Test
    fun `a second trash replaces the earlier undo offer and its timer`() = runTest(main.dispatcher) {
        viewModel.trash(listOf(1))
        runCurrent()
        advanceTimeBy(5_000)

        viewModel.trash(listOf(2, 3))
        runCurrent()
        assertEquals(Undoable(listOf(2, 3), "2 moved to trash"), viewModel.state.value.undoable)

        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf(2, 3), viewModel.state.value.undoable?.ids)

        viewModel.undo()
        runCurrent()
        assertEquals("restore [2, 3]", library.calls.last())
    }

    @Test
    fun `the snackbar going away drops the offer`() = runTest(main.dispatcher) {
        viewModel.trash(listOf(1))
        runCurrent()

        viewModel.undoShown()

        assertNull(viewModel.state.value.undoable)
        viewModel.undo()
        runCurrent()
        assertEquals(listOf("delete 1"), library.calls)
    }
}
