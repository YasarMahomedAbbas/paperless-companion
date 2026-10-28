package io.github.yasarmahomedabbas.paperlesscompanion.ui.viewer

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Document
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentFile
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.AppButton
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.AppIcons
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.AppSnackbarHost
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.ButtonVariant
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.DetailRow
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.DetailsPanel
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.DeterminateProgress
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.MenuDivider
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.MenuItem
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.AppDialog
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.NoticeBanner
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.NoticeTone
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.PanelStop
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.SkeletonBlock
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.StatusCard
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.StatusTone
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.TagPill
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.ViewerBar
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.rememberDetailsPanelState
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.stop
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.PaperlessTheme
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The document viewer (docs/design/layouts.md § Viewer): the PDF's pages under a three-stop details panel. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ViewerScreen(
    viewModel: ViewerViewModel,
    onBack: () -> Unit,
    onShare: (ViewerEvent.Share) -> Unit,
    onPickSaveLocation: (ViewerEvent.PickSaveLocation) -> Unit,
    onOpenUrl: (String) -> Unit,
    onEdit: (Document) -> Unit,
    /** Move to trash was confirmed. */
    onTrash: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ViewerEvent.Share -> onShare(event)
                is ViewerEvent.PickSaveLocation -> onPickSaveLocation(event)
                is ViewerEvent.OpenUrl -> onOpenUrl(event.url)
            }
        }
    }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        viewModel.messageShown()
        // Outside this effect, which restarts (and would cancel it) once the message is cleared.
        scope.launch { snackbar.showSnackbar(message) }
    }
    ViewerContent(
        state = state,
        snackbar = snackbar,
        onBack = onBack,
        onShare = viewModel::share,
        onDownload = viewModel::download,
        onDownloadOriginal = viewModel::downloadOriginal,
        onOpenInPaperless = viewModel::openInPaperless,
        onRetry = viewModel::retry,
        onEdit = { state.document?.let(onEdit) },
        onTrash = onTrash,
    )
}

/** The viewer drawn from [state] alone, for previews; [pages] draws a ready PDF. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ViewerContent(
    state: ViewerUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    onDownloadOriginal: () -> Unit,
    onOpenInPaperless: () -> Unit,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onTrash: () -> Unit,
    panel: AnchoredDraggableState<PanelStop> = rememberDetailsPanelState(),
    menuOpenAtStart: Boolean = false,
    confirmTrashAtStart: Boolean = false,
    pages: @Composable (file: File, fullScreen: Boolean, onTap: () -> Unit, bottomPadding: Dp) -> Unit =
        { file, fullScreen, onTap, bottom -> PdfPages(file, fullScreen, onTap, onDownload, bottomPadding = bottom) },
) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val stop = panel.stop
    val fullScreen = stop == PanelStop.FULL_SCREEN
    var menuOpen by remember { mutableStateOf(menuOpenAtStart) }
    var confirmTrash by remember { mutableStateOf(confirmTrashAtStart) }
    val askTrash = { confirmTrash = true }
    var peek by remember { mutableStateOf(false) }
    LaunchedEffect(peek) {
        if (peek) {
            delay(3_000)
            peek = false
        }
    }
    LaunchedEffect(fullScreen) { if (!fullScreen) peek = false }
    StatusBarHidden(fullScreen)
    // Back from full screen or all details returns to the quick actions; from there it leaves.
    BackHandler(enabled = stop != PanelStop.QUICK) { scope.launch { panel.animateTo(PanelStop.QUICK) } }

    val details = (state.details as? DetailsState.Ready)?.details
    val title = details?.title.orEmpty()
    val canExport = state.file is FileState.Ready || state.file is FileState.NotViewable
    val pagesBottom = if (fullScreen) 48.dp else 268.dp

    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize()) {
            AnimatedVisibility(visible = !fullScreen, enter = slideInVertically { -it }, exit = slideOutVertically { -it }) {
                Box(Modifier.statusBarsPadding()) { Bar(title, onBack, onShare, canExport, menuOpen, { menuOpen = it }, onDownload, onOpenInPaperless, askTrash.takeIf { state.document != null }) }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (val file = state.file) {
                    is FileState.Loading -> Centred(pagesBottom) {
                        DeterminateProgress(file.done, file.total, Modifier.width(240.dp))
                    }
                    is FileState.Ready -> pages(file.file.file, fullScreen, { if (fullScreen) peek = true }, pagesBottom)
                    is FileState.NotViewable -> Centred(pagesBottom) {
                        StatusCard(
                            AppIcons.Document, StatusTone.Empty, "Can't show this file here",
                            "Download it to open it in another app.",
                            actionLabel = "Download", onAction = onDownload,
                        )
                    }
                    is FileState.Failed -> Centred(pagesBottom) {
                        StatusCard(
                            if (file.unreachable) AppIcons.WifiOff else AppIcons.Alert,
                            if (file.unreachable) StatusTone.Unreachable else StatusTone.Refused,
                            if (file.unreachable) "Can't reach Paperless" else "Couldn't load this document",
                            if (file.unreachable) "Check you're on the home network or the VPN." else "Paperless didn't send the file. Try again in a moment.",
                            actionLabel = "Try again", onAction = onRetry,
                        )
                    }
                }
            }
        }
        // In full screen a tap on the page brings the bar back for a moment.
        AnimatedVisibility(visible = fullScreen && peek, enter = slideInVertically { -it }, exit = slideOutVertically { -it }) {
            Box(Modifier.background(colors.background).statusBarsPadding()) {
                Bar(title, onBack, onShare, canExport, menuOpen, { menuOpen = it }, onDownload, onOpenInPaperless, askTrash.takeIf { state.document != null })
            }
        }
        DetailsPanel(
            state = panel,
            quick = { QuickActions(state, onEdit, onDownload, onDownloadOriginal, onRetry) },
            all = { AllDetails(state, onEdit, onDownload, onRetry) },
        )
        AppSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
    if (confirmTrash) {
        AppDialog(
            title = "Move to trash?",
            body = "'$title' goes to the Paperless trash. You can bring it back from there.",
            confirmLabel = "Move to trash",
            onConfirm = {
                confirmTrash = false
                onTrash()
            },
            onDismiss = { confirmTrash = false },
            confirmVariant = ButtonVariant.Destructive,
        )
    }
}

@Composable
private fun Bar(
    title: String,
    onBack: () -> Unit,
    onShare: () -> Unit,
    canExport: Boolean,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onDownload: () -> Unit,
    onOpenInPaperless: () -> Unit,
    /** Null until the document has loaded. */
    onTrash: (() -> Unit)?,
) {
    ViewerBar(
        title = title,
        onBack = onBack,
        onShare = onShare,
        shareEnabled = canExport,
        menuOpen = menuOpen,
        onMenuOpenChange = onMenuOpenChange,
    ) {
        MenuItem("Download", AppIcons.Download, { onMenuOpenChange(false); onDownload() }, enabled = canExport)
        MenuItem("Open in Paperless", AppIcons.OpenExternal, { onMenuOpenChange(false); onOpenInPaperless() })
        MenuDivider()
        MenuItem("Move to trash", AppIcons.Trash, { onMenuOpenChange(false); onTrash?.invoke() }, enabled = onTrash != null, destructive = true)
    }
}

@Composable
private fun Centred(bottom: Dp, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(bottom = bottom), contentAlignment = Alignment.Center) { content() }
}

/** Hides the status bar while [hidden], and shows it again when the viewer goes. */
@Composable
private fun StatusBarHidden(hidden: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = (view.context as? Activity)?.window ?: return
    DisposableEffect(hidden) {
        val controller = WindowCompat.getInsetsController(window, view)
        if (hidden) controller.hide(WindowInsetsCompat.Type.statusBars()) else controller.show(WindowInsetsCompat.Type.statusBars())
        onDispose { controller.show(WindowInsetsCompat.Type.statusBars()) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickActions(state: ViewerUiState, onEdit: () -> Unit, onDownload: () -> Unit, onDownloadOriginal: () -> Unit, onRetry: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.s8),
    ) {
        when (val details = state.details) {
            DetailsState.Loading -> QuickSkeleton()
            is DetailsState.Failed -> DetailsFailed(details, onRetry)
            is DetailsState.Ready -> {
                val d = details.details
                Text(
                    d.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                val meta = listOfNotNull(d.correspondent, d.created?.let(::longDate), d.documentType).joinToString(" · ")
                if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                if (d.tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        d.tags.forEach { TagPill(it, inDetails = true) }
                    }
                }
                Spacer(Modifier.height(Spacing.s4))
                PanelActions(state, onEdit, onDownload)
                Text("Swipe up for all details, down for full screen", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                // The preview is Paperless's PDF of something else; the original can still be had.
                if (!d.originalIsPdf && state.file !is FileState.NotViewable) {
                    NoticeBanner(
                        if (d.isPhotoOriginal) "This came in as a photo. You're seeing Paperless's PDF of it."
                        else "This came in as another kind of file. You're seeing Paperless's PDF of it.",
                        actionLabel = if (d.isPhotoOriginal) "Get photo" else "Get original",
                        onAction = onDownloadOriginal,
                    )
                }
                Spacer(Modifier.height(Spacing.s12))
            }
        }
    }
}

@Composable
private fun QuickSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s12)) {
        SkeletonBlock(Modifier.fillMaxWidth(0.7f).height(24.dp))
        SkeletonBlock(Modifier.fillMaxWidth(0.5f).height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SkeletonBlock(Modifier.size(width = 56.dp, height = 26.dp), round = true)
            SkeletonBlock(Modifier.size(width = 72.dp, height = 26.dp), round = true)
        }
        SkeletonBlock(Modifier.fillMaxWidth().height(48.dp))
    }
}

@Composable
private fun DetailsFailed(failed: DetailsState.Failed, onRetry: () -> Unit) {
    NoticeBanner(
        if (failed.unreachable) "Can't reach Paperless. Check you're on the home network or the VPN."
        else "Couldn't load this document's details.",
        tone = if (failed.unreachable) NoticeTone.Unreachable else NoticeTone.Refused,
        actionLabel = "Try again",
        onAction = onRetry,
    )
}

@Composable
private fun AllDetails(state: ViewerUiState, onEdit: () -> Unit, onDownload: () -> Unit, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter),
        ) {
            when (val details = state.details) {
                DetailsState.Loading -> QuickSkeleton()
                is DetailsState.Failed -> DetailsFailed(details, onRetry)
                is DetailsState.Ready -> {
                    val d = details.details
                    DetailRow("Title", d.title)
                    DetailRow("Date", d.created?.let(::longDate) ?: "None")
                    DetailRow("Correspondent", d.correspondent ?: "None")
                    DetailRow("Document type", d.documentType ?: "None")
                    DetailRow("Tags", d.tags.joinToString(", ").ifEmpty { "None" })
                    DetailRow("Storage path", d.storagePath ?: "None")
                    DetailRow("Added", d.added?.let(::addedAt) ?: "Unknown")
                }
            }
        }
        PanelActions(
            state, onEdit, onDownload,
            Modifier.padding(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.actionTop, bottom = Spacing.s16),
        )
    }
}

/** Edit and Download, two `Secondary` buttons side by side (components.md § Details panel). */
@Composable
private fun PanelActions(state: ViewerUiState, onEdit: () -> Unit, onDownload: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s12)) {
        AppButton("Edit", onEdit, Modifier.weight(1f), ButtonVariant.Secondary, enabled = state.document != null)
        AppButton(
            "Download", onDownload, Modifier.weight(1f), ButtonVariant.Secondary,
            enabled = state.file is FileState.Ready || state.file is FileState.NotViewable,
            busy = state.saving,
        )
    }
}

private fun longDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))

private fun addedAt(instant: Instant): String =
    instant.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm"))

// --- previews ---

private val previewDetails = DocumentDetails(
    title = "Electricity bill — October",
    created = LocalDate.of(2026, 10, 3),
    added = Instant.parse("2026-10-04T08:12:00Z"),
    correspondent = "City Power",
    documentType = "Invoice",
    storagePath = "Bills",
    tags = listOf("Bills", "House"),
    isPhotoOriginal = false,
    originalIsPdf = true,
)
private val previewFile = DocumentFile("bill.pdf", "application/pdf", File("bill.pdf"))
private val previewDocument = Document(
    7, previewDetails.title, previewDetails.created, previewDetails.added, null, 1, 1, 1, listOf(1, 2), null, "bill.pdf", 2, "application/pdf",
)
private val previewReady = ViewerUiState(7, DetailsState.Ready(previewDetails), FileState.Ready(previewFile), document = previewDocument)

@Composable
private fun PreviewPages(file: File, fullScreen: Boolean, onTap: () -> Unit, bottom: Dp) {
    Box(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = bottom + 12.dp)) {
        // Paper is the document's own colour.
        Box(Modifier.fillMaxWidth().height(460.dp).background(Color.White))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Preview(state: ViewerUiState, stop: PanelStop = PanelStop.QUICK, menuOpen: Boolean = false, confirmTrash: Boolean = false) = PaperlessTheme {
    ViewerContent(
        state = state,
        snackbar = remember { SnackbarHostState() },
        onBack = {}, onShare = {}, onDownload = {}, onDownloadOriginal = {}, onOpenInPaperless = {}, onRetry = {}, onEdit = {}, onTrash = {},
        panel = rememberDetailsPanelState(stop),
        menuOpenAtStart = menuOpen,
        confirmTrashAtStart = confirmTrash,
        pages = { file, fullScreen, onTap, bottom -> PreviewPages(file, fullScreen, onTap, bottom) },
    )
}

@Preview(name = "View", widthDp = 360, heightDp = 780)
@Composable
private fun ViewPreview() = Preview(previewReady)

@Preview(name = "Full screen", widthDp = 360, heightDp = 780)
@Composable
private fun FullScreenPreview() = Preview(previewReady, PanelStop.FULL_SCREEN)

@Preview(name = "Details expanded", widthDp = 360, heightDp = 780)
@Composable
private fun DetailsExpandedPreview() = Preview(previewReady, PanelStop.ALL)

@Preview(name = "More menu", widthDp = 360, heightDp = 780)
@Composable
private fun MoreMenuPreview() = Preview(previewReady, menuOpen = true)

@Preview(name = "Move to trash?", widthDp = 360, heightDp = 780)
@Composable
private fun ConfirmTrashPreview() = Preview(previewReady, confirmTrash = true)

@Preview(name = "Loading", widthDp = 360, heightDp = 780)
@Composable
private fun LoadingPreview() = Preview(ViewerUiState(7, file = FileState.Loading(4_400_000, 6_800_000)))

@Preview(name = "Load failed", widthDp = 360, heightDp = 780)
@Composable
private fun LoadFailedPreview() = Preview(
    ViewerUiState(7, DetailsState.Ready(previewDetails), FileState.Failed("Can't reach Paperless", unreachable = true)),
)

@Preview(name = "Photo original", widthDp = 360, heightDp = 780)
@Composable
private fun PhotoOriginalPreview() = Preview(
    previewReady.copy(details = DetailsState.Ready(previewDetails.copy(isPhotoOriginal = true, originalIsPdf = false))),
)

@Preview(name = "Not viewable", widthDp = 360, heightDp = 780)
@Composable
private fun NotViewablePreview() = Preview(
    ViewerUiState(7, DetailsState.Ready(previewDetails), FileState.NotViewable(DocumentFile("note.txt", "text/plain", File("note.txt")))),
)
