package io.github.yasarmahomedabbas.paperlesscompanion.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.PaperlessTheme
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.Spacing

/** The chip roles of docs/design/components.md § Chips. */
enum class ChipVariant {
    /** Outline, `fg` 500, optional leading icon: Filters, the sort order. */
    Action,

    /** `primary-tint` ground, `primary` text, a check (or its own icon): a chosen option, "Filters · 2". */
    Selected,

    /** Outline, `fg` 400: an option you could choose. */
    Unselected,

    /** Outline with a trailing ×: an applied filter in the bar. */
    Removable,
}

/**
 * A 36-high, fully round chip in one of the house [ChipVariant]s; its touch target is 48.
 * [contentDescription] replaces what TalkBack reads ("Remove filter Bills"). Selected and
 * unselected chips expose their state to TalkBack.
 */
@Composable
fun AppChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ChipVariant = ChipVariant.Action,
    leadingIcon: ImageVector? = null,
    contentDescription: String? = null,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val selected = variant == ChipVariant.Selected
    val container = if (selected) colors.primaryContainer else Color.Transparent
    val content = when {
        !enabled -> colors.onSurfaceVariant
        selected -> colors.primary
        else -> colors.onSurface
    }
    val style = if (variant == ChipVariant.Action || selected) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
    val icon = leadingIcon ?: if (selected) AppIcons.Check else null
    Surface(
        onClick = onClick,
        modifier = modifier.semantics {
            if (variant == ChipVariant.Selected || variant == ChipVariant.Unselected) this.selected = selected
            if (contentDescription != null) this.contentDescription = contentDescription
        },
        enabled = enabled,
        shape = CircleShape,
        color = container,
        contentColor = content,
        border = if (selected) null else BorderStroke(1.dp, colors.outline),
    ) {
        Row(
            Modifier
                .height(36.dp)
                .padding(
                    start = if (icon != null && variant != ChipVariant.Action) 10.dp else 14.dp,
                    end = if (variant == ChipVariant.Removable) 10.dp else 14.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(if (selected && leadingIcon == null) 16.dp else 18.dp))
            Text(text, style = style, maxLines = 1)
            if (variant == ChipVariant.Removable) {
                Icon(AppIcons.Close, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** A row of chips that scrolls sideways (the Documents chip row), on the 20 page gutter. */
@Composable
fun ChipRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@OptIn(ExperimentalLayoutApi::class)
@Preview(name = "Chips", widthDp = 360)
@Composable
private fun ChipsPreview() = PaperlessTheme {
    Surface {
        Column(Modifier.padding(vertical = Spacing.gutter)) {
            ChipRow {
                AppChip("Filters · 2", {}, variant = ChipVariant.Selected, leadingIcon = AppIcons.Filters)
                AppChip("Newest first", {}, leadingIcon = AppIcons.Sort)
                AppChip("Bills", {}, variant = ChipVariant.Removable, contentDescription = "Remove filter Bills")
                AppChip("House", {}, variant = ChipVariant.Removable, contentDescription = "Remove filter House")
            }
            FlowRow(Modifier.padding(Spacing.gutter), horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                AppChip("Bills", {}, variant = ChipVariant.Selected)
                AppChip("Payslips", {}, variant = ChipVariant.Unselected)
                AppChip("Best match", {}, enabled = false)
            }
        }
    }
}
