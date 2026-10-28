package io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase

import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentSource
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException.Kind
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.TaskStatus
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.UploadState
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.DocumentServer
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Uploads one document and follows its consume task until the server accepts or refuses it.
 * Emits Uploading, then Processing, then exactly one of Done or Failed. With [uploadedTaskId] the file is
 * already on the server, so it skips the upload and only follows the task.
 */
class UploadDocumentUseCase(
    private val server: DocumentServer,
    private val settings: SettingsRepository,
    private val pollInterval: Duration = 2.seconds,
    private val timeout: Duration = 5.minutes,
) {
    operator fun invoke(document: DocumentSource, uploadedTaskId: String? = null): Flow<UploadState> = flow {
        val config = settings.load()
        if (config == null) {
            emit(UploadState.Failed("Not signed in"))
            return@flow
        }
        val taskId = uploadedTaskId ?: try {
            emit(UploadState.Uploading)
            server.upload(config, document)
        } catch (e: ServerException) {
            emit(UploadState.Failed(e.userMessage(), unreachable = e.isNetwork))
            return@flow
        }
        emit(UploadState.Processing(taskId))
        var waited = Duration.ZERO
        var lastError: ServerException? = null
        while (true) {
            val task = try {
                server.task(config, taskId).also { lastError = null }
            } catch (e: ServerException) {
                // The file is already on the server, so a dropped or slow check is tried again, not reported.
                if (!e.isNetwork) {
                    emit(UploadState.Failed(notConfirmed(e), taskId))
                    return@flow
                }
                lastError = e
                null
            }
            when (task?.status) {
                TaskStatus.SUCCESS -> {
                    emit(UploadState.Done(task.documentId))
                    return@flow
                }
                TaskStatus.FAILURE -> {
                    emit(UploadState.Failed(task.message ?: "Paperless refused the document"))
                    return@flow
                }
                else -> Unit
            }
            if (waited >= timeout) {
                val message = lastError?.let(::notConfirmed)
                    ?: "Uploaded, but Paperless is still processing it. Tap Retry to check again."
                emit(UploadState.Failed(message, taskId, unreachable = lastError != null))
                return@flow
            }
            delay(pollInterval)
            waited += pollInterval
        }
    }

    private val ServerException.isNetwork get() = kind == Kind.UNREACHABLE || kind == Kind.NO_ANSWER

    private fun notConfirmed(e: ServerException) =
        "Uploaded, but checking whether Paperless accepted it failed: ${e.userMessage()} Tap Retry to check again."
}

fun ServerException.userMessage(): String = when (kind) {
    Kind.UNREACHABLE -> "Can't reach Paperless ($message). Are you on the home network or the VPN?"
    Kind.NO_ANSWER -> "Paperless didn't answer ($message)."
    Kind.UNAUTHORIZED -> "Paperless rejected the login. Sign out and sign in again."
    Kind.UNREADABLE -> "Couldn't read the file on the phone"
    Kind.NOT_FOUND -> message ?: "Paperless couldn't find it"
    Kind.SERVER -> message ?: "Paperless returned an error"
}
