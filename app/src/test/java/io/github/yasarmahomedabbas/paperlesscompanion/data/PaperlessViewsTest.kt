package io.github.yasarmahomedabbas.paperlesscompanion.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class PaperlessViewsTest {
    private fun params(vararg rules: Pair<Int, String?>) =
        viewFilter(rules.map { (type, value) -> ViewRule(type, value) }, "created", true).params

    @Test
    fun `the old and new text rules both search text`() {
        assertEquals(mapOf("text" to "bill"), params(19 to "bill"))
        assertEquals(mapOf("text" to "bill"), params(49 to "bill"))
    }

    @Test
    fun `the old and new title rules both search the title`() {
        assertEquals(mapOf("title_search" to "bill"), params(0 to "bill"))
        assertEquals(mapOf("title_search" to "bill"), params(48 to "bill"))
    }

    @Test
    fun `a null value asks for none, and -1 asks for any`() {
        assertEquals(mapOf("correspondent__isnull" to "1"), params(3 to null))
        assertEquals(mapOf("document_type__isnull" to "0"), params(4 to "-1"))
        assertEquals(mapOf("storage_path__id" to "5"), params(25 to "5"))
    }

    @Test
    fun `multi rules join their values in the order given`() {
        assertEquals(mapOf("tags__id__all" to "5,2,9"), params(6 to "5", 6 to "2", 6 to "9"))
        assertEquals(
            mapOf("tags__id__none" to "1", "correspondent__id__in" to "3,4"),
            params(17 to "1", 26 to "3", 26 to "4"),
        )
    }

    @Test
    fun `booleans are 1 for true or 1, and 0 for anything else`() {
        assertEquals(mapOf("is_in_inbox" to "1"), params(5 to "true"))
        assertEquals(mapOf("is_tagged" to "1"), params(7 to "1"))
        assertEquals(mapOf("has_duplicates" to "0"), params(50 to "false"))
        assertEquals(mapOf("owner__isnull" to "0"), params(34 to null))
    }

    @Test
    fun `a single-valued rule is sent as it is, a later one replacing it`() {
        assertEquals(mapOf("created__date__gte" to "2024-01-01"), params(44 to "2024-01-01"))
        assertEquals(mapOf("query" to "tax 2024"), params(20 to "tax 2024"))
        assertEquals(mapOf("mime_type" to "image/png"), params(47 to "application/pdf", 47 to "image/png"))
    }

    @Test
    fun `an unknown rule type and a null value with nowhere to go are dropped`() {
        assertEquals(mapOf("is_in_inbox" to "1"), params(999 to "x", 5 to "true", 2 to null, 6 to null))
    }

    @Test
    fun `the order is the view's field, reversed with a minus`() {
        assertEquals("-added", viewFilter(emptyList(), "added", true).ordering)
        assertEquals("title", viewFilter(emptyList(), "title", false).ordering)
        assertEquals("-created", viewFilter(emptyList(), null, true).ordering)
        assertEquals("created", viewFilter(emptyList(), null, false).ordering)
    }

    @Test
    fun `rules are read from the view's JSON, numbers as their digits`() {
        val view = Json.parseToJsonElement(
            """{"id":1,"sort_field":"title","sort_reverse":false,"filter_rules":[""" +
                """{"rule_type":6,"value":"5"},{"rule_type":6,"value":7},{"rule_type":3,"value":null},{"value":"x"}]}""",
        ) as JsonObject

        assertEquals(listOf(ViewRule(6, "5"), ViewRule(6, "7"), ViewRule(3, null)), view.toViewRules())
        val filter = view.toViewFilter()
        assertEquals(mapOf("tags__id__all" to "5,7", "correspondent__isnull" to "1"), filter.params)
        assertEquals("title", filter.ordering)
    }

    @Test
    fun `a view without a sort field is newest first when reversed`() {
        val view = Json.parseToJsonElement("""{"id":1,"sort_field":null,"sort_reverse":true,"filter_rules":[]}""") as JsonObject
        assertEquals("-created", view.toViewFilter().ordering)
    }
}
