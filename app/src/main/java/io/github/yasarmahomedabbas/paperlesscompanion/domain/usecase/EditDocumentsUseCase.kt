package io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase

import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.BulkEdit
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Document
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentChanges
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentPage
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.DocumentLibrary
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.SettingsRepository

// Changing documents on the signed-in server. Every one throws ServerException, UNAUTHORIZED when signed out.

/** Saves [DocumentChanges] and returns the document as the server now has it. Nothing to change is refused. */
class UpdateDocumentUseCase(private val library: DocumentLibrary, private val settings: SettingsRepository) {
    suspend operator fun invoke(id: Int, changes: DocumentChanges): Document {
        require(!changes.isEmpty) { "Nothing to change" }
        return library.update(settings.signedIn(), id, changes)
    }
}

/** Moves one document to the trash. */
class DeleteDocumentUseCase(private val library: DocumentLibrary, private val settings: SettingsRepository) {
    suspend operator fun invoke(id: Int) = library.delete(settings.signedIn(), id)
}

/** The trash: what's in it, and bringing documents back. */
class TrashUseCase(private val library: DocumentLibrary, private val settings: SettingsRepository) {
    suspend fun list(page: Int = 1, pageSize: Int = 25): DocumentPage = library.trash(settings.signedIn(), page, pageSize)

    suspend fun restore(ids: List<Int>) = library.restore(settings.signedIn(), ids)
}

/** One change to many documents: tags, correspondent, type, storage path, or the trash. */
class BulkEditUseCase(private val library: DocumentLibrary, private val settings: SettingsRepository) {
    suspend operator fun invoke(ids: List<Int>, edit: BulkEdit) = library.bulkEdit(settings.signedIn(), ids, edit)
}
