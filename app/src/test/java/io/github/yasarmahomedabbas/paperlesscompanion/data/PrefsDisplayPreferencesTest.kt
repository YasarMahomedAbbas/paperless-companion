package io.github.yasarmahomedabbas.paperlesscompanion.data

import io.github.yasarmahomedabbas.paperlesscompanion.MapStore
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class PrefsDisplayPreferencesTest {
    private val store = MapStore()
    private val prefs = PrefsDisplayPreferences(store)

    @Test
    fun `nothing stored is the list`() {
        assertEquals(DocumentLayout.LIST, prefs.documentLayout())
    }

    @Test
    fun `cards are remembered across a restart`() {
        prefs.setDocumentLayout(DocumentLayout.CARDS)

        assertEquals("CARDS", store.values["document_layout"])
        assertEquals(DocumentLayout.CARDS, PrefsDisplayPreferences(store).documentLayout())
    }

    @Test
    fun `an unknown stored layout reads as the list`() {
        store.values["document_layout"] = "GRID"

        assertEquals(DocumentLayout.LIST, prefs.documentLayout())
    }

    @Test
    fun `going back to the list stores the list`() {
        prefs.setDocumentLayout(DocumentLayout.CARDS)
        prefs.setDocumentLayout(DocumentLayout.LIST)

        assertEquals("LIST", store.values["document_layout"])
        assertEquals(DocumentLayout.LIST, prefs.documentLayout())
    }
}
