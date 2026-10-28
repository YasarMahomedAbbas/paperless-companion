package io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase

import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.LabelKind
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.DocumentServer
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.SettingsRepository

/** The web address of an uploaded document on the signed-in server, or null when signed out. */
class DocumentLinkUseCase(private val server: DocumentServer, private val settings: SettingsRepository) {
    operator fun invoke(documentId: Int): String? = settings.load()?.let { server.documentUrl(it, documentId) }
}

/** The web address where the signed-in server manages a kind of label, or null when signed out. */
class LabelsLinkUseCase(private val server: DocumentServer, private val settings: SettingsRepository) {
    operator fun invoke(kind: LabelKind): String? = settings.load()?.let { server.labelsUrl(it, kind) }
}
