package io.github.yasarmahomedabbas.paperlesscompanion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A tag, display only (docs/design/components.md § Tags): a fully round `raised` pill, `mfg` text
 * in a list row and `fg` text in the details ([inDetails]). Paperless tag colours are ignored so
 * the one-green rule holds.
 */
@Composable
fun TagPill(name: String, inDetails: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Text(
        name,
        style = if (inDetails) MaterialTheme.typography.bodySmall else MaterialTheme.typography.labelSmall,
        color = if (inDetails) colors.onSurface else colors.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(colors.surfaceContainerHigh, CircleShape)
            .padding(horizontal = if (inDetails) 10.dp else 9.dp, vertical = if (inDetails) 4.dp else 3.dp),
    )
}
