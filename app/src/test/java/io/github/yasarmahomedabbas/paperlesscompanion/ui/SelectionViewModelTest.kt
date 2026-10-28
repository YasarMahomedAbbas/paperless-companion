package io.github.yasarmahomedabbas.paperlesscompanion.ui

import io.github.yasarmahomedabbas.paperlesscompanion.CONFIG
import io.github.yasarmahomedabbas.paperlesscompanion.FakeLibrary
import io.github.yasarmahomedabbas.paperlesscompanion.FakeSettings
import io.github.yasarmahomedabbas.paperlesscompanion.MainDispatcherRule
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.BulkEdit
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException.Kind
import io.github.yasarmahomedabbas.paperlesscompanion.FakeServer
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.LabelKind
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.BulkEditUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.LabelsLinkUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.ui.documents.Changed
import io.github.yasarmahomedabbas.paperlesscompanion.ui.documents.SelectionViewModel
import io.github.yasarmahomedabbas.paperlesscompanion.ui.documents.TagMark.ALL
import io.github.yasarmahomedabbas.paperlesscompanion.ui.documents.TagMark.NONE
import io.github.yasarmahomedabbas.paperlesscompanion.ui.documents.TagMark.SOME
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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
class SelectionViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val library = FakeLibrary()
    private val viewModel = SelectionViewModel(BulkEditUseCase(library, FakeSettings(CONFIG)), LabelsLinkUseCase(FakeServer(), FakeSettings(CONFIG)))
    private val selected get() = viewModel.state.value.selected

    private fun TestScope.changes(): List<Changed> {
        val seen = mutableListOf<Changed>()
        backgroundScope.launch { viewModel.changes.collect { seen += it } }
        runCurrent()
        return seen
    }

    @Test
    fun `start selects one`() {
        assertFalse(viewModel.state.value.selecting)

        viewModel.start(4)

        assertEquals(setOf(4), selected)
        assertTrue(viewModel.state.value.selecting)
    }

    @Test
    fun `toggle adds and removes, and dropping the last one stops selecting`() {
        viewModel.start(4)
        viewModel.toggle(5)
        assertEquals(setOf(4, 5), selected)

        viewModel.toggle(4)
        assertEquals(setOf(5), selected)

        viewModel.toggle(5)
        assertFalse(viewModel.state.value.selecting)
    }

    @Test
    fun `select all selects exactly the loaded ids, and stop clears`() {
        viewModel.start(99)
        viewModel.selectAll(listOf(1, 2, 3))
        assertEquals(setOf(1, 2, 3), selected)

        viewModel.stop()
        assertTrue(selected.isEmpty())
    }

    @Test
    fun `correspondent and type each go in one call, then selecting stops`() = runTest(main.dispatcher) {
        val changes = changes()
        for (edit in listOf(BulkEdit.SetCorrespondent(7), BulkEdit.SetDocumentType(null))) {
            viewModel.start(1)
            viewModel.toggle(2)
            viewModel.change(edit)
            assertTrue(viewModel.state.value.working)
            runCurrent()

            assertEquals("bulkEdit [1, 2] $edit", library.calls.last())
            assertEquals(Changed(listOf(1, 2), edit), changes.last())
            assertFalse(viewModel.state.value.selecting)
            assertFalse(viewModel.state.value.working)
        }
        assertEquals(2, library.calls.size)
    }

    @Test
    fun `tags go in one call with only what changed`() = runTest(main.dispatcher) {
        val changes = changes()
        viewModel.start(1)
        viewModel.toggle(2)

        viewModel.changeTags(mapOf(1 to SOME, 2 to ALL), mapOf(1 to ALL, 2 to ALL))
        runCurrent()

        val edit = BulkEdit.ModifyTags(add = setOf(1), remove = emptySet())
        assertEquals(listOf("bulkEdit [1, 2] $edit"), library.calls)
        assertEquals(listOf(Changed(listOf(1, 2), edit)), changes)
        assertFalse(viewModel.state.value.selecting)
    }

    @Test
    fun `tags left as they were make no call`() = runTest(main.dispatcher) {
        viewModel.start(1)

        viewModel.changeTags(mapOf(1 to SOME, 2 to NONE), mapOf(1 to SOME, 2 to NONE))
        advanceUntilIdle()

        assertTrue(library.calls.isEmpty())
        assertEquals(setOf(1), selected)
    }

    @Test
    fun `a failure keeps the selection and shows the message`() = runTest(main.dispatcher) {
        val changes = changes()
        library.writeFailure = ServerException(Kind.SERVER, "Paperless returned 400")
        viewModel.start(1)
        viewModel.toggle(2)

        viewModel.change(BulkEdit.SetCorrespondent(3))
        runCurrent()

        assertEquals("Paperless returned 400", viewModel.state.value.failure)
        assertEquals(setOf(1, 2), selected)
        assertFalse(viewModel.state.value.working)
        assertTrue(changes.isEmpty())

        viewModel.failureShown()
        assertNull(viewModel.state.value.failure)

        library.writeFailure = IllegalStateException("boom")
        viewModel.change(BulkEdit.SetCorrespondent(3))
        runCurrent()
        assertEquals("Couldn't change them", viewModel.state.value.failure)
    }

    @Test
    fun `a change while one is running, or with nothing selected, is ignored`() = runTest(main.dispatcher) {
        viewModel.change(BulkEdit.SetCorrespondent(3))
        runCurrent()
        assertTrue(library.calls.isEmpty())

        viewModel.start(1)
        viewModel.change(BulkEdit.SetCorrespondent(3))
        viewModel.change(BulkEdit.SetDocumentType(4))
        advanceUntilIdle()

        assertEquals(1, library.calls.size)
    }

    @Test
    fun `labelsUrl passes through`() {
        assertEquals("http://paperless:8000/labels/CORRESPONDENT", viewModel.labelsUrl(LabelKind.CORRESPONDENT))
    }
}
