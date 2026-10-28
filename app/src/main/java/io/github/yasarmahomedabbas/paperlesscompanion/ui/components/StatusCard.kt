package io.github.yasarmahomedabbas.paperlesscompanion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.AppTheme
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.PaperlessTheme
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.Spacing
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.cardTitle

/** What a [StatusCard] says, which colours its icon by the colour rules. */
enum class StatusTone {
    /** Nothing here yet, or no match: `mfg`. */
    Empty,

    /** The phone can't reach the server: `warning`. */
    Unreachable,

    /** Paperless refused: `destructive`. */
    Refused,

    /** It worked: `primary` on `primary-tint`. */
    Done,
}

/**
 * The empty / error / done card of docs/design/components.md § Status card: a 64 circle with a
 * 28dp icon, one statement, one line of what to do, and one `Secondary` button. The caller
 * places it (20 from the sides). [inSheet] steps it up a tone, `raised` with a `surface`
 * circle, for a sheet's no-match or load-failed state (no card on a card of the same tone).
 */
@Composable
fun StatusCard(
    icon: ImageVector,
    tone: StatusTone,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    inSheet: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val tint = when (tone) {
        StatusTone.Empty -> colors.onSurfaceVariant
        StatusTone.Unreachable -> AppTheme.extendedColors.warning
        StatusTone.Refused -> colors.error
        StatusTone.Done -> colors.primary
    }
    Column(
        modifier
            .fillMaxWidth()
            .background(if (inSheet) colors.surfaceContainerHigh else colors.surfaceContainer, MaterialTheme.shapes.medium)
            .semantics(mergeDescendants = false) { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = Spacing.s24, vertical = Spacing.s32),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s8),
    ) {
        Box(
            Modifier
                .padding(bottom = Spacing.s8)
                .size(64.dp)
                .background(
                    when {
                        tone == StatusTone.Done -> colors.primaryContainer
                        inSheet -> colors.surfaceContainer
                        else -> colors.surfaceContainerHigh
                    },
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
        }
        Text(title, style = MaterialTheme.typography.cardTitle, color = colors.onSurface, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null) {
            AppButton(actionLabel, onAction, Modifier.padding(top = Spacing.s12), ButtonVariant.Secondary)
        }
    }
}

@Preview(name = "Status cards", widthDp = 360)
@Composable
private fun StatusCardPreview() = PaperlessTheme {
    Surface {
        Column(Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.s16)) {
            StatusCard(
                AppIcons.File,
                StatusTone.Empty,
                "No documents yet",
                "Scan a letter, take a photo or pick a file, and it shows up here once Paperless has read it.",
                actionLabel = "Add a document",
            )
            StatusCard(
                AppIcons.WifiOff,
                StatusTone.Unreachable,
                "Couldn't load your documents",
                "Check you're on the home network or the VPN, then try again.",
                actionLabel = "Try again",
            )
            StatusCard(AppIcons.Alert, StatusTone.Refused, "Couldn't load your documents", "Paperless rejected the login. Sign out and sign in again.", actionLabel = "Try again")
        }
    }
}
