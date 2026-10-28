package io.github.yasarmahomedabbas.paperlesscompanion.ui

import io.github.yasarmahomedabbas.paperlesscompanion.CONFIG
import io.github.yasarmahomedabbas.paperlesscompanion.FakeServer
import io.github.yasarmahomedabbas.paperlesscompanion.FakeSettings
import io.github.yasarmahomedabbas.paperlesscompanion.MainDispatcherRule
import io.github.yasarmahomedabbas.paperlesscompanion.document
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentSource
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.UploadState
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.UploadDocumentUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.ui.documents.UploadsViewModel
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.TaskInfo
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.TaskStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UploadsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val server = FakeServer()
    private val settings = FakeSettings(CONFIG)
    private val viewModel = UploadsViewModel(UploadDocumentUseCase(server, settings))

    private val uploads get() = viewModel.state.value.uploads

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
        server.tasks[0] = server.tasks[0].copy(status = TaskStatus.RUNNING)
        viewModel.upload(listOf(document("busy.pdf")))
        main.dispatcher.scheduler.runCurrent()

        viewModel.clearFinished()

        assertEquals(listOf("busy.pdf"), uploads.map { it.name })
    }

    @Test
    fun `clear drops every upload, running or not`() = runTest(main.dispatcher) {
        viewModel.upload(listOf(document("done.pdf")))
        advanceUntilIdle()
        server.tasks[0] = server.tasks[0].copy(status = TaskStatus.RUNNING)
        viewModel.upload(listOf(document("busy.pdf")))
        main.dispatcher.scheduler.runCurrent()

        viewModel.clear()

        assertTrue(uploads.isEmpty())
        viewModel.retry(0)
        main.dispatcher.scheduler.runCurrent()
        assertEquals(2, server.uploads.size)
    }

    @Test
    fun `an accepted upload says so once, with the new document`() = runTest(main.dispatcher) {
        val added = mutableListOf<Int?>()
        backgroundScope.launch { viewModel.added.toList(added) }
        server.tasks.clear()
        server.tasks += listOf(TaskInfo(TaskStatus.PENDING, null, null), TaskInfo(TaskStatus.SUCCESS, "ok", 42))

        viewModel.upload(listOf(document()))
        advanceUntilIdle()
        runCurrent() // the collector runs in the background scope, which advanceUntilIdle doesn't wait for

        assertEquals(UploadState.Done(42), uploads.single().state)
        assertEquals(listOf<Int?>(42), added)
    }

    @Test
    fun `a failed upload says nothing was added`() = runTest(main.dispatcher) {
        val added = mutableListOf<Int?>()
        backgroundScope.launch { viewModel.added.toList(added) }
        server.uploadResult = { throw ServerException(ServerException.Kind.SERVER, "duplicate") }

        viewModel.upload(listOf(document()))
        advanceUntilIdle()
        runCurrent()

        assertTrue(uploads.single().state is UploadState.Failed)
        assertTrue(added.isEmpty())
    }

    @Test
    fun `an upload accepted before anyone listens still reaches the list`() = runTest(main.dispatcher) {
        viewModel.upload(listOf(document()))
        advanceUntilIdle()

        val added = mutableListOf<Int?>()
        backgroundScope.launch { viewModel.added.toList(added) }
        runCurrent()

        assertEquals(listOf<Int?>(42), added)
    }
}
