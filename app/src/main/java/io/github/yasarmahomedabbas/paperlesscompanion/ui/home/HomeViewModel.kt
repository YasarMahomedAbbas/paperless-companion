package io.github.yasarmahomedabbas.paperlesscompanion.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentSource
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.UploadState
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.isFinished
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.SettingsRepository
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.UploadDocumentUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UploadItem(val id: Long, val name: String, val state: UploadState)

data class HomeUiState(
    val serverUrl: String,
    val uploads: List<UploadItem> = emptyList(),
) {
    fun documentUrl(documentId: Int) = "$serverUrl/documents/$documentId/details"
}

class HomeViewModel(
    private val uploadDocument: UploadDocumentUseCase,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState(serverUrl = settings.load()?.baseUrl.orEmpty()))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

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
        if (_state.value.uploads.firstOrNull { it.id == id }?.state !is UploadState.Failed) return
        start(id, document)
    }

    fun clearFinished() {
        _state.update { s -> s.copy(uploads = s.uploads.filterNot { it.state.isFinished }) }
        sources.keys.retainAll(_state.value.uploads.map { it.id }.toSet())
    }

    fun signOut() {
        settings.clear()
        sources.clear()
        _state.update { it.copy(uploads = emptyList()) }
    }

    private fun start(id: Long, document: DocumentSource) {
        viewModelScope.launch {
            uploadDocument(document)
                .catch { e -> emit(UploadState.Failed(e.message ?: "Upload failed")) }
                .collect { state -> setState(id, state) }
        }
    }

    private fun setState(id: Long, state: UploadState) = _state.update { s ->
        s.copy(uploads = s.uploads.map { if (it.id == id) it.copy(state = state) else it })
    }
}
