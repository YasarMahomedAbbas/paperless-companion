package io.github.yasarmahomedabbas.paperlesscompanion.data

import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentLayout
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.DisplayPreferences

/** [DisplayPreferences] in a [KeyValueStore]: the layout by its name; a missing or unknown one reads as the list. */
class PrefsDisplayPreferences(private val store: KeyValueStore) : DisplayPreferences {
    override fun documentLayout(): DocumentLayout {
        val value = store.getString(LAYOUT)
        return DocumentLayout.entries.firstOrNull { it.name == value } ?: DocumentLayout.LIST
    }

    override fun setDocumentLayout(layout: DocumentLayout) = store.edit { put(LAYOUT, layout.name) }

    private companion object {
        const val LAYOUT = "document_layout"
    }
}
