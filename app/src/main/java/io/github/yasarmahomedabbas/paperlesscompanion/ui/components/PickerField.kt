package io.github.yasarmahomedabbas.paperlesscompanion.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.PaperlessTheme
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.Spacing

/** What sits at a [PickerField]'s end: a chevron to a picker sheet, or a calendar for the date picker. */
enum class PickerFieldVariant { Sheet, Date }

/**
 * A field that is a button (components.md § Picker field): an optional [label] above (14/500
 * `mfg`), then a 52-high `field` box, `r-ctl`, an optional 20dp icon, the current [value] (or
 * [placeholder] in `mfg`) and a chevron (a calendar for [PickerFieldVariant.Date]). With [tags]
 * it shows them as `surface` pills that wrap instead of a value. Tapping it opens the matching
 * picker; TalkBack reads it as one button, "<label>, <value>".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PickerField(
    value: String?,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    label: String? = null,
    tags: List<String>? = null,
    variant: PickerFieldVariant = PickerFieldVariant.Sheet,
) {
    val colors = MaterialTheme.colorScheme
    val shown = if (tags.isNullOrEmpty()) value else tags.joinToString(", ")
    val spoken = listOfNotNull(label, shown ?: placeholder).joinToString(", ")
    Column(
        modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = spoken
                role = if (variant == PickerFieldVariant.Date) Role.Button else Role.DropdownList
                onClick { onClick(); true }
            },
        verticalArrangement = Arrangement.spacedBy(Spacing.labelGap),
    ) {
        if (label != null) Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = MaterialTheme.shapes.small,
            color = colors.surfaceContainerHigh,
        ) {
            Row(
                Modifier.padding(start = Spacing.s16, end = Spacing.s12),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
            ) {
                if (icon != null) Icon(icon, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                if (!tags.isNullOrEmpty()) {
                    FlowRow(
                        Modifier.weight(1f).padding(vertical = Spacing.s12),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        tags.forEach { TagPill(it, inDetails = true, onRaised = true) }
                    }
                } else {
                    Text(
                        value ?: placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (value == null) colors.onSurfaceVariant else colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                Icon(
                    if (variant == PickerFieldVariant.Date) AppIcons.Calendar else AppIcons.ChevronRight,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Preview(name = "Picker fields", widthDp = 360)
@Composable
private fun PickerFieldPreview() = PaperlessTheme {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.s12)) {
            PickerField(null, "Any correspondent", {}, icon = AppIcons.Person)
            PickerField("City Power", "Any correspondent", {}, icon = AppIcons.Person)
            PickerField("3 October 2026", "None", {}, label = "Date", variant = PickerFieldVariant.Date)
            PickerField(null, "None", {}, label = "Tags", tags = listOf("Bills", "House", "Tax", "Paid", "Electricity"))
        }
    }
}
