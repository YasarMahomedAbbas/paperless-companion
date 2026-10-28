package io.github.yasarmahomedabbas.paperlesscompanion.ui

import io.github.yasarmahomedabbas.paperlesscompanion.CONFIG
import io.github.yasarmahomedabbas.paperlesscompanion.FakeServer
import io.github.yasarmahomedabbas.paperlesscompanion.FakeSettings
import io.github.yasarmahomedabbas.paperlesscompanion.MainDispatcherRule
import io.github.yasarmahomedabbas.paperlesscompanion.document
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentSource
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.UploadState
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.DocumentLinkUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.UploadDocumentUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.ui.home.HomeViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val server = FakeServer()
    private val settings = FakeSettings(CONFIG)
    private val viewModel = HomeViewModel(UploadDocumentUseCase(server, settings), DocumentLinkUseCase(server, settings), settings)

    private val uploads get() = viewModel.state.value.uploads

    @Test
    fun `shows the server`() {
        assertEquals("http://paperless:8000", viewModel.state.value.serverUrl)
    }

    @Test
    fun `document links come from the server`() {
        assertEquals("http://paperless:8000/documents/5", viewModel.documentUrl(5))
    }

    @Test
    fun `new uploads go on top and finish as done`() = runTest(main.dispatcher) {
        viewModel.upload(listOf(document("a.pdf"), document("b.pdf")))
        assertEquals(listOf("b.pdf", "a.pdf"), uploads.map { it.name })
        assertTrue(uploads.all { it.state == UploadState.Uploading })

        advanceUntilIdle()

        assertTrue(uploads.all { it.state == UploadState.Done(42) })
    }

    @Test
    fun `a failed upload can be retried`() = runTest(main.dispatcher) {
        server.uploadResult = { throw ServerException(ServerException.Kind.UNREACHABLE, "down") }
        viewModel.upload(listOf(document()))
        advanceUntilIdle()
        assertTrue(uploads.single().state is UploadState.Failed)

        server.uploadResult = { "task-2" }
        viewModel.retry(uploads.single().id)
        advanceUntilIdle()

        assertEquals(UploadState.Done(42), uploads.single().state)
        assertEquals(2, server.uploads.size)
    }

    @Test
    fun `retry after a failed status check checks again instead of uploading again`() = runTest(main.dispatcher) {
        server.taskAlwaysFails = ServerException(ServerException.Kind.NO_ANSWER, "timeout")
        viewModel.upload(listOf(document()))
        advanceUntilIdle()
        assertEquals("task-1", (uploads.single().state as UploadState.Failed).taskId)

        server.taskAlwaysFails = null
        viewModel.retry(uploads.single().id)
        advanceUntilIdle()

        assertEquals(UploadState.Done(42), uploads.single().state)
        assertEquals(1, server.uploads.size)
    }

    @Test
    fun `retry ignores uploads that did not fail`() = runTest(main.dispatcher) {
        viewModel.upload(listOf(document()))
        advanceUntilIdle()

        viewModel.retry(uploads.single().id)
        viewModel.retry(999)
        advanceUntilIdle()

        assertEquals(1, server.uploads.size)
    }

    @Test
    fun `unexpected errors become a failed upload instead of a crash`() = runTest(main.dispatcher) {
        viewModel.upload(listOf(DocumentSource("x.pdf", "application/pdf") { error("unused") }))
        server.uploadResult = { throw IllegalStateException("surprise") }
        advanceUntilIdle()

        assertEquals(UploadState.Failed("surprise"), uploads.single().state)
    }

    @Test
    fun `clear finished keeps uploads that are still running`() = runTest(main.dispatcher) {
        viewModel.upload(listOf(document("done.pdf")))
        advanceUntilIdle()
        server.tasks[0] = server.tasks[0].copy(status = io.github.yasarmahomedabbas.paperlesscompanion.domain.model.TaskStatus.RUNNING)
        viewModel.upload(listOf(document("busy.pdf")))
        main.dispatcher.scheduler.runCurrent()

        viewModel.clearFinished()

        assertEquals(listOf("busy.pdf"), uploads.map { it.name })
    }

    @Test
    fun `sign out forgets the server and the uploads`() = runTest(main.dispatcher) {
        viewModel.upload(listOf(document()))
        advanceUntilIdle()

        viewModel.signOut()

        assertNull(settings.config)
        assertTrue(uploads.isEmpty())
    }
}
