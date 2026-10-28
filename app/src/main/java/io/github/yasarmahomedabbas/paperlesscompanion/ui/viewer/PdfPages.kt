package io.github.yasarmahomedabbas.paperlesscompanion.ui.viewer

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.AppIcons
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.StatusCard
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.StatusTone
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

/**
 * A PDF's pages as white paper on `bg`, 12 apart, in a lazy column: pinch to zoom 1–4× and pan, a
 * double tap toggles 1× / 2.5×, and a "Page n of N" pill. A file the renderer can't read gets the
 * "Can't show this file" card with Download.
 */
@Composable
fun PdfPages(
    file: File,
    fullScreen: Boolean,
    onTap: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
) {
    var opened by remember(file) { mutableStateOf<Opened?>(null) }
    LaunchedEffect(file) {
        opened = withContext(Dispatchers.IO) {
            try {
                Opened.Ready(PdfDocument(file))
            } catch (e: Exception) {
                // A corrupt or password-protected PDF, or a file that went away.
                Opened.Broken
            }
        }
    }
    val document = (opened as? Opened.Ready)?.document
    DisposableEffect(document) { onDispose { document?.close() } }
    when {
        opened == Opened.Broken -> Box(modifier.fillMaxSize().padding(bottom = bottomPadding), contentAlignment = Alignment.Center) {
            StatusCard(
                AppIcons.Document, StatusTone.Refused, "Can't show this file",
                "It may be damaged. Download it to open it in another app.",
                actionLabel = "Download", onAction = onDownload,
            )
        }
        document != null -> Pages(document, fullScreen, onTap, bottomPadding, modifier)
        else -> Box(modifier.fillMaxSize())
    }
}

private sealed interface Opened {
    class Ready(val document: PdfDocument) : Opened
    data object Broken : Opened
}

@Composable
private fun Pages(document: PdfDocument, fullScreen: Boolean, onTap: () -> Unit, bottomPadding: Dp, modifier: Modifier) {
    val list = rememberLazyListState()
    var scale by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    val page by remember { derivedStateOf { list.firstVisibleItemIndex + 1 } }
    // The tap detector below lives as long as the pages; it must call the latest onTap, whose
    // full-screen check changes as the panel moves.
    val currentOnTap by rememberUpdatedState(onTap)
    val gutter = if (fullScreen) Spacing.s16 else Spacing.s12
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                // Two fingers zoom (taken before the list sees them); once zoomed, one finger also pans sideways.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pinching = event.changes.count { it.pressed } > 1
                        if (pinching || scale > 1f) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, MAX_ZOOM)
                            val limit = size.width * (scale - 1f) / 2f
                            panX = (panX + event.calculatePan().x).coerceIn(-limit, limit)
                            if (pinching) event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { currentOnTap() },
                    onDoubleTap = {
                        scale = if (scale > 1f) 1f else DOUBLE_TAP_ZOOM
                        panX = 0f
                    },
                )
            },
    ) {
        val widthPx = with(LocalDensity.current) { (maxWidth - gutter * 2).roundToPx() }
        LazyColumn(
            state = list,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = panX
                },
            contentPadding = PaddingValues(start = gutter, end = gutter, top = Spacing.s12, bottom = bottomPadding + Spacing.s12),
            verticalArrangement = Arrangement.spacedBy(Spacing.s12),
        ) {
            itemsIndexed(document.pageSizes) { index, (width, height) ->
                var bitmap by remember(document, index, widthPx) { mutableStateOf<ImageBitmap?>(null) }
                LaunchedEffect(document, index, widthPx) { bitmap = document.render(index, widthPx) }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(width.toFloat() / height)
                        .shadow(2.dp)
                        // Paper is the document's own colour, not a theme token.
                        .background(Color.White),
                ) {
                    bitmap?.let { Image(it, contentDescription = "Page ${index + 1}", contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize()) }
                }
            }
        }
        if (document.pageSizes.isNotEmpty()) {
            Text(
                "Page $page of ${document.pageSizes.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = Spacing.s12)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                    .padding(horizontal = Spacing.s12, vertical = Spacing.s4),
            )
        }
    }
}

/**
 * One open PDF. [PdfRenderer] allows one page open at a time and isn't thread-safe, so every page
 * goes through [lock]; [close] waits for a render in flight by leaving the closing to it.
 */
private class PdfDocument(file: File) {
    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = try {
        PdfRenderer(descriptor)
    } catch (e: Exception) {
        descriptor.close()
        throw e
    }
    private val lock = Mutex()
    @Volatile private var closed = false

    /** Each page's size in points, read once when the file opens. */
    val pageSizes: List<Pair<Int, Int>> = (0 until renderer.pageCount).map { index ->
        renderer.openPage(index).use { it.width to it.height }
    }

    /** Page [index] at [widthPx] × 2 (sharp when zoomed), capped so a page stays a few megabytes. */
    suspend fun render(index: Int, widthPx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        lock.withLock {
            if (closed) return@withLock null
            try {
                renderer.openPage(index).use { page ->
                    val width = min(widthPx * 2, MAX_BITMAP_WIDTH).coerceAtLeast(1)
                    val height = (width.toLong() * page.height / page.width).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap.asImageBitmap()
                }
            } catch (e: Exception) {
                null
            } finally {
                if (closed) release()
            }
        }
    }

    fun close() {
        closed = true
        if (lock.tryLock()) {
            try {
                release()
            } finally {
                lock.unlock()
            }
        }
    }

    private var released = false

    private fun release() {
        if (released) return
        released = true
        renderer.close()
        descriptor.close()
    }
}

private const val MAX_ZOOM = 4f
private const val DOUBLE_TAP_ZOOM = 2.5f
private const val MAX_BITMAP_WIDTH = 2048
