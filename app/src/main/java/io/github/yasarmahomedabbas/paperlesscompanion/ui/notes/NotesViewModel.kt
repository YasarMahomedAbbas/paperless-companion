package io.github.yasarmahomedabbas.paperlesscompanion.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Note
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException.Kind
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.NotesUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the notes stand. [Failed.unreachable] says the phone couldn't reach the server, as opposed to a refusal. */
sealed interface NotesStatus {
    data object Loading : NotesStatus
    data object Loaded : NotesStatus
    data class Failed(val message: String, val unreachable: Boolean) : NotesStatus
}

data class NotesUiState(
    val status: NotesStatus = NotesStatus.Loading,
    /** Newest first. */
    val notes: List<Note> = emptyList(),
    /** The note being written; kept when adding it fails. */
    val draft: String = "",
    val adding: Boolean = false,
    /** Why the last add failed, until the draft changes. */
    val addFailed: String? = null,
)

/** One document's notes: loads them, and adds the draft as a new one. */
class NotesViewModel(private val documentId: Int, private val notes: NotesUseCase) : ViewModel() {
    private val _state = MutableStateFlow(NotesUiState())
    val state: StateFlow<NotesUiState> = _state.asStateFlow()

    /** How many notes the document has, once they loaded; the viewer's Notes row follows it. */
    val count: Int? get() = _state.value.takeIf { it.status == NotesStatus.Loaded }?.notes?.size

    init {
        load()
    }

    fun retry() = load()

    fun setDraft(text: String) = _state.update { it.copy(draft = text, addFailed = null) }

    /** Adds the draft; blank text, or a second add while one is on its way, is ignored. */
    fun add() {
        val s = _state.value
        if (s.adding || s.draft.isBlank()) return
        _state.update { it.copy(adding = true, addFailed = null) }
        viewModelScope.launch {
            try {
                val all = notes.add(documentId, s.draft)
                _state.update { it.copy(notes = all, status = NotesStatus.Loaded, draft = "", adding = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = (e as? ServerException)?.userMessage() ?: "Couldn't add the note"
                _state.update { it.copy(adding = false, addFailed = message) }
            }
        }
    }

    private fun load() {
        _state.update { it.copy(status = NotesStatus.Loading) }
        viewModelScope.launch {
            try {
                val all = notes.load(documentId)
                _state.update { it.copy(status = NotesStatus.Loaded, notes = all) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(status = e.failure()) }
            }
        }
    }
}

private fun Exception.failure(): NotesStatus.Failed = when (this) {
    is ServerException -> NotesStatus.Failed(userMessage(), unreachable = kind == Kind.UNREACHABLE || kind == Kind.NO_ANSWER)
    else -> NotesStatus.Failed("Couldn't load the notes", unreachable = false)
}
