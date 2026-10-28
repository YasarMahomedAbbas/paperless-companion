package io.github.yasarmahomedabbas.paperlesscompanion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.AppTheme
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.PaperlessTheme
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.Spacing
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.cardTitle

/** The states of docs/design/components.md § Upload card, each with its own mark. */
enum class UploadStatus {
    /** Uploading or being processed: a spinner. */
    Working,

    /** Accepted: a green tick in a tinted circle. */
    Added,

    /** Paperless refused it: a red alert and the reason in red. */
    Refused,

    /** The phone couldn't reach the server: an amber triangle. */
    Unreachable,
}

/**
 * One upload: a 24dp status mark, the file name on one line, a status line and an optional
 * text button, on a `surface` card with `r-card` corners.
 */
@Composable
fun UploadCard(
    name: String,
    status: UploadStatus,
    statusText: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .background(colors.surfaceContainer, MaterialTheme.shapes.medium)
            .padding(start = Spacing.s16, top = Spacing.s12, bottom = Spacing.s12, end = if (actionLabel == null) Spacing.s16 else Spacing.s4),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when (status) {
                UploadStatus.Working ->
                    CircularProgressIndicator(Modifier.size(20.dp), color = colors.onSurfaceVariant, strokeWidth = 2.dp)
                UploadStatus.Added -> Box(
                    Modifier.size(24.dp).background(colors.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Check, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
                }
                UploadStatus.Refused -> Icon(AppIcons.Alert, contentDescription = null, tint = colors.error)
                UploadStatus.Unreachable ->
                    Icon(AppIcons.Warning, contentDescription = null, tint = AppTheme.extendedColors.warning)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                name,
                style = MaterialTheme.typography.cardTitle,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (status == UploadStatus.Refused) colors.error else colors.onSurfaceVariant,
            )
        }
        if (actionLabel != null) AppButton(actionLabel, onAction, variant = ButtonVariant.Text)
    }
}

@Preview(name = "Upload cards", widthDp = 360)
@Composable
private fun UploadCardPreview() = PaperlessTheme {
    Surface {
        Column(Modifier.padding(Spacing.cardGutter), verticalArrangement = Arrangement.spacedBy(Spacing.rowGap)) {
            UploadCard("Scan 2026-10-28 09-40-12.pdf", UploadStatus.Working, "Paperless is reading it…")
            UploadCard("Photo 2026-10-28 09-38-50.jpg", UploadStatus.Added, "Added to Paperless", actionLabel = "Open")
            UploadCard("Scan 2026-10-27.pdf", UploadStatus.Refused, "It's a duplicate of 'Electricity bill — October'", actionLabel = "Retry")
            UploadCard(
                "Document 2026-10-26.pdf",
                UploadStatus.Unreachable,
                "Couldn't reach the server. Check you're on the home network or the VPN.",
                actionLabel = "Retry",
            )
        }
    }
}
