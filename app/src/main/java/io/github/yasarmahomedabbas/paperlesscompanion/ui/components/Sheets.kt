package io.github.yasarmahomedabbas.paperlesscompanion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.PaperlessTheme
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.Spacing
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.cardTitle

/** The two sheet shapes of docs/design/layouts.md § Sheet. */
enum class SheetVariant {
    /** Hugs its content from the bottom: Add, Sort. */
    Short,

    /** Opens high and scrolls inside, with an optional pinned action bar: Filters, pickers. */
    List,
}

/**
 * A bottom sheet (components.md § Bottom sheet): `surface`, `r-sheet` top corners, the 36 × 4
 * handle in `border`, the 20/500 title, over the scrim. A [SheetVariant.List] sheet's [content]
 * takes the space between the title and the [actionBar] and scrolls itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    variant: SheetVariant = SheetVariant.Short,
    actionBar: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = MaterialTheme.shapes.large.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp)),
        containerColor = colors.surfaceContainer,
        contentColor = colors.onSurface,
        tonalElevation = 0.dp,
        scrimColor = colors.scrim,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = Spacing.s12, bottom = Spacing.s16)
                    .size(width = 36.dp, height = 4.dp)
                    .background(colors.outline, CircleShape),
            )
        },
    ) {
        Column(if (variant == SheetVariant.List) Modifier.fillMaxHeight(0.85f) else Modifier) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                modifier = Modifier.padding(horizontal = Spacing.gutter).padding(bottom = Spacing.s12).semantics { heading() },
            )
            if (variant == SheetVariant.List) {
                Column(Modifier.weight(1f).fillMaxWidth(), content = content)
            } else {
                Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.s12).padding(bottom = Spacing.actionBottom), content = content)
            }
            if (actionBar != null) ListSheetActionBar(actionBar)
        }
    }
}

/** A list sheet's action bar, pinned to the bottom above a `border-muted` divider. */
@Composable
fun ListSheetActionBar(content: @Composable RowScope.() -> Unit) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.actionTop, bottom = Spacing.s24),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/**
 * An option in the Add sheet (components.md § Option list, action rows): a 48 round icon tile
 * and a two-line label. The [main] option's tile is `primary-tint` with a `primary` icon.
 */
@Composable
fun ActionOptionRow(icon: ImageVector, title: String, subtitle: String, main: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small, color = colors.surfaceContainer) {
        Row(
            Modifier.padding(horizontal = Spacing.s8, vertical = Spacing.s12),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(48.dp).background(if (main) colors.primaryContainer else colors.surfaceContainerHigh, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = if (main) colors.primary else colors.onSurfaceVariant)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.cardTitle, color = colors.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
    }
}

/**
 * A single-choice option (sort, correspondent): the selected one gets `primary-tint` and a
 * `primary` check at the end. [muted] is the "None" row, in `mfg`.
 */
@Composable
fun SingleChoiceRow(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, muted: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) colors.primaryContainer else colors.surfaceContainer)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = Spacing.s16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = if (selected) MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium) else MaterialTheme.typography.bodyLarge,
            color = when {
                selected -> colors.primary
                muted -> colors.onSurfaceVariant
                else -> colors.onSurface
            },
            modifier = Modifier.weight(1f).padding(vertical = Spacing.s12),
        )
        if (selected) Icon(AppIcons.Check, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
    }
}

/** A multiple-choice option (tags): a 22dp checkbox, filled `primary` with an `on-primary` tick when checked. */
@Composable
fun MultiChoiceRow(text: String, checked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val box = RoundedCornerShape(7.dp)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(MaterialTheme.shapes.small)
            .toggleable(value = checked, onValueChange = { onClick() }, role = Role.Checkbox)
            .padding(horizontal = Spacing.s16),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s16),
    ) {
        Box(
            Modifier
                .size(22.dp)
                .then(if (checked) Modifier.background(colors.primary, box) else Modifier.border(2.dp, colors.onSurfaceVariant, box)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(AppIcons.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(16.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface, modifier = Modifier.weight(1f))
    }
}

@Preview(name = "Sheet parts", widthDp = 360)
@Composable
private fun SheetPartsPreview() = PaperlessTheme {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(Spacing.s12)) {
            ActionOptionRow(AppIcons.Scan, "Scan document", "Finds the edges, crops, several pages", main = true, onClick = {})
            ActionOptionRow(AppIcons.Camera, "Take photo", "Works without Google Play services", main = false, onClick = {})
            Spacer(Modifier.size(Spacing.s12))
            SingleChoiceRow("None", selected = false, onClick = {}, muted = true)
            SingleChoiceRow("Newest first", selected = true, onClick = {})
            SingleChoiceRow("Oldest first", selected = false, onClick = {})
            MultiChoiceRow("Bills", checked = true, onClick = {})
            MultiChoiceRow("House", checked = false, onClick = {})
            ListSheetActionBar {
                AppButton("Clear all", {}, variant = ButtonVariant.Text)
                AppButton("Show 23 documents", {}, Modifier.weight(1f))
            }
        }
    }
}
