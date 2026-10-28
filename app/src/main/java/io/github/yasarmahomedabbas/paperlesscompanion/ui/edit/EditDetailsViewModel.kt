package io.github.yasarmahomedabbas.paperlesscompanion.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Document
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.LabelKind
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.LabelsLinkUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.LoadLabelsUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.UpdateDocumentUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Edit details for one [document]: the form, the labels its pickers offer, and saving the difference. */
class EditDetailsViewModel(
    private val document: Document,
    private val update: UpdateDocumentUseCase,
    private val loadLabels: LoadLabelsUseCase,
    private val labelsLink: LabelsLinkUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(EditForm.from(document).let { EditDetailsUiState(form = it, original = it) })
    val state: StateFlow<EditDetailsUiState> = _state.asStateFlow()

    init {
        retryLabels()
    }

    fun setTitle(title: String) = _state.update { it.copy(form = it.form.copy(title = title), titleError = null) }
    fun setDate(date: LocalDate) = edit { copy(created = date) }
    fun setCorrespondent(id: Int?) = edit { copy(correspondentId = id) }
    fun setDocumentType(id: Int?) = edit { copy(documentTypeId = id) }
    fun setStoragePath(id: Int?) = edit { copy(storagePathId = id) }
    fun setTags(ids: List<Int>) = edit { copy(tagIds = ids) }

    private fun edit(change: EditForm.() -> EditForm) = _state.update { it.copy(form = it.form.change()) }

    fun retryLabels() {
        _state.update { it.copy(labels = LabelsStatus.Loading) }
        viewModelScope.launch {
            val status = try {
                LabelsStatus.Loaded(loadLabels())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LabelsStatus.Failed(e.messageOr("Couldn't load the labels"))
            }
            _state.update { it.copy(labels = status) }
        }
    }

    fun save() {
        val current = _state.value
        if (current.saving) return
        if (current.form.title.isBlank()) {
            _state.update { it.copy(titleError = "A document needs a title.") }
            return
        }
        val changes = current.form.changesFrom(current.original)
        if (changes.isEmpty) {
            _state.update { it.copy(saved = document) }
            return
        }
        _state.update { it.copy(saving = true, saveFailed = null) }
        viewModelScope.launch {
            try {
                val saved = update(document.id, changes)
                _state.update { it.copy(saving = false, saved = saved) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, saveFailed = e.messageOr("Couldn't save the changes")) }
            }
        }
    }

    fun labelsUrl(kind: LabelKind): String? = labelsLink(kind)
}

private fun Exception.messageOr(fallback: String) = (this as? ServerException)?.userMessage() ?: fallback
