package io.github.yasarmahomedabbas.paperlesscompanion.ui.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Document
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentFile
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentFileKind
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Label
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Labels
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException.Kind
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.DocumentLinkUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.ExportDocumentUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.GetDocumentUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.LoadLabelsUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.OpenDocumentFileUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.userMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

/** A document as the details panel shows it: label ids already turned into names (null when unknown). */
data class DocumentDetails(
    val title: String,
    val created: LocalDate?,
    val added: Instant?,
    val correspondent: String?,
    val documentType: String?,
    val storagePath: String?,
    val tags: List<String>,
    /** The document came in as a photo (or another image): what's shown is Paperless's PDF of it. */
    val isPhotoOriginal: Boolean,
    val originalIsPdf: Boolean,
)

sealed interface DetailsState {
    data object Loading : DetailsState
    data class Ready(val details: DocumentDetails) : DetailsState
    data class Failed(val message: String, val unreachable: Boolean) : DetailsState
}

sealed interface FileState {
    /** [total] is null when the server didn't say how big the file is. */
    data class Loading(val done: Long, val total: Long?) : FileState

    /** A PDF, ready to show. */
    data class Ready(val file: DocumentFile) : FileState

    /** The preview isn't a PDF, so it can't be shown here; it can still be shared or saved. */
    data class NotViewable(val file: DocumentFile) : FileState

    data class Failed(val message: String, val unreachable: Boolean) : FileState
}

data class ViewerUiState(
    val documentId: Int,
    val details: DetailsState = DetailsState.Loading,
    val file: FileState = FileState.Loading(0, null),
    /** A Download or Get original is on its way. */
    val saving: Boolean = false,
    /** Snackbar text, cleared by messageShown(). */
    val message: String? = null,
)

/** What the screen does once, outside itself: hand a file to another app, ask where to save, open a browser. */
sealed interface ViewerEvent {
    data class Share(val location: String, val name: String, val mimeType: String?) : ViewerEvent
    data class PickSaveLocation(val name: String, val mimeType: String?) : ViewerEvent
    data class OpenUrl(val url: String) : ViewerEvent
}

/**
 * The document viewer: loads the document, its labels and its preview file side by side, then shares,
 * saves (to Downloads, or where the user picks) and opens the document in the server's web interface.
 */
class ViewerViewModel(
    private val documentId: Int,
    private val getDocument: GetDocumentUseCase,
    private val loadLabels: LoadLabelsUseCase,
    private val openFile: OpenDocumentFileUseCase,
    private val documentLink: DocumentLinkUseCase,
    private val export: ExportDocumentUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(ViewerUiState(documentId))
    val state: StateFlow<ViewerUiState> = _state.asStateFlow()

    private val _events = Channel<ViewerEvent>(Channel.BUFFERED)
    val events: Flow<ViewerEvent> = _events.receiveAsFlow()

    /** The file waiting for the user to pick where it goes. */
    private var pending: DocumentFile? = null

    init {
        loadDetails()
        loadFile()
    }

    /** Reloads whichever of the details and the file failed. */
    fun retry() {
        if (_state.value.details is DetailsState.Failed) loadDetails()
        if (_state.value.file is FileState.Failed) loadFile()
    }

    fun share() {
        val file = currentFile() ?: return
        viewModelScope.launch {
            try {
                _events.send(ViewerEvent.Share(export.shareLocation(file), file.name, file.mimeType))
            } catch (e: ServerException) {
                _state.update { it.copy(message = e.userMessage()) }
            } catch (e: IllegalArgumentException) {
                // FileProvider refuses a file outside the folders it serves.
                _state.update { it.copy(message = "Couldn't share ${file.name}") }
            }
        }
    }

    /** Saves the file on show (Paperless's PDF for an image original). */
    fun download() {
        if (_state.value.saving) return
        val file = currentFile() ?: return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch { save(file) }
    }

    /** Fetches the file as it was uploaded, then saves it. */
    fun downloadOriginal() {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val file = try {
                openFile(documentId, DocumentFileKind.ORIGINAL)
            } catch (e: ServerException) {
                _state.update { it.copy(saving = false, message = e.userMessage()) }
                return@launch
            }
            save(file)
        }
    }

    /** Where the user picked to save the pending file; null when they cancelled. */
    fun saveTo(location: String?) {
        val file = pending ?: return
        pending = null
        if (location == null) {
            _state.update { it.copy(saving = false) }
            return
        }
        viewModelScope.launch {
            try {
                export.saveTo(file, location)
                _state.update { it.copy(saving = false, message = "Saved") }
            } catch (e: ServerException) {
                _state.update { it.copy(saving = false, message = saveFailed(file, e)) }
            }
        }
    }

    fun openInPaperless() {
        val url = documentLink(documentId) ?: return
        viewModelScope.launch { _events.send(ViewerEvent.OpenUrl(url)) }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    private suspend fun save(file: DocumentFile) {
        if (!export.canSaveDirectly) {
            pending = file
            _events.send(ViewerEvent.PickSaveLocation(file.name, file.mimeType))
            return
        }
        try {
            export.saveToDownloads(file)
            _state.update { it.copy(saving = false, message = "Saved to Downloads") }
        } catch (e: ServerException) {
            _state.update { it.copy(saving = false, message = saveFailed(file, e)) }
        }
    }

    private fun currentFile(): DocumentFile? = when (val file = _state.value.file) {
        is FileState.Ready -> file.file
        is FileState.NotViewable -> file.file
        else -> null
    }

    private fun loadDetails() {
        _state.update { it.copy(details = DetailsState.Loading) }
        viewModelScope.launch {
            // Labels only name things: a failure leaves the names out, never the document.
            val labels = async {
                try {
                    loadLabels()
                } catch (e: ServerException) {
                    null
                }
            }
            val details = try {
                DetailsState.Ready(getDocument(documentId).details(labels.await()))
            } catch (e: ServerException) {
                labels.cancel()
                DetailsState.Failed(e.userMessage(), e.isNetwork)
            }
            _state.update { it.copy(details = details) }
        }
    }

    private fun loadFile() {
        _state.update { it.copy(file = FileState.Loading(0, null)) }
        viewModelScope.launch {
            val file = try {
                val fetched = openFile(documentId, DocumentFileKind.PREVIEW) { done, total ->
                    _state.update { it.copy(file = FileState.Loading(done, total)) }
                }
                if (fetched.isPdf) FileState.Ready(fetched) else FileState.NotViewable(fetched)
            } catch (e: ServerException) {
                FileState.Failed(e.userMessage(), e.isNetwork)
            }
            _state.update { it.copy(file = file) }
        }
    }

    private companion object {
        val ServerException.isNetwork get() = kind == Kind.UNREACHABLE || kind == Kind.NO_ANSWER

        val DocumentFile.isPdf get() = mimeType == "application/pdf" || name.endsWith(".pdf", ignoreCase = true)

        fun saveFailed(file: DocumentFile, e: ServerException) =
            if (e.kind == Kind.UNREADABLE) "Couldn't save ${file.name}" else e.userMessage()

        fun Document.details(labels: Labels?): DocumentDetails {
            fun name(id: Int?, of: List<Label>?) =
                id?.let { wanted -> of?.firstOrNull { it.id == wanted }?.name }
            val tagNames = labels?.tags?.associate { it.id to it.name }.orEmpty()
            return DocumentDetails(
                title = title,
                created = created,
                added = added,
                correspondent = name(correspondentId, labels?.correspondents),
                documentType = name(documentTypeId, labels?.documentTypes),
                storagePath = name(storagePathId, labels?.storagePaths),
                tags = tagIds.mapNotNull { tagNames[it] },
                isPhotoOriginal = mimeType?.startsWith("image/") == true,
                originalIsPdf = mimeType == "application/pdf",
            )
        }
    }
}
