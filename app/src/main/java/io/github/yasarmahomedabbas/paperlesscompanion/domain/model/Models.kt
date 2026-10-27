package io.github.yasarmahomedabbas.paperlesscompanion.domain.model

import java.io.InputStream

/** A Paperless server and the API token that signs in to it. */
data class ServerConfig(val baseUrl: String, val token: String)

/** A file to upload. [open] is called once per upload attempt and must return a fresh stream. */
class DocumentSource(
    val name: String,
    val mimeType: String,
    val open: () -> InputStream,
)

/** Where one upload is in its life: sent, then processed by Paperless, then accepted or refused. */
sealed interface UploadState {
    data object Uploading : UploadState
    data class Processing(val taskId: String) : UploadState
    data class Done(val documentId: Int?) : UploadState
    data class Failed(val message: String) : UploadState
}

val UploadState.isFinished: Boolean
    get() = this is UploadState.Done || this is UploadState.Failed

enum class TaskStatus { PENDING, RUNNING, SUCCESS, FAILURE }

/** Paperless's view of a consume task. */
data class TaskInfo(val status: TaskStatus, val message: String?, val documentId: Int?)

/** Every error the data layer reports, sorted into what the user can act on. */
class PaperlessException(val kind: Kind, message: String, cause: Throwable? = null) :
    Exception(message, cause) {
    enum class Kind { UNREACHABLE, UNAUTHORIZED, UNREADABLE, SERVER }
}
