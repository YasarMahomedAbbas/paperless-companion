# Checklist — Toggle the document list between list and cards with previews

Issue: #43 — Toggle the document list between list and cards with previews (https://github.com/YasarMahomedAbbas/paperless-companion/issues/43)
Branch: `feat/43-list-or-card-previews`
Problem: the Documents list only shows a 56 × 72 thumbnail per row, too small to recognise a document without opening it, and there is no way to see larger previews.

## Done when

- A toggle on the Documents screen switches between the list and a cards view; the choice is remembered across app restarts (and sign-out).
- Cards show the thumbnail prominently with the title and "Correspondent · date".
- Thumbnails load lazily and are cached (the existing Coil loader); scrolling a long list in cards stays smooth, and infinite scroll, pull to refresh, selection, search, uploads and every state (skeleton, empty, failed, loading more) work in both modes.
- Built with `design-recipe`; the card's recipe is in `docs/design/components.md` and the choices in `docs/design/README.md` § Decisions.

## References

- Paperless API: no new or changed call. Cards use the existing `GET /api/documents/{id}/thumb/` (docs/api.md § Document versions: it now also accepts an optional `?version=`, which we don't send; no v9/v10 difference). Paperless generates thumbnails as WebP at most 500 px wide (`src/documents/parsers.py`, `_THUMBNAIL_MAX_WIDTH = 500`), which is why cards are two columns (about 180 dp, about 470 px at 2.6x) rather than full width.
- Design manual: `docs/design/components.md` § Document row, § Top bars; `docs/design/layouts.md` § List — Documents; `docs/design/README.md` rules 4, 5, 8, 9.

## Context

The list is a `LazyColumn` in `DocumentsScreen.kt` (`DocumentList`) of `DocumentRowCard`s (`ui/components/DocumentRow.kt`), with an infinite-scroll trigger on a `LazyListState` and full-width notice, upload, result-line, status and footer items. Thumbnails are already lazy and cached: `AsyncImage(model = DocumentThumbnail)` goes through `data/ThumbnailImages.kt` (memory and disk cache keyed on server + id + modified). Settled approach (pipeline on the user's behalf): a **two-column grid of portrait cards** (Q1-1), toggled by an **icon button in the large title bar beside Settings** (Q2-1), and **cards drop the search snippet** (Q3-1). Decided in planning: one `LazyVerticalGrid` serves both modes (1 column for list, 2 for cards), with the full-width items spanning the line, so there is one scroll state and one load-ahead trigger; switching keeps the first visible document in view; the layout is a domain preference (`DocumentLayout`, `DisplayPreferences`) stored on the phone in its own SharedPreferences file through `KeyValueStore`, kept across sign-out, default list; cards get a skeleton grid; selection on a card is a round check over the thumbnail's top-start corner plus the 2dp `primary` ring and `primary-tint` ground; tags are hidden on cards.

## Test plan

- Unit: `PrefsDisplayPreferences` against `MapStore` (default LIST, round-trip CARDS, an unknown stored value reads LIST); `DocumentLayoutUseCase` (reads, sets); `DocumentsViewModel` (state starts with the stored layout, `toggleLayout()` flips state and persists it, a new ViewModel on the same store starts in the stored layout, toggling doesn't reload the documents); `ArchitectureTest` still green (new domain file has no Android imports).
- MockWebServer: none (no new call).
- Real server: none needed. No `verify.env` on this machine, so self-verification records the server checks as skipped; the thumb endpoint is unchanged.
- Device only: how smoothly a long cards list scrolls, how sharp the thumbnails look at the card size, and TalkBack reading of a card and the toggle.

---

## Checkpoint 1 — The remembered layout · commit: `feat(documents): remember whether the list shows rows or cards`

Goal: the ViewModel exposes the layout, toggles it and persists it on the phone; no UI change yet.

### [x] 1.1 — Domain model and interface

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/domain/model/Documents.kt`
Add `enum class DocumentLayout { LIST, CARDS }` with a KDoc ("How the document list shows its documents: rows, or cards with a large preview").

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/domain/repository/Repositories.kt`
Add:
```kotlin
/** How the app is shown on this phone; kept across sign-out. */
interface DisplayPreferences {
    fun documentLayout(): DocumentLayout
    fun setDocumentLayout(layout: DocumentLayout)
}
```

### [x] 1.2 — Use case

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/domain/usecase/DocumentLayoutUseCase.kt` (new)
`class DocumentLayoutUseCase(private val preferences: DisplayPreferences) { fun current(): DocumentLayout; fun set(layout: DocumentLayout) }`, each delegating. Mirror the small use cases in `SavedViewsUseCase.kt` for style.

### [x] 1.3 — Data implementation

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/data/PrefsDisplayPreferences.kt` (new)
`class PrefsDisplayPreferences(private val store: KeyValueStore) : DisplayPreferences`. Key `"document_layout"`, value the enum's `name`; a missing or unknown value reads `LIST` (`DocumentLayout.entries.firstOrNull { it.name == value } ?: LIST`). `set` writes through `store.edit { put(KEY, layout.name) }`.

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/AppContainer.kt`
`private val display: DisplayPreferences = PrefsDisplayPreferences(PrefsStore(context.getSharedPreferences("display", Context.MODE_PRIVATE)))`. Its own file, so `SealedSettingsRepository.clear()` on sign-out doesn't touch it. Pass `DocumentLayoutUseCase(display)` to `DocumentsViewModel` in `documentsViewModelFactory`.

Test: `app/src/test/java/io/github/yasarmahomedabbas/paperlesscompanion/data/PrefsDisplayPreferencesTest.kt` (new) on `MapStore`: default LIST, CARDS round-trips, `"GRID"` stored reads LIST, setting LIST after CARDS stores `"LIST"`.

### [x] 1.4 — ViewModel state and action

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/ui/documents/DocumentsUiState.kt`
Add `val layout: DocumentLayout = DocumentLayout.LIST` to `DocumentsUiState`.

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/ui/documents/DocumentsViewModel.kt`
Constructor gains `private val layouts: DocumentLayoutUseCase` after `savedViews` (before `debounceMillis`). The initial state is `DocumentsUiState(layout = layouts.current())`. Add `fun toggleLayout()`: flips LIST ↔ CARDS in state and calls `layouts.set(...)`; it never touches the load jobs.

Test: `app/src/test/java/io/github/yasarmahomedabbas/paperlesscompanion/ui/DocumentsViewModelTest.kt`: `viewModel()` builds with `DocumentLayoutUseCase(PrefsDisplayPreferences(store))` on a shared `MapStore`. New tests: starts in LIST by default; starts in CARDS when stored; `toggleLayout` flips and persists (a second ViewModel on the same store starts in CARDS); toggling makes no extra page request (`library` request count unchanged) and keeps the rows. Add a domain test for `DocumentLayoutUseCase` in `app/src/test/java/io/github/yasarmahomedabbas/paperlesscompanion/domain/DocumentUseCasesTest.kt`.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*PrefsDisplayPreferencesTest' --tests '*DocumentsViewModelTest' --tests '*DocumentUseCasesTest' --tests '*ArchitectureTest'`
- The new tests pass; the existing DocumentsViewModel tests still pass with the new constructor argument.

---

## Checkpoint 2 — The document card · commit: `feat(ui): a document card with a large preview`

Goal: the card component and its skeleton exist, follow the manual and have previews; the screen doesn't use them yet.

### [x] 2.1 — Card component

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/ui/components/DocumentCard.kt` (new)
```kotlin
@Composable
fun DocumentCard(
    title: String,
    meta: String,
    thumbnail: Any?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selection: Boolean? = null,
    onLongClick: (() -> Unit)? = null,
)
```
`Column` on `surfaceContainer`, `MaterialTheme.shapes.medium` (`r-card`), padding `Spacing.s8`; selected: `primaryContainer` ground and a 2dp `primary` border on the same shape, `semantics { selected }` when `selection != null`; `combinedClickable(onClick, onLongClick, onLongClickLabel = "Select")` exactly as `DocumentRowCard` (the clickable merges its descendants, so TalkBack reads it as one item, as the row does). Inside: the thumbnail `AsyncImage` `fillMaxWidth().aspectRatio(3f / 4f)`, `shapes.extraSmall` (`r-thumb`) clip, `ContentScale.Crop` with `Alignment.TopCenter` (the letterhead shows), `surfaceContainerHigh` placeholder, `contentDescription = null`; then 8 below it the title (`cardTitle`, one line, ellipsised) and the meta (`bodySmall`-sized 13 `onSurfaceVariant`, one line, ellipsised), padded 4 horizontally. Selectable: a 24dp round check at the thumbnail's top-start, 8 in, reusing the check drawing from `DocumentRow.kt` (extract it there to an `internal fun SelectionCheck(selected: Boolean, modifier: Modifier)` rather than copying), on a `surfaceContainer` disc so it reads on any thumbnail. Mirror `DocumentRow.kt` for the image and click handling. Previews: plain, long title, no meta, selectable unselected and selected.

### [x] 2.2 — Card skeleton

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/ui/components/Skeleton.kt`
Add `fun DocumentCardSkeleton(modifier: Modifier = Modifier)`: the card's shell with a 3:4 skeleton block and two text bars (title 70% width, meta 45%), using the existing shimmer blocks as `DocumentRowSkeleton` does.

### [x] 2.3 — Layout icons

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/ui/components/AppIcons.kt`
Draw `Cards` (2 × 2 rounded squares) and `Rows` (three rounded bars) in the same stroke style as the other drawn icons (`material-icons-core` lacks both; see README § Decisions "More drawn icons").

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*DesignRulesTest' --tests '*ThemeTest' lintDebug`
- No colour literal outside `ui/theme/`; the previews compile.

---

## Checkpoint 3 — Cards on the Documents screen · commit: `feat(documents): switch the list between rows and cards with previews`

Goal: the toggle works on the screen and every list state renders in both modes.

### [ ] 3.1 — One grid for both modes

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/ui/documents/DocumentsScreen.kt`
Replace the `LazyColumn` in `DocumentList` with `LazyVerticalGrid(columns = GridCells.Fixed(if (state.layout == CARDS) 2 else 1), state = gridState, contentPadding = PaddingValues(start = cardGutter, end = cardGutter, bottom = 112.dp), horizontalArrangement = spacedBy(Spacing.rowGap))`. Every non-document item (notice, "Uploading" heading, upload cards, result line, empty, failed, loading-more and more-failed footers) is `item(span = { GridItemSpan(maxLineSpan) })`; drop the per-item horizontal `cardPadding` where the grid's padding now does it, keeping the bottom `rowGap`. The result line and the section heading keep their 20 text gutter (8 extra start/end on top of the 12). Documents: `items(state.rows, key = { it.id })` render `DocumentRowCard` in LIST and `DocumentCard` in CARDS with the same click, long-click and selection lambdas. Loading: `SKELETON_ROWS` row skeletons in LIST, `SKELETON_CARDS = 6` card skeletons in CARDS.

`DocumentsContent`'s `listState: LazyListState` becomes `gridState: LazyGridState = rememberLazyGridState()`; the load-ahead `snapshotFlow` reads `gridState.layoutInfo` the same way (in cards, `LOAD_AHEAD` items is 2.5 lines, which is fine). Wherever `MainActivity` or the screen creates/scrolls the list state (scroll to top after a refresh, etc.), switch to the grid state's equivalents.

### [ ] 3.2 — Keep the place when switching

File: `DocumentsScreen.kt`
`LaunchedEffect(state.layout)` (skipping the first composition): before the column count changes, remember `gridState.firstVisibleItemIndex`; after, `scrollToItem` the same index. Since items keep their keys and order, the first visible document stays at the top.

### [ ] 3.3 — The toggle

File: `DocumentsScreen.kt`
`DocumentsScreenActions` gains `val onToggleLayout: () -> Unit = {}`, wired to `documents::toggleLayout` where the actions are built. In the `LargeTitleBar("Documents")` slot, before Settings: `AppIconButton(if (state.layout == LIST) AppIcons.Cards else AppIcons.Rows, if (state.layout == LIST) "Show as cards" else "Show as list", actions.onToggleLayout, variant = IconButtonVariant.Tonal)`, 8 apart. It hides with the title row while searching or selecting; the layout stays as chosen in both.

### [ ] 3.4 — Previews

File: `DocumentsScreen.kt`
Add previews: cards view, cards loading (skeleton grid), cards selecting, cards with uploads, cards loading more.

### [ ] 3.5 — The manual

File: `docs/design/components.md`
Add `## Document card` after § Document row: the recipe in 2.1 (two columns, 8 apart, 12 from the edges; `surface`, `r-card`, padding 8; 3:4 thumbnail, `r-thumb`, cropped from the top; title 16/500 one line; meta 13 `mfg` one line; no tags, no snippet; selectable: a 24dp check over the thumbnail's top-start, `primary-tint` and 2dp `primary` ring). § Top bars, Large title: the Documents title bar holds the layout toggle beside Settings.

File: `docs/design/layouts.md` § List — Documents: the title bar line shows `(Cards/Rows) (Settings)`; a bullet that the toggle switches rows ↔ a two-column card grid, remembered on the phone, and that searching and selecting keep the chosen layout.

File: `docs/design/README.md` § Decisions: a "Settled while building list or cards (#43)" block with Q1-1/Q2-1/Q3-1 and the drawn icons, the skeleton grid, and the 500 px thumbnail reasoning.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest koverVerify lintDebug assembleDebug`
- The full check is green; the previews render the cards view, its skeleton and selection.

---

## Out of scope

- A third layout, adaptive column counts for tablets or landscape, or a column-count setting.
- Larger or higher-resolution previews (rendering the PDF's first page or fetching `/preview/`); cards use the existing thumbnail.
- Showing tags or the search snippet on cards.
- Changes to the thumbnail loader or its caches, or to any Paperless call.
- A Settings entry for the layout.
- A design canvas (the ticket isn't `design`-labelled).
