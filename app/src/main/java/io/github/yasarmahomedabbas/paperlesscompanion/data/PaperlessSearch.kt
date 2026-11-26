package io.github.yasarmahomedabbas.paperlesscompanion.data

// Typed search text, turned into the full-text `query` Paperless parses.

/**
 * [text] as a Paperless `query` in which each plain word also matches the longer words it starts:
 * `burger` becomes `(burger OR burger*)`, so "Burgerservicenummer" is found while it is still being typed.
 *
 * Paperless (Whoosh on 2.x, Tantivy's whoosh-compatible parser on 3.x) scores a wildcard term flat, without
 * BM25, so the exact word is kept beside the prefix: a document holding the word itself still ranks first.
 * Words stay ANDed, as Paperless does by default. On a server whose search language has a stemmer, a prefix
 * that runs past the stem can find nothing (`universit*` misses "university", indexed as `univers`); the
 * exact half matches again once the whole word is typed.
 *
 * Only words of two or more letters or digits are widened (`a*` would match nearly everything). English stop
 * words (`of`, `for`, `the`, ...) are sent as typed too: Paperless 2.x drops them from the index and from a plain
 * query, but keeps a wildcard, so `of*` would demand a word starting with "of" and lose "bill of lading". A token
 * carrying any syntax (quotes, `field:value`, wildcards, brackets, parentheses, punctuation such as
 * `A-1312/B`) and the operators `AND`, `OR` and `NOT` are sent as typed. A double-quoted run is one token,
 * and an unclosed quote runs to the end. Tokens are joined with single spaces.
 */
internal fun partialWordQuery(text: String): String =
    tokens(text).joinToString(" ") { token ->
        if (PLAIN_WORD.matches(token) && token !in OPERATORS && token.lowercase() !in STOP_WORDS) {
            "($token OR $token*)"
        } else {
            token
        }
    }

/** [text] split on whitespace, except inside double quotes. */
private fun tokens(text: String): List<String> {
    val tokens = mutableListOf<String>()
    val current = StringBuilder()
    var quoted = false
    for (c in text) {
        when {
            c == '"' -> {
                quoted = !quoted
                current.append(c)
            }
            c.isWhitespace() && !quoted -> {
                if (current.isNotEmpty()) tokens += current.toString()
                current.clear()
            }
            else -> current.append(c)
        }
    }
    if (current.isNotEmpty()) tokens += current.toString()
    return tokens
}

private val PLAIN_WORD = Regex("""^[\p{L}\p{N}]{2,}$""")

private val OPERATORS = setOf("AND", "OR", "NOT")

/** Whoosh's English stop list, which Paperless 2.x indexes with: a word on it is never in the index. */
private val STOP_WORDS = setOf(
    "a", "an", "and", "are", "as", "at", "be", "by", "can", "for", "from", "have", "if", "in", "is", "it", "may",
    "not", "of", "on", "or", "tbd", "that", "the", "this", "to", "us", "we", "when", "will", "with", "yet", "you",
    "your",
)
