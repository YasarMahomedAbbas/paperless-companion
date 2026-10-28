package io.github.yasarmahomedabbas.paperlesscompanion.ui

import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentSort
import io.github.yasarmahomedabbas.paperlesscompanion.ui.documents.DocumentsUiState
import io.github.yasarmahomedabbas.paperlesscompanion.ui.documents.SortOption
import org.junit.Assert.assertEquals
import org.junit.Test

class SortOptionTest {
    @Test
    fun `each option sorts by its field in its direction`() {
        assertEquals(
            listOf(
                Triple("Newest first", DocumentSort.CREATED, true),
                Triple("Oldest first", DocumentSort.CREATED, false),
                Triple("Recently added", DocumentSort.ADDED, true),
                Triple("Title A–Z", DocumentSort.TITLE, false),
                Triple("Correspondent A–Z", DocumentSort.CORRESPONDENT, false),
            ),
            SortOption.entries.map { Triple(it.label, it.sort, it.descending) },
        )
    }

    @Test
    fun `newest first is the default`() {
        assertEquals(SortOption.NEWEST, DocumentsUiState().sort)
    }
}
