package io.github.yasarmahomedabbas.paperlesscompanion.ui.documents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.BulkEdit
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.BulkEditUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.DeleteDocumentUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.TrashUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the Undo snackbar offers to bring back. */
data class Undoable(val ids: List<Int>, val message: String)

/** [failure] is a message for a snackbar with no action. */
data class TrashUiState(val working: Boolean = false, val undoable: Undoable? = null, val failure: String? = null)

sealed interface TrashEvent {
    data class Trashed(val ids: List<Int>) : TrashEvent
    data class Restored(val ids: List<Int>) : TrashEvent
}

/**
 * Moving documents to the trash, and the Undo that brings them back for [undoMillis] after.
 * Activity-scoped: it outlives the document view, so the Undo shows on the list.
 */
class TrashViewModel(
    private val delete: DeleteDocumentUseCase,
    private val bulkEdit: BulkEditUseCase,
    private val trash: TrashUseCase,
    private val undoMillis: Long = 8_000,
) : ViewModel() {
    private val _state = MutableStateFlow(TrashUiState())
    val state: StateFlow<TrashUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<TrashEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<TrashEvent> = _events.asSharedFlow()

    private var undoTimer: Job? = null

    fun trash(ids: List<Int>) {
        if (ids.isEmpty() || _state.value.working) return
        _state.update { it.copy(working = true, failure = null) }
        viewModelScope.launch {
            try {
                if (ids.size == 1) delete(ids.single()) else bulkEdit(ids, BulkEdit.Delete)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(working = false, failure = e.messageOr("Couldn't move it to the trash")) }
                return@launch
            }
            val message = if (ids.size == 1) "Moved to trash" else "${ids.size} moved to trash"
            offerUndo(Undoable(ids, message))
            _state.update { it.copy(working = false) }
            _events.emit(TrashEvent.Trashed(ids))
        }
    }

    fun undo() {
        val undoable = _state.value.undoable ?: return
        if (_state.value.working) return
        undoTimer?.cancel()
        _state.update { it.copy(working = true, undoable = null, failure = null) }
        viewModelScope.launch {
            try {
                trash.restore(undoable.ids)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(working = false, failure = "Couldn't bring it back: ${e.messageOr("something went wrong")}") }
                return@launch
            }
            _state.update { it.copy(working = false) }
            _events.emit(TrashEvent.Restored(undoable.ids))
        }
    }

    /** The snackbar timed out or was swiped away. */
    fun undoShown() {
        undoTimer?.cancel()
        _state.update { it.copy(undoable = null) }
    }

    fun failureShown() = _state.update { it.copy(failure = null) }

    private fun offerUndo(undoable: Undoable) {
        undoTimer?.cancel()
        _state.update { it.copy(undoable = undoable) }
        undoTimer = viewModelScope.launch {
            delay(undoMillis)
            _state.update { if (it.undoable == undoable) it.copy(undoable = null) else it }
        }
    }
}

internal fun Exception.messageOr(fallback: String) = (this as? ServerException)?.userMessage() ?: fallback
