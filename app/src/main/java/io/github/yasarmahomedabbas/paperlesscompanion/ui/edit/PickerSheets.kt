package io.github.yasarmahomedabbas.paperlesscompanion.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Label
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Tag
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.AppBottomSheet
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.AppButton
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.AppIcons
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.ListSheetActionBar
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.MultiChoiceRow
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.SearchField
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.SearchFieldVariant
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.SheetVariant
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.SingleChoiceRow
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.SkeletonBlock
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.StatusCard
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.StatusTone
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.PaperlessTheme
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.Spacing

/**
 * Where a picker sheet's choices stand: [Ready] with them, still [Loading], or [Failed] to load
 * (the sheet offers Try again).
 */
sealed interface PickerChoices<out T> {
    data object Loading : PickerChoices<Nothing>
    data object Failed : PickerChoices<Nothing>
    data class Ready<T>(val items: List<T>) : PickerChoices<T>
}

/**
 * A single-choice list sheet for a correspondent, document type or storage path (components.md
 * § Option list): an in-sheet search, "None" first in `mfg`, and a tap chooses and closes.
 * [noun] names one ("correspondent") for the no-match line, which links to the web UI, where
 * new ones are made.
 */
@Composable
fun LabelPickerSheet(
    title: String,
    noun: String,
    choices: PickerChoices<Label>,
    selectedId: Int?,
    onPick: (Int?) -> Unit,
    onOpenPaperless: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    var find by rememberSaveable { mutableStateOf("") }
    AppBottomSheet(title, onDismiss, variant = SheetVariant.List) {
        PickerBody(choices, onRetry) { labels ->
            FindField(find, { find = it }, "Find a $noun")
            val shown = labels.matching(find) { it.name }
            LazyColumn(Modifier.selectableGroup().padding(horizontal = Spacing.s8)) {
                if (find.isBlank()) {
                    item(key = "none") { SingleChoiceRow("None", selected = selectedId == null, onClick = { onPick(null) }, muted = true) }
                }
                items(shown, key = { it.id }) { label ->
                    SingleChoiceRow(label.name, selected = label.id == selectedId, onClick = { onPick(label.id) })
                }
                if (shown.isEmpty()) noMatch(noun, find, onOpenPaperless)
            }
        }
    }
}

/**
 * The tags picker (components.md § Option list, multiple choice): a draft of [chosen] that only
 * reaches the form on Done, the chosen tags above a divider, then the rest; the pinned bar says
 * how many are chosen.
 */
@Composable
fun TagPickerSheet(
    choices: PickerChoices<Tag>,
    chosen: List<Int>,
    onDone: (List<Int>) -> Unit,
    onOpenPaperless: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf(chosen) }
    // Which tags float to the top is fixed when the sheet opens, so a tick doesn't move the row under the finger.
    val pinned by rememberSaveable { mutableStateOf(chosen) }
    var find by rememberSaveable { mutableStateOf("") }
    AppBottomSheet(
        "Tags",
        onDismiss,
        variant = SheetVariant.List,
        actionBar = if (choices is PickerChoices.Ready) {
            {
                Text(
                    "${draft.size} chosen",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                AppButton("Done", { onDone(draft) })
            }
        } else {
            null
        },
    ) {
        PickerBody(choices, onRetry) { tags ->
            FindField(find, { find = it }, "Find a tag")
            val (top, rest) = tags.matching(find) { it.name }.chosenFirst(pinned)
            val toggle = { id: Int -> draft = if (id in draft) draft - id else draft + id }
            LazyColumn(Modifier.padding(horizontal = Spacing.s8)) {
                items(top, key = { "top-${it.id}" }) { tag -> MultiChoiceRow(tag.name, tag.id in draft, { toggle(tag.id) }) }
                if (top.isNotEmpty() && rest.isNotEmpty()) {
                    item(key = "divider") {
                        HorizontalDivider(Modifier.padding(vertical = Spacing.s8), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                items(rest, key = { it.id }) { tag -> MultiChoiceRow(tag.name, tag.id in draft, { toggle(tag.id) }) }
                if (top.isEmpty() && rest.isEmpty()) noMatch("tag", find, onOpenPaperless)
            }
        }
    }
}

/** The search field at the top of a picker sheet. */
@Composable
internal fun FindField(value: String, onChange: (String) -> Unit, placeholder: String) {
    SearchField(
        value,
        onChange,
        Modifier.padding(horizontal = Spacing.gutter).padding(bottom = Spacing.s8),
        placeholder = placeholder,
        variant = SearchFieldVariant.Sheet,
    )
}

/** Draws [ready] once the choices are in; until then a skeleton, or the load-failed card with Try again. */
@Composable
internal fun <T> PickerBody(choices: PickerChoices<T>, onRetry: () -> Unit, ready: @Composable (List<T>) -> Unit) {
    when (choices) {
        is PickerChoices.Ready -> ready(choices.items)
        PickerChoices.Loading -> PickerSkeleton()
        PickerChoices.Failed -> StatusCard(
            AppIcons.WifiOff,
            StatusTone.Unreachable,
            "Couldn't load the choices",
            "Check you're on the home network or the VPN, then try again.",
            Modifier.padding(vertical = Spacing.s16),
            actionLabel = "Try again",
            onAction = onRetry,
            inSheet = true,
        )
    }
}

@Composable
private fun PickerSkeleton() {
    Column(Modifier.padding(horizontal = Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.s12)) {
        SkeletonBlock(Modifier.fillMaxWidth().height(Spacing.s40 + Spacing.s8))
        repeat(5) { SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(Spacing.s24)) }
    }
}

/** "No tag called 'warranty'" and Open Paperless, where new ones are made. */
internal fun LazyListScope.noMatch(noun: String, find: String, onOpenPaperless: () -> Unit) {
    item(key = "no-match") {
        StatusCard(
            AppIcons.Search,
            StatusTone.Empty,
            if (find.isBlank()) "No ${noun}s yet" else "No $noun called '${find.trim()}'",
            "New ${noun}s are made in Paperless's web interface.",
            Modifier.padding(vertical = Spacing.s16),
            actionLabel = "Open Paperless",
            onAction = onOpenPaperless,
            inSheet = true,
        )
    }
}

private val previewTags = listOf("Bills", "House", "Payslips", "Tax", "Medical").mapIndexed { i, name -> Tag(i + 1, name, null, null, false, null) }

@Preview(name = "Tag picker — body", widthDp = 412, heightDp = 600)
@Composable
private fun TagPickerPreview() = PaperlessTheme {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column {
            val (top, rest) = previewTags.chosenFirst(listOf(2, 1))
            Column(Modifier.padding(horizontal = Spacing.s8)) {
                top.forEach { MultiChoiceRow(it.name, true, {}) }
                HorizontalDivider(Modifier.padding(vertical = Spacing.s8), color = MaterialTheme.colorScheme.outlineVariant)
                rest.forEach { MultiChoiceRow(it.name, false, {}) }
            }
            ListSheetActionBar {
                Text("2 chosen", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                AppButton("Done", {})
            }
        }
    }
}

@Preview(name = "Picker — no match", widthDp = 412)
@Composable
private fun NoMatchPreview() = PaperlessTheme {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        LazyColumn { noMatch("tag", "warranty") {} }
    }
}

@Preview(name = "Picker — load failed", widthDp = 412)
@Composable
private fun PickerFailedPreview() = PaperlessTheme {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column { PickerBody<Tag>(PickerChoices.Failed, {}) {} }
    }
}

@Preview(name = "Picker — loading", widthDp = 412)
@Composable
private fun PickerLoadingPreview() = PaperlessTheme {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column { PickerBody<Tag>(PickerChoices.Loading, {}) {} }
    }
}
