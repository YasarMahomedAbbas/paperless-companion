package io.github.yasarmahomedabbas.paperlesscompanion.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.PaperlessTheme
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.Spacing

/**
 * A skeleton block (components.md § Skeleton): `surface` → `raised` → `surface` sweeping every
 * 1.8 s. Compose scales the sweep by the system animator scale, so "remove animations" stops it.
 */
@Composable
private fun Modifier.shim(shape: Shape): Modifier {
    val colors = MaterialTheme.colorScheme
    val sweep by rememberInfiniteTransition(label = "shim").animateFloat(
        initialValue = 1f,
        targetValue = -1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep",
    )
    return drawBehind {
        val w = size.width
        val brush = Brush.linearGradient(
            0f to colors.surfaceContainer,
            0.4f to colors.surfaceContainerHigh,
            0.8f to colors.surfaceContainer,
            start = Offset(sweep * w * 2, 0f),
            end = Offset(sweep * w * 2 + w * 2, 0f),
        )
        val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawBehind)) }
        clipPath(path) { drawRect(brush) }
    }
}

/** A document row with every shape a [shim] block: same sizes and places as [DocumentRowCard]. */
@Composable
fun DocumentRowSkeleton(modifier: Modifier = Modifier) {
    val thumb = MaterialTheme.shapes.extraSmall
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
            .padding(Spacing.s12),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
    ) {
        Box(Modifier.size(width = 56.dp, height = 72.dp).shim(thumb))
        Column(Modifier.weight(1f).padding(top = Spacing.s4), verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
            Box(Modifier.fillMaxWidth(0.7f).height(16.dp).shim(thumb))
            Box(Modifier.fillMaxWidth(0.45f).height(12.dp).shim(thumb))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.width(48.dp).height(20.dp).shim(CircleShape))
                Box(Modifier.width(56.dp).height(20.dp).shim(CircleShape))
            }
        }
    }
}

/** The list footer while the next page loads: one skeleton row, then a spinner and "Loading more". */
@Composable
fun LoadingMoreFooter(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
        DocumentRowSkeleton()
        Row(
            Modifier.fillMaxWidth().padding(vertical = Spacing.s12).semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(Spacing.s8, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, strokeWidth = 2.dp)
            Text("Loading more", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The list footer when the next page failed: why, and Try again. The rows above stay. */
@Composable
fun LoadMoreFailedFooter(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    NoticeBanner(message, modifier, tone = NoticeTone.Unreachable, actionLabel = "Try again", onAction = onRetry)
}

@Preview(name = "Skeleton and footers", widthDp = 360)
@Composable
private fun SkeletonPreview() = PaperlessTheme {
    Surface {
        Column(Modifier.padding(Spacing.cardGutter), verticalArrangement = Arrangement.spacedBy(Spacing.rowGap)) {
            DocumentRowSkeleton(Modifier.semantics { contentDescription = "Loading" })
            LoadingMoreFooter()
            LoadMoreFailedFooter("Can't reach Paperless (timeout). Are you on the home network or the VPN?", {})
        }
    }
}
