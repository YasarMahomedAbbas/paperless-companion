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

    // --- download file names ---

    @Test
    fun `the extended name wins and is percent-decoded`() {
        assertEquals(
            "Rechnung März+1.pdf",
            fileName("""attachment; filename="Rechnung Marz.pdf"; filename*=utf-8''Rechnung%20M%C3%A4rz+1.pdf""", "document-1"),
        )
        assertEquals("a b.pdf", fileName("""inline; filename*=UTF-8''a%20b.pdf""", "document-1"))
    }

    @Test
    fun `a quoted or bare name is read`() {
        assertEquals("bill 2024.pdf", fileName("""attachment; filename="bill 2024.pdf"""", "document-1"))
        assertEquals("say \"hi\".pdf", fileName("""attachment; filename="say \"hi\".pdf"""", "document-1"))
        assertEquals("bill.pdf", fileName("attachment; filename=bill.pdf; size=3", "document-1"))
    }

    @Test
    fun `a broken extended name falls back to the plain one`() {
        assertEquals("plain.pdf", fileName("""attachment; filename*=nope''x%ZZ.pdf; filename="plain.pdf"""", "document-1"))
    }

    @Test
    fun `no name gives the fallback`() {
        assertEquals("document-7.pdf", fileName(null, "document-7.pdf"))
        assertEquals("document-7", fileName("attachment", "document-7"))
        assertEquals("document-7", fileName("""attachment; filename="""""", "document-7"))
    }

    @Test
    fun `path separators, dot-dot and control characters never reach the name`() {
        assertEquals("__etc_passwd", fileName("""attachment; filename="../../etc/passwd"""", "d"))
        assertEquals("_etc_passwd", fileName("""attachment; filename*=UTF-8''%2Fetc%2Fpasswd""", "d"))
        assertEquals("__x.pdf", fileName("""attachment; filename="../..\\x.pdf"""", "d"))
        assertEquals("ab.pdf", fileName("attachment; filename=\"a\u0000\nb.pdf\"", "d"))
        assertEquals("d", fileName("""attachment; filename="..""""", "d"))
        assertEquals("hidden", fileName("""attachment; filename=".hidden"""", "d"))
    }

    @Test
    fun `a note's author is the full name, else the username, else nobody`() {
        assertEquals("Ann", json("""{"id":1,"user":{"username":"ann","first_name":"Ann","last_name":""}}""").toNote().author)
        assertEquals("ann", json("""{"id":1,"user":{"username":"ann","first_name":" ","last_name":null}}""").toNote().author)
        assertNull(json("""{"id":1,"user":{"username":""}}""").toNote().author)
        assertNull(json("""{"id":1,"user":7}""").toNote().author)
        assertNull(json("""{"id":1}""").toNote().author)
    }

    @Test
    fun `a note without text or a readable date is still a note`() {
        val note = json("""{"id":4,"created":"soon"}""").toNote()
        assertEquals("", note.text)
        assertNull(note.created)
    }

    @Test(expected = ServerException::class)
    fun `a note without an id is refused`() {
        json("""{"note":"x"}""").toNote()
    }
}
