package io.github.yasarmahomedabbas.paperlesscompanion.ui.documents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentSource
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.UploadState
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.isFinished
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.UploadDocumentUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UploadItem(val id: Long, val name: String, val state: UploadState)

data class UploadsUiState(val uploads: List<UploadItem> = emptyList())

/** The uploads in progress, shown as cards at the top of the Documents list until they're cleared. */
class UploadsViewModel(
    private val uploadDocument: UploadDocumentUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(UploadsUiState())
    val state: StateFlow<UploadsUiState> = _state.asStateFlow()

    private val _added = Channel<Int?>(Channel.UNLIMITED)

    /**
     * The new document's id (null when the server didn't say) once each time an upload is
     * accepted, so the list can show it. Buffered, so one that lands while Settings is open
     * still reaches the list when it comes back.
     */
    val added: Flow<Int?> = _added.receiveAsFlow()

    private val sources = mutableMapOf<Long, DocumentSource>()
    private var nextId = 0L

    fun upload(documents: List<DocumentSource>) {
        for (document in documents) {
            val id = nextId++
            sources[id] = document
            _state.update { it.copy(uploads = listOf(UploadItem(id, document.name, UploadState.Uploading)) + it.uploads) }
            start(id, document)
        }
    }

    fun retry(id: Long) {
        val document = sources[id] ?: return
        val failed = _state.value.uploads.firstOrNull { it.id == id }?.state as? UploadState.Failed ?: return
        start(id, document, failed.taskId)
    }

    fun clearFinished() {
        _state.update { s -> s.copy(uploads = s.uploads.filterNot { it.state.isFinished }) }
        sources.keys.retainAll(_state.value.uploads.map { it.id }.toSet())
    }

    /** Drops every upload and its file, for sign-out. */
    fun clear() {
        sources.clear()
        _state.update { it.copy(uploads = emptyList()) }
    }

    private fun start(id: Long, document: DocumentSource, uploadedTaskId: String? = null) {
        viewModelScope.launch {
            uploadDocument(document, uploadedTaskId)
                .catch { e -> emit(UploadState.Failed(e.message ?: "Upload failed")) }
                .collect { state -> setState(id, state) }
        }
    }

    private fun setState(id: Long, state: UploadState) {
        // A cleared upload whose flow is still running stays gone.
        if (_state.value.uploads.none { it.id == id }) return
        _state.update { s -> s.copy(uploads = s.uploads.map { if (it.id == id) it.copy(state = state) else it }) }
        if (state is UploadState.Done) _added.trySend(state.documentId)
    }
}
