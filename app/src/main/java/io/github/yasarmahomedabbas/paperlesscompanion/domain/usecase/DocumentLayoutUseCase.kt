package io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase

import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentLayout
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.DisplayPreferences

/** Whether the document list shows rows or cards, as remembered on this phone. */
class DocumentLayoutUseCase(private val preferences: DisplayPreferences) {
    fun current(): DocumentLayout = preferences.documentLayout()

    fun set(layout: DocumentLayout) = preferences.setDocumentLayout(layout)
}
