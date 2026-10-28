package io.github.yasarmahomedabbas.paperlesscompanion.ui.edit

import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Change
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Document
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentChanges
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Labels
import java.time.LocalDate

/** The fields Edit details can change, as the form holds them. */
data class EditForm(
    val title: String,
    val created: LocalDate?,
    val correspondentId: Int?,
    val documentTypeId: Int?,
    val storagePathId: Int?,
    val tagIds: List<Int>,
) {
    companion object {
        fun from(document: Document) = EditForm(
            title = document.title,
            created = document.created,
            correspondentId = document.correspondentId,
            documentTypeId = document.documentTypeId,
            storagePathId = document.storagePathId,
            tagIds = document.tagIds,
        )
    }
}

/**
 * Only what differs from [original]. The title is sent trimmed. A date can't be cleared, so it is
 * sent only when set and different. Tags are compared as sets; when they differ the form's order is sent.
 */
internal fun EditForm.changesFrom(original: EditForm): DocumentChanges {
    val trimmed = title.trim()
    return DocumentChanges(
        title = if (trimmed != original.title) Change.To(trimmed) else Change.Keep,
        created = if (created != null && created != original.created) Change.To(created) else Change.Keep,
        correspondentId = changed(correspondentId, original.correspondentId),
        documentTypeId = changed(documentTypeId, original.documentTypeId),
        storagePathId = changed(storagePathId, original.storagePathId),
        tagIds = if (tagIds.toSet() != original.tagIds.toSet()) Change.To(tagIds) else Change.Keep,
    )
}

private fun changed(now: Int?, before: Int?): Change<Int?> = if (now != before) Change.To(now) else Change.Keep

/** The tags, correspondents, document types and storage paths the pickers offer. */
sealed interface LabelsStatus {
    data object Loading : LabelsStatus
    data class Loaded(val labels: Labels) : LabelsStatus
    data class Failed(val message: String) : LabelsStatus
}

/**
 * [titleError] is set by a save with a blank title. [saveFailed] is why the last save failed (the form is kept).
 * [saved] is the document as the server now has it, once a save went through (or there was nothing to save).
 */
data class EditDetailsUiState(
    val form: EditForm,
    val original: EditForm,
    val labels: LabelsStatus = LabelsStatus.Loading,
    val titleError: String? = null,
    val saving: Boolean = false,
    val saveFailed: String? = null,
    val saved: Document? = null,
) {
    val hasChanges: Boolean get() = !form.changesFrom(original).isEmpty
}
