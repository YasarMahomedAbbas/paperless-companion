package io.github.yasarmahomedabbas.paperlesscompanion.ui.documents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Label
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Labels
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Tag
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.AppBottomSheet
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.AppButton
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.AppChip
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.AppIcons
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.ButtonVariant
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.ChipVariant
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.ListSheetActionBar
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.PickerField
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.SearchField
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.SearchFieldVariant
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.SheetVariant
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.SingleChoiceRow
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.StatusCard
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.StatusTone
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.PaperlessTheme
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.Spacing

/**
 * The "Filters" list sheet (the canvas's "Filters — sheet" board). It edits a draft of the
 * applied filters: tags (all of them must match), the correspondent through its own picker
 * sheet, and the document type. Every change asks [onPreview] for the count on the main button;
 * Show applies the draft. Without [labels] (they failed to load) it offers Try again.
 */
@Composable
fun FiltersSheet(
    applied: DocumentFilters,
    labels: Labels?,
    previewCount: Int?,
    onPreview: (DocumentFilters) -> Unit,
    onApply: (DocumentFilters) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    var tagIds by rememberSaveable { mutableStateOf(applied.tagIds.toList()) }
    var correspondentId by rememberSaveable { mutableStateOf(applied.correspondentId) }
    var documentTypeId by rememberSaveable { mutableStateOf(applied.documentTypeId) }
    val draft = DocumentFilters(tagIds.toSet(), correspondentId, documentTypeId)
    var pickingCorrespondent by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(draft, labels != null) { if (labels != null) onPreview(draft) }

    AppBottomSheet(
        "Filters",
        onDismiss,
        variant = SheetVariant.List,
        actionBar = if (labels == null) {
            null
        } else {
            {
                AppButton(
                    "Clear all",
                    {
                        tagIds = emptyList()
                        correspondentId = null
                        documentTypeId = null
                    },
                    variant = ButtonVariant.Text,
                )
                AppButton(showLabel(previewCount), { onApply(draft) }, Modifier.weight(1f))
            }
        },
    ) {
        if (labels == null) {
            LabelsFailed(onRetry)
        } else {
            FiltersBody(
                draft,
                labels,
                onToggleTag = { id -> tagIds = if (id in tagIds) tagIds - id else tagIds + id },
                onPickCorrespondent = { pickingCorrespondent = true },
                onDocumentType = { documentTypeId = it },
            )
        }
    }

    if (pickingCorrespondent && labels != null) {
        CorrespondentSheet(
            labels.correspondents,
            selected = correspondentId,
            onSelect = {
                correspondentId = it
                pickingCorrespondent = false
            },
            onDismiss = { pickingCorrespondent = false },
        )
    }
}

private fun showLabel(count: Int?) = when (count) {
    null -> "Show documents"
    1 -> "Show 1 document"
    else -> "Show $count documents"
}

@Composable
private fun LabelsFailed(onRetry: () -> Unit) {
    StatusCard(
        AppIcons.WifiOff,
        StatusTone.Unreachable,
        "Couldn't load your filters",
        "Check you're on the home network or the VPN, then try again.",
        Modifier.padding(vertical = Spacing.s16),
        actionLabel = "Try again",
        onAction = onRetry,
        inSheet = true,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FiltersBody(
    draft: DocumentFilters,
    labels: Labels,
    onToggleTag: (Int) -> Unit,
    onPickCorrespondent: () -> Unit,
    onDocumentType: (Int?) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter)
            .padding(bottom = Spacing.s16),
    ) {
        Heading("Tags")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
            // The inbox tag first: it's the one most often filtered on.
            labels.tags.sortedByDescending { it.isInbox }.forEach { tag ->
                val on = tag.id in draft.tagIds
                AppChip(tag.name, { onToggleTag(tag.id) }, variant = if (on) ChipVariant.Selected else ChipVariant.Unselected)
            }
        }
        Heading("Correspondent", Modifier.padding(top = Spacing.s20))
        PickerField(
            labels.correspondents.firstOrNull { it.id == draft.correspondentId }?.name,
            "Any correspondent",
            onPickCorrespondent,
            icon = AppIcons.Person,
        )
        Heading("Document type", Modifier.padding(top = Spacing.s20))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
            val any = draft.documentTypeId == null
            AppChip("Any", { onDocumentType(null) }, variant = if (any) ChipVariant.Selected else ChipVariant.Unselected)
            labels.documentTypes.forEach { type ->
                val on = type.id == draft.documentTypeId
                AppChip(type.name, { onDocumentType(type.id) }, variant = if (on) ChipVariant.Selected else ChipVariant.Unselected)
            }
        }
    }
}

@Composable
private fun Heading(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(bottom = Spacing.s8).semantics { heading() },
    )
}

/** The correspondent picker: a list sheet with a search field, "Any correspondent" first. */
@Composable
private fun CorrespondentSheet(correspondents: List<Label>, selected: Int?, onSelect: (Int?) -> Unit, onDismiss: () -> Unit) {
    var find by remember { mutableStateOf("") }
    val shown = correspondents.filter { it.name.contains(find.trim(), ignoreCase = true) }.sortedBy { it.name.lowercase() }
    AppBottomSheet("Correspondent", onDismiss, variant = SheetVariant.List) {
        SearchField(
            find,
            { find = it },
            Modifier.padding(horizontal = Spacing.gutter).padding(bottom = Spacing.s8),
            placeholder = "Find a correspondent",
            variant = SearchFieldVariant.Sheet,
        )
        LazyColumn(Modifier.selectableGroup().padding(horizontal = Spacing.s8)) {
            if (find.isBlank()) {
                item(key = "any") { SingleChoiceRow("Any correspondent", selected = selected == null, onClick = { onSelect(null) }, muted = true) }
            }
            items(shown, key = { it.id }) { label ->
                SingleChoiceRow(label.name, selected = label.id == selected, onClick = { onSelect(label.id) })
            }
            if (shown.isEmpty()) {
                item(key = "none") {
                    StatusCard(
                        AppIcons.Search,
                        StatusTone.Empty,
                        "No correspondent called \"${find.trim()}\"",
                        "New correspondents are made in Paperless's web interface.",
                        Modifier.padding(vertical = Spacing.s16),
                        inSheet = true,
                    )
                }
            }
        }
    }
}

private val previewLabels = Labels(
    tags = listOf("Bills", "House", "Payslips", "Tax", "Medical", "Car").mapIndexed { i, name -> Tag(i + 1, name, null, null, false, null) },
    correspondents = listOf(Label(1, "City Power", null), Label(2, "Acme Logistics", null)),
    documentTypes = listOf("Invoice", "Statement", "Payslip", "Letter", "Contract").mapIndexed { i, name -> Label(i + 1, name, null) },
    storagePaths = emptyList(),
)

@Preview(name = "Filters", widthDp = 412, heightDp = 760)
@Composable
private fun FiltersPreview() = PaperlessTheme {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column {
            FiltersBody(DocumentFilters(tagIds = setOf(1, 2)), previewLabels, {}, {}, {})
            ListSheetActionBar {
                AppButton("Clear all", {}, variant = ButtonVariant.Text)
                AppButton(showLabel(23), {}, Modifier.weight(1f))
            }
        }
    }
}

@Preview(name = "Filters — labels failed", widthDp = 412)
@Composable
private fun FiltersFailedPreview() = PaperlessTheme {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) { Column { LabelsFailed {} } }
}
