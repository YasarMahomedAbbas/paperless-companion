package io.github.yasarmahomedabbas.paperlesscompanion.ui

import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Label
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Tag
import io.github.yasarmahomedabbas.paperlesscompanion.ui.edit.chosenFirst
import io.github.yasarmahomedabbas.paperlesscompanion.ui.edit.matching
import org.junit.Assert.assertEquals
import org.junit.Test

class PickerFilterTest {
    private val labels = listOf(Label(1, "Power company", null), Label(2, "Water board", null), Label(3, "Bank", null))
    private fun tag(id: Int, name: String) = Tag(id, name, null, null, false, null)
    private val tags = listOf(tag(1, "Inbox"), tag(2, "Bills"), tag(3, "Car"), tag(4, "Tax"))

    @Test
    fun `matching ignores case and surrounding blanks`() {
        assertEquals(listOf(labels[0]), labels.matching("  POWER ") { it.name })
        assertEquals(listOf(labels[1]), labels.matching("bOaRd") { it.name })
    }

    @Test
    fun `an empty or blank query returns everything`() {
        assertEquals(labels, labels.matching("") { it.name })
        assertEquals(labels, labels.matching("   ") { it.name })
    }

    @Test
    fun `nothing matching gives an empty list`() {
        assertEquals(emptyList<Label>(), labels.matching("gas") { it.name })
    }

    @Test
    fun `chosen tags float to the top in the form's order`() {
        val (chosen, rest) = tags.chosenFirst(listOf(4, 2))

        assertEquals(listOf(tags[3], tags[1]), chosen)
        assertEquals(listOf(tags[0], tags[2]), rest)
    }

    @Test
    fun `a chosen id the server doesn't have is skipped`() {
        val (chosen, rest) = tags.chosenFirst(listOf(9, 1))

        assertEquals(listOf(tags[0]), chosen)
        assertEquals(tags.drop(1), rest)
    }
}
