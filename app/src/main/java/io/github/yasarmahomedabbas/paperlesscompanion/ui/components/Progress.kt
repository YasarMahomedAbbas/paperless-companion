package io.github.yasarmahomedabbas.paperlesscompanion.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.PaperlessTheme
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.Spacing
import java.util.Locale

/**
 * Determinate progress (docs/design/components.md § Progress): a fully round 6 high bar, `raised`
 * track and `primary` fill, with "4.4 of 6.8 MB" under it in 14 `mfg`. Without a [total] it is the
 * 24dp `mfg` spinner instead.
 */
@Composable
fun DeterminateProgress(done: Long, total: Long?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    if (total == null || total <= 0) {
        CircularProgressIndicator(
            modifier.size(24.dp).semantics { contentDescription = "Loading" },
            color = colors.onSurfaceVariant,
            strokeWidth = 2.dp,
        )
        return
    }
    val amount = "${megabytes(done)} of ${megabytes(total)} MB"
    Column(
        modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s8),
    ) {
        LinearProgressIndicator(
            progress = { (done.toFloat() / total).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = colors.primary,
            trackColor = colors.surfaceContainerHigh,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Text(amount, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    }
}

private fun megabytes(bytes: Long) = String.format(Locale.ROOT, "%.1f", bytes / 1_000_000.0)

@Preview(name = "Progress", widthDp = 360)
@Composable
private fun ProgressPreview() = PaperlessTheme {
    Surface {
        Column(Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.s24)) {
            DeterminateProgress(4_400_000, 6_800_000, Modifier.fillMaxWidth())
            DeterminateProgress(1_000, null)
        }
    }
}
