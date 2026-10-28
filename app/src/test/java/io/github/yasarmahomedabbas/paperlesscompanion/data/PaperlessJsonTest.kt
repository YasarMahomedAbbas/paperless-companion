package io.github.yasarmahomedabbas.paperlesscompanion.data

import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class PaperlessJsonTest {
    private fun json(text: String) = Json.parseToJsonElement(text) as JsonObject

    @Test
    fun `an older datetime created still gives the date`() {
        val document = json("""{"id":1,"created":"2023-05-06T00:00:00+02:00"}""").toDocument()
        assertEquals(LocalDate.of(2023, 5, 6), document.created)
    }

    @Test
    fun `unparseable dates become null, never an exception`() {
        val document = json("""{"id":1,"title":"x","created":"soon","added":"yesterday","modified":"2024-13-45T99:00:00Z"}""").toDocument()
        assertNull(document.created)
        assertNull(document.added)
        assertNull(document.modified)
    }

    @Test
    fun `a missing title is empty and missing lists are empty`() {
        val document = json("""{"id":1}""").toDocument()
        assertEquals("", document.title)
        assertEquals(emptyList<Int>(), document.tagIds)
        assertNull(document.searchHit)
    }

    @Test
    fun `the thumbnail follows the modified time`() {
        val document = json("""{"id":3,"modified":"2024-01-01T00:00:00Z"}""").toDocument()
        assertEquals(3, document.thumbnail.documentId)
        assertEquals(Instant.parse("2024-01-01T00:00:00Z"), document.thumbnail.version)
    }

    @Test(expected = ServerException::class)
    fun `a document without an id is a server error`() {
        json("""{"title":"x"}""").toDocument()
    }

    @Test(expected = ServerException::class)
    fun `a tag without an id is a server error`() {
        json("""{"name":"x"}""").toTag()
    }

    @Test(expected = ServerException::class)
    fun `a label without an id is a server error`() {
        json("""{"name":"x"}""").toLabel()
    }

    @Test
    fun `a search hit without highlights or score`() {
        val hit = searchHit(json("""{"score":null,"rank":0}"""))!!
        assertNull(hit.score)
        assertEquals("", hit.excerpt)
        assertEquals(emptyList<IntRange>(), hit.matches)
        assertNull(searchHit(null))
    }

    // --- highlights ---

    @Test
    fun `plain text has no matches`() {
        assertEquals("just text" to emptyList<IntRange>(), parseHighlights("just text"))
    }

    @Test
    fun `each match span becomes a range in the plain text`() {
        val (text, matches) = parseHighlights("""<span class="match">Power</span> bill for <span class="match">power</span>""")
        assertEquals("Power bill for power", text)
        assertEquals(listOf(0..4, 15..19), matches)
    }

    @Test
    fun `entities are decoded and ranges count the decoded text`() {
        val (text, matches) = parseHighlights("""A &amp; B &lt;x&gt; &quot;q&quot; it&#39;s <span class="match">&amp;co</span> &nbsp;""")
        assertEquals("""A & B <x> "q" it's &co &nbsp;""", text)
        assertEquals(listOf(19..21), matches)
    }

    @Test
    fun `other tags are dropped, nested tags inside a match keep it`() {
        val (text, matches) = parseHighlights("""<b>a</b> <span class="match"><em>bc</em></span> <span>d</span>""")
        assertEquals("a bc d", text)
        assertEquals(listOf(2..3), matches)
    }

    @Test
    fun `a span inside a match does not end it early`() {
        val (text, matches) = parseHighlights("""<span class="match">a<span>b</span>c</span>d""")
        assertEquals("abcd", text)
        assertEquals(listOf(0..2), matches)
    }

    @Test
    fun `an unclosed match runs to the end`() {
        val (text, matches) = parseHighlights("""x <span class="match">yz""")
        assertEquals("x yz", text)
        assertEquals(listOf(2..3), matches)
    }

    @Test
    fun `a cut-off tag is dropped`() {
        assertEquals("x " to emptyList<IntRange>(), parseHighlights("""x <span class="ma"""))
    }

    @Test
    fun `an empty match leaves no range`() {
        assertEquals("ab" to emptyList<IntRange>(), parseHighlights("""a<span class="match"></span>b"""))
    }
}
