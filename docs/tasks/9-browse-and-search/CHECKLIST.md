# Checklist — Browse and search documents

Issue: #9 — Browse and search documents (https://github.com/YasarMahomedAbbas/paperless-companion/issues/9)
Branch: `feat/9-browse-and-search`
Problem: the app opens on an upload screen and can't show what is already in Paperless, so nobody can find a document from the phone.

## Done when

- The app opens on Documents: a list with thumbnails, title, date, correspondent and tags, infinite scroll, pull to refresh, and skeleton / empty / load-failed states.
- Full-text search (the server's index, OCR text included) finds a document by a word in it, with the matching snippet shown, over the home network and over the VPN.
- Filters by tag, correspondent and document type, and a sort order, change the list.
- Upload (Scan / Take photo / Choose file / share) is the green **Add** button and its sheet; uploads in progress are cards at the top of the list; a newly uploaded document shows up in the list.
- Today's menu (Updates, Lock with fingerprint, Sign out) lives in Settings, behind the gear on the Documents title row.
- ViewModels tested; CI passes (`testDebugUnitTest koverVerify lintDebug assembleDebug`), coverage ≥ 80%, `ArchitectureTest` green.

## References

- Paperless API: **no new call.** Everything goes through #7's `DocumentLibrary`: `GET /api/documents/` (`page`, `page_size`, `ordering`, `query`, `tags__id__all`, `correspondent__id`, `document_type__id`, `fields`; `__search_hit__` on `query`), the four label lists, and `/api/documents/{id}/thumb/` through the Coil loader. Shapes, and the v9 `all` / v10 difference, were checked in #7 (`docs/tasks/7-document-data-layer/CHECKLIST.md` § References) against the server source at v3.3.4 and v2.20.15. `~/.config/paperless-companion/verify.env` is absent, so no live `/api/schema/` was read here either: self-verification must read it and try the list, search and filter calls against a real server.
- Design: canvas https://claude.ai/artifact/YPcrFxT1joGdxmBHfiMdf6, rows "Documents (home)", "Add a document", "Search, filters and sort" and "Settings"; manual `docs/design/` (`layouts.md` § Navigation, § List — Documents, § Sheet, § Settings; `components.md`). The manual wins over this checklist on anything visual. Every screen is built with the `design-recipe` skill.
- Related: #16 (sign out bounces back home, its own fix), #10 (document view: rows open nothing until it lands), #11 (selection and bulk edit), #8 (lock timing).

## Context

#7 left a full data layer: `BrowseDocumentsUseCase(DocumentQuery) → DocumentPage(total, documents, hasMore)` with `SearchHit(excerpt, matches)`, `LoadLabelsUseCase() → Labels`, and Coil showing `document.thumbnail`. Today's `HomeScreen` + `HomeViewModel` are upload-only, with a ⋮ menu; `MainActivity` switches screens by hand (`showUpdates`). This ticket makes a Documents screen the home and builds Settings. **Decisions** (see NOTES): paging is hand-rolled in a `DocumentsViewModel` (no Paging library); the next page loads when the last five rows are visible. Labels load alongside the first page, and if either fails the list shows load-failed; pull to refresh reloads both. Search runs as you type, debounced 400 ms, and right away on the keyboard's Search key; a newer request cancels the older one. The filter sheet's "Show N documents" comes from a debounced one-row query. Uploads stay in their own ViewModel (today's `HomeViewModel`, renamed `UploadsViewModel`); when one reaches Done, the list refreshes from the top. Navigation stays hand-rolled in `MainActivity` (Documents → Settings → Updates). Settings carries only what the old menu did, plus the server address. The sort options are those drawn on the canvas.

Paths below are under `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/` (main) and `app/src/test/java/io/github/yasarmahomedabbas/paperlesscompanion/` (test).

## Test plan

- Unit (`DocumentsViewModelTest`, fakes, `MainDispatcherRule` with virtual time): the first load (labels + page 1) goes to loaded, empty or failed (unreachable vs refused wording via `ServerException.userMessage()`). Load-more appends page 2, is ignored while one is running or when `hasMore` is false, and a failed load-more keeps the rows and shows a retry footer. Refresh resets to page 1 and reloads labels. Typing debounces (no call before 400 ms, one call after), submit searches at once, a newer query cancels the older one, and clearing the text goes back to browsing in the chosen sort. Search uses the "Best match" label and rows carry their snippet. Tag (all-of), correspondent and document-type filters plus each sort option map to the right `DocumentQuery`. Clear filters, remove one filter, the filter-preview count (debounced, `pageSize = 1`) and its failure (no count shown). Row mapping covers correspondent name and date, date only, an unknown label id, tags as two plus "+N", and a gone label.
- Unit (`UploadsViewModelTest`, moved from `HomeViewModelTest`): the existing cases, plus `added` firing once when an upload reaches Done (not on failure) and `clear()` dropping everything.
- Unit (`SettingsViewModelTest`): shows the server address, sign out clears the settings.
- Unit (`SortOptionTest`): each option's `DocumentSort` and direction.
- MockWebServer: none new (no new call); #7's `PaperlessLibraryTest` covers every request this screen makes, v9 and v10.
- Real server (self-verification, read-only): `GET /api/documents/?page=1&page_size=25&ordering=-created&fields=…`, page 2, `query=<a word from a known document's OCR text>` (check `__search_hit__`), `tags__id__all`, `correspondent__id`, `document_type__id`, a one-row count query, the four label lists, and one `/thumb/`. All are skipped and recorded if `verify.env` is still absent.
- Device only: thumbnails show and survive a restart from the disk cache (open from #7); pull to refresh and infinite scroll feel; the Add sheet starts the scanner, camera and picker; a shared file lands as an upload card on Documents; search over the VPN.

---

## Checkpoint 1 — Documents ViewModel · commit: `feat(documents): list, search, filter and sort documents in a ViewModel`

Goal: everything the Documents screen shows exists as tested state, with no UI yet.

### [x] 1.1 — Sort options

File: `ui/documents/SortOption.kt` (new)
`enum class SortOption(val label: String, val sort: DocumentSort, val descending: Boolean)`: `NEWEST("Newest first", CREATED, true)` (the default), `OLDEST("Oldest first", CREATED, false)`, `RECENTLY_ADDED("Recently added", ADDED, true)`, `TITLE("Title, A to Z", TITLE, false)`, `CORRESPONDENT("Correspondent, A to Z", CORRESPONDENT, false)`. `const val BEST_MATCH = "Best match"` is the chip label while searching. Test: `ui/SortOptionTest.kt`.

### [x] 1.2 — UI state and row mapping

File: `ui/documents/DocumentsUiState.kt` (new)
- `data class DocumentFilters(val tagIds: Set<Int> = emptySet(), val correspondentId: Int? = null, val documentTypeId: Int? = null) { val count: Int; val isEmpty: Boolean }`. `count` is the number of filters on (each tag counts once).
- `data class DocumentRow(val id: Int, val title: String, val meta: String, val tags: List<String>, val moreTags: Int, val thumbnail: DocumentThumbnail, val snippet: SearchHit?)`.
- `internal fun Document.toRow(labels: Labels): DocumentRow`: `meta` is `"<correspondent> · d MMM yyyy"`, or just the date without a correspondent, or just the correspondent without a date (`DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)`). An id with no label is skipped. Tags are the first two names in the order the document lists them, and `moreTags` is the rest; untitled shows `"Untitled"`.
- `sealed interface ListStatus { Loading; Loaded; Empty; data class Failed(val message: String, val unreachable: Boolean) }`.
- `data class DocumentsUiState(val text: String = "", val searching: Boolean = false, val filters: DocumentFilters = DocumentFilters(), val sort: SortOption = SortOption.NEWEST, val labels: Labels? = null, val rows: List<DocumentRow> = emptyList(), val total: Int? = null, val status: ListStatus = ListStatus.Loading, val refreshing: Boolean = false, val loadingMore: Boolean = false, val loadMoreFailed: String? = null, val filterPreviewCount: Int? = null)`, with `val sortLabel` (`BEST_MATCH` when `text` is not blank), and `val showResultLine` (`text` not blank or filters on).

### [x] 1.3 — DocumentsViewModel

File: `ui/documents/DocumentsViewModel.kt` (new)
`class DocumentsViewModel(private val browse: BrowseDocumentsUseCase, private val loadLabels: LoadLabelsUseCase, private val debounceMillis: Long = 400) : ViewModel()`, `val state: StateFlow<DocumentsUiState>`. It loads on `init`.
- `fun refresh()`: reloads labels and page 1 side by side (`coroutineScope { async … }`), keeping the rows on screen with `refreshing = true`. The first load uses `status = Loading` instead. Either failure gives `Failed(e.userMessage(), kind == UNREACHABLE || kind == NO_ANSWER)` for a `ServerException`, or a generic message for anything else. A failure during a pull-to-refresh with rows already loaded also goes to `Failed` (no cache, per the manual).
- `fun loadMore()`: no-op when `loadingMore`, the status isn't `Loaded`, or the last page had `hasMore == false`. Otherwise it fetches `page + 1` and appends. A failure sets `loadMoreFailed` and keeps the rows; calling `loadMore()` again retries.
- `fun onTextChange(text: String)`: updates `text` and restarts a debounced job (`delay(debounceMillis)`) that reloads page 1. `fun search()` (keyboard Search key) cancels the debounce and reloads now. `fun startSearch()` / `fun stopSearch()` toggle `searching`; `stopSearch` also clears `text` and reloads if it wasn't blank.
- `fun setSort(option: SortOption)`, `fun setFilters(filters: DocumentFilters)`, `fun removeTag(id: Int)`, `fun removeCorrespondent()`, `fun removeDocumentType()`, `fun clearFilters()`: each reloads page 1 (showing `Loading`, and loading the labels too if they never loaded). `clearFilters()` also clears the search text, since its only caller is "No documents match" → Clear filters.
- `fun previewFilters(draft: DocumentFilters)`: debounced; queries with `pageSize = 1` and sets `filterPreviewCount = page.total`. A failure sets it to null. `fun stopPreview()` cancels it and clears the count.
- One `loadJob` for page loads: a new page-1 load cancels the running one (and any load-more), so an older search never overwrites a newer one.
- Query building: `DocumentQuery(text = text.takeIf { it.isNotBlank() }, tagIds, correspondentId, documentTypeId, sort = sort.sort, descending = sort.descending, page, pageSize = 25)`.
- Status after a page-1 load: `Empty` when the page has no documents, else `Loaded`.

### [x] 1.4 — Fake library that pages and blocks

File: `Fakes.kt` (test)
Extend `FakeLibrary` without breaking its current users: `var pages: ((DocumentQuery) -> DocumentPage)? = null`, used by `documents()` when set; `var documentsGate: CompletableDeferred<Unit>?` awaited before answering, for in-flight and cancel tests; `var labelsFailure: Exception?`, thrown only by the label calls; `val queries`, every `documents()` query recorded before it waits or fails.

### [x] 1.5 — Tests

File: `ui/DocumentsViewModelTest.kt` (new), `ui/SortOptionTest.kt` (new)
The cases in the test plan's first and fourth bullets, on `MainDispatcherRule` and virtual time (`advanceTimeBy(399)` makes no call, `advanceTimeBy(1)` makes one).

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*DocumentsViewModelTest' --tests '*SortOptionTest' --tests '*ArchitectureTest'`

---

## Checkpoint 2 — Shared parts for the list · commit: `feat(ui): add the document row, search field, chips, sheets and states`

Goal: every part the Documents, sheet and Settings screens need exists once in `ui/components/`, built to `components.md` with the `design-recipe` skill, each with a `@Preview`. No call site passes a colour, shape or size.

### [x] 2.1 — Document row

File: `ui/components/DocumentRow.kt` (new)
`DocumentRowCard(title: String, meta: String, tags: List<String>, moreTags: Int, thumbnail: Any?, snippet: AnnotatedString?, onClick: () -> Unit)`. 56 × 72 `AsyncImage` (`r-thumb`, crop) with a `raised` placeholder, `TagPill`s, and a search variant with a two-line snippet in the tags' place (as the canvas draws it). Put the snippet builder `fun SearchHit.highlighted(match: SpanStyle): AnnotatedString` here, with `searchMatch` (`primary-tint` background on `fg`) as the style the screen passes, ranges clamped to the excerpt; the style is a parameter so the test needs no theme. Unit-tested in `ui/HighlightTest.kt`. Components take plain values, not `DocumentRow`, so they keep no dependency on `ui/documents`.

### [x] 2.2 — Search field, chips, icon button, action button

Files: `ui/components/SearchField.kt`, `ui/components/Chips.kt`, `ui/components/Buttons.kt` (extend)
- `SearchField(value, onValueChange, modifier, placeholder, variant: SearchFieldVariant { Page, Sheet }, onSearch, onFocusChange)`: 52 high (48 in a sheet), fully round, a 2dp ring while focused (tracked inside), Clear icon button once there is text, IME action Search.
- `AppChip(text, onClick, variant: ChipVariant { Action, Selected, Unselected, Removable }, leadingIcon?, contentDescription?, enabled)`, and `ChipRow` (scrolls sideways on the page gutter).
- `AppIconButton(icon, contentDescription, onClick, variant: IconButtonVariant { Plain, Tonal }, iconSize)` (48 × 48).
- `AddButton(onClick)`: the extended FAB "Add".
- The canvas's icons in `AppIcons.kt` (gear, search, close, back, plus, chevron, filters, sort, scan, camera, file, person, server): `material-icons-core` has only filled shapes, and #6 already draws the outlined ones (recorded in `docs/design/README.md` § Decisions).

### [x] 2.3 — Status card, skeleton, footer

Files: `ui/components/StatusCard.kt`, `ui/components/Skeleton.kt`
- `StatusCard(icon, tone: StatusTone { Empty, Unreachable, Refused, Done }, title, body, actionLabel, onAction)`.
- `DocumentRowSkeleton()` (the row's shapes in shim blocks, a 1.8 s sweep) and `LoadingMoreFooter()` (one skeleton row + spinner + "Loading more"); `LoadMoreFailedFooter(message, onRetry)`, an `Unreachable` notice banner with Try again.

### [x] 2.4 — Bottom sheet, option list, settings row

Files: `ui/components/Sheets.kt`, `ui/components/SettingsRow.kt`
- `AppBottomSheet(title, onDismiss, variant: SheetVariant { Short, List }, actionBar?, content)` on M3 `ModalBottomSheet` with the manual's shape, handle and scrim; a list sheet is 85% high and its content scrolls itself; `ListSheetActionBar(content)`, pinned above a `border-muted` divider.
- `ActionOptionRow(icon, title, subtitle, main: Boolean, onClick)` (the Add sheet), `SingleChoiceRow(text, selected, onClick, muted = false)`, `MultiChoiceRow(text, checked, onClick)`.
- `SettingsCard { … }`, `SettingsDivider()`, `SettingsRow(label, secondary?, icon?, pill?, trailing: SettingsTrailing { Chevron, Switch(checked, onChange), None }, onClick?)`.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*HighlightTest' --tests '*DesignRulesTest' --tests '*ArchitectureTest' lintDebug`
- Previews render (`assembleDebug` compiles them); `docs/design/checklist.md` walked for each part.

---

## Checkpoint 3 — Documents becomes home · commit: `feat(documents): open on the document list with Add for uploads`

Goal: the app opens on Documents, with the list in all its states, pull to refresh, infinite scroll, upload cards on top and the Add sheet. Uploads keep working exactly as today.

### [x] 3.1 — Uploads ViewModel

Files: `ui/home/HomeViewModel.kt` → `ui/documents/UploadsViewModel.kt` (`git mv`), `ui/HomeViewModelTest.kt` → `ui/UploadsViewModelTest.kt`
Rename the class to `UploadsViewModel` and drop `serverUrl` from its state (Settings shows it now), the `settings` parameter and `signOut()`. Add `fun clear()` (drops every upload and source, used on sign-out) and `val added: Flow<Int?>`, which emits the document id (or null) once when an upload reaches `UploadState.Done`. It is a buffered `Channel` rather than a `SharedFlow`, so an upload accepted while Settings is open still refreshes the list when Documents comes back. Keep every existing test, and add the `added` and `clear()` tests.

### [x] 3.2 — Documents screen

File: `ui/documents/DocumentsScreen.kt` (new; replaces `ui/home/HomeScreen.kt`, which is deleted)
`DocumentsScreen(documents: DocumentsViewModel, uploads: UploadsViewModel, files: DocumentFiles, pending, onPendingConsumed, onDocuments, onOpenSettings)`. The layout follows `layouts.md` § List — Documents:
- A `LargeTitleBar("Documents")` with the tonal gear ("Settings"), then the search field and the chip row (Filters / sort and the applied filters, wired in checkpoint 4; here the sort chip and the Filters chip only open nothing yet).
- `PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = documents::refresh)` around a `LazyColumn`. The "Uploading" section with `UploadCard`s and "Clear finished" goes at the top when there are uploads, then the result line ("23 documents", only when `showResultLine`), then rows (`key = id`), then the loading-more or load-more-failed footer.
- Infinite scroll: `snapshotFlow { listState.layoutInfo }` triggers `documents.loadMore()` when the last visible index is within five rows of the end.
- States: `Loading` gives skeleton rows (no spinner). `Empty` gives a status card ("No documents yet", "Add a document" opens the Add sheet), or, with search text or filters on, "Nothing matches "<text>"" / "No documents match" with "Clear filters". `Failed` gives a status card titled "Couldn't load your documents" (the canvas's copy): unreachable → `warning`, "Check you're on the home network or the VPN, then try again."; refused → `destructive` with the message; both with Try again.
- The Add sheet is its own file, `ui/documents/AddSheet.kt`; the screen's actions go through a `DocumentsScreenActions` class so the previews pass no-ops.
- Row tap does nothing yet (#10 adds the document view); long-press does nothing (#11).
- `AddButton` opens `AddSheet`, which moves today's scanner, photo and file launchers and the Play-services camera notice out of `HomeScreen` unchanged. The camera notice stays an `Info` notice banner above the list, as on the "Add a document — scanner needs camera" board.
- `LaunchedEffect(Unit) { uploads.added.collect { documents.refresh() } }`.
- Previews for view, loading, empty, failed, uploading and loading more.

### [x] 3.3 — Wiring

Files: `AppContainer.kt`, `MainActivity.kt`
- `documentsViewModelFactory` (`DocumentsViewModel(BrowseDocumentsUseCase(library, settings), LoadLabelsUseCase(library, settings))`) and `uploadsViewModelFactory` (replacing `homeViewModelFactory`). Drop the "#9-#11 build their use cases from it" comment's #9.
- `MainActivity` shows `DocumentsScreen` where `HomeScreen` was, with both ViewModels keyed on `home.config.baseUrl` (as `"documents <url>"` and `"uploads <url>"`: one store key can hold only one ViewModel). Updates stays reachable through a temporary path until checkpoint 5 (the gear opens Updates directly), so nothing is lost between commits.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*UploadsViewModelTest' --tests '*DocumentsViewModelTest' --tests '*ArchitectureTest' --tests '*DesignRulesTest' assembleDebug`

---

## Checkpoint 4 — Search, filters and sort · commit: `feat(documents): search, filter and sort the document list`

Goal: the "Search, filters and sort" row works end to end.

### [x] 4.1 — Searching mode

File: `ui/documents/DocumentsScreen.kt`
Focusing the search field calls `startSearch()`: the title row gives way to a back arrow ("Stop searching", which calls `stopSearch()` and clears focus; `BackHandler` too). The field stays in the same row, so it keeps focus. `onValueChange` goes to `onTextChange`, the IME Search key to `search()`. Rows show their snippet via `SearchHit.highlighted(searchMatch)`. The sort chip reads `state.sortLabel` and is disabled while there is search text (results come by relevance). No results shows "No documents match" with Clear filters (clears the text too).

### [x] 4.2 — Sort sheet

File: `ui/documents/SortSheet.kt` (new)
A short sheet "Sort by" with a `SingleChoiceRow` per `SortOption` and the canvas's hint that "Newest first" goes by the document's date. Tapping one calls `setSort` and closes. The labels follow the canvas: "Title, A to Z" and "Correspondent, A to Z" (1.1's "A–Z" changed with them).

### [x] 4.3 — Filters sheet

File: `ui/documents/FiltersSheet.kt` (new)
A list sheet "Filters" holding a local draft `DocumentFilters` (starting from `state.filters`):
- Tags as `Selected` / `Unselected` chips (all-of), with the inbox tag first.
- Correspondent as a `PickerField` (new in `ui/components/PickerField.kt`, the manual's picker field) opening a single-choice list sheet with search, "Any correspondent" first and a no-match card.
- Document type as single-choice chips, "Any" first.
- The pinned bar has "Clear all" (`Text`, as on the canvas) and the primary "Show N documents" ("Show documents" while `filterPreviewCount` is null). Each draft change calls `previewFilters(draft)`; Show calls `setFilters(draft)` and closes; dismissing calls `stopPreview()`.
- When `state.labels` is null, the sheet shows the load-failed state with Try again (`refresh()`), on a `StatusCard(inSheet = true)` (`raised`, so no card on a card of the same tone).

### [x] 4.4 — Chip row

File: `ui/documents/DocumentsScreen.kt`
Filters chip is `Action` with no filters on, `Selected` "Filters · N" with some. Then one `Removable` chip per applied filter, with content description "Remove filter <name>", calling `removeTag` / `removeCorrespondent` / `removeDocumentType`, then the sort chip (the "Filters — applied" board's order). A horizontally scrolling row.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*DocumentsViewModelTest' --tests '*ArchitectureTest' --tests '*DesignRulesTest' assembleDebug`

---

## Checkpoint 5 — Settings · commit: `feat(settings): move Updates, the fingerprint lock and sign out into Settings`

Goal: the gear opens Settings; Updates sits under it; the old ⋮ menu is gone.

### [x] 5.1 — SettingsViewModel

File: `ui/settings/SettingsViewModel.kt` (new)
`class SettingsViewModel(private val settings: SettingsRepository, updates: UpdateStore, installedVersionCode: () -> Long) : ViewModel()`, with `val serverUrl: String` (from `settings.load()?.baseUrl.orEmpty()`), `val updateReady: StateFlow<String?>` (the offer's version when it is newer than the installed one, so the pill is tested here rather than worked out in the activity) and `fun signOut()` (`settings.clear()`). Test: `ui/SettingsViewModelTest.kt`.

### [x] 5.2 — Settings screen

File: `ui/settings/SettingsScreen.kt` (new)
Back bar "Settings" (new `BackBar` in `ui/components/Headers.kt`). Server card: the address. Security card: "Unlock with fingerprint" switch row with the canvas's second line (only when `lockAvailable`; `onLockChange` as today, through the system prompt). App card: "Updates" row with "Version <installed>", a chevron, and a status pill ("<version> ready") from `viewModel.updateReady`. Then a centred `Secondary` "Sign out" opening a confirm dialog ("Sign out?" with the canvas's body, Cancel / Sign out `Primary`) per the canvas "Settings — sign out confirm" board, on a new `AppDialog` in `ui/components/Dialog.kt`. No "Signed in as", no lock-again delay, no Source and licences row (out of scope).

### [x] 5.3 — Navigation

Files: `MainActivity.kt`, `AppContainer.kt`
Replace `showUpdates: Boolean` with `screen: Screen` (`enum class Screen { DOCUMENTS, SETTINGS, UPDATES }`, held in `mutableStateOf`). The gear goes to SETTINGS, the Updates row to UPDATES. Back goes UPDATES → SETTINGS → DOCUMENTS (`BackHandler`, disabled while locked, as today). The "Update ready" notification extra opens UPDATES, whose back goes to SETTINGS. Sign out calls `settingsViewModel.signOut()`, `uploadsViewModel.clear()`, `screen = DOCUMENTS`, bumps a `session` counter, then `gate.onSignedOut()`. The ViewModels are keyed on `"<session> <baseUrl>"`, so signing in again to the same server starts the list afresh instead of showing the old session's rows; `session` and `screen` are kept across a rotation in `onSaveInstanceState`, since the ViewModels outlive it. Add `settingsViewModelFactory` to `AppContainer`. Remove the temporary checkpoint-3 path.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest koverVerify lintDebug assembleDebug` (the full CI command)

---

## Out of scope

- Opening a document (#10): a row tap does nothing yet.
- Selection, bulk edit, delete and undo (#11): long-press does nothing.
- Saved views and "more like this" (#12).
- An offline cache of the list (the manual says no cache).
- In Settings: "Signed in as" (needs a new Paperless call), the lock-again delay (and the 1-minute vs 5-minute mismatch with the design) and Source and licences. These are follow-up tickets, filed by doc-drift or raise-pr.
- The sign-out bounce (#16) and 403 wording (#21): their own tickets.
- New Paperless calls or changes to `data/`.
- Restyling the Updates and lock screens (follow-up from #6).
