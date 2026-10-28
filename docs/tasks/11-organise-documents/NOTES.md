# Notes — Organise documents: edit details, delete, bulk edit

Issue: #11 — Organise documents: edit details, delete, bulk edit (https://github.com/YasarMahomedAbbas/paperless-companion/issues/11)
Branch: `feat/11-organise-documents`

## Planning

- Approach: ViewModels over #7's write use cases. `EditDetailsViewModel` drives a full-screen Edit details with picker sheets. An activity-scoped `TrashViewModel` handles delete and the 8-second Undo (restore from the trash). `SelectionViewModel` handles selection and the bulk Tags / From / Type changes. The list patches its loaded rows in place. No new Paperless call; one new web-UI link (`labelsUrl`).
- Decided by the pipeline on the user's behalf (approach questions):
  - **Q1, sequencing against #9 and #10** (flags: scope, open-risk): **option 1** taken. Checkpoints 1–2 (ViewModels and pure state, new files only) are built now. Checkpoints 3–5 (screens and wiring) begin by merging origin/main and need #9 and #10 merged; implement-plan parks if they aren't. Options were: (1) as taken; (2) build everything now on main with its own sheets, snackbar, dialog and test entry points, duplicating #9/#10's parts with a large merge; (3) park the whole ticket until #9 and #10 merge.
  - **Q2, what "Select all" selects** (flags: ux, scope): **option 1** taken: the rows already loaded. Options were: (1) as taken; (2) every document matching the current search and filters on the server, which pages all the ids and can change unseen documents.
  - **Q3, how the list catches up after a change** (flags: ux): **option 1** taken: loaded rows are patched in place (the edit uses the returned document, bulk changes are applied locally, trash removes rows and Undo puts them back) and the scroll position is kept. Options were: (1) as taken; (2) reload from the top after every change.
- Decided in planning (stand as written):
  - Delete calls the server as soon as it's confirmed. The 8-second Undo snackbar restores from the Paperless trash. Bulk trash works the same way for all selected ids (v10 `/api/documents/delete/`, 2.x fallback from #7).
  - Single delete lives in the viewer's More menu. After it the app returns to the list, which shows the Undo snackbar. Edit opens from the viewer's Edit button as a full screen (Cancel / Save, title required, "Discard changes?" dialog).
  - A picker with no match opens that label's page in the Paperless web UI (`/tags`, `/correspondents`, `/documenttypes`, `/storagepaths`; valid on 2.20 and 3.x, where they redirect to `/attributes/<section>`), built in `data/` as `DocumentServer.labelsUrl`.
  - The bulk Tags sheet shows mixed checkboxes worked out from the selected documents' tags and sends only the tags that changed. The bulk bar is Tags, From, Type and Trash, with no storage path. Nothing creates tags or correspondents.
  - The Date can be changed but not cleared (`DocumentChanges.created` is a non-null date).
- API: no new call. PATCH, DELETE, trash restore, bulk_edit (`modify_tags`, `set_correspondent`, `set_document_type`) and `/api/documents/delete/` were checked in #7 against the server source (v3.3.4 / v2.20.15). The web-UI routes were checked in `src-ui` `app-routing.module.ts` (v2.20.15 and main). docs.paperless-ngx.com answered 403 (Cloudflare) and `verify.env` is absent, so no live `/api/schema/` was read.
- Open risks:
  - Checkpoints 3–5 depend on #9 and #10 merging. Their final names (the list's ViewModel and row, the viewer's ViewModel and panel, navigation, sheets, snackbar, dialog) may differ from the checklist; implement-plan records each in "Contradicted the plan".
  - No real Paperless has ever answered these write calls (open since #7). Self-verification needs `verify.env` with `PAPERLESS_ALLOW_WRITES=1` to prove "shows in the web UI straight after".
  - A 403 on a document the user doesn't own reads as "sign in again" until #21 lands.

## Implementation — checkpoints 1–2 (before the 3.0 gate)

- Shipped so far:
  - Checkpoint 1 (`0be13a3`): `LabelKind` (`domain/model/Documents.kt`), `DocumentServer.labelsUrl` (`domain/repository/Repositories.kt`) built in `data/PaperlessApi.kt` next to `documentUrl`, `LabelsLinkUseCase` (`domain/usecase/DocumentLinkUseCase.kt`), `EditForm` / `changesFrom` / `LabelsStatus` / `EditDetailsUiState` (`ui/edit/EditDetailsUiState.kt`), `EditDetailsViewModel` (`ui/edit/EditDetailsViewModel.kt`), `matching` / `chosenFirst` (`ui/edit/PickerFilter.kt`).
  - Checkpoint 2 (`5ed21c8`): `Document.with(BulkEdit)` (`domain/model/DocumentEdits.kt`), `TrashViewModel` (`ui/documents/TrashViewModel.kt`), `TagMark` / `tagMarks` / `toggled` / `bulkTagChange` (`ui/documents/BulkTags.kt`), `SelectionViewModel` (`ui/documents/SelectionViewModel.kt`). `FakeLibrary` gained `updateFailure`, `updateGate` and `writeFailure`.
- Tests: full `testDebugUnitTest koverVerify` green, 335 passed / 0 failed.
- Stopped at 3.0: #9 (no PR yet) and #10 (PR #25 open) are both still OPEN, so checkpoints 3–5 can't start (Q1, option 1). Resume implement-plan at 3.0 once both are merged.
- Contradicted the plan: none. Small additions written into the checklist text: `undo()` is ignored with no offer or while working; `SelectionViewModel.change` is ignored with nothing selected and falls back to "Couldn't change them".

## Implementation

- Shipped:
  - Checkpoints 1–2 as recorded above (`0be13a3`, `5ed21c8`), then `origin/main` merged in (`7f30059`, #9, #10, #16, #21 and the dependabot bumps; one conflict in the test `Fakes.kt`, both sides kept).
  - Checkpoint 3 (`55815d6`): Edit details screen `ui/edit/EditDetailsScreen.kt:71` with the date picker and the four pickers `ui/edit/PickerSheets.kt:58` (`LabelPickerSheet`), `:93` (`TagPickerSheet`); `PickerField` extended `ui/components/PickerField.kt:44`; `FormBar` `ui/components/Headers.kt:77`; `MixedChoiceRow` `ui/components/Sheets.kt:212`; Edit beside Download in the viewer's panel; `ViewerViewModel.showUpdated` `ui/viewer/ViewerViewModel.kt:180`; `DocumentsViewModel.patch` `ui/documents/DocumentsViewModel.kt:160`; `editDetailsViewModelFactory` `AppContainer.kt:132`; Edit drawn in the viewer's place `MainActivity.kt:181`.
  - Checkpoint 4 (`3504dab`): Move to trash in the More menu with its confirm (`ui/viewer/ViewerScreen.kt`, `MenuItem(destructive)` / `MenuDivider` `ui/components/ViewerBar.kt:123`); `apply` / `remove` / `putBack` / `documents` `ui/documents/DocumentsViewModel.kt:168-195`; Undo snackbar on the list (`ui/documents/DocumentsScreen.kt`); trash events collected in `MainActivity.kt:158`; `trashViewModelFactory` `AppContainer.kt:123`.
  - Checkpoint 5: selectable `DocumentRowCard` `ui/components/DocumentRow.kt:51`, `SelectionBar` `ui/components/Headers.kt:102`, `BulkActionBar` `ui/components/BulkActionBar.kt:34`, `BulkTagsSheet` `ui/documents/BulkTagsSheet.kt:39`, selecting on `DocumentsScreen`, swipe-away snackbars `ui/components/Snackbar.kt:27`, `SelectionViewModel.labelsUrl` `ui/documents/SelectionViewModel.kt:75`, `selectionViewModelFactory` `AppContainer.kt:128`.
  - Design decisions recorded in `docs/design/README.md` § "Settled while building Organise documents (#11)".
- Tests: full CI (`testDebugUnitTest koverVerify lintDebug assembleDebug`) green, 446 passed / 0 failed, Kover verify passed.
- Outstanding: none in the checklist. Device-only checks (long-press feel, the date picker, the keyboard over the action area, the 8-second Undo, TalkBack on selected rows and mixed checkboxes, Open Paperless opening the right page) and the real-server writes are for self-verification. The design checklist was walked from the code only; no screenshots.
- Contradicted the plan (names #9 and #10 actually shipped, resolved without changing what is built):
  - `PickerField` already existed (#9) as `PickerField(value, placeholder, onClick, modifier, icon)`; extended with `label`, `tags` and a `Date` variant instead of a new one.
  - No `ConfirmDialog`: #9's `AppDialog` (`ui/components/Dialog.kt`) takes `confirmVariant` (`Destructive`, `DestructiveText`) and `dismissLabel`, so it serves the trash confirms and "Discard changes?".
  - The row component is `DocumentRowCard`, and #9's `DocumentsViewModel` kept only rows, not `Document`s; it now keeps the loaded documents beside them.
  - #10's `ViewerViewModel` kept only `DocumentDetails`; `ViewerUiState` gained `document` and the ViewModel keeps its labels. It also gained `showMessage` so a failed trash shows on the viewer.
  - `MainActivity` has no screen stack for the viewer (`openDocumentId` plus a `Screen` enum), so Edit details is a saveable `editing` flag drawn in the viewer's place rather than a pushed screen value.
  - `TrashEvent`s are collected in `MainActivity`, not on the list screen: the list isn't composed while the viewer covers it, and a `SharedFlow` without replay would drop the event.
  - #10's `MenuItem` had no destructive look and #10's snackbar host couldn't be swiped away; both were added.
  - The bulk sheets' Open Paperless needed a link the list didn't have, so `SelectionViewModel` takes a `LabelsLinkUseCase` too.
  - #9's `CorrespondentSheet` in `FiltersSheet.kt` stays private and unchanged; the pickers here are separate because they add "None", no-match → Open Paperless and the load states.

