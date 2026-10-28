package io.github.yasarmahomedabbas.paperlesscompanion.ui

import io.github.yasarmahomedabbas.paperlesscompanion.doc
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.BulkEdit
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Tag
import io.github.yasarmahomedabbas.paperlesscompanion.ui.documents.TagMark.ALL
import io.github.yasarmahomedabbas.paperlesscompanion.ui.documents.TagMark.NONE
import io.github.yasarmahomedabbas.paperlesscompanion.ui.documents.TagMark.SOME
import io.github.yasarmahomedabbas.paperlesscompanion.ui.documents.bulkTagChange
import io.github.yasarmahomedabbas.paperlesscompanion.ui.documents.tagMarks
import io.github.yasarmahomedabbas.paperlesscompanion.ui.documents.toggled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BulkTagsTest {
    private val tags = (1..3).map { Tag(it, "Tag $it", null, null, false, null) }

    @Test
    fun `with no documents every tag is NONE`() {
        assertEquals(mapOf(1 to NONE, 2 to NONE, 3 to NONE), tagMarks(emptyList(), tags))
    }

    @Test
    fun `marks are ALL, SOME or NONE across the documents`() {
        val documents = listOf(doc(1).copy(tagIds = listOf(1, 2)), doc(2).copy(tagIds = listOf(1)))

        assertEquals(mapOf(1 to ALL, 2 to SOME, 3 to NONE), tagMarks(documents, tags))
    }

    @Test
    fun `toggling goes SOME to ALL, ALL to NONE and NONE to ALL`() {
        assertEquals(ALL, SOME.toggled())
        assertEquals(NONE, ALL.toggled())
        assertEquals(ALL, NONE.toggled())
    }

    @Test
    fun `the change adds what became ALL and removes what became NONE`() {
        val start = mapOf(1 to ALL, 2 to SOME, 3 to NONE, 4 to SOME)
        val now = mapOf(1 to NONE, 2 to ALL, 3 to ALL, 4 to SOME)

        assertEquals(BulkEdit.ModifyTags(add = setOf(2, 3), remove = setOf(1)), bulkTagChange(start, now))
    }

    @Test
    fun `a mixed tag cleared is removed from all`() {
        assertEquals(BulkEdit.ModifyTags(add = emptySet(), remove = setOf(2)), bulkTagChange(mapOf(2 to SOME), mapOf(2 to NONE)))
    }

    @Test
    fun `leaving everything as it was gives no change`() {
        val start = mapOf(1 to ALL, 2 to SOME, 3 to NONE)

        assertNull(bulkTagChange(start, start))
        assertNull(bulkTagChange(start, mapOf(1 to ALL, 2 to SOME, 3 to NONE)))
    }
}
