package io.github.yasarmahomedabbas.paperlesscompanion.ui.documents

import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.BulkEdit
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Document
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Tag

/** Whether every, some or none of the selected documents carry a tag (the bulk sheet's tri-state checkbox). */
enum class TagMark { ALL, SOME, NONE }

/** Each tag's mark across [documents]; with no documents every tag is NONE. */
internal fun tagMarks(documents: List<Document>, tags: List<Tag>): Map<Int, TagMark> = tags.associate { tag ->
    val carrying = documents.count { tag.id in it.tagIds }
    tag.id to when {
        carrying == 0 -> TagMark.NONE
        carrying == documents.size -> TagMark.ALL
        else -> TagMark.SOME
    }
}

/** A tap on the checkbox: a mixed one becomes ALL, ALL becomes NONE, NONE becomes ALL. */
internal fun TagMark.toggled(): TagMark = if (this == TagMark.ALL) TagMark.NONE else TagMark.ALL

/** Adds the tags that became ALL and removes those that became NONE; null when nothing changed. */
internal fun bulkTagChange(start: Map<Int, TagMark>, now: Map<Int, TagMark>): BulkEdit.ModifyTags? {
    val add = now.filter { (id, mark) -> mark == TagMark.ALL && start[id] != TagMark.ALL }.keys
    val remove = now.filter { (id, mark) -> mark == TagMark.NONE && start[id] != TagMark.NONE }.keys
    return if (add.isEmpty() && remove.isEmpty()) null else BulkEdit.ModifyTags(add, remove)
}
