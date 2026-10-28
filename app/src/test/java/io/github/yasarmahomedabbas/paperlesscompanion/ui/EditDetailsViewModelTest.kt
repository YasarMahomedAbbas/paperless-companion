package io.github.yasarmahomedabbas.paperlesscompanion.ui

import io.github.yasarmahomedabbas.paperlesscompanion.CONFIG
import io.github.yasarmahomedabbas.paperlesscompanion.FakeLibrary
import io.github.yasarmahomedabbas.paperlesscompanion.FakeServer
import io.github.yasarmahomedabbas.paperlesscompanion.FakeSettings
import io.github.yasarmahomedabbas.paperlesscompanion.MainDispatcherRule
import io.github.yasarmahomedabbas.paperlesscompanion.doc
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Change
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentChanges
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.LabelKind
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException.Kind
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.LabelsLinkUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.LoadLabelsUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.UpdateDocumentUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.ui.edit.EditDetailsViewModel
import io.github.yasarmahomedabbas.paperlesscompanion.ui.edit.EditForm
import io.github.yasarmahomedabbas.paperlesscompanion.ui.edit.LabelsStatus
import io.github.yasarmahomedabbas.paperlesscompanion.ui.edit.changesFrom
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class EditDetailsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val original = doc(7, "Power bill").copy(
        created = LocalDate.of(2026, 3, 1),
        correspondentId = 2,
        documentTypeId = 3,
        storagePathId = null,
        tagIds = listOf(1, 5),
    )
    private val library = FakeLibrary()
    private val settings = FakeSettings(CONFIG)

    private fun viewModel(signedIn: Boolean = true) = EditDetailsViewModel(
        original,
        UpdateDocumentUseCase(library, settings),
        LoadLabelsUseCase(library, settings),
        LabelsLinkUseCase(FakeServer(), if (signedIn) settings else FakeSettings()),
    )

    private val updates get() = library.calls.filter { it.startsWith("update") }

    @Test
    fun `it starts from the document and loads the labels`() = runTest(main.dispatcher) {
        val vm = viewModel()
        assertEquals(EditForm.from(original), vm.state.value.form)
        assertEquals(LabelsStatus.Loading, vm.state.value.labels)
        assertFalse(vm.state.value.hasChanges)

        advanceUntilIdle()

        val labels = (vm.state.value.labels as LabelsStatus.Loaded).labels
        assertEquals(library.tags, labels.tags)
        assertEquals(library.storagePaths, labels.storagePaths)
    }

    @Test
    fun `labels that fail to load can be tried again`() = runTest(main.dispatcher) {
        library.failure = ServerException(Kind.SERVER, "Paperless returned 500")
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(LabelsStatus.Failed("Paperless returned 500"), vm.state.value.labels)

        library.failure = null
        vm.retryLabels()
        assertEquals(LabelsStatus.Loading, vm.state.value.labels)
        advanceUntilIdle()

        assertTrue(vm.state.value.labels is LabelsStatus.Loaded)
    }

    @Test
    fun `a failure that isn't the server's gets a plain message`() = runTest(main.dispatcher) {
        library.failure = IllegalStateException("boom")
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(LabelsStatus.Failed("Couldn't load the labels"), vm.state.value.labels)
    }

    @Test
    fun `each setter changes the form, and undoing the change leaves nothing to save`() = runTest(main.dispatcher) {
        val vm = viewModel()
        val date = LocalDate.of(2026, 4, 2)

        vm.setTitle("Water bill")
        vm.setDate(date)
        vm.setCorrespondent(9)
        vm.setDocumentType(null)
        vm.setStoragePath(4)
        vm.setTags(listOf(5))

        assertEquals(EditForm("Water bill", date, 9, null, 4, listOf(5)), vm.state.value.form)
        assertTrue(vm.state.value.hasChanges)

        vm.setTitle("Power bill")
        vm.setDate(original.created!!)
        vm.setCorrespondent(2)
        vm.setDocumentType(3)
        vm.setStoragePath(null)
        vm.setTags(listOf(5, 1))

        assertFalse(vm.state.value.hasChanges)
    }

    @Test
    fun `changes hold only the fields that differ`() {
        val before = EditForm.from(original)

        assertEquals(DocumentChanges(correspondentId = Change.To(null)), before.copy(correspondentId = null).changesFrom(before))
        assertEquals(DocumentChanges(title = Change.To("New")), before.copy(title = "  New ").changesFrom(before))
        assertTrue(before.copy(title = "Power bill  ").changesFrom(before).isEmpty)
        assertTrue(before.copy(tagIds = listOf(5, 1)).changesFrom(before).isEmpty)
        assertEquals(DocumentChanges(tagIds = Change.To(listOf(5, 1, 8))), before.copy(tagIds = listOf(5, 1, 8)).changesFrom(before))
        assertEquals(
            DocumentChanges(documentTypeId = Change.To(4), storagePathId = Change.To(6)),
            before.copy(documentTypeId = 4, storagePathId = 6).changesFrom(before),
        )
    }

    @Test
    fun `a date is sent when changed but never cleared`() {
        val before = EditForm.from(original)
        val date = LocalDate.of(2025, 1, 1)

        assertEquals(DocumentChanges(created = Change.To(date)), before.copy(created = date).changesFrom(before))
        assertTrue(before.copy(created = null).changesFrom(before).isEmpty)
    }

    @Test
    fun `a blank title is refused without a call, and typing clears the error`() = runTest(main.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.setTitle("   ")
        vm.save()
        advanceUntilIdle()

        assertEquals("A document needs a title.", vm.state.value.titleError)
        assertTrue(updates.isEmpty())
        assertNull(vm.state.value.saved)

        vm.setTitle("Bill")
        assertNull(vm.state.value.titleError)
    }

    @Test
    fun `saving with nothing changed closes without a call`() = runTest(main.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.save()
        advanceUntilIdle()

        assertSame(original, vm.state.value.saved)
        assertTrue(updates.isEmpty())
    }

    @Test
    fun `save sends the difference and ends with the returned document`() = runTest(main.dispatcher) {
        val returned = original.copy(title = "Water bill", modified = null)
        library.document = returned
        val vm = viewModel()
        advanceUntilIdle()

        vm.setTitle("Water bill")
        vm.setCorrespondent(null)
        vm.save()
        assertTrue(vm.state.value.saving)
        advanceUntilIdle()

        assertEquals(
            listOf("update 7 ${DocumentChanges(title = Change.To("Water bill"), correspondentId = Change.To(null))}"),
            updates,
        )
        assertFalse(vm.state.value.saving)
        assertEquals(returned, vm.state.value.saved)
    }

    @Test
    fun `a failed save keeps the form and says why`() = runTest(main.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.setTitle("Water bill")
        vm.setTags(listOf(1))

        library.updateFailure = ServerException(Kind.UNREACHABLE, "timeout")
        vm.save()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.saveFailed!!.contains("VPN"))
        assertFalse(state.saving)
        assertNull(state.saved)
        assertEquals("Water bill", state.form.title)
        assertEquals(listOf(1), state.form.tagIds)

        library.updateFailure = ServerException(Kind.SERVER, "Paperless returned 400")
        vm.save()
        advanceUntilIdle()
        assertEquals("Paperless returned 400", vm.state.value.saveFailed)

        library.updateFailure = IllegalStateException("boom")
        vm.save()
        advanceUntilIdle()
        assertEquals("Couldn't save the changes", vm.state.value.saveFailed)

        library.updateFailure = null
        vm.save()
        assertNull(vm.state.value.saveFailed)
        advanceUntilIdle()
        assertEquals(library.document, vm.state.value.saved)
    }

    @Test
    fun `a second save while one is running is ignored`() = runTest(main.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.setTitle("Water bill")
        val gate = CompletableDeferred<Unit>()
        library.updateGate = gate

        vm.save()
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, updates.size)
    }

    @Test
    fun `labelsUrl passes through, and is null signed out`() = runTest(main.dispatcher) {
        assertEquals("http://paperless:8000/labels/TAG", viewModel().labelsUrl(LabelKind.TAG))
        assertNull(viewModel(signedIn = false).labelsUrl(LabelKind.TAG))
    }
}
