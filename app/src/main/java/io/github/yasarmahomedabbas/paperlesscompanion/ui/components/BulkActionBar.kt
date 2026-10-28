package io.github.yasarmahomedabbas.paperlesscompanion.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.PaperlessTheme
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.Spacing

/**
 * The bar that replaces the Add button while selecting (components.md § Bulk action bar): a
 * floating `surface` card with a 1dp `border` ring, `r-card`, and four equal 64-high buttons, a
 * 22dp icon over a 13 label: Tags, From, Type and Trash (in `destructive`). The caller places it
 * 24 above the bottom. [enabled] is false while a change is on its way.
 */
@Composable
fun BulkActionBar(
    onTags: () -> Unit,
    onFrom: () -> Unit,
    onType: () -> Unit,
    onTrash: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    Surface(
        modifier = modifier.fillMaxWidth().border(1.dp, colors.outline, shape),
        shape = shape,
        color = colors.surfaceContainer,
        shadowElevation = 6.dp,
    ) {
        Row(Modifier.padding(Spacing.s4)) {
            BulkButton(AppIcons.Tag, "Tags", onTags, enabled, destructive = false, Modifier.weight(1f))
            BulkButton(AppIcons.Person, "From", onFrom, enabled, destructive = false, Modifier.weight(1f))
            BulkButton(AppIcons.DocumentType, "Type", onType, enabled, destructive = false, Modifier.weight(1f))
            BulkButton(AppIcons.Trash, "Trash", onTrash, enabled, destructive = true, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BulkButton(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean, destructive: Boolean, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val tint = when {
        !enabled -> colors.onSurfaceVariant
        destructive -> colors.error
        else -> colors.onSurface
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(64.dp).semantics { role = Role.Button },
        shape = MaterialTheme.shapes.small,
        color = colors.surfaceContainer,
        contentColor = tint,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s4, Alignment.CenterVertically)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Preview(name = "Bulk action bar", widthDp = 412)
@Composable
private fun BulkActionBarPreview() = PaperlessTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(Spacing.s16), verticalArrangement = Arrangement.spacedBy(Spacing.s16)) {
            BulkActionBar({}, {}, {}, {})
            BulkActionBar({}, {}, {}, {}, enabled = false)
            SelectionBar(3, {}, {})
        }
    }
}
