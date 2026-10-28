package io.github.yasarmahomedabbas.paperlesscompanion.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.SearchHit
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.highlighted
import org.junit.Assert.assertEquals
import org.junit.Test

class HighlightTest {
    private val match = SpanStyle(background = Color.Green)

    private fun spans(hit: SearchHit) = hit.highlighted(match).let { text -> text.text to text.spanStyles.map { it.start until it.end } }

    @Test
    fun `each match is marked over the excerpt`() {
        val (text, ranges) = spans(SearchHit(null, "your rates and taxes", listOf(5..9, 15..19)))

        assertEquals("your rates and taxes", text)
        assertEquals(listOf(5 until 10, 15 until 20), ranges)
    }

    @Test
    fun `a match reaching past the excerpt is cut to it`() {
        assertEquals(listOf(5 until 10), spans(SearchHit(null, "your rates", listOf(5..40))).second)
        assertEquals(listOf(0 until 4), spans(SearchHit(null, "your rates", listOf(-3..3))).second)
    }

    @Test
    fun `a match outside the excerpt or empty is dropped`() {
        assertEquals(emptyList<IntRange>(), spans(SearchHit(null, "rates", listOf(9..12, 3 until 3))).second)
    }

    @Test
    fun `no matches is the plain excerpt`() {
        val text = SearchHit(null, "rates", emptyList()).highlighted(match)

        assertEquals("rates", text.text)
        assertEquals(0, text.spanStyles.size)
    }
}
