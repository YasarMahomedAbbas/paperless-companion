package io.github.yasarmahomedabbas.paperlesscompanion.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PaperlessSearchTest {
    @Test
    fun `a plain word also matches the words it starts`() {
        assertEquals("(burger OR burger*)", partialWordQuery("burger"))
    }

    @Test
    fun `every plain word is widened and they stay ANDed`() {
        assertEquals("(power OR power*) (bill OR bill*)", partialWordQuery("power bill"))
    }

    @Test
    fun `a one-character word is sent as typed`() {
        assertEquals("a", partialWordQuery("a"))
    }

    @Test
    fun `umlauts, accents and digits count as word characters`() {
        assertEquals("(müll OR müll*)", partialWordQuery("müll"))
        assertEquals("(café OR café*)", partialWordQuery("café"))
        assertEquals("(1312 OR 1312*)", partialWordQuery("1312"))
    }

    @Test
    fun `a token with punctuation is sent as typed`() {
        assertEquals("A-1312/B", partialWordQuery("A-1312/B"))
    }

    @Test
    fun `wildcards and field terms are sent as typed`() {
        assertEquals("burger*", partialWordQuery("burger*"))
        assertEquals("bu?ger", partialWordQuery("bu?ger"))
        assertEquals("tag:unpaid", partialWordQuery("tag:unpaid"))
    }

    @Test
    fun `a quoted phrase is sent as typed, and words outside it are widened`() {
        assertEquals("\"quick brown\" (fox OR fox*)", partialWordQuery("\"quick brown\" fox"))
        assertEquals("title:\"power bill\"", partialWordQuery("title:\"power bill\""))
    }

    @Test
    fun `operators are kept and the words around them widened`() {
        assertEquals("(invoice OR invoice*) NOT (draft OR draft*)", partialWordQuery("invoice NOT draft"))
        assertEquals("(gas OR gas*) OR (power OR power*)", partialWordQuery("gas OR power"))
        assertEquals("(gas OR gas*) AND (power OR power*)", partialWordQuery("gas AND power"))
    }

    @Test
    fun `an unclosed quote leaves the rest as typed`() {
        assertEquals("(bill OR bill*) \"power  bill", partialWordQuery("bill \"power  bill"))
    }

    @Test
    fun `parentheses are sent as typed`() {
        assertEquals("(gas OR power)", partialWordQuery("(gas OR power)"))
        assertEquals("(gas", partialWordQuery("(gas"))
    }

    @Test
    fun `extra whitespace collapses to one space`() {
        assertEquals("(power OR power*) (bill OR bill*)", partialWordQuery("  power \t\n bill  "))
    }
}
