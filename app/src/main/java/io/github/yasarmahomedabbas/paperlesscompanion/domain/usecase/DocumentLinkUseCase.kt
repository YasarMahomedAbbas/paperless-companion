package io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase

import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.DocumentServer
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.SettingsRepository

/** The web address of an uploaded document on the signed-in server, or null when signed out. */
class DocumentLinkUseCase(private val server: DocumentServer, private val settings: SettingsRepository) {
    operator fun invoke(documentId: Int): String? = settings.load()?.let { server.documentUrl(it, documentId) }
}
