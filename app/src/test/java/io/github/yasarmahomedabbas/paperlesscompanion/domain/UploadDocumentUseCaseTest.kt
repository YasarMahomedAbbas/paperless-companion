package io.github.yasarmahomedabbas.paperlesscompanion.domain

import io.github.yasarmahomedabbas.paperlesscompanion.CONFIG
import io.github.yasarmahomedabbas.paperlesscompanion.FakeServer
import io.github.yasarmahomedabbas.paperlesscompanion.FakeSettings
import io.github.yasarmahomedabbas.paperlesscompanion.document
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException.Kind
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.TaskInfo
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.TaskStatus
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.UploadState
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.isFinished
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.UploadDocumentUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.userMessage
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class UploadDocumentUseCaseTest {
    private val server = FakeServer()
    private val settings = FakeSettings(CONFIG)
    private val upload = UploadDocumentUseCase(server, settings, pollInterval = 1.seconds, timeout = 10.seconds)

    @Test
    fun `uploads, waits for processing and reports the new document`() = runTest {
        server.tasks.apply {
            clear()
            add(TaskInfo(TaskStatus.PENDING, null, null))
            add(TaskInfo(TaskStatus.RUNNING, null, null))
            add(TaskInfo(TaskStatus.SUCCESS, "Success", 42))
        }

        val states = upload(document("bill.pdf")).toList()

        assertEquals(
            listOf(UploadState.Uploading, UploadState.Processing("task-1"), UploadState.Done(42)),
            states,
        )
        assertEquals(listOf("bill.pdf"), server.uploads)
        assertEquals(3, server.taskPolls)
    }

    @Test
    fun `reports Paperless's reason when it refuses the document`() = runTest {
        server.tasks[0] = TaskInfo(TaskStatus.FAILURE, "Not consuming bill.pdf: It is a duplicate", null)

        val last = upload(document()).toList().last()

        assertEquals(UploadState.Failed("Not consuming bill.pdf: It is a duplicate"), last)
    }

    @Test
    fun `has a fallback message when a failure has no reason`() = runTest {
        server.tasks[0] = TaskInfo(TaskStatus.FAILURE, null, null)

        assertEquals(UploadState.Failed("Paperless refused the document"), upload(document()).toList().last())
    }

    @Test
    fun `gives up polling after the timeout`() = runTest {
        server.tasks[0] = TaskInfo(TaskStatus.RUNNING, null, null)

        val last = upload(document()).toList().last()

        assertTrue(last is UploadState.Failed)
        assertTrue((last as UploadState.Failed).message.contains("still processing"))
        assertEquals(11, server.taskPolls)
    }

    @Test
    fun `a dropped status check is retried, because the file is already uploaded`() = runTest {
        server.taskFailures += ServerException(Kind.NO_ANSWER, "no answer from paperless: timeout")
        server.taskFailures += ServerException(Kind.UNREACHABLE, "can't connect to paperless: refused")

        val states = upload(document()).toList()

        assertEquals(UploadState.Done(42), states.last())
        assertEquals(1, server.uploads.size)
        assertEquals(3, server.taskPolls)
    }

    @Test
    fun `status checks that keep failing end in a failure that keeps the task and the cause`() = runTest {
        server.taskAlwaysFails = ServerException(Kind.NO_ANSWER, "no answer from paperless: timeout")

        val last = upload(document()).toList().last() as UploadState.Failed

        assertEquals("task-1", last.taskId)
        assertTrue(last.message.startsWith("Uploaded, but"))
        assertTrue(last.message.contains("no answer from paperless: timeout"))
    }

    @Test
    fun `a login problem while checking ends straight away but keeps the task`() = runTest {
        server.taskAlwaysFails = ServerException(Kind.UNAUTHORIZED, "Wrong username or password")

        val last = upload(document()).toList().last() as UploadState.Failed

        assertEquals("task-1", last.taskId)
        assertEquals(1, server.taskPolls)
    }

    @Test
    fun `with a task id it only checks on the upload, without uploading again`() = runTest {
        val states = upload(document(), uploadedTaskId = "task-9").toList()

        assertEquals(listOf(UploadState.Processing("task-9"), UploadState.Done(42)), states)
        assertTrue(server.uploads.isEmpty())
    }

    @Test
    fun `a refused document can be uploaded again`() = runTest {
        server.tasks[0] = TaskInfo(TaskStatus.FAILURE, "nope", null)
        assertEquals(null, (upload(document()).toList().last() as UploadState.Failed).taskId)
    }

    @Test
    fun `fails straight away when not signed in`() = runTest {
        settings.config = null

        assertEquals(listOf(UploadState.Failed("Not signed in")), upload(document()).toList())
        assertTrue(server.uploads.isEmpty())
    }

    @Test
    fun `turns an unreachable server into a VPN hint`() = runTest {
        server.uploadResult = { throw ServerException(Kind.UNREACHABLE, "boom") }

        val last = upload(document()).toList().last() as UploadState.Failed

        assertTrue(last.message.contains("VPN"))
        assertTrue(last.message.contains("boom"))
        assertEquals(null, last.taskId)
        assertTrue(last.unreachable)
    }

    @Test
    fun `a refusal is not mistaken for an unreachable server`() = runTest {
        server.tasks[0] = TaskInfo(TaskStatus.FAILURE, "duplicate", null)
        assertFalse((upload(document()).toList().last() as UploadState.Failed).unreachable)

        server.uploadResult = { throw ServerException(Kind.SERVER, "413") }
        assertFalse((upload(document()).toList().last() as UploadState.Failed).unreachable)
    }

    @Test
    fun `status checks that never get through count as unreachable, still processing does not`() = runTest {
        server.taskAlwaysFails = ServerException(Kind.UNREACHABLE, "refused")
        assertTrue((upload(document()).toList().last() as UploadState.Failed).unreachable)

        server.taskAlwaysFails = null
        server.tasks[0] = TaskInfo(TaskStatus.RUNNING, null, null)
        assertFalse((upload(document()).toList().last() as UploadState.Failed).unreachable)
    }

    @Test
    fun `every error kind has a user message`() {
        Kind.entries.forEach { kind ->
            assertTrue(ServerException(kind, "detail").userMessage().isNotBlank())
        }
        assertEquals("detail", ServerException(Kind.SERVER, "detail").userMessage())
    }

    @Test
    fun `a permission refusal does not tell the user to sign in again`() {
        val message = ServerException(Kind.FORBIDDEN, "x").userMessage()
        assertEquals("You don't have permission to do that in Paperless.", message)
        assertFalse(message.contains("sign in", ignoreCase = true))
    }

    @Test
    fun `only done and failed count as finished`() {
        assertFalse(UploadState.Uploading.isFinished)
        assertFalse(UploadState.Processing("t").isFinished)
        assertTrue(UploadState.Done(null).isFinished)
        assertTrue(UploadState.Failed("x").isFinished)
    }
}
