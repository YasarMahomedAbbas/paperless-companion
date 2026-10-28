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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.PaperlessTheme
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.Spacing

/**
 * One block of a skeleton (docs/design/components.md § Skeleton): a `surface` → `raised` → `surface`
 * gradient sweeping every 1.8s, `r-thumb` corners, or fully round for chips and tags ([round]).
 * Size it with [modifier] to the shape it stands in for. Hidden from TalkBack.
 */
@Composable
fun SkeletonBlock(modifier: Modifier, round: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val sweep by rememberInfiniteTransition(label = "shim").animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "shim",
    )
    val shape = if (round) CircleShape else MaterialTheme.shapes.extraSmall
    Box(
        modifier
            .clearAndSetSemantics {}
            .background(colors.surfaceContainer, shape)
            .background(
                Brush.linearGradient(
                    0f to colors.surfaceContainer,
                    0.5f to colors.surfaceContainerHigh,
                    1f to colors.surfaceContainer,
                    start = Offset(sweep * 600f - 300f, 0f),
                    end = Offset(sweep * 600f + 300f, 0f),
                ),
                shape,
            ),
    )
}

/** A document row with every shape a [SkeletonBlock]: same sizes and places as [DocumentRowCard]. */
@Composable
fun DocumentRowSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
            .padding(Spacing.s12),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
    ) {
        SkeletonBlock(Modifier.size(width = 56.dp, height = 72.dp))
        Column(Modifier.weight(1f).padding(top = Spacing.s4), verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
            SkeletonBlock(Modifier.fillMaxWidth(0.7f).height(16.dp))
            SkeletonBlock(Modifier.fillMaxWidth(0.45f).height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SkeletonBlock(Modifier.width(48.dp).height(20.dp), round = true)
                SkeletonBlock(Modifier.width(56.dp).height(20.dp), round = true)
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
            DocumentRowSkeleton()
            LoadingMoreFooter()
            LoadMoreFailedFooter("Can't reach Paperless (timeout). Are you on the home network or the VPN?", {})
        }
    }
}
