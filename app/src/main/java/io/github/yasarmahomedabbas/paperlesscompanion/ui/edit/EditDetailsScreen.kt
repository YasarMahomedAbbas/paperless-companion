package io.github.yasarmahomedabbas.paperlesscompanion.ui.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Document
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Label
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.LabelKind
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Labels
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Tag
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.AppButton
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.AppDialog
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.AppTextField
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.ButtonVariant
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.FormBar
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.NoticeBanner
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.NoticeTone
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.PickerField
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.PickerFieldVariant
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.PaperlessTheme
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Which picker the form has open over itself. */
enum class EditPicker { DATE, CORRESPONDENT, DOCUMENT_TYPE, TAGS, STORAGE_PATH }

/**
 * Edit details (layouts.md § Form — Edit details): the form bar, the fields 18 apart, and Cancel /
 * Save pinned above the keyboard. Leaving with unsaved changes asks first. [onSaved] gets the
 * document as the server now has it, once.
 */
@Composable
fun EditDetailsScreen(
    viewModel: EditDetailsViewModel,
    onClose: () -> Unit,
    onSaved: (Document) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val saved by rememberUpdatedState(onSaved)
    LaunchedEffect(state.saved) { state.saved?.let { saved(it) } }
    EditDetailsContent(
        state = state,
        actions = EditDetailsActions(
            onClose = onClose,
            onTitle = viewModel::setTitle,
            onDate = viewModel::setDate,
            onCorrespondent = viewModel::setCorrespondent,
            onDocumentType = viewModel::setDocumentType,
            onStoragePath = viewModel::setStoragePath,
            onTags = viewModel::setTags,
            onSave = viewModel::save,
            onRetryLabels = viewModel::retryLabels,
            onOpenPaperless = { kind -> viewModel.labelsUrl(kind)?.let(onOpenUrl) },
        ),
    )
}

/** What the form can be asked to do; a class so the previews can pass no-ops. */
class EditDetailsActions(
    val onClose: () -> Unit = {},
    val onTitle: (String) -> Unit = {},
    val onDate: (LocalDate) -> Unit = {},
    val onCorrespondent: (Int?) -> Unit = {},
    val onDocumentType: (Int?) -> Unit = {},
    val onStoragePath: (Int?) -> Unit = {},
    val onTags: (List<Int>) -> Unit = {},
    val onSave: () -> Unit = {},
    val onRetryLabels: () -> Unit = {},
    val onOpenPaperless: (LabelKind) -> Unit = {},
)

@Composable
private fun EditDetailsContent(state: EditDetailsUiState, actions: EditDetailsActions, discardAtStart: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    var picker by rememberSaveable { mutableStateOf<EditPicker?>(null) }
    var askDiscard by rememberSaveable { mutableStateOf(discardAtStart) }
    val leave = { if (state.hasChanges) askDiscard = true else actions.onClose() }
    BackHandler(onBack = leave)

    val form = state.form
    val labels = (state.labels as? LabelsStatus.Loaded)?.labels
    // A label set on the document reads "Loading…" until the names arrive, and "Unknown" if they don't.
    fun name(id: Int?, of: List<Label>?): String? = id?.let { wanted ->
        when (state.labels) {
            LabelsStatus.Loading -> "Loading…"
            is LabelsStatus.Failed -> "Unknown"
            is LabelsStatus.Loaded -> of?.firstOrNull { it.id == wanted }?.name ?: "Unknown"
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .imePadding(),
    ) {
        FormBar("Edit details", leave)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter)
                .padding(top = Spacing.s8, bottom = Spacing.s24),
            verticalArrangement = Arrangement.spacedBy(Spacing.fieldGap),
        ) {
            state.saveFailed?.let {
                NoticeBanner(it, tone = NoticeTone.Refused, title = "Couldn't save the changes")
            }
            AppTextField(
                "Title",
                form.title,
                actions.onTitle,
                errorText = state.titleError,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            )
            PickerField(form.created?.let(::longDate), "None", { picker = EditPicker.DATE }, label = "Date", variant = PickerFieldVariant.Date)
            PickerField(name(form.correspondentId, labels?.correspondents), "None", { picker = EditPicker.CORRESPONDENT }, label = "Correspondent")
            PickerField(name(form.documentTypeId, labels?.documentTypes), "None", { picker = EditPicker.DOCUMENT_TYPE }, label = "Document type")
            PickerField(
                null,
                "None",
                { picker = EditPicker.TAGS },
                label = "Tags",
                tags = when {
                    labels != null -> form.tagIds.mapNotNull { id -> labels.tags.firstOrNull { it.id == id }?.name }
                    form.tagIds.isEmpty() -> null
                    else -> listOf(if (state.labels is LabelsStatus.Loading) "Loading…" else "${form.tagIds.size} tags")
                },
            )
            PickerField(name(form.storagePathId, labels?.storagePaths), "None", { picker = EditPicker.STORAGE_PATH }, label = "Storage path")
        }
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.actionTop, bottom = Spacing.actionBottom),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
        ) {
            AppButton("Cancel", leave, variant = ButtonVariant.Secondary)
            AppButton(
                if (state.saveFailed != null) "Save again" else "Save",
                actions.onSave,
                Modifier.weight(1f),
                busy = state.saving,
            )
        }
    }

    if (askDiscard) {
        AppDialog(
            title = "Discard changes?",
            body = "Your changes to this document haven't been saved.",
            confirmLabel = "Discard",
            onConfirm = {
                askDiscard = false
                actions.onClose()
            },
            onDismiss = { askDiscard = false },
            confirmVariant = ButtonVariant.DestructiveText,
            dismissLabel = "Keep editing",
        )
    }

    val close = { picker = null }
    when (picker) {
        EditPicker.DATE -> DateDialog(form.created, onPick = { actions.onDate(it); close() }, onDismiss = close)
        EditPicker.CORRESPONDENT -> LabelPickerSheet(
            "Correspondent", "correspondent", state.labels.choices { it.correspondents }, form.correspondentId,
            onPick = { actions.onCorrespondent(it); close() },
            onOpenPaperless = { actions.onOpenPaperless(LabelKind.CORRESPONDENT) },
            onRetry = actions.onRetryLabels,
            onDismiss = close,
        )
        EditPicker.DOCUMENT_TYPE -> LabelPickerSheet(
            "Document type", "document type", state.labels.choices { it.documentTypes }, form.documentTypeId,
            onPick = { actions.onDocumentType(it); close() },
            onOpenPaperless = { actions.onOpenPaperless(LabelKind.DOCUMENT_TYPE) },
            onRetry = actions.onRetryLabels,
            onDismiss = close,
        )
        EditPicker.STORAGE_PATH -> LabelPickerSheet(
            "Storage path", "storage path", state.labels.choices { it.storagePaths }, form.storagePathId,
            onPick = { actions.onStoragePath(it); close() },
            onOpenPaperless = { actions.onOpenPaperless(LabelKind.STORAGE_PATH) },
            onRetry = actions.onRetryLabels,
            onDismiss = close,
        )
        EditPicker.TAGS -> TagPickerSheet(
            state.labels.choices { it.tags }, form.tagIds,
            onDone = { actions.onTags(it); close() },
            onOpenPaperless = { actions.onOpenPaperless(LabelKind.TAG) },
            onRetry = actions.onRetryLabels,
            onDismiss = close,
        )
        null -> Unit
    }
}

internal fun <T> LabelsStatus.choices(of: (Labels) -> List<T>): PickerChoices<T> = when (this) {
    LabelsStatus.Loading -> PickerChoices.Loading
    is LabelsStatus.Failed -> PickerChoices.Failed
    is LabelsStatus.Loaded -> PickerChoices.Ready(of(labels))
}

/** Material's date picker; dates go through it as midnight UTC, the way it counts days. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(initial: LocalDate?, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val picker = rememberDatePickerState(initialSelectedDateMillis = initial?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            AppButton(
                "OK",
                { picker.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss() },
                variant = ButtonVariant.Text,
            )
        },
        dismissButton = { AppButton("Cancel", onDismiss, variant = ButtonVariant.Text) },
        shape = MaterialTheme.shapes.large,
        tonalElevation = 0.dp,
        colors = DatePickerDefaults.colors(containerColor = colors.surfaceContainer),
    ) {
        DatePicker(picker, colors = DatePickerDefaults.colors(containerColor = colors.surfaceContainer))
    }
}

private fun longDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH))

// --- previews ---

private val previewDocument = Document(
    id = 7, title = "Electricity bill — October", created = LocalDate.of(2026, 10, 3), added = null, modified = null,
    correspondentId = 1, documentTypeId = 1, storagePathId = null, tagIds = listOf(1, 2),
    archiveSerialNumber = null, originalFileName = null, pageCount = null, mimeType = null,
)
private val previewLabels = Labels(
    tags = listOf(Tag(1, "Bills", null, null, false, null), Tag(2, "House", null, null, false, null)),
    correspondents = listOf(Label(1, "City Power", null)),
    documentTypes = listOf(Label(1, "Invoice", null)),
    storagePaths = emptyList(),
)
private val previewState = EditForm.from(previewDocument).let { EditDetailsUiState(it, it, LabelsStatus.Loaded(previewLabels)) }

@Composable
private fun Preview(state: EditDetailsUiState, discard: Boolean = false) = PaperlessTheme {
    EditDetailsContent(state, EditDetailsActions(), discardAtStart = discard)
}

@Preview(name = "Edit details — view", widthDp = 412, heightDp = 915)
@Composable
private fun EditViewPreview() = Preview(previewState)

@Preview(name = "Edit details — field error", widthDp = 412, heightDp = 915)
@Composable
private fun EditFieldErrorPreview() = Preview(previewState.copy(form = previewState.form.copy(title = ""), titleError = "A document needs a title."))

@Preview(name = "Edit details — save failed", widthDp = 412, heightDp = 915)
@Composable
private fun EditSaveFailedPreview() = Preview(
    previewState.copy(form = previewState.form.copy(title = "Electricity bill — Oct"), saveFailed = "Can't reach Paperless. Check you're on the home network or the VPN."),
)

@Preview(name = "Edit details — labels failed", widthDp = 412, heightDp = 915)
@Composable
private fun EditLabelsFailedPreview() = Preview(previewState.copy(labels = LabelsStatus.Failed("Can't reach Paperless")))

@Preview(name = "Edit details — discard", widthDp = 412, heightDp = 915)
@Composable
private fun EditDiscardPreview() = Preview(previewState.copy(form = previewState.form.copy(title = "Changed")), discard = true)
