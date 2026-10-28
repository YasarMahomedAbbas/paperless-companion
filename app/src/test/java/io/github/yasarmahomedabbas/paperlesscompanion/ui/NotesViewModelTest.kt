package io.github.yasarmahomedabbas.paperlesscompanion.ui

import io.github.yasarmahomedabbas.paperlesscompanion.CONFIG
import io.github.yasarmahomedabbas.paperlesscompanion.FakeLibrary
import io.github.yasarmahomedabbas.paperlesscompanion.FakeSettings
import io.github.yasarmahomedabbas.paperlesscompanion.MainDispatcherRule
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Note
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException.Kind
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.NotesUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.userMessage
import io.github.yasarmahomedabbas.paperlesscompanion.ui.notes.NotesStatus
import io.github.yasarmahomedabbas.paperlesscompanion.ui.notes.NotesViewModel
import io.github.yasarmahomedabbas.paperlesscompanion.ui.notes.noteMeta
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val library = FakeLibrary()
    private val settings = FakeSettings(CONFIG)

    private fun viewModel() = NotesViewModel(7, NotesUseCase(library, settings))

    private fun TestScope.loaded() = viewModel().also { advanceUntilIdle() }

    private val NotesViewModel.s get() = state.value

    @Test
    fun `it loads the notes on start`() = runTest(main.dispatcher) {
        val vm = viewModel()
        assertEquals(NotesStatus.Loading, vm.s.status)
        assertNull(vm.count)

        advanceUntilIdle()

        assertEquals(NotesStatus.Loaded, vm.s.status)
        assertEquals(library.notes, vm.s.notes)
        assertEquals(1, vm.count)
        assertEquals(listOf("notes 7"), library.calls)
    }

    @Test
    fun `no notes is loaded and empty`() = runTest(main.dispatcher) {
        library.notes = emptyList()
        val vm = loaded()

        assertEquals(NotesStatus.Loaded, vm.s.status)
        assertTrue(vm.s.notes.isEmpty())
        assertEquals(0, vm.count)
    }

    @Test
    fun `an unreachable server fails as the network, and Try again loads again`() = runTest(main.dispatcher) {
        val e = ServerException(Kind.UNREACHABLE, "timeout")
        library.notesFailure = e
        val vm = loaded()
        assertEquals(NotesStatus.Failed(e.userMessage(), unreachable = true), vm.s.status)
        assertNull(vm.count)

        library.notesFailure = null
        vm.retry()
        assertEquals(NotesStatus.Loading, vm.s.status)
        advanceUntilIdle()

        assertEquals(NotesStatus.Loaded, vm.s.status)
        assertEquals(listOf("notes 7", "notes 7"), library.calls)
    }

    @Test
    fun `a refusal fails with the server's words, and an odd error plainly`() = runTest(main.dispatcher) {
        library.notesFailure = ServerException(Kind.FORBIDDEN, "nope")
        assertFalse((loaded().s.status as NotesStatus.Failed).unreachable)

        library.notesFailure = IllegalStateException("surprise")
        assertEquals(NotesStatus.Failed("Couldn't load the notes", unreachable = false), loaded().s.status)
    }

    @Test
    fun `adding sends the draft, clears it and shows the server's list`() = runTest(main.dispatcher) {
        val vm = loaded()
        vm.setDraft("  Paid in full ")

        vm.add()
        assertTrue(vm.s.adding)
        advanceUntilIdle()

        assertFalse(vm.s.adding)
        assertEquals("", vm.s.draft)
        assertEquals(listOf(7 to "Paid in full"), library.addedNotes)
        assertEquals(listOf("Paid in full", "Paid"), vm.s.notes.map { it.text })
        assertEquals(2, vm.count)
    }

    @Test
    fun `a failed add keeps the draft and says why, until the draft changes`() = runTest(main.dispatcher) {
        val vm = loaded()
        vm.setDraft("Paid")
        library.notesFailure = ServerException(Kind.FORBIDDEN, "403")

        vm.add()
        advanceUntilIdle()

        assertEquals("Paid", vm.s.draft)
        assertFalse(vm.s.adding)
        assertEquals(ServerException(Kind.FORBIDDEN, "403").userMessage(), vm.s.addFailed)
        assertEquals(1, vm.s.notes.size)

        vm.setDraft("Paid!")
        assertNull(vm.s.addFailed)
    }

    @Test
    fun `an odd add failure says so plainly`() = runTest(main.dispatcher) {
        val vm = loaded()
        vm.setDraft("Paid")
        library.notesFailure = IllegalStateException("surprise")

        vm.add()
        advanceUntilIdle()

        assertEquals("Couldn't add the note", vm.s.addFailed)
    }

    @Test
    fun `a blank draft is not sent`() = runTest(main.dispatcher) {
        val vm = loaded()
        vm.setDraft("   ")

        vm.add()
        advanceUntilIdle()

        assertFalse(vm.s.adding)
        assertTrue(library.addedNotes.isEmpty())
    }

    @Test
    fun `a second add while one is on its way is ignored`() = runTest(main.dispatcher) {
        val vm = loaded()
        val gate = CompletableDeferred<Unit>()
        library.addNoteGate = gate
        vm.setDraft("Paid")

        vm.add()
        runCurrent()
        vm.add()
        runCurrent()
        assertEquals(1, library.addedNotes.size)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, library.addedNotes.size)
    }

    @Test
    fun `a note's line names the author and the time, or whichever is known`() {
        val created = Instant.parse("2026-10-03T09:14:00Z")
        assertEquals("Ann · 3 Oct 2026, 09:14", noteMeta(Note(1, "x", created, "Ann"), ZoneOffset.UTC))
        assertEquals("3 Oct 2026, 09:14", noteMeta(Note(1, "x", created, null), ZoneOffset.UTC))
        assertEquals("Ann", noteMeta(Note(1, "x", null, "Ann"), ZoneOffset.UTC))
        assertNull(noteMeta(Note(1, "x", null, null), ZoneOffset.UTC))
    }
}
