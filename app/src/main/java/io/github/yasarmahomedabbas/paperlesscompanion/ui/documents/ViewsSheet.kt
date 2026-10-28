package io.github.yasarmahomedabbas.paperlesscompanion.ui.documents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.SavedView
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.AppBottomSheet
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.SectionHeading
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.SingleChoiceRow
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.PaperlessTheme
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.Spacing

/**
 * "Saved views": every view as a single-choice option, the [chosen] one marked. The quick-list views
 * come first in the server's order; the rest follow under "Other views". Choosing one closes the sheet.
 */
@Composable
fun ViewsSheet(views: List<SavedView>, chosen: Int?, onChoose: (SavedView) -> Unit, onDismiss: () -> Unit) {
    AppBottomSheet("Saved views", onDismiss) { ViewOptions(views, chosen, onChoose) }
}

@Composable
private fun ViewOptions(views: List<SavedView>, chosen: Int?, onChoose: (SavedView) -> Unit) {
    val (quick, other) = views.partition { it.quickFilter }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Options(quick, chosen, onChoose)
        if (quick.isNotEmpty() && other.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = Spacing.s8), color = MaterialTheme.colorScheme.outlineVariant)
            SectionHeading("Other views")
        }
        Options(other, chosen, onChoose)
    }
}

@Composable
private fun Options(views: List<SavedView>, chosen: Int?, onChoose: (SavedView) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        views.forEach { view -> SingleChoiceRow(view.name, selected = view.id == chosen, onClick = { onChoose(view) }) }
    }
}

@Preview(name = "Saved views", widthDp = 412)
@Composable
private fun ViewsSheetPreview() = PaperlessTheme {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(Spacing.s12)) {
            ViewOptions(
                listOf(
                    SavedView(1, "Inbox", true, true),
                    SavedView(3, "Tax 2026", false, true),
                    SavedView(2, "This month", true, false),
                    SavedView(4, "Warranties", false, false),
                ),
                chosen = 3,
            ) {}
        }
    }
}
