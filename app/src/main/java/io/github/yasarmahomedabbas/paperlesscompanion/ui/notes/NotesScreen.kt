package io.github.yasarmahomedabbas.paperlesscompanion.ui.notes

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Note
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.AppButton
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.AppIcons
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.AppTextField
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.BackBar
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.NoticeBanner
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.NoticeTone
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.SkeletonBlock
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.StatusCard
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.StatusTone
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.PaperlessTheme
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.Spacing
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.lead
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A document's notes (layouts.md § Detail, pushed over the viewer): the back bar titled "Notes"
 * with the document's [title] under it, the notes newest first as cards, and "Add a note" pinned
 * above the keyboard.
 */
@Composable
fun NotesScreen(viewModel: NotesViewModel, title: String, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    BackHandler(onBack = onBack)
    NotesContent(state, title, onBack, viewModel::setDraft, viewModel::add, viewModel::retry)
}

@Composable
private fun NotesContent(
    state: NotesUiState,
    title: String,
    onBack: () -> Unit,
    onDraft: (String) -> Unit,
    onAdd: () -> Unit,
    onRetry: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .imePadding(),
    ) {
        BackBar("Notes", onBack, subtitle = title.ifBlank { "Untitled" })
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val status = state.status) {
                NotesStatus.Loading -> Column(
                    Modifier.padding(horizontal = Spacing.cardGutter).padding(top = Spacing.s8),
                    verticalArrangement = Arrangement.spacedBy(Spacing.rowGap),
                ) { repeat(SKELETON_NOTES) { NoteSkeleton() } }
                is NotesStatus.Failed -> Centred {
                    StatusCard(
                        icon = if (status.unreachable) AppIcons.WifiOff else AppIcons.Alert,
                        tone = if (status.unreachable) StatusTone.Unreachable else StatusTone.Refused,
                        statement = if (status.unreachable) "Can't reach Paperless" else "Couldn't load the notes",
                        hint = if (status.unreachable) {
                            "Check you're on the home network or the VPN, then try again."
                        } else {
                            "Paperless didn't send them. Try again in a moment."
                        },
                        actionLabel = "Try again",
                        onAction = onRetry,
                    )
                }
                NotesStatus.Loaded -> if (state.notes.isEmpty()) {
                    Centred {
                        StatusCard(
                            icon = AppIcons.Note,
                            tone = StatusTone.Empty,
                            statement = "No notes yet",
                            hint = "Write the first one below. Everyone who can see this document can read it.",
                        )
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = Spacing.cardGutter, end = Spacing.cardGutter, top = Spacing.s8, bottom = Spacing.s8),
                        verticalArrangement = Arrangement.spacedBy(Spacing.rowGap),
                    ) {
                        items(state.notes, key = { it.id }) { NoteCard(it) }
                    }
                }
            }
        }
        AddNote(state, onDraft, onAdd)
    }
}

/** The field and Add, pinned under the notes; a failed add shows above them and keeps the text. */
@Composable
private fun AddNote(state: NotesUiState, onDraft: (String) -> Unit, onAdd: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.s12, bottom = Spacing.s16),
        verticalArrangement = Arrangement.spacedBy(Spacing.s12),
    ) {
        state.addFailed?.let { NoticeBanner(it, tone = NoticeTone.Refused, title = "Couldn't add the note") }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.s12)) {
            AppTextField(
                "Add a note",
                state.draft,
                onDraft,
                Modifier.weight(1f),
                enabled = !state.adding,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                multiLine = true,
            )
            AppButton("Add", onAdd, enabled = state.draft.isNotBlank(), busy = state.adding)
        }
    }
}

/** One note: its text (selectable), then who wrote it and when. */
@Composable
private fun NoteCard(note: Note) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer, MaterialTheme.shapes.medium)
            .padding(Spacing.s16)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(Spacing.s8),
    ) {
        SelectionContainer { Text(note.text, style = MaterialTheme.typography.lead, color = colors.onSurface) }
        noteMeta(note)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
    }
}

@Composable
private fun NoteSkeleton() {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
            .padding(Spacing.s16),
        verticalArrangement = Arrangement.spacedBy(Spacing.s8),
    ) {
        SkeletonBlock(Modifier.fillMaxWidth().height(14.dp))
        SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(14.dp))
        SkeletonBlock(Modifier.fillMaxWidth(0.35f).height(11.dp))
    }
}

@Composable
private fun Centred(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = Spacing.s24),
        contentAlignment = Alignment.Center,
    ) { content() }
}

private const val SKELETON_NOTES = 3

private val NOTE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH)

/** "Ann · 3 Oct 2026, 09:14", just the date without an author, the author without a date, or null. */
internal fun noteMeta(note: Note, zone: ZoneId = ZoneId.systemDefault()): String? =
    listOfNotNull(note.author, note.created?.atZone(zone)?.format(NOTE_TIME)).joinToString(" · ").ifEmpty { null }

// --- previews ---

private val previewNotes = listOf(
    Note(3, "Paid by EFT on 5 Oct. Reference CP-88231.", Instant.parse("2026-10-05T07:14:00Z"), "Ann Lee"),
    Note(2, "The meter reading looks too high; asked City Power to check.", Instant.parse("2026-10-03T16:40:00Z"), "bob"),
    Note(1, "Imported from the old filing cabinet.", Instant.parse("2026-10-01T09:00:00Z"), null),
)

@Composable
private fun NotesPreview(state: NotesUiState) = PaperlessTheme {
    NotesContent(state, "Electricity bill — October", {}, {}, {}, {})
}

@Preview(name = "Notes", widthDp = 412, heightDp = 915)
@Composable
private fun NotesLoadedPreview() = NotesPreview(NotesUiState(NotesStatus.Loaded, previewNotes, draft = "Disputed"))

@Preview(name = "Notes — loading", widthDp = 412, heightDp = 915)
@Composable
private fun NotesLoadingPreview() = NotesPreview(NotesUiState())

@Preview(name = "Notes — empty", widthDp = 412, heightDp = 915)
@Composable
private fun NotesEmptyPreview() = NotesPreview(NotesUiState(NotesStatus.Loaded))

@Preview(name = "Notes — load failed", widthDp = 412, heightDp = 915)
@Composable
private fun NotesFailedPreview() = NotesPreview(NotesUiState(NotesStatus.Failed("timeout", unreachable = true)))

@Preview(name = "Notes — add failed", widthDp = 412, heightDp = 915)
@Composable
private fun NotesAddFailedPreview() = NotesPreview(
    NotesUiState(NotesStatus.Loaded, previewNotes, draft = "Disputed with City Power", addFailed = "You don't have permission to do that."),
)
