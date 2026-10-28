package io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase

import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Note
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.DocumentLibrary
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.SettingsRepository

/** A document's notes on the signed-in server: reading them, adding one, and whether notes are on at all. Throws ServerException. */
class NotesUseCase(private val library: DocumentLibrary, private val settings: SettingsRepository) {
    /** The notes, newest first. */
    suspend fun load(documentId: Int): List<Note> = library.notes(settings.signedIn(), documentId)

    /** Adds [text] (trimmed) and returns every note, newest first. Blank text is refused. */
    suspend fun add(documentId: Int, text: String): List<Note> {
        require(text.isNotBlank()) { "A note needs text" }
        return library.addNote(settings.signedIn(), documentId, text.trim())
    }

    /** False when the server's owner switched notes off. */
    suspend fun enabled(): Boolean = library.notesEnabled(settings.signedIn())
}
