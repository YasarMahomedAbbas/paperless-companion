# Notes — Toggle the document list between list and cards with previews

Issue: #43 — Toggle the document list between list and cards with previews (https://github.com/YasarMahomedAbbas/paperless-companion/issues/43)
Branch: `feat/43-list-or-card-previews`

## Planning

- Approach: one `LazyVerticalGrid` on the Documents screen (1 column for rows, 2 for cards), with a new `DocumentCard` and a layout toggle in the title bar. The choice is a domain `DisplayPreferences` stored in its own SharedPreferences file. No Paperless call changes; the existing lazy, cached thumbnails feed the cards.
- Decided by the pipeline on the user's behalf (approach questions, flag: ux):
  - **Q1, card layout**: **option 1** taken. A two-column grid of portrait cards: a 3:4 thumbnail cropped from the top, then the title and "Correspondent · date". Paperless thumbnails are at most 500 px wide, so they stay sharp at about 180 dp. The other options were (2) one full-width card per row, where the thumbnail looks soft and about two fit on screen, and (3) an adaptive grid of cards at least 160 dp wide.
  - **Q2, where the toggle sits**: **option 1** taken. An icon button in the large title bar beside Settings ("Show as cards" / "Show as list"), hidden with the title row while searching or selecting. The other options were (2) a chip at the end of the chip row and (3) a Settings switch only.
  - **Q3, cards while searching**: **option 1** taken. Cards keep their shape and drop the snippet; switching to the list shows it. The other options were (2) a two-line snippet on each card and (3) always showing the list while searching.
- Decided in planning; the pipeline accepted them on the user's behalf (flag: ux):
  - One grid for both modes, with the notice, uploads, result line, states and footers spanning the full width, so there is one scroll state and one load-ahead trigger.
  - Switching keeps the first visible document in view.
  - The layout is remembered on the phone in its own `display` preferences file. It survives sign-out, and the default is list.
  - Cards get a skeleton grid. Selection is a round check over the thumbnail's corner plus the 2dp `primary` ring and `primary-tint` ground. No tags on cards.
- API: no new or changed call; `/api/documents/{id}/thumb/` is unchanged (docs only add an optional `?version=`). `~/.config/paperless-companion/verify.env` is absent, so real-server checks will be skipped.
- Open risks: smooth scrolling and thumbnail sharpness at card size can only be judged on a phone.

## Implementation

- Shipped:
  - The remembered layout: `DocumentLayout` and `DisplayPreferences` in `domain/`, `DocumentLayoutUseCase`, `PrefsDisplayPreferences` (`data/PrefsDisplayPreferences.kt:7`) on its own `display` SharedPreferences file (`AppContainer.kt:80`), so sign-out keeps it; `DocumentsViewModel.toggleLayout()` (`ui/documents/DocumentsViewModel.kt:126`) flips and persists without touching the load jobs.
  - `DocumentCard` (`ui/components/DocumentCard.kt:44`), `DocumentCardSkeleton` (`ui/components/Skeleton.kt:94`), a `cards` variant of `LoadingMoreFooter` (`Skeleton.kt:118`), drawn `Cards` / `Rows` icons (`ui/components/AppIcons.kt:61`); the row's `SelectionCheck` is now `internal` and shared.
  - Documents is one `LazyVerticalGrid` for both layouts (`ui/documents/DocumentsScreen.kt:656`), the toggle before Settings in the title bar (`DocumentsScreen.kt:490`), the place kept across a switch (`DocumentsScreen.kt:649`), five cards previews.
  - Manual: `docs/design/components.md` § Document card and § Top bars, `layouts.md` § List — Documents, `README.md` § Decisions "Settled while building list or cards (#43)".
- Tests: full check `testDebugUnitTest koverVerify lintDebug assembleDebug` green; 542 passed / 0 failed (debug unit tests). New: `PrefsDisplayPreferencesTest` (4), 4 layout tests in `DocumentsViewModelTest`, 1 in `DocumentUseCasesTest`. In this run `koverVerify` did not run `testReleaseUnitTest` (no release test task in the log), which contradicts CLAUDE.md's "koverVerify also runs the release unit tests"; left for doc-drift.
- Outstanding: none unticked. Device-only (for self-verification): how smoothly a long cards list scrolls, how sharp thumbnails look at card size, TalkBack on a card and the toggle, and that switching keeps the first document in view.
- Contradicted the plan: 3.1's grid-wide horizontal `contentPadding` would have doubled the gutter of `StatusCard` and `SectionHeading`, which pad themselves (20). Resolved by keeping the full-width items' own padding and padding each card cell instead (`cardCell`, `DocumentsScreen.kt:833`: 12 at the edge, 4 toward the other column), same widths and gaps as planned; the checklist text was updated. The loading-more footer's single skeleton row looked wrong under cards, so it gained a `cards` variant (recorded in the manual).

## Review

- Fixed: none. Checked and held up: the switch anchor is taken before the grid remeasures, and every item keeps its key and order in both layouts, so `scrollToItem` brings back the same document. `cardCell(index)` works out the column correctly because every item before the documents spans the line. The cards loading-more footer lines up with the grid (12 from the edges, 8 apart). Toggling leaves the load jobs alone (tested). Selection, long-press and TalkBack merging on the card mirror the row.
- Raised: none
- API: no endpoint touched; cards reuse `GET /api/documents/{id}/thumb/` through the unchanged loader.
- Layers: clean (`DocumentLayout` / `DisplayPreferences` in domain with no Android or Paperless names; `PrefsDisplayPreferences` only named in `AppContainer`).

## Doc drift

- Applied: `README.md` intro § Documents — the rows-or-cards toggle beside Settings, remembered on the phone (rung 3, using the app).
- Applied: `README.md` § Architecture use-case table — a `DocumentLayoutUseCase` row; the table listed every other use case, so leaving it out would make it read as complete when it isn't (rung 3).
- Applied: `docs/design/README.md` § #43 decisions — the 500 px thumbnail claim now cites `_THUMBNAIL_MAX_WIDTH` in Paperless's `src/documents/parsers.py` (checked with `gh search code`), so a server fact names its source (rung 4).
- Skipped: the branch's manual edits (`components.md` § Document card and § Top bars, `layouts.md` § List — Documents) — checked against `DocumentCard.kt`, `Skeleton.kt` and `DocumentsScreen.kt` (padding 8, 3:4 crop from the top, check on a `surface` disc, 70%/45% bars, six skeleton cards, two columns, labels "Show as cards" / "Show as list"); all hold.
- Skipped: `PrefsDisplayPreferences` and the `display` preferences file in the README's `data/` block — the block doesn't list the other preference stores either, and `AppContainer` names it.
- Skipped: a CLAUDE.md rule for the layout — nothing cross-cutting; the code and the manual carry it.
- Stale, fixed separately: `CLAUDE.md` § Working on the app — said `koverVerify` "also runs the release unit tests" and is stricter than CI; `./build.sh ./gradlew -m koverVerify` schedules `compileReleaseKotlin` and `koverGenerateArtifactRelease` but no `testReleaseUnitTest`, so coverage comes from the debug tests only. Corrected in `docs: correct stale build docs`.
