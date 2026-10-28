package io.github.yasarmahomedabbas.paperlesscompanion.ui.components

import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.Spacing
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.lead
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** The three stops of the details panel (docs/design/components.md § Details panel). */
enum class PanelStop {
    /** Only the 120 × 48 "Show details" tab shows. */
    FULL_SCREEN,

    /** 268 high: title, meta, tags and the quick actions. */
    QUICK,

    /** From 180 below the top: every detail. */
    ALL,
}

/** The panel's state, opening on [initial] (quick actions unless a preview asks otherwise). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberDetailsPanelState(initial: PanelStop = PanelStop.QUICK): AnchoredDraggableState<PanelStop> {
    val density = LocalDensity.current
    return remember {
        AnchoredDraggableState(
            initialValue = initial,
            positionalThreshold = { distance -> distance * 0.5f },
            velocityThreshold = { with(density) { 125.dp.toPx() } },
            snapAnimationSpec = tween(),
            decayAnimationSpec = exponentialDecay(),
        )
    }
}

/** The stop the panel is at or heading to. */
@OptIn(ExperimentalFoundationApi::class)
val AnchoredDraggableState<PanelStop>.stop: PanelStop get() = targetValue

/**
 * The pull-up details panel over the viewer: `surface`, `r-sheet` top corners, three stops, and a
 * swipe moves one stop. It fills the space it is given and places itself at [state]'s stop. At
 * QUICK it shows [quick]; at ALL it shows [all] (which pins its own bottom actions). At FULL_SCREEN
 * only the handle tab is left, a button that brings the quick actions back.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailsPanel(
    state: AnchoredDraggableState<PanelStop>,
    quick: @Composable () -> Unit,
    all: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val stop = state.stop
    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                with(density) {
                    val height = size.height.toFloat()
                    state.updateAnchors(
                        DraggableAnchors {
                            PanelStop.FULL_SCREEN at height - TAB_HEIGHT.toPx()
                            PanelStop.QUICK at height - QUICK_HEIGHT.toPx()
                            PanelStop.ALL at ALL_TOP.toPx()
                        },
                    )
                }
            },
    ) {
        val offset = if (state.offset.isNaN()) null else state.offset
        if (offset != null) {
            Column(
                Modifier
                    .offset { IntOffset(0, offset.roundToInt()) }
                    .fillMaxSize()
                    .padding(bottom = ALL_TOP),
            ) {
                // The tab: the handle, and in full screen the only part left showing.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .anchoredDraggable(state, Orientation.Vertical),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    val full = stop == PanelStop.FULL_SCREEN
                    Box(
                        Modifier
                            .then(if (full) Modifier.width(120.dp) else Modifier.fillMaxWidth())
                            .height(TAB_HEIGHT)
                            .background(colors.surfaceContainer, MaterialTheme.shapes.large.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize))
                            .then(
                                if (full) {
                                    Modifier.clickable(role = Role.Button, onClickLabel = "Show details") {
                                        scope.launch { state.animateTo(PanelStop.QUICK) }
                                    }.semantics { contentDescription = "Show details" }
                                } else {
                                    Modifier
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(width = 36.dp, height = 4.dp).background(colors.outline, CircleShape))
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(colors.surfaceContainer)
                        .anchoredDraggable(state, Orientation.Vertical),
                ) {
                    if (stop == PanelStop.ALL) all() else quick()
                }
            }
        }
    }
}

/** One line of the details' definition list: label 14 `mfg` left, value 15 `fg` right, divided by `border-muted`. */
@Composable
fun DetailRow(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = Spacing.s12),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s16),
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.lead,
                color = colors.onSurface,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider(color = colors.outlineVariant, thickness = 1.dp)
    }
}

private val TAB_HEIGHT = 48.dp
private val QUICK_HEIGHT = 268.dp
private val ALL_TOP = 180.dp
