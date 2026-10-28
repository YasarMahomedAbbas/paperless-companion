package io.github.yasarmahomedabbas.paperlesscompanion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.PaperlessTheme
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.Spacing
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.cardTitle

/** How a [SettingsRow] ends. */
sealed interface SettingsTrailing {
    /** Opens another screen. */
    data object Chevron : SettingsTrailing

    /** A switch; the whole row toggles it. */
    data class Switch(val checked: Boolean, val onChange: (Boolean) -> Unit) : SettingsTrailing

    data object None : SettingsTrailing
}

/** A `surface` card of settings rows (components.md § Settings row); put [SettingsDivider] between rows. */
@Composable
fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer),
        content = content,
    )
}

/** The `border-muted` line between rows in one card, inset 18. */
@Composable
fun SettingsDivider() = HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.outlineVariant)

/**
 * One row: an optional 44 icon tile, the 16/500 [label] and an optional `mfg` [secondary]
 * line, then a status [pill] ("0.4.0 ready") and the [trailing] part. With [onClick] (or a
 * switch) the whole row is the touch target.
 */
@Composable
fun SettingsRow(
    label: String,
    modifier: Modifier = Modifier,
    secondary: String? = null,
    icon: ImageVector? = null,
    pill: String? = null,
    trailing: SettingsTrailing = SettingsTrailing.None,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val touch = when {
        trailing is SettingsTrailing.Switch -> Modifier.toggleable(trailing.checked, role = Role.Switch, onValueChange = trailing.onChange)
        onClick != null -> Modifier.clickable(onClick = onClick)
        else -> Modifier
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(touch)
            .padding(horizontal = 18.dp, vertical = Spacing.s12),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.size(44.dp).background(colors.surfaceContainerHigh, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.cardTitle, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            secondary?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) }
        }
        pill?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.primary,
                modifier = Modifier.background(colors.primaryContainer, CircleShape).padding(horizontal = 10.dp, vertical = Spacing.s4),
            )
        }
        when (trailing) {
            SettingsTrailing.Chevron -> Icon(AppIcons.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
            is SettingsTrailing.Switch -> Switch(
                checked = trailing.checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = colors.primary,
                    checkedThumbColor = colors.onPrimary,
                    checkedBorderColor = colors.primary,
                    uncheckedTrackColor = colors.surfaceContainerHigh,
                    uncheckedThumbColor = colors.onSurfaceVariant,
                    uncheckedBorderColor = colors.onSurfaceVariant,
                ),
            )
            SettingsTrailing.None -> Unit
        }
    }
}

@Preview(name = "Settings rows", widthDp = 360)
@Composable
private fun SettingsRowPreview() = PaperlessTheme {
    Surface {
        Column(Modifier.padding(Spacing.cardGutter), verticalArrangement = Arrangement.spacedBy(Spacing.s16)) {
            SettingsCard { SettingsRow("http://paperless:8000", icon = AppIcons.Server) }
            SettingsCard {
                SettingsRow("Unlock with fingerprint", secondary = "Asks when the app opens. Falls back to your PIN.", trailing = SettingsTrailing.Switch(true) {})
                SettingsDivider()
                SettingsRow("Updates", secondary = "Version 0.3.2", pill = "0.4.0 ready", trailing = SettingsTrailing.Chevron, onClick = {})
            }
        }
    }
}
