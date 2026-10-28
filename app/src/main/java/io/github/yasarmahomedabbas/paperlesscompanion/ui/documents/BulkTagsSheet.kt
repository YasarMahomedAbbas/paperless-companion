package io.github.yasarmahomedabbas.paperlesscompanion.ui.documents

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.tooling.preview.Preview
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Tag
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.AppBottomSheet
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.AppButton
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.ListSheetActionBar
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.MixedChoiceRow
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.SheetVariant
import io.github.yasarmahomedabbas.paperlesscompanion.ui.edit.FindField
import io.github.yasarmahomedabbas.paperlesscompanion.ui.edit.PickerBody
import io.github.yasarmahomedabbas.paperlesscompanion.ui.edit.PickerChoices
import io.github.yasarmahomedabbas.paperlesscompanion.ui.edit.matching
import io.github.yasarmahomedabbas.paperlesscompanion.ui.edit.noMatch
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.PaperlessTheme
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.Spacing

/**
 * The bulk Tags sheet: each tag's checkbox says whether all, some or none of the selected
 * documents carry it ([start]). Taps change a draft; Done hands both to [onDone], which sends only
 * the tags that changed.
 */
@Composable
fun BulkTagsSheet(
    choices: PickerChoices<Tag>,
    start: Map<Int, TagMark>,
    count: Int,
    onDone: (start: Map<Int, TagMark>, now: Map<Int, TagMark>) -> Unit,
    onOpenPaperless: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    var marks by remember(start) { mutableStateOf(start) }
    var find by rememberSaveable { mutableStateOf("") }
    AppBottomSheet(
        if (count == 1) "Tags for 1 document" else "Tags for $count documents",
        onDismiss,
        variant = SheetVariant.List,
        actionBar = if (choices is PickerChoices.Ready) {
            {
                Text(
                    changedLine(start, marks),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                AppButton("Done", { onDone(start, marks) })
            }
        } else {
            null
        },
    ) {
        PickerBody(choices, onRetry) { tags ->
            FindField(find, { find = it }, "Find a tag")
            val shown = tags.matching(find) { it.name }
            LazyColumn(Modifier.padding(horizontal = Spacing.s8)) {
                items(shown, key = { it.id }) { tag ->
                    val mark = marks[tag.id] ?: TagMark.NONE
                    MixedChoiceRow(tag.name, mark.toggleableState(), { marks = marks + (tag.id to mark.toggled()) })
                }
                if (shown.isEmpty()) noMatch("tag", find, onOpenPaperless)
            }
        }
    }
}

internal fun TagMark.toggleableState(): ToggleableState = when (this) {
    TagMark.ALL -> ToggleableState.On
    TagMark.SOME -> ToggleableState.Indeterminate
    TagMark.NONE -> ToggleableState.Off
}

private fun changedLine(start: Map<Int, TagMark>, now: Map<Int, TagMark>): String {
    val changed = now.count { (id, mark) -> start[id] != mark }
    return when (changed) {
        0 -> "No changes"
        1 -> "1 tag changed"
        else -> "$changed tags changed"
    }
}

@Preview(name = "Bulk tags — body", widthDp = 412)
@Composable
private fun BulkTagsPreview() = PaperlessTheme {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column {
            Column(Modifier.padding(horizontal = Spacing.s8)) {
                MixedChoiceRow("Bills", ToggleableState.On, {})
                MixedChoiceRow("House", ToggleableState.Indeterminate, {})
                MixedChoiceRow("Tax", ToggleableState.Off, {})
            }
            ListSheetActionBar {
                Text("1 tag changed", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                AppButton("Done", {})
            }
        }
    }
}
