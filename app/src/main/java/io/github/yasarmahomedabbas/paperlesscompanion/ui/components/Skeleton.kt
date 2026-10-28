package io.github.yasarmahomedabbas.paperlesscompanion.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.clearAndSetSemantics

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
