package io.github.yasarmahomedabbas.paperlesscompanion.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.PaperlessTheme
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.Spacing
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.cardTitle

/**
 * One document in the cards view (docs/design/components.md § Document card): a 3:4 thumbnail
 * cropped from the top so the letterhead shows, then the title and the meta line, each on one
 * line, on a `surface` card with `r-card` corners. No tags and no search snippet; the list view
 * shows those. [thumbnail] is whatever the app's image loader takes; null (or still loading)
 * shows the `raised` placeholder. TalkBack reads it as one item. With a non-null [selection] it
 * is selectable: a 24dp round check over the thumbnail's top-start corner, and when selected a
 * `primary-tint` ground with a 2dp `primary` ring. [onLongClick] starts selecting.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentCard(
    title: String,
    meta: String,
    thumbnail: Any?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selection: Boolean? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val selected = selection == true
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) colors.primaryContainer else colors.surfaceContainer)
            .then(if (selected) Modifier.border(2.dp, colors.primary, shape) else Modifier)
            .then(if (selection != null) Modifier.semantics { this.selected = selected } else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = if (onLongClick != null) "Select" else null)
            .padding(Spacing.s8),
        verticalArrangement = Arrangement.spacedBy(Spacing.s8),
    ) {
        Box {
            AsyncImage(
                model = thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(colors.surfaceContainerHigh),
            )
            if (selection != null) {
                // On a `surface` disc, so the ring reads on any thumbnail.
                SelectionCheck(selected, Modifier.padding(Spacing.s8).background(colors.surfaceContainer, CircleShape))
            }
        }
        Column(Modifier.padding(horizontal = Spacing.s4), verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
            Text(title, style = MaterialTheme.typography.cardTitle, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Preview(name = "Document cards", widthDp = 360)
@Composable
private fun DocumentCardPreview() = PaperlessTheme {
    Surface {
        Column(Modifier.padding(Spacing.cardGutter), verticalArrangement = Arrangement.spacedBy(Spacing.rowGap)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.rowGap)) {
                DocumentCard("Electricity bill — October", "City Power · 3 Oct 2026", null, {}, Modifier.weight(1f))
                DocumentCard(
                    "Medical aid statement — August with a title too long for one line",
                    "Discovery Health · 12 Sep 2026",
                    null,
                    {},
                    Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.rowGap)) {
                DocumentCard("Scan 2026-10-28", "", null, {}, Modifier.weight(1f))
                DocumentCard("Car licence renewal", "Dept. of Transport · 18 Sep 2026", null, {}, Modifier.weight(1f), selection = false)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.rowGap)) {
                DocumentCard("Payslip — September", "Acme Logistics · 25 Sep 2026", null, {}, Modifier.weight(1f), selection = true)
                DocumentCardSkeleton(Modifier.weight(1f))
            }
        }
    }
}
