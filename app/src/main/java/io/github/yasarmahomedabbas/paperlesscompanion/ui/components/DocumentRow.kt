package io.github.yasarmahomedabbas.paperlesscompanion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.SearchHit
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.PaperlessTheme
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.Spacing
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.cardTitle

/**
 * One document in the list (docs/design/components.md § Document row): a 56 × 72 thumbnail,
 * the title on one line, the meta line, up to two tag pills and "+N", on a `surface` card with
 * `r-card` corners. With a [snippet] it is the search variant: two lines of the matching text
 * under the meta line instead of the tags. [thumbnail] is whatever the app's image loader
 * takes; null (or still loading) shows the `raised` placeholder. TalkBack reads it as one item.
 * With a non-null [selection] it is the selectable variant: a 24dp round check at the start, a
 * smaller thumbnail, and when selected a `primary-tint` ground with a 2dp `primary` ring.
 * [onLongClick] starts selecting.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentRowCard(
    title: String,
    meta: String,
    tags: List<String>,
    moreTags: Int,
    thumbnail: Any?,
    snippet: AnnotatedString?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selection: Boolean? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val selected = selection == true
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) colors.primaryContainer else colors.surfaceContainer)
            .then(if (selected) Modifier.border(2.dp, colors.primary, shape) else Modifier)
            .then(if (selection != null) Modifier.semantics { this.selected = selected } else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = if (onLongClick != null) "Select" else null)
            .padding(Spacing.s12),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
        verticalAlignment = if (selection != null) Alignment.CenterVertically else Alignment.Top,
    ) {
        if (selection != null) SelectionCheck(selected)
        AsyncImage(
            model = thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .then(if (selection != null) Modifier.size(width = 48.dp, height = 62.dp) else Modifier.size(width = 56.dp, height = 72.dp))
                .clip(MaterialTheme.shapes.extraSmall)
                .background(colors.surfaceContainerHigh),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
            Text(title, style = MaterialTheme.typography.cardTitle, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (snippet != null) {
                Text(snippet, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            } else if (tags.isNotEmpty()) {
                Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    tags.forEach { TagPill(it, inDetails = false, Modifier.weight(1f, fill = false)) }
                    if (moreTags > 0) TagPill("+$moreTags", inDetails = false)
                }
            }
        }
    }
}

/** The selectable row's 24dp round check: a `mfg` ring, or filled `primary` with an `on-primary` tick. */
@Composable
private fun SelectionCheck(selected: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(24.dp)
            .then(if (selected) Modifier.background(colors.primary, CircleShape) else Modifier.border(2.dp, colors.onSurfaceVariant, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(AppIcons.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(16.dp))
    }
}

/** How a search match is marked in a snippet: `primary-tint` behind `fg` text. */
val searchMatch: SpanStyle
    @Composable @ReadOnlyComposable get() = SpanStyle(
        background = MaterialTheme.colorScheme.primaryContainer,
        color = MaterialTheme.colorScheme.onSurface,
    )

/** The excerpt with each match in [match]; ranges reaching past the excerpt are cut to it, empty ones dropped. */
fun SearchHit.highlighted(match: SpanStyle): AnnotatedString = buildAnnotatedString {
    append(excerpt)
    for (range in matches) {
        val start = range.first.coerceAtLeast(0)
        val end = (range.last + 1).coerceAtMost(excerpt.length)
        if (start < end) addStyle(match, start, end)
    }
}

@Preview(name = "Document rows", widthDp = 360)
@Composable
private fun DocumentRowPreview() = PaperlessTheme {
    Surface {
        Column(Modifier.padding(Spacing.cardGutter), verticalArrangement = Arrangement.spacedBy(Spacing.rowGap)) {
            DocumentRowCard("Electricity bill — October", "City Power · 3 Oct 2026", listOf("Bills", "House"), 0, null, null, {})
            DocumentRowCard(
                "Medical aid statement — August with a title too long for one line",
                "Discovery Health · 12 Sep 2026",
                listOf("Medical", "Tax"),
                3,
                null,
                null,
                {},
            )
            DocumentRowCard("Scan 2026-10-28", "28 Oct 2026", emptyList(), 0, null, null, {})
            DocumentRowCard("Payslip — September", "Acme Logistics · 25 Sep 2026", listOf("Payslips"), 0, null, null, {}, selection = true)
            DocumentRowCard("Car licence renewal", "Dept. of Transport · 18 Sep 2026", listOf("Car"), 0, null, null, {}, selection = false)
            DocumentRowCard(
                "Municipal rates — August",
                "City of Johannesburg · 28 Aug 2026",
                emptyList(),
                0,
                null,
                SearchHit(1.0, "…property rates for erf 4127, valuation R 1 850 000, due 15 Sept…", listOf(10..14)).highlighted(searchMatch),
                {},
            )
        }
    }
}
