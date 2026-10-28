package io.github.yasarmahomedabbas.paperlesscompanion.ui.documents

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentSource
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentThumbnail
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.UploadState
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.isFinished
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.DocumentFiles
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.AddButton
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.AppButton
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.AppChip
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.AppIconButton
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.AppIcons
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.ButtonVariant
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.ChipRow
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.DocumentRowCard
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.DocumentRowSkeleton
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.IconButtonVariant
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.LargeTitleBar
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.LoadMoreFailedFooter
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.LoadingMoreFooter
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.NoticeBanner
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.SearchField
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.SectionHeading
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.StatusCard
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.StatusTone
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.UploadCard
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.UploadStatus
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.highlighted
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.searchMatch
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.PaperlessTheme
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.Spacing
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** How many rows from the end the next page starts loading (layouts.md § List — Documents). */
private const val LOAD_AHEAD = 5

/**
 * The home: the documents on the server (layouts.md § List — Documents), with the uploads in
 * progress as cards at the top and Add for new ones.
 */
@Composable
fun DocumentsScreen(
    documents: DocumentsViewModel,
    uploads: UploadsViewModel,
    files: DocumentFiles,
    /** Documents shared, scanned, photographed or picked, waiting to be uploaded now that the app is unlocked. */
    pending: List<DocumentSource>,
    onPendingConsumed: () -> Unit,
    /** Every document the screen brings in goes here first, so one that arrives while locked waits. */
    onDocuments: (List<DocumentSource>) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by documents.state.collectAsState()
    val uploadState by uploads.state.collectAsState()
    val context = LocalContext.current
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    var playServicesNeedsCamera by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(pending) {
        if (pending.isNotEmpty()) {
            uploads.upload(pending)
            onPendingConsumed()
        }
    }
    // A document Paperless accepted shows up in the list.
    LaunchedEffect(uploads) { uploads.added.collect { documents.refresh() } }

    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pdf?.uri?.let { uri ->
            onDocuments(listOf(files.from(uri.toString(), "Scan ${timestamp()}.pdf")))
        }
    }
    var photoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = photoUri
        if (saved && uri != null) onDocuments(listOf(files.from(uri.toString(), "Photo ${timestamp()}.jpg")))
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onDocuments(uris.map { files.from(it.toString(), "Document ${timestamp()}") })
    }

    fun scan() {
        val activity = context as? Activity ?: return
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
            .addOnSuccessListener {
                // The scanner runs inside Play services, so it is Play services that needs the camera, not this app.
                // On GrapheneOS's sandboxed Play it starts without it, and the scanner only says to go and enable it.
                if (context.packageManager.checkPermission(Manifest.permission.CAMERA, PLAY_SERVICES) != PackageManager.PERMISSION_GRANTED) {
                    notice = "The scanner runs in Google Play services, which needs camera access. " +
                        "Open settings, then Permissions → Camera → Allow only while using the app."
                    playServicesNeedsCamera = true
                    return@addOnSuccessListener
                }
                notice = null
                playServicesNeedsCamera = false
                scanLauncher.launch(IntentSenderRequest.Builder(it).build())
            }
            .addOnFailureListener {
                notice = "The scanner needs Google Play services, which this phone doesn't have. Use Take photo instead."
                playServicesNeedsCamera = false
            }
    }

    fun takePhoto() {
        val dir = File(context.cacheDir, "photos").apply { mkdirs() }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", File(dir, "Photo ${timestamp()}.jpg"))
        photoUri = uri
        photoLauncher.launch(uri)
    }

    DocumentsContent(
        state = state,
        uploads = uploadState.uploads,
        notice = notice,
        actions = DocumentsScreenActions(
            onRefresh = documents::refresh,
            onLoadMore = documents::loadMore,
            onTextChange = documents::onTextChange,
            onSearch = documents::search,
            onClearFilters = documents::clearFilters,
            onAdd = { adding = true },
            onOpenSettings = onOpenSettings,
            onClearFinished = uploads::clearFinished,
            onRetryUpload = uploads::retry,
            onOpenUpload = { id -> uploads.documentUrl(id)?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } },
            onOpenPlayServicesSettings = if (playServicesNeedsCamera) {
                {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", PLAY_SERVICES, null)))
                }
            } else {
                null
            },
        ),
    )

    if (adding) {
        AddSheet(
            onDismiss = { adding = false },
            onScan = {
                adding = false
                scan()
            },
            onTakePhoto = {
                adding = false
                takePhoto()
            },
            onChooseFile = {
                adding = false
                fileLauncher.launch(arrayOf("application/pdf", "image/*"))
            },
        )
    }
}

/** What the Documents screen can be asked to do; a class so the previews can pass no-ops. */
class DocumentsScreenActions(
    val onRefresh: () -> Unit = {},
    val onLoadMore: () -> Unit = {},
    val onTextChange: (String) -> Unit = {},
    val onSearch: () -> Unit = {},
    val onClearFilters: () -> Unit = {},
    val onAdd: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onClearFinished: () -> Unit = {},
    val onRetryUpload: (Long) -> Unit = {},
    val onOpenUpload: (Int) -> Unit = {},
    /** Set while the scanner needs Play services to get the camera: the notice's "Open settings". */
    val onOpenPlayServicesSettings: (() -> Unit)? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentsContent(
    state: DocumentsUiState,
    uploads: List<UploadItem>,
    notice: String?,
    actions: DocumentsScreenActions,
    listState: LazyListState = rememberLazyListState(),
) {
    val colors = MaterialTheme.colorScheme
    val rowCount by rememberUpdatedState(state.rows.size)
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            (rowCount > 0 && last >= info.totalItemsCount - LOAD_AHEAD) to rowCount
        }
            .distinctUntilChanged()
            .filter { (nearEnd, _) -> nearEnd }
            .collect { actions.onLoadMore() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        Column(Modifier.fillMaxSize()) {
            LargeTitleBar("Documents") {
                AppIconButton(AppIcons.Settings, "Settings", actions.onOpenSettings, variant = IconButtonVariant.Tonal)
            }
            Row(Modifier.padding(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.s4, bottom = Spacing.s12)) {
                SearchField(state.text, actions.onTextChange, onSearch = actions.onSearch)
            }
            ChipRow(Modifier.padding(bottom = Spacing.s8)) {
                AppChip("Filters", {}, leadingIcon = AppIcons.Filters)
                AppChip(state.sortLabel, {}, leadingIcon = AppIcons.Sort)
            }
            val pull = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = actions.onRefresh,
                modifier = Modifier.weight(1f),
                state = pull,
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = pull,
                        isRefreshing = state.refreshing,
                        modifier = Modifier.align(Alignment.TopCenter),
                        containerColor = colors.surfaceContainerHigh,
                        color = colors.onSurfaceVariant,
                    )
                },
            ) {
                DocumentList(state, uploads, notice, actions, listState)
            }
        }
        AddButton(
            actions.onAdd,
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = Spacing.gutter, bottom = Spacing.actionBottom),
        )
    }
}

@Composable
private fun DocumentList(
    state: DocumentsUiState,
    uploads: List<UploadItem>,
    notice: String?,
    actions: DocumentsScreenActions,
    listState: LazyListState,
) {
    val colors = MaterialTheme.colorScheme
    val cardPadding = Modifier.padding(horizontal = Spacing.cardGutter)
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        // Room under the last row for the Add button.
        contentPadding = PaddingValues(bottom = 112.dp),
    ) {
        notice?.let {
            item(key = "notice") {
                NoticeBanner(
                    it,
                    modifier = cardPadding.padding(bottom = Spacing.s8),
                    actionLabel = if (actions.onOpenPlayServicesSettings != null) "Open settings" else null,
                    onAction = { actions.onOpenPlayServicesSettings?.invoke() },
                )
            }
        }
        if (uploads.isNotEmpty()) {
            item(key = "uploading") {
                SectionHeading("Uploading") {
                    if (uploads.any { it.state.isFinished }) {
                        AppButton("Clear finished", actions.onClearFinished, variant = ButtonVariant.Text)
                    }
                }
            }
            items(uploads, key = { "upload-${it.id}" }) { item ->
                val (status, text) = item.state.card()
                val documentId = (item.state as? UploadState.Done)?.documentId
                UploadCard(
                    name = item.name,
                    status = status,
                    statusText = text,
                    modifier = cardPadding.padding(bottom = Spacing.rowGap),
                    actionLabel = when {
                        item.state is UploadState.Failed -> "Retry"
                        documentId != null -> "Open"
                        else -> null
                    },
                    onAction = { if (documentId != null) actions.onOpenUpload(documentId) else actions.onRetryUpload(item.id) },
                )
            }
        }
        val total = state.total
        if (state.showResultLine && total != null) {
            item(key = "result") {
                Text(
                    resultLine(total, state.text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.s8),
                )
            }
        }
        when (val status = state.status) {
            ListStatus.Loading -> items(SKELETON_ROWS, key = { "skeleton-$it" }) {
                DocumentRowSkeleton(cardPadding.padding(bottom = Spacing.rowGap))
            }
            ListStatus.Empty -> item(key = "empty") {
                val filtered = state.text.isNotBlank() || !state.filters.isEmpty
                StatusCard(
                    icon = if (filtered) AppIcons.Search else AppIcons.File,
                    tone = StatusTone.Empty,
                    title = when {
                        state.text.isNotBlank() -> "Nothing matches \"${state.text.trim()}\""
                        filtered -> "No documents match"
                        else -> "No documents yet"
                    },
                    body = when {
                        state.text.isNotBlank() -> "Search looks inside every document, OCR text included. Try another word, or clear the filters."
                        filtered -> "No document carries every one of these filters."
                        else -> "Scan a letter, take a photo or pick a file, and it shows up here once Paperless has read it."
                    },
                    actionLabel = if (filtered) "Clear filters" else "Add a document",
                    onAction = if (filtered) actions.onClearFilters else actions.onAdd,
                    modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.s24),
                )
            }
            is ListStatus.Failed -> item(key = "failed") {
                StatusCard(
                    icon = if (status.unreachable) AppIcons.WifiOff else AppIcons.Alert,
                    tone = if (status.unreachable) StatusTone.Unreachable else StatusTone.Refused,
                    title = "Couldn't load your documents",
                    body = if (status.unreachable) "Check you're on the home network or the VPN, then try again." else status.message,
                    actionLabel = "Try again",
                    onAction = actions.onRefresh,
                    modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.s24),
                )
            }
            ListStatus.Loaded -> {
                items(state.rows, key = { it.id }) { row ->
                    DocumentRowCard(
                        title = row.title,
                        meta = row.meta,
                        tags = row.tags,
                        moreTags = row.moreTags,
                        thumbnail = row.thumbnail,
                        snippet = row.snippet?.highlighted(searchMatch),
                        // Opening a document comes with the document view (#10).
                        onClick = {},
                        modifier = cardPadding.padding(bottom = Spacing.rowGap),
                    )
                }
                val failed = state.loadMoreFailed
                when {
                    failed != null -> item(key = "more-failed") {
                        LoadMoreFailedFooter(failed, actions.onLoadMore, cardPadding)
                    }
                    state.loadingMore -> item(key = "more") { LoadingMoreFooter(cardPadding) }
                }
            }
        }
    }
}

private const val SKELETON_ROWS = 6

private fun resultLine(total: Int, text: String): String {
    val documents = if (total == 1) "1 document" else "$total documents"
    return if (text.isBlank()) documents else "$documents ${if (total == 1) "mentions" else "mention"} \"${text.trim()}\""
}

private fun UploadState.card(): Pair<UploadStatus, String> = when (this) {
    UploadState.Uploading -> UploadStatus.Working to "Uploading…"
    is UploadState.Processing -> UploadStatus.Working to "Paperless is reading it…"
    is UploadState.Done -> UploadStatus.Added to "Added to Paperless"
    is UploadState.Failed -> (if (unreachable) UploadStatus.Unreachable else UploadStatus.Refused) to message
}

private const val PLAY_SERVICES = "com.google.android.gms"

private fun timestamp(): String = SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.ROOT).format(Date())

// Previews

private val previewRows = listOf(
    DocumentRow(1, "Electricity bill — October", "City Power · 3 Oct 2026", listOf("Bills", "House"), 0, DocumentThumbnail(1, null), null),
    DocumentRow(2, "Payslip — September", "Acme Logistics · 25 Sep 2026", listOf("Payslips", "Tax"), 0, DocumentThumbnail(2, null), null),
    DocumentRow(3, "Car licence renewal", "Dept. of Transport · 18 Sep 2026", listOf("Car"), 0, DocumentThumbnail(3, null), null),
    DocumentRow(4, "Medical aid statement — August", "Discovery Health · 12 Sep 2026", listOf("Medical", "Tax"), 2, DocumentThumbnail(4, null), null),
    DocumentRow(5, "Home insurance schedule", "Old Mutual · 1 Sep 2026", listOf("Insurance", "House"), 0, DocumentThumbnail(5, null), null),
)

private val previewUploads = listOf(
    UploadItem(3, "Scan 2026-10-28 09-40-12.pdf", UploadState.Processing("t3")),
    UploadItem(2, "Photo 2026-10-28 09-38-50.jpg", UploadState.Done(42)),
    UploadItem(1, "Scan 2026-10-27 18-02-11.pdf", UploadState.Failed("Not consuming it: it's a duplicate of 'Electricity bill — October'")),
)

@Composable
private fun DocumentsPreview(state: DocumentsUiState, uploads: List<UploadItem> = emptyList(), notice: String? = null) = PaperlessTheme {
    DocumentsContent(state, uploads, notice, DocumentsScreenActions())
}

@Preview(name = "Documents — view", widthDp = 412, heightDp = 915)
@Composable
private fun DocumentsViewPreview() = DocumentsPreview(DocumentsUiState(rows = previewRows, total = 60, status = ListStatus.Loaded))

@Preview(name = "Documents — loading", widthDp = 412, heightDp = 915)
@Composable
private fun DocumentsLoadingPreview() = DocumentsPreview(DocumentsUiState())

@Preview(name = "Documents — empty", widthDp = 412, heightDp = 915)
@Composable
private fun DocumentsEmptyPreview() = DocumentsPreview(DocumentsUiState(total = 0, status = ListStatus.Empty))

@Preview(name = "Documents — load failed", widthDp = 412, heightDp = 915)
@Composable
private fun DocumentsFailedPreview() = DocumentsPreview(DocumentsUiState(status = ListStatus.Failed("Can't reach Paperless (timeout).", unreachable = true)))

@Preview(name = "Documents — uploading", widthDp = 412, heightDp = 915)
@Composable
private fun DocumentsUploadingPreview() =
    DocumentsPreview(DocumentsUiState(rows = previewRows, total = 60, status = ListStatus.Loaded), previewUploads)

@Preview(name = "Documents — loading more", widthDp = 412, heightDp = 915)
@Composable
private fun DocumentsLoadingMorePreview() =
    DocumentsPreview(DocumentsUiState(rows = previewRows, total = 60, status = ListStatus.Loaded, loadingMore = true))

@Preview(name = "Documents — scanner needs camera", widthDp = 360, heightDp = 800)
@Composable
private fun DocumentsNeedsCameraPreview() = DocumentsPreview(
    DocumentsUiState(rows = previewRows, total = 60, status = ListStatus.Loaded),
    previewUploads.take(1),
    notice = "The scanner runs in Google Play services, which needs camera access. " +
        "Open settings, then Permissions → Camera → Allow only while using the app.",
)
