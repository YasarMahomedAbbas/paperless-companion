# Checklist — Saved views, notes and "more like this"

Issue: #12 — Saved views, notes and "more like this" (https://github.com/YasarMahomedAbbas/paperless-companion/issues/12)
Branch: `feat/12-saved-views-and-notes`
Problem: the views we saved in the Paperless web UI, a document's notes and "more like this" are only in the web UI. On the phone we can't open a saved view, read or add a note, or find documents similar to the one we're looking at.

## Done when

- The saved views from the web UI show in the Documents list. Views set to show on the dashboard are chips at the start of the chip row. A **Views** chip opens a sheet with every view: sidebar views first, in the server's order, then the rest by name. Choosing a view shows exactly the documents the web UI shows for it.
- A document's notes can be read (newest first, with author and date) and added, from a **Notes** row in the viewer's all-details panel that opens a Notes screen. Notes are hidden when the server has them switched off.
- The viewer's More menu has **More like this**. It opens the Documents list with the similar documents and a removable "Like: <title>" chip.
- Each works against the real server (self-verification; skipped and recorded if `verify.env` is absent) and is tested. CI passes (`testDebugUnitTest koverVerify lintDebug assembleDebug`), coverage ≥ 80%, `ArchitectureTest` green.

## References

- Paperless API, checked against the server source at **v3.3.4** (latest, 2026-10-22). docs.paperless-ngx.com answered 403, and `verify.env` is absent, so **no live `/api/schema/` was read**. Self-verification must read it.
  - **Saved views**: `GET /api/saved_views/` (paginated, `http.pages`) → `{id, name, sort_field, sort_reverse, filter_rules: [{rule_type, value}], …}` (`src/documents/serialisers.py` `SavedViewSerializer`). `GET /api/saved_views/{id}/` gives one.
    - **v9** also sends `show_on_dashboard` / `show_in_sidebar` on each view.
    - **v10** (Paperless 3 default) drops them. They live in `GET /api/ui_settings/` → `settings.saved_views.dashboard_views_visible_ids` / `sidebar_views_visible_ids` (int arrays), with the order in `settings.saved_views.dashboard_views_sort_order` / `sidebar_views_sort_order` (`docs/api.md` § Version 10; `src-ui/src/app/data/ui-settings.ts`; the keys are nested by `settings.service.ts`: `general-settings:` dropped, `:` → nesting, `-` → `_`).
  - **Filter rules → query parameters**: the web UI's `queryParamsFromFilterRules` (`src-ui/src/app/utils/query-params.ts`) over the table in `src-ui/src/app/data/filter-rule-type.ts` (both at v3.3.4).
    - Rules 19 (`title_content`) and 41 (`text`) go to `text`. Rules 0 (`title`) and 40 (`title_search`) go to `title_search`.
    - A null value goes to `isnull_filtervar=1`, and the value `-1` (NEGATIVE_NULL_FILTER_VALUE) goes to `isnull_filtervar=0`.
    - `multi` types join their values with commas. Boolean types send `1`/`0` for `"true"`/`"1"` or anything else.
  - **Notes**:
    - `GET /api/documents/{id}/notes/` gives a bare list `[{id, note, created, user: {id, username, first_name, last_name} | null}]`, newest first. `POST` the same path with `{"note": "<text>"}` and it answers the whole list again.
    - On an internal error both answer **HTTP 200 with `{"error": "…"}`**. A user without `view_note` / `add_note` gets 403 (`views.py` `DocumentViewSet.notes`, `PaperlessNotePermissions`). The user object has been the shape since v8.
    - Notes can be switched off in the web UI: `GET /api/ui_settings/` → `settings.notes_enabled`, true when absent.
  - **More like this**: `GET /api/documents/?more_like_id={id}`. It is paginated like any list and answers in relevance order with `__search_hit__` (score 0, no highlights) per document.
    - It combines with the normal filter parameters, but not with `text`, `title_search` or `query` (400 "Specify only one of …"). An unknown or forbidden id gives 403 "Invalid more_like_id".
    - The source document is left out of the results on v3 (`search/_backend.py` `more_like_this_ids`).
- Design manual: `docs/design/` (`layouts.md` § List — Documents, § Viewer, § Navigation; `components.md` § Chips, § Bottom sheet, § Option list, § Details panel, § Menu, § Text field, § Status card, § Skeleton, § Notice banner). No canvas covers these screens (decision Q1). Every screen is built with the `design-recipe` skill, and the new choices go in `docs/design/README.md` § Decisions under "Settled while building saved views, notes and more like this (#12)".
- Related: #9 (list, chips, sheets), #10 (viewer, More menu, details panel), #11 (picker sheets, `MenuItem`, patterns for screen ViewModels).

## Context

The list is `DocumentsViewModel` over `BrowseDocumentsUseCase(DocumentQuery)`. Paging, search, filters, sort and in-place patching all exist there, and `PaperlessLibrary.documents` turns a `DocumentQuery` into parameters. The viewer is `ViewerViewModel` + `ViewerScreen`, with a More menu (`MenuItem`) and a details panel (`AllDetails`, `DetailRow`). Navigation is hand-rolled in `MainActivity`.

**Decisions** (the pipeline took Q1–Q5 on the user's behalf; see NOTES):
- **Q1**: build from the existing parts with `design-recipe`, no canvas first.
- **Q2**: home views are chips at the start of the chip row, and a Views chip opens a sheet with all views.
- **Q3**: `data/` turns a view's rules into exactly the parameters the web UI sends. The domain only says "view N", and the user's own search and filters narrow it.
- **Q4**: a Notes row in all details opens a Notes screen.
- **Q5**: More like this opens the list in a "similar to" mode with a removable chip.

**Also decided:**
- Notes are read and add only.
- Choosing a view clears the user's filters.
- The view's own order is used until the user picks a sort; the sort chip reads "View's order".
- Views follow the server's order.

Saved views and the notes switch both come from `ui_settings`. `data/` reads it inside `savedViews()` and `notesEnabled()`, and the domain never sees it.

Paths below are under `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/` (main) and `app/src/test/java/io/github/yasarmahomedabbas/paperlesscompanion/` (test).

## Test plan

- Unit, data (`PaperlessViewsTest`, pure): rule → parameter translation for each branch:
  - text (19, 41) and title (0, 40);
  - null → isnull=1, and `-1` → isnull=0;
  - multi rules joined with commas, in the order given;
  - booleans ("true", "1", "false");
  - a single-valued rule as is;
  - an unknown rule type dropped.
  Also ordering: `sort_reverse` gives `-field`, and a null `sort_field` gives `-created`.
- MockWebServer (`PaperlessLibraryTest`):
  - `savedViews()` on **v9** reads the flags from the view and ignores `ui_settings` ids. On **v10** it reads them from `ui_settings`. Both orders follow the `*_sort_order` arrays, and ids missing from an order come last by name. `ui_settings` with no `saved_views` gives no home or quick views.
  - `documents()` with a `view`:
    - It sends the view's parameters plus page, page_size and fields.
    - The user's tags are added to `tags__id__all`, and the user's correspondent and type replace the view's.
    - Typed text replaces the view's `text`/`title_search`/`query`/`more_like_id` with `query`.
    - With `viewOrder` it sends the view's ordering, and otherwise the app's.
    - A view not loaded yet is fetched by id first.
  - `documents()` with `similarTo` sends `more_like_id` and no `ordering` or `query`. A 403 is FORBIDDEN.
  - `notes()` parses the list: author from first/last name, else the username, else null for a null user. `{"error"}` on 200 is SERVER, and 403 is FORBIDDEN.
  - `addNote()` sends `{"note": …}` as JSON and parses the answered list. `{"error"}` is SERVER.
  - `notesEnabled()` is true when the key is absent and false when it's false.
  - `document()` reads `notes` into `noteCount`.
- Unit, domain (`DocumentUseCasesTest`): `NotesUseCase` load and add (blank text is refused without a call), `notesEnabled`, signed out; `SavedViewsUseCase`.
- Unit, ViewModels:
  - `DocumentsViewModelTest`:
    - Views load with the labels, and a views failure doesn't fail the list (no view chips).
    - `chooseView` clears the filters, sets `viewOrder` and reloads with `view`. `clearView` goes back.
    - `setSort` while a view is on turns `viewOrder` off. `setFilters` keeps the view.
    - `showSimilar` clears the view, filters and text, and the query carries `similarTo`. `clearSimilar` and `startSearch` end it.
    - `sortLabel` is "View's order" with a view and "Best match" when similar.
  - `NotesViewModelTest`:
    - Loading, loaded, empty, failed and Try again.
    - Add: the draft is kept on failure; on success the draft clears and the list is the server's answer. Blank text is ignored, and a second add while one runs is ignored.
    - `noteAdded` count for the viewer.
  - `ViewerViewModelTest`: `notesEnabled` loads alongside and false hides it; a failure counts as enabled; `noteCount` comes from the document.
- Real server (self-verification, read-only unless allowed):
  - Read `/api/schema/`.
  - `GET /api/saved_views/` and `GET /api/ui_settings/`: compare the app's home and quick views with the web UI. For each view, `GET /api/documents/` with the app's parameters and check the count against the web UI's view.
  - `GET /api/documents/{id}/notes/`, and `GET /api/documents/?more_like_id={id}`.
  - With `PAPERLESS_ALLOW_WRITES=1`, on a document it uploaded itself only: add a note and read it back. The note stays, because delete is out of scope; record its id.
- Device only: chip row scrolling with view chips; the Notes screen with the keyboard open (`imePadding`); TalkBack on the Views sheet and notes.

---

## Checkpoint 1 — Notes and more like this, data and domain · commit: `feat(api): read and add notes, and find documents like one`

Goal: the domain can load and add a document's notes, knows whether notes are on, and can list documents similar to one. Each call is tested against MockWebServer, with no UI yet.

### [x] 1.1 — Domain models

File: `domain/model/Documents.kt`
- `data class Note(val id: Int, val text: String, val created: Instant?, val author: String?)`, KDoc "A note someone left on a document; [author] is null when the server doesn't say who".
- `Document` gains `val noteCount: Int? = null`, KDoc "How many notes it has; only known when one document is loaded".
- `DocumentQuery` gains `val similarTo: Int? = null`, KDoc "Documents like this one, most alike first; [text] and [sort] are ignored". Update the class KDoc to match.

### [x] 1.2 — Library interface

File: `domain/repository/Repositories.kt` (`DocumentLibrary`)
- `suspend fun notes(config: ServerConfig, documentId: Int): List<Note>`, KDoc "Newest first".
- `suspend fun addNote(config: ServerConfig, documentId: Int, text: String): List<Note>`, KDoc "Adds a note and returns all of the document's notes, newest first".
- `suspend fun notesEnabled(config: ServerConfig): Boolean`, KDoc "False when the server's owner switched notes off".

### [x] 1.3 — Paperless implementation

Files: `data/PaperlessLibrary.kt`, `data/PaperlessJson.kt`
- `notes`: `http.json(config, "GET", "api/documents/$id/notes/")`. A `JsonArray` maps through `JsonObject.toNote()`. A `JsonObject` with `error` throws `ServerException(Kind.SERVER, "Paperless couldn't load the notes")`, and anything else throws SERVER "Paperless sent no notes".
- `addNote`: POST the same path with `buildJsonObject { put("note", text) }`, parsed the same way; the error text is "Paperless couldn't save the note".
- `notesEnabled`: `GET api/ui_settings/` → `settings.notes_enabled` as a boolean primitive, true when missing or not a boolean. Keep a private `uiSettings(config): JsonObject` helper (it returns `settings`, or empty) for checkpoint 2 to reuse.
- `documents()`: when `query.similarTo != null`, put `more_like_id` and send neither `query` nor `ordering`. The filters stay.
- `PaperlessJson.kt`:
  - `internal fun JsonObject.toNote() = Note(int("id"), string("note").orEmpty(), instant("created"), author)`. The author is `"first last".trim()` if not blank, else `username`, else null when `user` is null or not an object. Make `instant` internal if needed.
  - `toDocument()` sets `noteCount = (this["notes"] as? JsonArray)?.size`. `LIST_FIELDS` stays without notes.
- KDoc on `notes`: it says Paperless answers 200 + `{"error"}` on failure.

### [x] 1.4 — Use cases

File: `domain/usecase/NotesUseCase.kt` (new)
`class NotesUseCase(library, settings)` with:
- `suspend fun load(documentId: Int): List<Note>`;
- `suspend fun add(documentId: Int, text: String): List<Note>`, which does `require(text.isNotBlank())` and sends the text trimmed;
- `suspend fun enabled(): Boolean`.
More like this needs no new use case: it's `BrowseDocumentsUseCase` with `similarTo`.

### [x] 1.5 — Fakes and tests

Files: `Fakes.kt`, `data/PaperlessLibraryTest.kt`, `data/PaperlessJsonTest.kt`, `domain/DocumentUseCasesTest.kt`
- `FakeLibrary` gains:
  - `var notes = listOf(Note(1, "Paid", Instant.parse("2026-10-01T09:00:00Z"), "Ann"))`;
  - `var notesEnabled = true`;
  - `var notesFailure: Exception?`, thrown by `notes`/`addNote`/`notesEnabled`;
  - `var addNoteGate: CompletableDeferred<Unit>?`;
  - `val addedNotes = mutableListOf<Pair<Int, String>>()`.
  `addNote` prepends a new note and returns the list.
- The MockWebServer and use-case cases in the test plan for notes, `notesEnabled`, `noteCount` and `similarTo`.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*PaperlessLibraryTest' --tests '*PaperlessJsonTest' --tests '*DocumentUseCasesTest' --tests '*ArchitectureTest'`

---

## Checkpoint 2 — Saved views, data and domain · commit: `feat(api): load saved views and list a view's documents as the web UI does`

Goal: the domain lists the saved views (with home and quick-filter flags, in the server's order) and lists a view's documents. The data layer turns the view's rules into exactly the web UI's parameters, on v9 and v10.

### [ ] 2.1 — Domain

Files: `domain/model/Documents.kt`, `domain/repository/Repositories.kt`, `domain/usecase/SavedViewsUseCase.kt` (new)
- Add the model:
  ```kotlin
  data class SavedView(
      val id: Int,
      val name: String,
      val onHome: Boolean,
      val quickFilter: Boolean,
  )
  ```
  with KDoc "A list someone saved in the server's web interface: [onHome] when it shows on its home (dashboard), [quickFilter] when it's in its quick list (sidebar)".
- `data class SavedViews(val home: List<SavedView>, val all: List<SavedView>)`. `home` is the home views in the server's home order. `all` is every view: quick-filter ones first in the server's order, then the rest by name, ignoring case.
- `DocumentQuery` gains:
  - `val view: Int? = null`, KDoc "Only the documents this saved view shows; the other fields narrow it further".
  - `val viewOrder: Boolean = false`, KDoc "With [view], use the view's own order instead of [sort]".
- `DocumentLibrary.savedViews(config): SavedViews`.
- `class SavedViewsUseCase(library, settings) { suspend operator fun invoke(): SavedViews }`.

### [ ] 2.2 — Rule translation

File: `data/PaperlessViews.kt` (new, `internal`)
- `internal data class ViewRule(val type: Int, val value: String?)` and `internal data class ViewFilter(val params: Map<String, String>, val ordering: String)`.
- `internal fun viewFilter(rules: List<ViewRule>, sortField: String?, sortReverse: Boolean): ViewFilter`:
  - It follows `queryParamsFromFilterRules` at v3.3.4 rule for rule, with a private table `id → RuleType(filtervar, isnullFiltervar, multi, boolean)` copied from `filter-rule-type.ts` at v3.3.4 (every id in `FILTER_RULE_TYPES`).
  - An unknown rule type is dropped.
  - Ordering is `(if (sortReverse) "-" else "") + (sortField ?: "created")`, and `-created` when `sortField` is null and `sortReverse` is true.
  - KDoc names the two source files and the tag.
- `internal fun JsonObject.toViewRules()` reads `filter_rules`. Each `value` is a string, a number as a string, or null.

### [ ] 2.3 — Paperless implementation

File: `data/PaperlessLibrary.kt`
- `savedViews`:
  - Fetch `http.pages(config, "api/saved_views/", mapOf("page_size" to "100"))` and `uiSettings(config)` side by side (`coroutineScope` + `async`).
  - Per view: if the JSON has `show_on_dashboard` / `show_in_sidebar` (v9), use them. Otherwise use membership in `saved_views.dashboard_views_visible_ids` / `sidebar_views_visible_ids` (v10).
  - Home order follows `dashboard_views_sort_order`, then ids missing from it by name. The quick part of `all` follows `sidebar_views_sort_order` the same way.
  - A `ui_settings` failure is ignored when every view carried v9 flags, and thrown otherwise.
  - Cache each view's `ViewFilter` in a private `ConcurrentHashMap<Pair<String, Int>, ViewFilter>` keyed by `config.baseUrl` and id.
- `documents()` with `query.view`:
  - Get the filter from the cache, or `GET api/saved_views/{id}/` → cache.
  - Start from its `params`; `page`, `page_size` and `fields` are the app's.
  - User tags: union with the view's `tags__id__all` (sorted, comma-joined). User correspondent / type: overwrite `correspondent__id` / `document_type__id`.
  - User text (non-blank): remove `text`, `title_search`, `query` and `more_like_id`, then put `query`.
  - Ordering: no text and `viewOrder` gives the view's ordering. No text and not `viewOrder` gives the app's. With text there is no ordering, as today.
  - `similarTo` together with `view` is not used by the UI (2.1 KDoc). If both are set, `similarTo` wins, and the view's search parameters are dropped as for text.
- KDoc on `documents` explaining the merge.

### [ ] 2.4 — Fakes and tests

Files: `Fakes.kt`, `data/PaperlessViewsTest.kt` (new), `data/PaperlessLibraryTest.kt`, `domain/DocumentUseCasesTest.kt`
- `FakeLibrary`:
  - `var savedViews = SavedViews(home = listOf(SavedView(7, "Inbox", true, true)), all = listOf(SavedView(7, "Inbox", true, true), SavedView(8, "Taxes", false, false)))`;
  - `var savedViewsFailure: Exception?`;
  - `documents()` records the queries it was asked (if not already).
- The cases in the test plan's rule-translation and `savedViews`/`documents()`-with-view bullets. The v9 and v10 fixtures are written from the serializer shapes above.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*PaperlessViewsTest' --tests '*PaperlessLibraryTest' --tests '*DocumentUseCasesTest' --tests '*ArchitectureTest'`

---

## Checkpoint 3 — Saved views in the Documents list · commit: `feat(documents): open saved views from the list`

Goal: the home views show as chips at the start of the chip row, and a Views chip opens a sheet with every view. Choosing one lists its documents, and it can be cleared.

### [ ] 3.1 — State and ViewModel

Files: `ui/documents/DocumentsUiState.kt`, `ui/documents/DocumentsViewModel.kt`, `AppContainer.kt`
- `DocumentsUiState` gains:
  - `val views: SavedViews? = null`;
  - `val view: SavedView? = null`;
  - `val viewOrder: Boolean = false`.
  `sortLabel` gives "View's order" when `view != null && viewOrder && text.isBlank()`. `showResultLine` is also true with a view.
- `DocumentsViewModel(browse, loadLabels, savedViews: SavedViewsUseCase, debounceMillis)`:
  - The views load alongside the labels in `loadFirstPage(withLabels = true)`, in their own `async` whose failure becomes `views = null` and never fails the list.
  - `chooseView(view: SavedView)`: `stopPreview()`, then set `view`, `viewOrder = true`, `filters = DocumentFilters()` and clear `similar` (checkpoint 4), then `reload()`. Choosing the chosen view again clears it.
  - `clearView()`: `view = null`, `viewOrder = false`, `reload()`.
  - `setSort` sets `viewOrder = false`.
  - `clearFilters()` keeps the view.
  - `query()` passes `view = s.view?.id` and `viewOrder`.
- `AppContainer`: pass `SavedViewsUseCase(library, settings)`.

### [ ] 3.2 — Chips and the Views sheet

Files: `ui/documents/DocumentsScreen.kt`, `ui/documents/ViewsSheet.kt` (new). Apply `design-recipe` first.
- `FilterChips`:
  - Each `views.home` view comes first as an `AppChip` (`Selected` when chosen, `Unselected` otherwise); a tap calls `chooseView`.
  - Then a **Views** `Action` chip (leading icon: a list/bookmark icon drawn in `AppIcons.kt` if missing). It shows only when `views.all` is non-empty.
  - A chosen view that isn't a home view shows as a `Selected` chip with its name right after the home chips; a tap clears it.
  - Then the existing Filters, filter and sort chips.
- `ViewsSheet`:
  - A bottom sheet titled "Saved views" listing `views.all` as single-choice option rows (the #9/#11 option-row part) with the chosen one marked.
  - If there are quick-filter views and others, a divider and an "Other views" section heading go between them.
  - Tapping a row chooses it and closes the sheet. The sheet is added to the `DocumentsSheet` enum.
- `DocumentsScreenActions` gains `onChooseView`, `onClearView` and `onOpenViews`, wired in `DocumentsScreen`.
- `@Preview` for the chip row with views and for the sheet.

### [ ] 3.3 — Tests and manual

Files: `ui/DocumentsViewModelTest.kt`, `docs/design/README.md`
- The `DocumentsViewModelTest` view cases from the test plan.
- The manual's § Decisions gets the "#12" heading with: view chips first in the chip row, the Views chip and sheet, the "View's order" sort label, and the drawn icon.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*DocumentsViewModelTest' --tests '*ArchitectureTest' --tests '*DesignRulesTest'`

---

## Checkpoint 4 — More like this · commit: `feat(documents): find documents like the one you're viewing`

Goal: the viewer's More menu has More like this. It closes the viewer and shows the list in a "similar to" mode with a removable "Like: <title>" chip.

### [ ] 4.1 — List state

Files: `ui/documents/DocumentsUiState.kt`, `ui/documents/DocumentsViewModel.kt`
- `data class Similar(val id: Int, val title: String)`, and `DocumentsUiState.similar: Similar? = null`. `sortLabel` gives `BEST_MATCH` when `similar != null`, and `showResultLine` is true.
- `showSimilar(id: Int, title: String)`:
  - `stopPreview()`;
  - stop searching (`searching = false`, `text = ""`);
  - `view = null`, `viewOrder = false`, `filters = DocumentFilters()`, `similar = Similar(id, title.ifBlank { "Untitled" })`;
  - `reload()`.
- `clearSimilar()` sets `similar = null` and reloads.
- `startSearch()` and `chooseView()` clear `similar`.
- `setFilters` keeps `similar`.
- `query()` passes `similarTo = s.similar?.id`.
- The sort chip is disabled while `similar` is on.

### [ ] 4.2 — Screen and wiring

Files: `ui/documents/DocumentsScreen.kt`, `ui/viewer/ViewerScreen.kt`, `MainActivity.kt`. Apply `design-recipe`.
- Chip row: while `similar` is on, a `Removable` chip "Like: <title>" comes first (ellipsised at 24 chars, content description "Stop showing documents like <title>"). It calls `clearSimilar`, and the view chips hide.
- `ViewerScreen`: a `MenuItem("More like this", AppIcons.<similar icon>, …)` after Open in Paperless and before the trash divider, enabled once `state.document != null`. A new `onMoreLikeThis: (Document) -> Unit` parameter.
- `MainActivity`: `onMoreLikeThis` closes the viewer (as trash does) and calls `list.showSimilar(document.id, document.title)`, where `list` is the `DocumentsViewModel` that `MainActivity` already holds for `patch` and `remove`.
- Empty state while similar is on: the existing "no results" card with "Clear filters" relabelled "Show all documents", which calls `clearSimilar` (and clears the filters).

### [ ] 4.3 — Tests and manual

- The `DocumentsViewModelTest` similar cases from the test plan.
- `docs/design/README.md` § Decisions (#12): the menu item's place, the "Like:" chip, the sort chip disabled, and the empty-state wording.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*DocumentsViewModelTest' --tests '*ViewerViewModelTest' --tests '*ArchitectureTest'`

---

## Checkpoint 5 — Notes · commit: `feat(documents): read and add a document's notes`

Goal: all details shows a Notes row ("Notes · 3", or "No notes yet") when notes are on. It opens a Notes screen that lists them newest first and adds one.

### [ ] 5.1 — Viewer knows about notes

Files: `ui/viewer/ViewerViewModel.kt`, `AppContainer.kt`
- `ViewerViewModel` gains `notes: NotesUseCase`. On load it calls `notes.enabled()` alongside the document, and a failure counts as enabled.
- `ViewerUiState` gains `notesEnabled: Boolean = false` (true once known) and `noteCount: Int?` from `document.noteCount`.
- `fun notesChanged(count: Int)` updates `noteCount` after the Notes screen adds one.

### [ ] 5.2 — NotesViewModel

File: `ui/notes/NotesViewModel.kt` (new)
- `sealed interface NotesStatus { Loading; Loaded; Failed(val message: String, val unreachable: Boolean) }`.
- `data class NotesUiState(val status: NotesStatus = Loading, val notes: List<Note> = emptyList(), val draft: String = "", val adding: Boolean = false, val addFailed: String? = null)`.
- `class NotesViewModel(private val documentId: Int, private val notes: NotesUseCase) : ViewModel()`:
  - It loads on `init`. `retry()` loads again.
  - `setDraft(String)` sets the draft and clears `addFailed`.
  - `add()`: ignored while `adding` or when the draft is blank. On success the draft clears and `notes` becomes the answer. On failure `addFailed` is `userMessage()` or "Couldn't add the note", and the draft is kept.
  - `val count: Int` is `notes.size` once loaded.
  - Failures use `userMessage()`, with `unreachable` for UNREACHABLE / NO_ANSWER, mirroring `DocumentsViewModel.failure()`.

### [ ] 5.3 — Notes screen and the panel row

Files: `ui/notes/NotesScreen.kt` (new), `ui/viewer/ViewerScreen.kt`, `MainActivity.kt`, `AppContainer.kt`. Apply `design-recipe`: a stack screen with the form bar's back arrow, titled "Notes", the document title as the subtitle.
- Loaded: cards newest first, each with the text (15 `fg`, selectable) and "Ann · 3 Oct 2026, 09:14" (13 `mfg`; just the date when there's no author).
- Bottom area pinned above the keyboard (`imePadding`): a multi-line `AppTextField` "Add a note" and a `Primary` **Add** button, disabled while blank or adding. A failure shows as a notice banner above the field.
- States: skeleton (three note-card blocks), empty ("No notes yet", with the field as its action), load failed (status card, Try again; unreachable wording per the manual).
- `AllDetails` gets a `DetailRow("Notes", "3" / "None")`, made clickable (≥ 48dp, role button, "Open notes") only when `notesEnabled`. It is hidden entirely when notes are off.
- `MainActivity`: `openNotesFor: Int?` (`rememberSaveable`) shows `NotesScreen` over the viewer. Back returns to the viewer, which calls `notesChanged(count)`.
- `AppContainer`: a `notesViewModelFactory(documentId)`, mirroring `viewerViewModelFactory(documentId)`. `MainActivity` builds it with `viewModel<NotesViewModel>(key = "notes-$documentId-$notesOpens", …)`.

### [ ] 5.4 — Tests and manual

Files: `ui/NotesViewModelTest.kt` (new), `ui/ViewerViewModelTest.kt`, `docs/design/README.md`
- The cases in the test plan's Notes and Viewer bullets.
- `docs/design/README.md` § Decisions (#12): the Notes row in all details, the Notes screen shape (note cards, pinned add field), and its states.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*NotesViewModelTest' --tests '*ViewerViewModelTest' --tests '*ArchitectureTest' --tests '*DesignRulesTest'`
- Full: `./build.sh ./gradlew --console=plain testDebugUnitTest koverVerify lintDebug assembleDebug`

---

## Out of scope

- Creating, editing, deleting or reordering saved views, or changing their dashboard/sidebar flags.
- Deleting or editing notes; note highlights in search results.
- Dashboard-style sections showing a view's first documents (Q2 option 2).
- Showing a view's display mode or display fields; the app's row stays as it is.
- More like this combined with a saved view or with search text.
- Offline caching of views or notes.
