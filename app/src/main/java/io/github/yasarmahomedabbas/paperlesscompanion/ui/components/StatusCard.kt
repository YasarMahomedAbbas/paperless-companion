package io.github.yasarmahomedabbas.paperlesscompanion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.statement

/** What a [StatusCard] says, which picks its icon colour by the colour rules. */
enum class StatusTone {
    /** Nothing here (yet): `mfg`. */
    Empty,

    /** The phone can't reach the server: `warning`. */
    Unreachable,

    /** The server or the phone refused: `destructive`. */
    Refused,

    /** It worked: `primary` on `primary-tint`. */
    Done,
}

/**
 * The empty / error / done card of docs/design/components.md § Status card: a `surface` card,
 * `r-card`, with a 64 circle holding the icon, one statement (17/500), one line of what to do
 * (14 `mfg`) and one `Secondary` button. Centre it in the space it fills; it keeps 20 from the sides.
 * [inSheet] steps it up a tone, `raised` with a `surface` circle, for a sheet's no-match or
 * load-failed state (no card on a card of the same tone).
 */
@Composable
fun StatusCard(
    icon: ImageVector,
    tone: StatusTone,
    statement: String,
    hint: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    inSheet: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val plain = if (inSheet) colors.surfaceContainer else colors.surfaceContainerHigh
    val (tint, circle) = when (tone) {
        StatusTone.Empty -> colors.onSurfaceVariant to plain
        StatusTone.Unreachable -> AppTheme.extendedColors.warning to plain
        StatusTone.Refused -> colors.error to plain
        StatusTone.Done -> colors.primary to colors.primaryContainer
    }
    Column(
        modifier
            .padding(horizontal = Spacing.s20)
            .fillMaxWidth()
            .background(if (inSheet) colors.surfaceContainerHigh else colors.surfaceContainer, MaterialTheme.shapes.medium)
            .semantics(mergeDescendants = false) { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = Spacing.s24, vertical = Spacing.s32),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s8),
    ) {
        Box(Modifier.size(64.dp).background(circle, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(Spacing.s4))
        Text(
            statement,
            style = MaterialTheme.typography.statement,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null) {
            Spacer(Modifier.height(Spacing.s8))
            AppButton(actionLabel, onAction, variant = ButtonVariant.Secondary)
        }
    }
}

@Preview(name = "Status cards", widthDp = 360)
@Composable
private fun StatusCardPreview() = PaperlessTheme {
    Surface {
        Column(Modifier.padding(vertical = Spacing.s20), verticalArrangement = Arrangement.spacedBy(Spacing.s12)) {
            StatusCard(
                AppIcons.WifiOff, StatusTone.Unreachable, "Can't reach Paperless",
                "Check you're on the home network or the VPN.", actionLabel = "Try again",
            )
            StatusCard(
                AppIcons.Document, StatusTone.Empty, "Can't show this file here",
                "Download it to open it in another app.", actionLabel = "Download",
            )
        }
    }
}
