# Checklist — Organise documents: edit details, delete, bulk edit

Issue: #11 — Organise documents: edit details, delete, bulk edit (https://github.com/YasarMahomedAbbas/paperless-companion/issues/11)
Branch: `feat/11-organise-documents`
Problem: from the phone you can upload and (with #9 and #10) find and read a document, but you can't fix its title, date, correspondent, type, tags or storage path, trash it, or change several documents at once. All of that still needs the web UI.

## Done when

- Edit details (title, date, correspondent, document type, tags, storage path) opens from the document view as a full screen with Cancel and Save. The pickers are loaded from the server, and a picker search that finds nothing links to that label's page in the Paperless web UI. Nothing creates a tag or correspondent.
- Move to trash, from the document view's More menu, asks first. The app then goes back to the list with an 8-second snackbar whose Undo restores the document from the Paperless trash.
- Long-press on a list row starts selecting. The bulk bar's Tags, From and Type change every selected document in one call, and Trash moves them all to the trash (confirmed, with Undo).
- Each change shows in the Paperless web UI straight after (self-verification against a real server when `verify.env` allows writes, otherwise recorded as not run).
- ViewModels tested; CI passes (`testDebugUnitTest koverVerify lintDebug assembleDebug`), coverage ≥ 80%, `ArchitectureTest` green.

## References

- Paperless API: **no new server call.** Every write goes through #7's `DocumentLibrary`. That covers `PATCH /api/documents/{id}/` (partial JSON; returns the document), `DELETE /api/documents/{id}/` (soft delete to the trash, 204), `POST /api/trash/` `{"documents":[ids],"action":"restore"}`, and `POST /api/documents/bulk_edit/` with `modify_tags` (`add_tags`, `remove_tags`), `set_correspondent` and `set_document_type` (null clears). Bulk delete uses `POST /api/documents/delete/` (v10), with the `bulk_edit` `delete` fallback on 405/404 (Paperless 2.x). #7 checked these shapes against the server source at v3.3.4 and v2.20.15 (`docs/tasks/7-document-data-layer/CHECKLIST.md` § References). `~/.config/paperless-companion/verify.env` is absent and docs.paperless-ngx.com answers 403 (Cloudflare), so no live `/api/schema/` was read. Self-verification must read it and try each write against a real server.
- Paperless web UI routes for the "no match → Open Paperless" link: `/tags`, `/correspondents`, `/documenttypes` and `/storagepaths`. They were checked in `src-ui/src/app/app-routing.module.ts`. In v2.20.15 they are pages of their own; on main (3.x) they redirect to `/attributes/<section>`. The bare paths work on both.
- Design: canvas https://claude.ai/artifact/YPcrFxT1joGdxmBHfiMdf6, rows "Edit details", "Tag and correspondent pickers", "Delete with undo" and "Select several". The manual is `docs/design/`: `layouts.md` § Navigation, § Form — Edit details, § Sheet, § Dialog; `components.md` § Picker field, § Option list, § Dialog, § Menu, § Snackbar, § Bulk action bar, § Top bars (Selection bar, Form bar), § Document row (selectable variant). The manual wins over this checklist on anything visual. Every screen is built with the `design-recipe` skill.
- Related: #9 (the list: long-press, rows, sheets, chips; in progress), #10 (the viewer: Edit button, More menu, snackbar; in progress), #21 (a 403 reads as "sign in again"; open).

## Context

#7 left every write as a use case: `UpdateDocumentUseCase(id, DocumentChanges) → Document`, `DeleteDocumentUseCase(id)`, `TrashUseCase.restore(ids)`, `BulkEditUseCase(ids, BulkEdit)` and `LoadLabelsUseCase() → Labels`. `DocumentChanges` keeps, sets or clears each field. The screens this ticket hangs off are being built in parallel: #9 makes the Documents list home and adds the shared sheet, option-row, chip, search-field and status parts; #10 adds the document view with its details panel, More menu and snackbar. **Decisions** (see NOTES; the pipeline took Q1–Q3 on the user's behalf). Checkpoints 1–2 are ViewModels and pure state in new files that need nothing from #9 or #10. Checkpoints 3–5 begin by merging `origin/main` and need #9 and #10 merged: they reuse those parts and never duplicate them. "Select all" selects the rows already loaded. After any change the list patches its loaded rows in place and never reloads from the top: an edit uses the document the server returns, a bulk change is applied locally, trash removes the rows and Undo puts them back where they were. Delete calls the server as soon as it's confirmed, and Undo restores from the trash. One web-UI link is new: `DocumentServer.labelsUrl`, built in `data/` like `documentUrl`.

Paths below are under `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/` (main) and `app/src/test/java/io/github/yasarmahomedabbas/paperlesscompanion/` (test).

## Test plan

- Unit (`EditDetailsViewModelTest`, fakes, `MainDispatcherRule`):
  - It starts from the document, and the labels load, or fail with Try again.
  - Each setter changes the form. `hasChanges` is false until something differs, and false again when the change is undone.
  - `changes()` holds only the fields that differ, so clearing a correspondent gives `To(null)`.
  - Save with a blank title sets the title error and makes no call. Save with nothing changed closes without a call. Save sends the diff and ends in `saved` with the returned document.
  - A failed save keeps every field and shows the message and "Save again" (unreachable vs refused wording via `userMessage()`). A second save while one is running is ignored.
  - `labelsUrl` passes through.
- Unit (`PickerFilterTest`): name matching ignores case and surrounding blanks, an empty query returns everything, and chosen tags float to the top.
- Unit (`BulkTagsTest`): with no documents everything is NONE. The marks are ALL / SOME / NONE across the selected documents' tags. Toggling SOME gives ALL, ALL gives NONE and NONE gives ALL. `changes()` adds only the newly ALL tags and removes only the newly NONE ones, and leaving everything as it was gives no change.
- Unit (`DocumentEditTest`, domain): `Document.with(BulkEdit)` for add/remove tags (order kept, no duplicates), setting and clearing the correspondent, type and storage path; `Delete` leaves it unchanged.
- Unit (`TrashViewModelTest`, virtual time):
  - One document goes through `DeleteDocumentUseCase`, several through `BulkEdit.Delete`. Success emits `Trashed` and offers Undo for 8 s: still there at 7 999 ms, gone at 8 000.
  - `undo()` restores the same ids and emits `Restored`. A failed undo shows "Couldn't bring it back" with the reason, and a failed trash shows the message and emits nothing.
  - A second trash replaces the earlier undo offer.
- Unit (`SelectionViewModelTest`):
  - `start(id)` selects one. `toggle` adds and removes, and removing the last one stops selecting. `selectAll(ids)` selects exactly the loaded ids, and `stop()` clears.
  - Tags, correspondent and type each send one `BulkEdit` with the selected ids and emit `Changed(ids, edit)`, then stop selecting.
  - A bulk-tags call with no change makes no call. A failure keeps the selection and shows the message.
- Unit (`DocumentsViewModelTest`, extended in checkpoint 4/5): `patch(document)` replaces one row in place and keeps the others and the page; `apply(ids, edit)` patches several; `remove(ids)` drops rows and `putBack(ids)` re-inserts them at their old positions; `total` follows.
- Unit (`PaperlessApiTest`): `labelsUrl` gives `<base>/tags`, `/correspondents`, `/documenttypes`, `/storagepaths`.
- MockWebServer: none new (no new call). #7's `PaperlessLibraryTest` covers PATCH, DELETE, restore and each bulk edit body, including the 2.x delete fallback.
- Real server (self-verification): read `/api/schema/` and check PATCH `/api/documents/{id}/` (title, created, correspondent, document_type, storage_path, tags), `DELETE /api/documents/{id}/`, `POST /api/trash/` restore, and the `bulk_edit` methods `modify_tags`, `set_correspondent` and `set_document_type`, plus `/api/documents/delete/`. With `PAPERLESS_ALLOW_WRITES=1`, and only on a document it uploaded itself: change and restore each field, bulk add and remove a tag, trash it, restore it, then trash it again. Confirm each change in the web UI's document page (`/api/documents/{id}/` read back). Skipped and recorded if `verify.env` is absent.
- Device only: long-press feel; the date picker; the keyboard keeping the action area visible; the 8-second Undo; TalkBack on selected rows and mixed checkboxes; Open Paperless opening the browser at the right page.

---

## Checkpoint 1 — Edit details ViewModel · commit: `feat(documents): edit a document's details in a ViewModel`

Goal: everything the Edit details screen and its pickers show exists as tested state, with no UI yet.

### [x] 1.1 — Link to a label's page in the web UI

Files: `domain/model/Documents.kt`, `domain/repository/Repositories.kt`, `data/PaperlessApi.kt`, `domain/usecase/DocumentLinkUseCase.kt`, test `Fakes.kt`, `data/PaperlessApiTest.kt`
- `enum class LabelKind { TAG, CORRESPONDENT, DOCUMENT_TYPE, STORAGE_PATH }` in `Documents.kt`.
- `DocumentServer.labelsUrl(config: ServerConfig, kind: LabelKind): String`, KDoc "Where the server's own web interface manages this kind of label (new ones are made there)".
- `PaperlessApi`: `"${config.baseUrl}/" + when (kind) { TAG -> "tags"; CORRESPONDENT -> "correspondents"; DOCUMENT_TYPE -> "documenttypes"; STORAGE_PATH -> "storagepaths" }`, next to `documentUrl`.
- `class LabelsLinkUseCase(server, settings) { operator fun invoke(kind: LabelKind): String? }`, null when signed out, mirroring `DocumentLinkUseCase` in the same file.
- `FakeServer.labelsUrl` gives `"${config.baseUrl}/labels/$kind"`. Tests: one `PaperlessApiTest` case per kind; `DocumentLinkUseCaseTest` gets signed-in and signed-out cases for `LabelsLinkUseCase`.

### [x] 1.2 — Edit form state

File: `ui/edit/EditDetailsUiState.kt` (new)
- `data class EditForm(val title: String, val created: LocalDate?, val correspondentId: Int?, val documentTypeId: Int?, val storagePathId: Int?, val tagIds: List<Int>)`, with `companion fun from(document: Document)`.
- `internal fun EditForm.changesFrom(original: EditForm): DocumentChanges`: `To` for each differing field, otherwise `Keep`. The title is trimmed. `created` is sent only when non-null and different, because a date can't be cleared (`Change<LocalDate>`). Tags are compared as sets, and when they differ the form's order is sent.
- `sealed interface LabelsStatus { Loading; data class Loaded(val labels: Labels); data class Failed(val message: String) }`.
- `data class EditDetailsUiState(val form: EditForm, val original: EditForm, val labels: LabelsStatus = Loading, val titleError: String? = null, val saving: Boolean = false, val saveFailed: String? = null, val saved: Document? = null)` with `val hasChanges get() = !form.changesFrom(original).isEmpty`.

### [x] 1.3 — EditDetailsViewModel

File: `ui/edit/EditDetailsViewModel.kt` (new)
`class EditDetailsViewModel(document: Document, private val update: UpdateDocumentUseCase, private val loadLabels: LoadLabelsUseCase, private val labelsLink: LabelsLinkUseCase) : ViewModel()`, `val state: StateFlow<EditDetailsUiState>`. It loads labels on `init`.
- `setTitle(String)` (clears `titleError`), `setDate(LocalDate)`, `setCorrespondent(Int?)`, `setDocumentType(Int?)`, `setStoragePath(Int?)`, `setTags(List<Int>)`.
- `retryLabels()`. A failure gives `Failed(e.userMessage())` for a `ServerException`, or "Couldn't load the labels" otherwise.
- `save()`: ignored while `saving`. A blank title sets `titleError = "A document needs a title."` and stops. With no changes it sets `saved = document` (the original) without a call. Otherwise it calls `update(document.id, changes)`: success sets `saved`, failure sets `saveFailed` (`userMessage()` or "Couldn't save the changes") and keeps the form.
- `fun labelsUrl(kind: LabelKind): String? = labelsLink(kind)`.

### [x] 1.4 — Picker filtering

File: `ui/edit/PickerFilter.kt` (new)
`internal fun <T> List<T>.matching(query: String, name: (T) -> String): List<T>`: case-insensitive `contains` on the trimmed query; a blank query returns the list. `internal fun List<Tag>.chosenFirst(chosen: Collection<Int>): Pair<List<Tag>, List<Tag>>` gives the chosen tags in the form's order and then the rest in the server's order (the sheet draws a divider between them).

### [x] 1.5 — Tests

Files: `ui/EditDetailsViewModelTest.kt` (new), `ui/PickerFilterTest.kt` (new); `Fakes.kt` gains `var updateFailure: Exception?` on `FakeLibrary`, thrown only by `update` (the label calls must still answer), and `var updateGate: CompletableDeferred<Unit>?` so a test can hold a save in flight.
The cases in the test plan's first two bullets.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*EditDetailsViewModelTest' --tests '*PickerFilterTest' --tests '*PaperlessApiTest' --tests '*DocumentLinkUseCaseTest' --tests '*ArchitectureTest'`

---

## Checkpoint 2 — Trash with undo and bulk change, as state · commit: `feat(documents): trash with undo and change many documents in ViewModels`

Goal: trashing (one or many) with an 8-second Undo, selection, and the three bulk changes exist as tested state, with no UI yet.

### [x] 2.1 — Apply a bulk change locally

File: `domain/model/DocumentEdits.kt` (new)
`fun Document.with(edit: BulkEdit): Document`. `ModifyTags` drops `remove` and appends each of `add` the document lacks, keeping its order. `SetCorrespondent`, `SetDocumentType` and `SetStoragePath` set the id (null clears). `Delete` returns the document unchanged. Test: `domain/DocumentEditTest.kt`.

### [x] 2.2 — TrashViewModel

File: `ui/documents/TrashViewModel.kt` (new)
`class TrashViewModel(private val delete: DeleteDocumentUseCase, private val bulkEdit: BulkEditUseCase, private val trash: TrashUseCase, private val undoMillis: Long = 8_000) : ViewModel()`. It is activity-scoped: it outlives the viewer, so the Undo shows on the list.
- `data class Undoable(val ids: List<Int>, val message: String)`, where the message is "Moved to trash" for one and "3 moved to trash" for many. `data class TrashUiState(val working: Boolean = false, val undoable: Undoable? = null, val failure: String? = null)`.
- `sealed interface TrashEvent { data class Trashed(val ids: List<Int>); data class Restored(val ids: List<Int>) }` on `val events: SharedFlow<TrashEvent>`.
- `fun trash(ids: List<Int>)`: ignored while `working` or for none. One id uses `delete`, more use `bulkEdit(ids, BulkEdit.Delete)`. Success emits `Trashed` and sets `undoable`, replacing any earlier one, and a job clears it after `undoMillis`. Failure sets `failure` (`userMessage()` or "Couldn't move it to the trash").
- `fun undo()`: ignored with no offer or while `working`; cancels the timer, clears the offer, calls `trash.restore(ids)`, then emits `Restored`. Failure sets `failure = "Couldn't bring it back: <reason>"`.
- `fun undoShown()` (the snackbar timed out or was swiped away) clears `undoable`; `fun failureShown()` clears `failure`.

### [x] 2.3 — Bulk tag marks

File: `ui/documents/BulkTags.kt` (new)
`enum class TagMark { ALL, SOME, NONE }`. `internal fun tagMarks(documents: List<Document>, tags: List<Tag>): Map<Int, TagMark>`. `internal fun TagMark.toggled()`: SOME → ALL, ALL → NONE, NONE → ALL. `internal fun bulkTagChange(start: Map<Int, TagMark>, now: Map<Int, TagMark>): BulkEdit.ModifyTags?` adds the tags that became ALL and removes those that became NONE, and is null when nothing changed. Test: `ui/BulkTagsTest.kt`.

### [x] 2.4 — SelectionViewModel

File: `ui/documents/SelectionViewModel.kt` (new)
`class SelectionViewModel(private val bulkEdit: BulkEditUseCase) : ViewModel()`.
- `data class SelectionUiState(val selected: Set<Int> = emptySet(), val working: Boolean = false, val failure: String? = null) { val selecting get() = selected.isNotEmpty() }`.
- `start(id)`, `toggle(id)` (dropping the last one stops selecting), `selectAll(loadedIds: List<Int>)` (exactly the rows already loaded), `stop()`.
- `fun change(edit: BulkEdit)` for `ModifyTags`, `SetCorrespondent` and `SetDocumentType`, and `fun changeTags(start, now)` via `bulkTagChange` (null makes no call). Both are ignored while `working` or with nothing selected. Success emits `Changed(ids, edit)` on `val changes: SharedFlow<Changed>` and stops selecting. Failure keeps the selection and sets `failure` (`userMessage()` or "Couldn't change them"); `failureShown()` clears it. Trash goes through `TrashViewModel` (the screen passes the selected ids, then calls `stop()` on `Trashed`).

### [x] 2.5 — Tests

Files: `domain/DocumentEditTest.kt`, `ui/TrashViewModelTest.kt`, `ui/BulkTagsTest.kt`, `ui/SelectionViewModelTest.kt` (all new). `Fakes.kt`: `FakeLibrary` gains `var writeFailure: Exception?`, thrown by `delete`, `restore` and `bulkEdit` only.
The cases in the test plan's third to sixth bullets, on virtual time for the Undo window.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*DocumentEditTest' --tests '*TrashViewModelTest' --tests '*BulkTagsTest' --tests '*SelectionViewModelTest' --tests '*ArchitectureTest'`

---

## Checkpoint 3 — Edit details screen and pickers · commit: `feat(documents): edit a document's details from the document view`

Goal: Edit on the document view opens the Edit details screen; its pickers work; Save updates the viewer and the list.

### [ ] 3.0 — Gate: #9 and #10 are merged

`gh issue view 9 --json state` and `gh issue view 10 --json state` are both CLOSED, and `git merge origin/main` (no rebase, no force-push) brings in their code. If either is still open, **stop and park** with a note naming which one. Never re-create their sheets, option rows, chips, search field, snackbar or dialog here. Once merged, read their final names: the list's ViewModel and row component, the viewer's ViewModel and details panel, the navigation in `MainActivity`, and the parts in `ui/components/`. Where a name below differs, use the merged one and record it in NOTES under "Contradicted the plan".

### [ ] 3.1 — Shared parts this ticket adds

Files: `ui/components/PickerField.kt` (new), `ui/components/Dialogs.kt` (extend #9's dialog if it has one, else new)
- `PickerField(label: String, value: String?, placeholder: String, onClick: () -> Unit, tags: List<String>? = null)`, per `components.md` § Picker field: the label above a `field` box with a chevron, or the tags wrapping as pills. It is a button to TalkBack ("<label>, <value>").
- `ConfirmDialog(title, body, confirmLabel, onConfirm, onDismiss, destructive: Boolean)`: `Destructive` for trash, and `destructive` text for "Discard" (`layouts.md` § Form — Edit details).
- Add `MixedChoiceRow(text: String, state: androidx.compose.ui.state.ToggleableState, onClick)` (the tri-state checkbox: `Indeterminate` is `raised` with a `mfg` ring and a dash) next to #9's `MultiChoiceRow`. The screen maps `TagMark` to `ToggleableState`, so components stay free of `ui/documents`. Each part gets a `@Preview`.

### [ ] 3.2 — Picker sheets

File: `ui/edit/PickerSheets.kt` (new)
- `LabelPickerSheet(title: String, labels: List<Label>, selectedId: Int?, onPick: (Int?) -> Unit, onOpenPaperless: () -> Unit, onDismiss)`: a list sheet (#9's `AppBottomSheet` List variant) with an in-sheet search field, "None" first in `mfg`, `SingleChoiceRow`s, and tap-to-choose-and-close. Used for correspondent, document type and storage path.
- `TagPickerSheet(tags: List<Tag>, chosen: List<Int>, onDone: (List<Int>) -> Unit, onOpenPaperless, onDismiss)`: a local draft, chosen tags above a divider (`chosenFirst`), `MultiChoiceRow`s, and a pinned bar with "<n> chosen" and a `Primary` Done.
- Both: no match shows "No tag called '<query>'" (or correspondent / document type / storage path) with an "Open Paperless" `Secondary` button. A `Failed` labels status shows the load-failed state with Try again.

### [ ] 3.3 — Edit details screen

File: `ui/edit/EditDetailsScreen.kt` (new)
`EditDetailsScreen(viewModel: EditDetailsViewModel, onClose: () -> Unit, onSaved: (Document) -> Unit, onOpenUrl: (String) -> Unit)`, per `layouts.md` § Form — Edit details:
- The form bar has a close icon ("Close without saving").
- The fields sit 18 apart: Title (`AppTextField`, with the error line), Date (`PickerField` → M3 `DatePickerDialog`), then Correspondent, Document type, Tags and Storage path (`PickerField` → sheet).
- The bottom action area holds `Secondary` Cancel and `Primary` Save ("Save again" after a failure, a spinner while saving), with `imePadding`. A failed save shows a destructive notice banner at the top.
- Close, Cancel or Back with `hasChanges` asks "Discard changes?" (Keep editing / Discard); without changes it closes at once. `saved != null` calls `onSaved` once.
- Previews: view, field error, save failed, labels failed.

### [ ] 3.4 — Wiring

Files: `AppContainer.kt`, `MainActivity.kt`, the viewer from #10
- `AppContainer`: `fun editDetailsViewModelFactory(document: Document)` building `EditDetailsViewModel(document, UpdateDocumentUseCase(library, settings), LoadLabelsUseCase(library, settings), LabelsLinkUseCase(server, settings))`.
- The viewer's details panel shows Edit beside Download (quick actions and all details), per `components.md` § Details panel. Edit pushes the Edit screen onto `MainActivity`'s stack with the viewer's current `Document` (a new screen value, Back pops it).
- `onSaved(document)` updates the viewer's document (add `fun showUpdated(document: Document)` to #10's viewer ViewModel if it has none, tested) and calls the list's `patch(document)` (4.1). `onOpenUrl` opens the browser as `documentUrl` does today.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*EditDetailsViewModelTest' --tests '*DesignRulesTest' --tests '*ArchitectureTest' lintDebug assembleDebug`
- `docs/design/checklist.md` walked for the Edit screen and both sheets.

---

## Checkpoint 4 — Move to trash with undo · commit: `feat(documents): move a document to the trash with undo`

Goal: the More menu's Move to trash asks first, returns to the list, and offers Undo for 8 seconds; edits and trash show in the list without a reload.

### [ ] 4.1 — List patches in place

File: `ui/documents/DocumentsViewModel.kt` (from #9), test `ui/DocumentsViewModelTest.kt`
Add `fun patch(document: Document)` (replace the loaded document with that id and re-map its row; no-op if not loaded), `fun apply(ids: Collection<Int>, edit: BulkEdit)` (via `Document.with`), `fun remove(ids: Collection<Int>)` (drop the rows, remember each one's document and index, `total - n`) and `fun putBack(ids: Collection<Int>)` (re-insert the remembered ones at their old indices, clamped, `total + n`). Also `fun documents(ids: Collection<Int>): List<Document>` for the bulk Tags sheet. If #9 keeps only rows, keep the `Document`s alongside them. Tests: the cases in the test plan's DocumentsViewModel bullet.

### [ ] 4.2 — More menu and confirm

File: the viewer screen from #10
Add a `border` divider and then "Move to trash" (`destructive` text and icon) as the More menu's last item. It opens `ConfirmDialog("Move to trash?", "'<title>' goes to the Paperless trash. You can bring it back from there.", "Move to trash", destructive = true)`. Confirm calls `trashViewModel.trash(listOf(id))`. On `Trashed`, the viewer closes back to the list. A failure stays on the viewer with the snackbar message.

### [ ] 4.3 — Snackbar with Undo on the list

Files: `ui/documents/DocumentsScreen.kt`, `AppContainer.kt`, `MainActivity.kt`
- `trashViewModelFactory` in `AppContainer`. One activity-scoped `TrashViewModel` is passed to the viewer and the list.
- The list collects `TrashEvent`: `Trashed` → `documents.remove(ids)`, `Restored` → `documents.putBack(ids)`.
- While `undoable` is set, the list shows #10's snackbar (`components.md` § Snackbar) with the message and a `primary` Undo calling `undo()`. The app's 8-second timer, not `SnackbarDuration`, decides when it goes. Swiping it away calls `undoShown()`, and the Add button moves up above it. `failure` shows as a snackbar with no action, then `failureShown()`.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*DocumentsViewModelTest' --tests '*TrashViewModelTest' --tests '*ArchitectureTest' assembleDebug`

---

## Checkpoint 5 — Select several · commit: `feat(documents): select several documents and change them at once`

Goal: long-press selects; the bulk bar changes tags, correspondent or type for all selected, or trashes them with Undo.

### [ ] 5.1 — Selectable row, selection bar, bulk bar

Files: `ui/components/DocumentRow.kt` (from #9), `ui/components/Headers.kt`, `ui/components/BulkActionBar.kt` (new)
- The row gets `selection: Boolean?` (null means not selecting) and `onLongClick`. While selecting it shows a 24dp round check at the start, the thumbnail shrinks to 48 × 62, and a selected row gets the `primary-tint` ground, a 2dp `primary` inner ring and a filled check. It exposes `selected` to TalkBack, and a tap toggles instead of opening.
- `SelectionBar(count: Int, onClose, onSelectAll)`: a close icon ("Stop selecting"), "<n> selected" at 22/500, and a "Select all" text button.
- `BulkActionBar(onTags, onFrom, onType, onTrash, enabled)`: four equal 64-high buttons (22dp icon over a 13 label) on `surface` with a 1dp `border` ring and `r-card`, floating 24 above the bottom. Trash is destructive. Previews for each part.

### [ ] 5.2 — Selecting on the Documents screen

File: `ui/documents/DocumentsScreen.kt`
- A long-press calls `selection.start(id)`. While `selecting`, the title row gives way to the `SelectionBar` and the Add button to the `BulkActionBar`, and `BackHandler` calls `stop()`. "Select all" passes the loaded ids.
- Tags opens `BulkTagsSheet` (new, `ui/documents/BulkTagsSheet.kt`): a list sheet with search, `MixedChoiceRow`s from `tagMarks(documents.documents(selected), tags)`, "Done" calling `changeTags(start, now)`, and the no-match and Open Paperless state as in 3.2.
- From and Type open `LabelPickerSheet` for correspondent and document type ("None" clears), calling `change(SetCorrespondent / SetDocumentType)`.
- Trash opens `ConfirmDialog("Move <n> documents to trash?", …, "Move to trash", destructive = true)` and then calls `trashViewModel.trash(selected.toList())`; on `Trashed` it calls `selection.stop()`.
- The screen collects `selection.changes` → `documents.apply(ids, edit)`. A `failure` shows the snackbar.

### [ ] 5.3 — Wiring

File: `AppContainer.kt`
`selectionViewModelFactory` (`SelectionViewModel(BulkEditUseCase(library, settings))`). The labels for the sheets come from the list's already-loaded `Labels`.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest koverVerify lintDebug assembleDebug` (the full CI command)
- `docs/design/checklist.md` walked for the selecting list, the bulk sheets and both dialogs.

---

## Out of scope

- Creating tags, correspondents, document types or storage paths. A picker with no match links to the web UI.
- Editing the archive serial number, custom fields, notes, permissions or owner.
- Bulk storage path (not in the design's bulk bar), bulk title or date.
- "Select all" reaching documents not loaded yet (Q2).
- A trash screen: Undo is the only way back in the app.
- The 403 wording (#21): its own ticket.
- New Paperless calls or changes to how `data/` talks to the server (beyond the web-UI link `labelsUrl`).
- Re-creating any part #9 or #10 builds.
