package io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase

import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentSource
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.PaperlessException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.TaskStatus
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.UploadState
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.PaperlessRepository
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Uploads one document and follows its consume task until Paperless accepts or refuses it.
 * Emits Uploading, then Processing, then exactly one of Done or Failed.
 */
class UploadDocumentUseCase(
    private val paperless: PaperlessRepository,
    private val settings: SettingsRepository,
    private val pollInterval: Duration = 2.seconds,
    private val timeout: Duration = 5.minutes,
) {
    operator fun invoke(document: DocumentSource): Flow<UploadState> = flow {
        val config = settings.load()
        if (config == null) {
            emit(UploadState.Failed("Not signed in"))
            return@flow
        }
        emit(UploadState.Uploading)
        try {
            val taskId = paperless.upload(config, document)
            emit(UploadState.Processing(taskId))
            var waited = Duration.ZERO
            while (true) {
                val task = paperless.task(config, taskId)
                when (task.status) {
                    TaskStatus.SUCCESS -> {
                        emit(UploadState.Done(task.documentId))
                        return@flow
                    }
                    TaskStatus.FAILURE -> {
                        emit(UploadState.Failed(task.message ?: "Paperless refused the document"))
                        return@flow
                    }
                    TaskStatus.PENDING, TaskStatus.RUNNING -> Unit
                }
                if (waited >= timeout) {
                    emit(UploadState.Failed("Uploaded, but Paperless is still processing it. Check Paperless later."))
                    return@flow
                }
                delay(pollInterval)
                waited += pollInterval
            }
        } catch (e: PaperlessException) {
            emit(UploadState.Failed(e.userMessage()))
        }
    }
}

fun PaperlessException.userMessage(): String = when (kind) {
    PaperlessException.Kind.UNREACHABLE -> "Can't reach Paperless. Are you on the home network or the VPN?"
    PaperlessException.Kind.UNAUTHORIZED -> "Paperless rejected the login. Sign out and sign in again."
    PaperlessException.Kind.UNREADABLE -> "Couldn't read the file on the phone"
    PaperlessException.Kind.SERVER -> message ?: "Paperless returned an error"
}
