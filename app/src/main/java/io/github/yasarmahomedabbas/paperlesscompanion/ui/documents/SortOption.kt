package io.github.yasarmahomedabbas.paperlesscompanion.ui.documents

import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentSort

/** The orders the sort sheet offers, as drawn on the design canvas. [NEWEST] is the default. */
enum class SortOption(val label: String, val sort: DocumentSort, val descending: Boolean) {
    NEWEST("Newest first", DocumentSort.CREATED, true),
    OLDEST("Oldest first", DocumentSort.CREATED, false),
    RECENTLY_ADDED("Recently added", DocumentSort.ADDED, true),
    TITLE("Title, A to Z", DocumentSort.TITLE, false),
    CORRESPONDENT("Correspondent, A to Z", DocumentSort.CORRESPONDENT, false),
}

/** The sort chip's label while searching: results come in relevance order whatever the sort. */
const val BEST_MATCH = "Best match"
