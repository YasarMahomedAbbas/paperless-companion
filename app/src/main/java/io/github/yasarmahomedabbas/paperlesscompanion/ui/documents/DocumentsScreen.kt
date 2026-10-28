package io.github.yasarmahomedabbas.paperlesscompanion.ui.documents

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.AppSnackbarHost
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.BulkEdit
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.LabelKind
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.AppDialog
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.BulkActionBar
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.SelectionBar
import io.github.yasarmahomedabbas.paperlesscompanion.ui.edit.LabelPickerSheet
import io.github.yasarmahomedabbas.paperlesscompanion.ui.edit.PickerChoices
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentSource
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentThumbnail
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Label
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.SavedView
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.SavedViews
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Labels
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.SearchHit
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Tag
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.UploadState
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.isFinished
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.DocumentFiles
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.AddButton
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.AppButton
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.AppChip
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.AppIconButton
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.AppIcons
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.ButtonVariant
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.ChipVariant
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
    /** Opens a document in the viewer: a row's tap, or a finished upload card's "Open". */
    onOpenDocument: (Int) -> Unit,
    /** Activity-wide, so a trash from the viewer offers its Undo here. */
    trash: TrashViewModel,
    selection: SelectionViewModel,
    /** Opens a page of the server's web interface (a bulk sheet's Open Paperless). */
    onOpenUrl: (String) -> Unit,
) {
    val state by documents.state.collectAsState()
    val trashState by trash.state.collectAsState()
    val selectionState by selection.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // The Undo stays until the app's own 8-second timer clears the offer, which cancels this and takes the snackbar away.
    LaunchedEffect(trashState.undoable) {
        val offer = trashState.undoable ?: return@LaunchedEffect
        when (snackbar.showSnackbar(offer.message, actionLabel = "Undo", duration = SnackbarDuration.Indefinite)) {
            SnackbarResult.ActionPerformed -> trash.undo()
            SnackbarResult.Dismissed -> trash.undoShown()
        }
    }
    // A bulk change shows on the loaded rows; a bulk trash ends the selecting.
    LaunchedEffect(selection) { selection.changes.collect { documents.apply(it.ids, it.edit) } }
    LaunchedEffect(trash) {
        trash.events.collect { event -> if (event is TrashEvent.Trashed) selection.stop() }
    }
    LaunchedEffect(selectionState.failure) {
        val message = selectionState.failure ?: return@LaunchedEffect
        selection.failureShown()
        scope.launch { snackbar.showSnackbar(message) }
    }
    LaunchedEffect(trashState.failure) {
        val message = trashState.failure ?: return@LaunchedEffect
        trash.failureShown()
        // Outside this effect, which restarts (and would cancel it) once the failure is cleared.
        scope.launch { snackbar.showSnackbar(message) }
    }
    val uploadState by uploads.state.collectAsState()
    val context = LocalContext.current
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    var playServicesNeedsCamera by rememberSaveable { mutableStateOf(false) }
    var sheet by rememberSaveable { mutableStateOf<DocumentsSheet?>(null) }

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
        snackbar = snackbar,
        selection = selectionState,
        actions = DocumentsScreenActions(
            onRefresh = documents::refresh,
            onLoadMore = documents::loadMore,
            onTextChange = documents::onTextChange,
            onSearch = documents::search,
            onClearFilters = documents::clearFilters,
            onStartSearch = documents::startSearch,
            onStopSearch = documents::stopSearch,
            onOpenFilters = { sheet = DocumentsSheet.FILTERS },
            onOpenSort = { sheet = DocumentsSheet.SORT },
            onRemoveTag = documents::removeTag,
            onRemoveCorrespondent = documents::removeCorrespondent,
            onRemoveDocumentType = documents::removeDocumentType,
            onChooseView = documents::chooseView,
            onClearView = documents::clearView,
            onOpenViews = { sheet = DocumentsSheet.VIEWS },
            onAdd = { sheet = DocumentsSheet.ADD },
            onOpenSettings = onOpenSettings,
            onClearFinished = uploads::clearFinished,
            onRetryUpload = uploads::retry,
            onOpenDocument = onOpenDocument,
            onStartSelecting = selection::start,
            onToggleSelected = selection::toggle,
            onSelectAll = { selection.selectAll(state.rows.map { it.id }) },
            onStopSelecting = selection::stop,
            onBulkTags = { sheet = DocumentsSheet.BULK_TAGS },
            onBulkFrom = { sheet = DocumentsSheet.BULK_FROM },
            onBulkType = { sheet = DocumentsSheet.BULK_TYPE },
            onBulkTrash = { sheet = DocumentsSheet.BULK_TRASH },
            onOpenPlayServicesSettings = if (playServicesNeedsCamera) {
                {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", PLAY_SERVICES, null)))
                }
            } else {
                null
            },
        ),
    )

    when (sheet) {
        DocumentsSheet.ADD -> AddSheet(
            onDismiss = { sheet = null },
            onScan = {
                sheet = null
                scan()
            },
            onTakePhoto = {
                sheet = null
                takePhoto()
            },
            onChooseFile = {
                sheet = null
                fileLauncher.launch(arrayOf("application/pdf", "image/*"))
            },
        )
        DocumentsSheet.SORT -> SortSheet(
            selected = state.sort,
            onSelect = {
                sheet = null
                documents.setSort(it)
            },
            onDismiss = { sheet = null },
        )
        DocumentsSheet.FILTERS -> FiltersSheet(
            applied = state.filters,
            labels = state.labels,
            previewCount = state.filterPreviewCount,
            onPreview = documents::previewFilters,
            onApply = {
                sheet = null
                documents.setFilters(it)
            },
            onRetry = documents::refresh,
            onDismiss = {
                sheet = null
                documents.stopPreview()
            },
        )
        DocumentsSheet.VIEWS -> ViewsSheet(
            views = state.views?.all.orEmpty(),
            chosen = state.view?.id,
            onChoose = {
                sheet = null
                documents.chooseView(it)
            },
            onDismiss = { sheet = null },
        )
        DocumentsSheet.BULK_TAGS -> {
            val start = remember(selectionState.selected, state.labels) {
                tagMarks(documents.documents(selectionState.selected), state.labels?.tags.orEmpty())
            }
            BulkTagsSheet(
                choices = state.labels.choices { it.tags },
                start = start,
                count = selectionState.selected.size,
                onDone = { from, to ->
                    sheet = null
                    selection.changeTags(from, to)
                },
                onOpenPaperless = { selection.labelsUrl(LabelKind.TAG)?.let(onOpenUrl) },
                onRetry = documents::refresh,
                onDismiss = { sheet = null },
            )
        }
        DocumentsSheet.BULK_FROM -> LabelPickerSheet(
            "From", "correspondent", state.labels.choices { it.correspondents }, selectedId = null,
            onPick = {
                sheet = null
                selection.change(BulkEdit.SetCorrespondent(it))
            },
            onOpenPaperless = { selection.labelsUrl(LabelKind.CORRESPONDENT)?.let(onOpenUrl) },
            onRetry = documents::refresh,
            onDismiss = { sheet = null },
            showCurrent = false,
        )
        DocumentsSheet.BULK_TYPE -> LabelPickerSheet(
            "Type", "document type", state.labels.choices { it.documentTypes }, selectedId = null,
            onPick = {
                sheet = null
                selection.change(BulkEdit.SetDocumentType(it))
            },
            onOpenPaperless = { selection.labelsUrl(LabelKind.DOCUMENT_TYPE)?.let(onOpenUrl) },
            onRetry = documents::refresh,
            onDismiss = { sheet = null },
            showCurrent = false,
        )
        DocumentsSheet.BULK_TRASH -> {
            val count = selectionState.selected.size
            AppDialog(
                title = if (count == 1) "Move 1 document to trash?" else "Move $count documents to trash?",
                body = "They go to the Paperless trash. You can bring them back from there.",
                confirmLabel = "Move to trash",
                onConfirm = {
                    sheet = null
                    trash.trash(selectionState.selected.toList())
                },
                onDismiss = { sheet = null },
                confirmVariant = ButtonVariant.Destructive,
            )
        }
        null -> Unit
    }
}

/** Picker choices from the list's labels; without them (they failed to load) the sheet offers Try again. */
private fun <T> Labels?.choices(of: (Labels) -> List<T>): PickerChoices<T> =
    if (this == null) PickerChoices.Failed else PickerChoices.Ready(of(this))

/** The sheets (and the bulk trash dialog) Documents opens over itself. */
enum class DocumentsSheet { ADD, SORT, FILTERS, VIEWS, BULK_TAGS, BULK_FROM, BULK_TYPE, BULK_TRASH }

/** What the Documents screen can be asked to do; a class so the previews can pass no-ops. */
class DocumentsScreenActions(
    val onRefresh: () -> Unit = {},
    val onLoadMore: () -> Unit = {},
    val onTextChange: (String) -> Unit = {},
    val onSearch: () -> Unit = {},
    val onClearFilters: () -> Unit = {},
    val onStartSearch: () -> Unit = {},
    val onStopSearch: () -> Unit = {},
    val onOpenFilters: () -> Unit = {},
    val onOpenSort: () -> Unit = {},
    val onRemoveTag: (Int) -> Unit = {},
    val onRemoveCorrespondent: () -> Unit = {},
    val onRemoveDocumentType: () -> Unit = {},
    val onChooseView: (SavedView) -> Unit = {},
    val onClearView: () -> Unit = {},
    val onOpenViews: () -> Unit = {},
    val onAdd: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onClearFinished: () -> Unit = {},
    val onRetryUpload: (Long) -> Unit = {},
    val onOpenDocument: (Int) -> Unit = {},
    val onStartSelecting: (Int) -> Unit = {},
    val onToggleSelected: (Int) -> Unit = {},
    val onSelectAll: () -> Unit = {},
    val onStopSelecting: () -> Unit = {},
    val onBulkTags: () -> Unit = {},
    val onBulkFrom: () -> Unit = {},
    val onBulkType: () -> Unit = {},
    val onBulkTrash: () -> Unit = {},
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
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    selection: SelectionUiState = SelectionUiState(),
    listState: LazyListState = rememberLazyListState(),
) {
    val colors = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    val stopSearching = {
        focus.clearFocus()
        actions.onStopSearch()
    }
    BackHandler(enabled = state.searching, onBack = stopSearching)
    // Registered after searching's, so Back leaves selecting first.
    BackHandler(enabled = selection.selecting, onBack = actions.onStopSelecting)
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
            // Searching swaps the title row for a back arrow beside the field; the field stays put, so it keeps focus.
            if (selection.selecting) {
                SelectionBar(selection.selected.size, actions.onStopSelecting, actions.onSelectAll)
            } else if (!state.searching) {
                LargeTitleBar("Documents") {
                    AppIconButton(AppIcons.Settings, "Settings", actions.onOpenSettings, variant = IconButtonVariant.Tonal)
                }
            }
            Row(
                Modifier.padding(
                    start = if (state.searching) Spacing.s8 else Spacing.gutter,
                    end = Spacing.gutter,
                    top = if (state.searching) Spacing.s12 else Spacing.s4,
                    bottom = Spacing.s12,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s4),
            ) {
                if (state.searching) AppIconButton(AppIcons.Back, "Stop searching", stopSearching)
                SearchField(
                    state.text,
                    actions.onTextChange,
                    Modifier.weight(1f),
                    onSearch = actions.onSearch,
                    onFocusChange = { focused -> if (focused) actions.onStartSearch() },
                )
            }
            FilterChips(state, actions, Modifier.padding(bottom = Spacing.s12))
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
                DocumentList(state, uploads, notice, actions, listState, selection)
            }
        }
        // The Add button moves up above a snackbar.
        val lift by animateDpAsState(if (snackbar.currentSnackbarData != null) SNACKBAR_LIFT else 0.dp, label = "lift")
        if (selection.selecting) {
            BulkActionBar(
                actions.onBulkTags,
                actions.onBulkFrom,
                actions.onBulkType,
                actions.onBulkTrash,
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = Spacing.cardGutter, end = Spacing.cardGutter, bottom = Spacing.s24 + lift),
                enabled = !selection.working,
            )
        } else {
            AddButton(
                actions.onAdd,
                Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = Spacing.gutter, bottom = Spacing.actionBottom + lift),
            )
        }
        AppSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

/** How far the Add button rises over a snackbar: the snackbar's height and a gap. */
private val SNACKBAR_LIFT = 64.dp

/**
 * The home saved views (the chosen one marked), a chosen view that isn't a home one, Views, then Filters
 * (with how many are on), one removable chip per applied filter, and the sort order.
 */
@Composable
private fun FilterChips(state: DocumentsUiState, actions: DocumentsScreenActions, modifier: Modifier = Modifier) {
    val filters = state.filters
    val labels = state.labels
    val views = state.views
    ChipRow(modifier) {
        views?.home?.forEach { view ->
            val chosen = state.view?.id == view.id
            AppChip(
                view.name,
                { actions.onChooseView(view) },
                variant = if (chosen) ChipVariant.Selected else ChipVariant.Unselected,
            )
        }
        state.view?.takeIf { chosen -> views?.home.orEmpty().none { it.id == chosen.id } }?.let { chosen ->
            AppChip(chosen.name, actions.onClearView, variant = ChipVariant.Selected, contentDescription = "Stop showing ${chosen.name}")
        }
        if (!views?.all.isNullOrEmpty()) AppChip("Views", actions.onOpenViews, leadingIcon = AppIcons.Views)
        if (filters.isEmpty) {
            AppChip("Filters", actions.onOpenFilters, leadingIcon = AppIcons.Filters)
        } else {
            AppChip("Filters · ${filters.count}", actions.onOpenFilters, variant = ChipVariant.Selected, leadingIcon = AppIcons.Filters)
        }
        if (labels != null) {
            filters.tagIds.forEach { id ->
                labels.tags.firstOrNull { it.id == id }?.let { RemovableChip(it.name) { actions.onRemoveTag(id) } }
            }
            filters.correspondentId?.let { id ->
                labels.correspondents.firstOrNull { it.id == id }?.let { RemovableChip(it.name, actions.onRemoveCorrespondent) }
            }
            filters.documentTypeId?.let { id ->
                labels.documentTypes.firstOrNull { it.id == id }?.let { RemovableChip(it.name, actions.onRemoveDocumentType) }
            }
        }
        // While searching, results come by relevance, so there is no order to choose.
        AppChip(
            state.sortLabel,
            actions.onOpenSort,
            leadingIcon = if (state.text.isBlank()) AppIcons.Sort else null,
            enabled = state.text.isBlank(),
        )
    }
}

@Composable
private fun RemovableChip(name: String, onRemove: () -> Unit) {
    AppChip(name, onRemove, variant = ChipVariant.Removable, contentDescription = "Remove filter $name")
}

@Composable
private fun DocumentList(
    state: DocumentsUiState,
    uploads: List<UploadItem>,
    notice: String?,
    actions: DocumentsScreenActions,
    listState: LazyListState,
    selection: SelectionUiState,
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
                    onAction = { if (documentId != null) actions.onOpenDocument(documentId) else actions.onRetryUpload(item.id) },
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
                val view = state.view
                val emptyView = view != null && !filtered
                StatusCard(
                    icon = if (filtered || emptyView) AppIcons.Search else AppIcons.File,
                    tone = StatusTone.Empty,
                    statement = when {
                        state.text.isNotBlank() -> "Nothing matches \"${state.text.trim()}\""
                        filtered -> "No documents match"
                        view != null -> "Nothing in ${view.name}"
                        else -> "No documents yet"
                    },
                    hint = when {
                        state.text.isNotBlank() -> "Search looks inside every document, OCR text included. Try another word, or clear the filters."
                        filtered -> "No document carries every one of these filters."
                        emptyView -> "No document is in this saved view right now."
                        else -> "Scan a letter, take a photo or pick a file, and it shows up here once Paperless has read it."
                    },
                    actionLabel = when {
                        filtered -> "Clear filters"
                        emptyView -> "Show all documents"
                        else -> "Add a document"
                    },
                    onAction = when {
                        filtered -> actions.onClearFilters
                        emptyView -> actions.onClearView
                        else -> actions.onAdd
                    },
                    modifier = Modifier.padding(vertical = Spacing.s24),
                )
            }
            is ListStatus.Failed -> item(key = "failed") {
                StatusCard(
                    icon = if (status.unreachable) AppIcons.WifiOff else AppIcons.Alert,
                    tone = if (status.unreachable) StatusTone.Unreachable else StatusTone.Refused,
                    statement = "Couldn't load your documents",
                    hint = if (status.unreachable) "Check you're on the home network or the VPN, then try again." else status.message,
                    actionLabel = "Try again",
                    onAction = actions.onRefresh,
                    modifier = Modifier.padding(vertical = Spacing.s24),
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
                        onClick = { if (selection.selecting) actions.onToggleSelected(row.id) else actions.onOpenDocument(row.id) },
                        modifier = cardPadding.padding(bottom = Spacing.rowGap),
                        selection = if (selection.selecting) row.id in selection.selected else null,
                        onLongClick = { if (selection.selecting) actions.onToggleSelected(row.id) else actions.onStartSelecting(row.id) },
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

@Preview(name = "Documents — selecting", widthDp = 412, heightDp = 915)
@Composable
private fun DocumentsSelectingPreview() = PaperlessTheme {
    DocumentsContent(
        DocumentsUiState(rows = previewRows, total = 60, status = ListStatus.Loaded),
        emptyList(),
        null,
        DocumentsScreenActions(),
        selection = SelectionUiState(selected = setOf(1, 3, 4)),
    )
}

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

private val previewLabels = Labels(
    tags = listOf(Tag(1, "Bills", null, null, false, null), Tag(2, "House", null, null, false, null)),
    correspondents = listOf(Label(1, "City Power", null)),
    documentTypes = emptyList(),
    storagePaths = emptyList(),
)

@Preview(name = "Search — searching", widthDp = 412, heightDp = 915)
@Composable
private fun DocumentsSearchingPreview() = DocumentsPreview(
    DocumentsUiState(
        text = "rates",
        searching = true,
        rows = previewRows.take(3).map {
            it.copy(snippet = SearchHit(1.0, "…property rates for erf 4127, valuation R 1 850 000, due 15 Sept…", listOf(10..14)))
        },
        total = 12,
        status = ListStatus.Loaded,
    ),
)

@Preview(name = "Filters — applied", widthDp = 412, heightDp = 915)
@Composable
private fun DocumentsFilteredPreview() = DocumentsPreview(
    DocumentsUiState(filters = DocumentFilters(tagIds = setOf(1, 2)), labels = previewLabels, rows = previewRows, total = 23, status = ListStatus.Loaded),
)

@Preview(name = "Search — no results", widthDp = 412, heightDp = 915)
@Composable
private fun DocumentsNoResultsPreview() = DocumentsPreview(
    DocumentsUiState(
        text = "pool service",
        searching = true,
        filters = DocumentFilters(tagIds = setOf(2)),
        labels = previewLabels,
        total = 0,
        status = ListStatus.Empty,
    ),
)

@Preview(name = "Documents — scanner needs camera", widthDp = 360, heightDp = 800)
@Composable
private fun DocumentsNeedsCameraPreview() = DocumentsPreview(
    DocumentsUiState(rows = previewRows, total = 60, status = ListStatus.Loaded),
    previewUploads.take(1),
    notice = "The scanner runs in Google Play services, which needs camera access. " +
        "Open settings, then Permissions → Camera → Allow only while using the app.",
)

private val previewViews = SavedViews(
    home = listOf(SavedView(1, "Inbox", true, true), SavedView(2, "This month", true, false)),
    all = listOf(SavedView(1, "Inbox", true, true), SavedView(3, "Tax 2026", false, true), SavedView(2, "This month", true, false)),
)

@Preview(name = "Saved views — chips", widthDp = 412, heightDp = 915)
@Composable
private fun DocumentsViewsPreview() = DocumentsPreview(
    DocumentsUiState(views = previewViews, rows = previewRows, total = 60, status = ListStatus.Loaded),
)

@Preview(name = "Saved views — one chosen", widthDp = 412, heightDp = 915)
@Composable
private fun DocumentsViewChosenPreview() = DocumentsPreview(
    DocumentsUiState(
        views = previewViews,
        view = previewViews.home.first(),
        viewOrder = true,
        rows = previewRows.take(2),
        total = 2,
        status = ListStatus.Loaded,
    ),
)

@Preview(name = "Saved views — empty view", widthDp = 412, heightDp = 915)
@Composable
private fun DocumentsViewEmptyPreview() = DocumentsPreview(
    DocumentsUiState(views = previewViews, view = previewViews.all[1], viewOrder = true, total = 0, status = ListStatus.Empty),
)
