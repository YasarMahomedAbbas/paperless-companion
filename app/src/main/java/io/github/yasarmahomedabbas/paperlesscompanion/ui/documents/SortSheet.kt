package io.github.yasarmahomedabbas.paperlesscompanion.ui.documents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.AppBottomSheet
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.SingleChoiceRow
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.PaperlessTheme
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.Spacing

/** The short "Sort by" sheet: one option per [SortOption]; choosing one closes it. */
@Composable
fun SortSheet(selected: SortOption, onSelect: (SortOption) -> Unit, onDismiss: () -> Unit) {
    AppBottomSheet("Sort by", onDismiss) { SortOptions(selected, onSelect) }
}

@Composable
private fun SortOptions(selected: SortOption, onSelect: (SortOption) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SortOption.entries.forEach { option ->
            SingleChoiceRow(option.label, selected = option == selected, onClick = { onSelect(option) })
        }
    }
    Text(
        "\"${SortOption.NEWEST.label}\" goes by the date on the document, not the day it was scanned.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.s16).padding(top = Spacing.s12),
    )
}

@Preview(name = "Sort by", widthDp = 412)
@Composable
private fun SortSheetPreview() = PaperlessTheme {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(Spacing.s12)) { SortOptions(SortOption.NEWEST) {} }
    }
}
