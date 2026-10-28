package io.github.yasarmahomedabbas.paperlesscompanion.domain.model

/**
 * The document as it is after [edit], worked out locally so a list can show a bulk change without
 * reloading. Tags keep their order: removed ones drop out, added ones the document lacks go at the end.
 * [BulkEdit.Delete] leaves it as it is (the caller removes it).
 */
fun Document.with(edit: BulkEdit): Document = when (edit) {
    is BulkEdit.ModifyTags -> copy(tagIds = (tagIds.filterNot { it in edit.remove } + edit.add.filterNot { it in tagIds }).distinct())
    is BulkEdit.SetCorrespondent -> copy(correspondentId = edit.id)
    is BulkEdit.SetDocumentType -> copy(documentTypeId = edit.id)
    is BulkEdit.SetStoragePath -> copy(storagePathId = edit.id)
    BulkEdit.Delete -> this
}
