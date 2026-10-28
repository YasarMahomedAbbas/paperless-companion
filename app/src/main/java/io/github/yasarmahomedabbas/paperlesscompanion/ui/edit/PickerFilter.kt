package io.github.yasarmahomedabbas.paperlesscompanion.ui.edit

import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Tag

/** The items whose [name] contains [query], ignoring case and blanks around it; a blank query keeps them all. */
internal fun <T> List<T>.matching(query: String, name: (T) -> String): List<T> {
    val wanted = query.trim()
    if (wanted.isEmpty()) return this
    return filter { name(it).contains(wanted, ignoreCase = true) }
}

/** The [chosen] tags in the form's order, then the rest in the server's order (the sheet draws a divider between). */
internal fun List<Tag>.chosenFirst(chosen: Collection<Int>): Pair<List<Tag>, List<Tag>> {
    val byId = associateBy { it.id }
    val first = chosen.mapNotNull { byId[it] }
    val chosenIds = chosen.toSet()
    return first to filterNot { it.id in chosenIds }
}
