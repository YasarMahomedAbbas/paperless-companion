package io.github.yasarmahomedabbas.paperlesscompanion.ui.documents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.BulkEdit
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.BulkEditUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The list is selecting while [selected] holds anything. [failure] is a message for a snackbar. */
data class SelectionUiState(val selected: Set<Int> = emptySet(), val working: Boolean = false, val failure: String? = null) {
    val selecting: Boolean get() = selected.isNotEmpty()
}

/** A bulk change that went through, for the list to apply to its loaded rows. */
data class Changed(val ids: List<Int>, val edit: BulkEdit)

/**
 * Selecting documents on the list and changing their tags, correspondent or type in one call.
 * Trashing them goes through [TrashViewModel]: the screen passes the selected ids and calls [stop] on Trashed.
 */
class SelectionViewModel(private val bulkEdit: BulkEditUseCase) : ViewModel() {
    private val _state = MutableStateFlow(SelectionUiState())
    val state: StateFlow<SelectionUiState> = _state.asStateFlow()

    private val _changes = MutableSharedFlow<Changed>(extraBufferCapacity = 8)
    val changes: SharedFlow<Changed> = _changes.asSharedFlow()

    fun start(id: Int) = _state.update { it.copy(selected = setOf(id)) }

    /** Adds or drops [id]; dropping the last one stops selecting. */
    fun toggle(id: Int) = _state.update { it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id) }

    /** Selects exactly the rows already loaded. */
    fun selectAll(loadedIds: List<Int>) = _state.update { it.copy(selected = loadedIds.toSet()) }

    fun stop() = _state.update { it.copy(selected = emptySet()) }

    fun changeTags(start: Map<Int, TagMark>, now: Map<Int, TagMark>) {
        bulkTagChange(start, now)?.let(::change)
    }

    /** Sends [edit] (tags, correspondent or type) for every selected document. */
    fun change(edit: BulkEdit) {
        val current = _state.value
        if (current.working || !current.selecting) return
        val ids = current.selected.toList()
        _state.update { it.copy(working = true, failure = null) }
        viewModelScope.launch {
            try {
                bulkEdit(ids, edit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(working = false, failure = e.messageOr("Couldn't change them")) }
                return@launch
            }
            _state.update { it.copy(working = false, selected = emptySet()) }
            _changes.emit(Changed(ids, edit))
        }
    }

    fun failureShown() = _state.update { it.copy(failure = null) }
}
