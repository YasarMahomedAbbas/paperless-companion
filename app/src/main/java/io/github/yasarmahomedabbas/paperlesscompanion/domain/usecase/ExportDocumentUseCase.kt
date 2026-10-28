package io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase

import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentFile
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.DocumentExports

/** Shares a fetched document file with another app, or saves it on the phone. Throws ServerException. */
class ExportDocumentUseCase(private val exports: DocumentExports) {
    fun shareLocation(file: DocumentFile): String = exports.shareLocation(file)

    val canSaveDirectly: Boolean get() = exports.canSaveDirectly

    suspend fun saveToDownloads(file: DocumentFile): String = exports.saveToDownloads(file)

    suspend fun saveTo(file: DocumentFile, location: String) = exports.saveTo(file, location)
}
