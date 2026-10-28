package io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase

import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Document
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentFile
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentFileKind
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentPage
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentQuery
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerConfig
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException.Kind
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.DocumentLibrary
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.SettingsRepository

/** The signed-in server, or UNAUTHORIZED "Not signed in". */
internal fun SettingsRepository.signedIn(): ServerConfig =
    load() ?: throw ServerException(Kind.UNAUTHORIZED, "Not signed in")

/** One page of the documents on the signed-in server, sorted, filtered or searched. Throws ServerException. */
class BrowseDocumentsUseCase(private val library: DocumentLibrary, private val settings: SettingsRepository) {
    suspend operator fun invoke(query: DocumentQuery): DocumentPage = library.documents(settings.signedIn(), query)
}

/** One document with its text. A gone document throws ServerException NOT_FOUND. */
class GetDocumentUseCase(private val library: DocumentLibrary, private val settings: SettingsRepository) {
    suspend operator fun invoke(id: Int): Document = library.document(settings.signedIn(), id)
}

/** Fetches a document's preview, archived or original file into the app's cache, reporting progress. Throws ServerException. */
class OpenDocumentFileUseCase(private val library: DocumentLibrary, private val settings: SettingsRepository) {
    suspend operator fun invoke(
        id: Int,
        kind: DocumentFileKind,
        onProgress: (done: Long, total: Long?) -> Unit = { _, _ -> },
    ): DocumentFile = library.file(settings.signedIn(), id, kind, onProgress)
}
