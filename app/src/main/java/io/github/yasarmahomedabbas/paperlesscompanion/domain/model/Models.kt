package io.github.yasarmahomedabbas.paperlesscompanion.domain.model

import java.io.InputStream

/** A Paperless server and the API token that signs in to it. */
data class ServerConfig(val baseUrl: String, val token: String)

/** What reading the saved sign-in found. */
sealed interface StoredSignIn {
    /** Nobody has signed in on this phone. */
    data object None : StoredSignIn
    /** The token is sealed behind the phone's lock: unlock, then call AppLockRepository.unlock(). */
    data object Locked : StoredSignIn
    /** The phone voided the key (a new fingerprint, or the screen lock was removed); the sign-in is gone. */
    data object Voided : StoredSignIn
    data class Ready(val config: ServerConfig) : StoredSignIn
}

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
    /** [taskId] is set when the file already reached Paperless, so a retry only checks on it again. */
    data class Failed(val message: String, val taskId: String? = null) : UploadState
}

val UploadState.isFinished: Boolean
    get() = this is UploadState.Done || this is UploadState.Failed

enum class TaskStatus { PENDING, RUNNING, SUCCESS, FAILURE }

/** Paperless's view of a consume task. */
data class TaskInfo(val status: TaskStatus, val message: String?, val documentId: Int?)

/** Every error the data layer reports, sorted into what the user can act on. */
class PaperlessException(val kind: Kind, message: String, cause: Throwable? = null) :
    Exception(message, cause) {
    /** UNREACHABLE: no connection at all. NO_ANSWER: connected, but the answer timed out or the connection dropped. */
    enum class Kind { UNREACHABLE, NO_ANSWER, UNAUTHORIZED, UNREADABLE, SERVER }
}
