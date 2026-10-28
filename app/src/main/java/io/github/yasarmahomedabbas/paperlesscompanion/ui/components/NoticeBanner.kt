package io.github.yasarmahomedabbas.paperlesscompanion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.AppTheme
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.PaperlessTheme
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.Spacing
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.cardTitle

/** What a [NoticeBanner] is about, which picks its icon and colour by the colour rules. */
enum class NoticeTone {
    /** Something to know: an info icon in `mfg`. */
    Info,

    /** The phone can't reach the server: the no-connection icon in `warning` (amber is the network). */
    Unreachable,

    /** Paperless or a field said no: an alert in `destructive`. */
    Refused,
}

/**
 * The in-flow notice of docs/design/components.md § Notice banner: `raised`, `r-card`, a 20dp
 * icon, an optional bold first line, one or two lines of text and an optional text button.
 * Announced to TalkBack when it appears.
 */
@Composable
fun NoticeBanner(
    text: String,
    modifier: Modifier = Modifier,
    tone: NoticeTone = NoticeTone.Info,
    title: String? = null,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val (icon, tint) = when (tone) {
        NoticeTone.Info -> AppIcons.Info to colors.onSurfaceVariant
        NoticeTone.Unreachable -> AppIcons.WifiOff to AppTheme.extendedColors.warning
        NoticeTone.Refused -> AppIcons.Alert to colors.error
    }
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surfaceContainerHigh, MaterialTheme.shapes.medium)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = Spacing.s16, top = Spacing.s12, end = Spacing.s16, bottom = if (actionLabel == null) Spacing.s12 else Spacing.s4),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s12), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(top = 1.dp).size(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                title?.let { Text(it, style = MaterialTheme.typography.cardTitle, color = colors.onSurface) }
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (title == null) colors.onSurface else colors.onSurfaceVariant,
                )
            }
        }
        if (actionLabel != null) {
            AppButton(actionLabel, onAction, Modifier.align(Alignment.End), ButtonVariant.Text)
        }
    }
}

@Preview(name = "Notice banners", widthDp = 360)
@Composable
private fun NoticeBannerPreview() = PaperlessTheme {
    Surface {
        Column(Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.s12)) {
            NoticeBanner(
                "Can't reach Paperless (timeout). Are you on the home network or the VPN?",
                tone = NoticeTone.Unreachable,
            )
            NoticeBanner("Wrong username or password", tone = NoticeTone.Refused)
            NoticeBanner(
                "The scanner runs in Google Play services, which needs camera access.",
                title = "The scanner needs the camera",
                actionLabel = "Open settings",
            )
        }
    }
}
