package io.github.yasarmahomedabbas.paperlesscompanion.data

import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Document
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Label
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.SearchHit
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException.Kind
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Tag
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

// Paperless's JSON shapes, turned into domain models. The document fields are the same on API v9 and v10.

internal fun JsonObject.toDocument(): Document = Document(
    id = int("id") ?: throw ServerException(Kind.SERVER, "Paperless sent a document without an id"),
    title = string("title").orEmpty(),
    // A date since API v9; older servers sent a datetime, whose first ten characters are the date.
    created = string("created")?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    added = instant("added"),
    modified = instant("modified"),
    correspondentId = int("correspondent"),
    documentTypeId = int("document_type"),
    storagePathId = int("storage_path"),
    tagIds = intList("tags"),
    archiveSerialNumber = int("archive_serial_number"),
    originalFileName = string("original_file_name"),
    pageCount = int("page_count"),
    mimeType = string("mime_type"),
    content = string("content"),
    searchHit = searchHit(this["__search_hit__"]),
)

internal fun JsonObject.toTag() = Tag(
    id = int("id") ?: throw ServerException(Kind.SERVER, "Paperless sent a tag without an id"),
    name = string("name").orEmpty(),
    color = string("color"),
    textColor = string("text_color"),
    isInbox = (this["is_inbox_tag"] as? JsonPrimitive)?.booleanOrNull ?: false,
    documentCount = int("document_count"),
)

internal fun JsonObject.toLabel() = Label(
    id = int("id") ?: throw ServerException(Kind.SERVER, "Paperless sent a label without an id"),
    name = string("name").orEmpty(),
    documentCount = int("document_count"),
)

private fun JsonObject.instant(key: String): Instant? =
    string(key)?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }

/** `__search_hit__ {score, highlights, rank}` from a full-text search, or null outside one. */
internal fun searchHit(json: JsonElement?): SearchHit? {
    val hit = json as? JsonObject ?: return null
    val (excerpt, matches) = parseHighlights(hit.string("highlights").orEmpty())
    val score = (hit["score"] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.doubleOrNull
    return SearchHit(score, excerpt, matches)
}

private val ENTITIES = mapOf("&amp;" to '&', "&lt;" to '<', "&gt;" to '>', "&quot;" to '"', "&#39;" to '\'')
private val MATCH_CLASS = Regex("""class\s*=\s*["']?[^"'>]*\bmatch\b""")

/**
 * Paperless's highlight HTML as plain text plus the ranges inside `<span class="match">`. Every tag is
 * dropped, the common entities are decoded, a span left open runs to the end and a cut-off tag is dropped.
 */
internal fun parseHighlights(html: String): Pair<String, List<IntRange>> {
    val text = StringBuilder()
    val matches = mutableListOf<IntRange>()
    val spans = ArrayDeque<Boolean>() // per open span: is it a match?
    var matchStart = -1
    fun inMatch() = spans.any { it }
    fun update(wasInMatch: Boolean) {
        val now = inMatch()
        if (!wasInMatch && now) matchStart = text.length
        if (wasInMatch && !now && text.length > matchStart) matches += matchStart until text.length
    }
    var i = 0
    while (i < html.length) {
        val c = html[i]
        when {
            c == '<' -> {
                val end = html.indexOf('>', i)
                if (end < 0) break
                val tag = html.substring(i + 1, end).trim()
                val was = inMatch()
                when {
                    tag.startsWith("/span", ignoreCase = true) -> spans.removeLastOrNull()
                    tag.startsWith("span", ignoreCase = true) && !tag.endsWith("/") ->
                        spans.addLast(MATCH_CLASS.containsMatchIn(tag))
                }
                update(was)
                i = end + 1
            }
            c == '&' -> {
                val entity = ENTITIES.entries.firstOrNull { html.startsWith(it.key, i) }
                if (entity != null) {
                    text.append(entity.value)
                    i += entity.key.length
                } else {
                    text.append(c)
                    i++
                }
            }
            else -> {
                text.append(c)
                i++
            }
        }
    }
    if (inMatch() && text.length > matchStart) matches += matchStart until text.length
    return text.toString() to matches
}
