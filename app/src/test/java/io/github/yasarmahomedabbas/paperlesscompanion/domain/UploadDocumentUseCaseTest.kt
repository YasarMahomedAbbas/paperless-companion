package io.github.yasarmahomedabbas.paperlesscompanion.domain

import io.github.yasarmahomedabbas.paperlesscompanion.CONFIG
import io.github.yasarmahomedabbas.paperlesscompanion.FakePaperless
import io.github.yasarmahomedabbas.paperlesscompanion.FakeSettings
import io.github.yasarmahomedabbas.paperlesscompanion.document
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.PaperlessException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.PaperlessException.Kind
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
    private val paperless = FakePaperless()
    private val settings = FakeSettings(CONFIG)
    private val upload = UploadDocumentUseCase(paperless, settings, pollInterval = 1.seconds, timeout = 10.seconds)

    @Test
    fun `uploads, waits for processing and reports the new document`() = runTest {
        paperless.tasks.apply {
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
        assertEquals(listOf("bill.pdf"), paperless.uploads)
        assertEquals(3, paperless.taskPolls)
    }

    @Test
    fun `reports Paperless's reason when it refuses the document`() = runTest {
        paperless.tasks[0] = TaskInfo(TaskStatus.FAILURE, "Not consuming bill.pdf: It is a duplicate", null)

        val last = upload(document()).toList().last()

        assertEquals(UploadState.Failed("Not consuming bill.pdf: It is a duplicate"), last)
    }

    @Test
    fun `has a fallback message when a failure has no reason`() = runTest {
        paperless.tasks[0] = TaskInfo(TaskStatus.FAILURE, null, null)

        assertEquals(UploadState.Failed("Paperless refused the document"), upload(document()).toList().last())
    }

    @Test
    fun `gives up polling after the timeout`() = runTest {
        paperless.tasks[0] = TaskInfo(TaskStatus.RUNNING, null, null)

        val last = upload(document()).toList().last()

        assertTrue(last is UploadState.Failed)
        assertTrue((last as UploadState.Failed).message.contains("still processing"))
        assertEquals(11, paperless.taskPolls)
    }

    @Test
    fun `fails straight away when not signed in`() = runTest {
        settings.config = null

        assertEquals(listOf(UploadState.Failed("Not signed in")), upload(document()).toList())
        assertTrue(paperless.uploads.isEmpty())
    }

    @Test
    fun `turns an unreachable server into a VPN hint`() = runTest {
        paperless.uploadResult = { throw PaperlessException(Kind.UNREACHABLE, "boom") }

        val last = upload(document()).toList().last() as UploadState.Failed

        assertTrue(last.message.contains("VPN"))
    }

    @Test
    fun `every error kind has a user message`() {
        Kind.entries.forEach { kind ->
            assertTrue(PaperlessException(kind, "detail").userMessage().isNotBlank())
        }
        assertEquals("detail", PaperlessException(Kind.SERVER, "detail").userMessage())
    }

    @Test
    fun `only done and failed count as finished`() {
        assertFalse(UploadState.Uploading.isFinished)
        assertFalse(UploadState.Processing("t").isFinished)
        assertTrue(UploadState.Done(null).isFinished)
        assertTrue(UploadState.Failed("x").isFinished)
    }
}
