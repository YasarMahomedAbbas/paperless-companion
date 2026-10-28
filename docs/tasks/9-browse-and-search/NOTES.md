# Notes — Browse and search documents

Issue: #9 — Browse and search documents (https://github.com/YasarMahomedAbbas/paperless-companion/issues/9)
Branch: `feat/9-browse-and-search`

## Planning

- Approach: a new `DocumentsViewModel` over #7's `BrowseDocumentsUseCase` and `LoadLabelsUseCase` drives a Documents home screen (list, search, filters, sort). Uploads move behind the Add button with their own `UploadsViewModel`, and today's menu moves into a new Settings screen. No new Paperless call and no change to `data/`.
- Decided by the pipeline on the user's behalf (approach questions):
  - **Q1, Settings scope** (flags: scope): **option 1** taken. Settings shows the server address, the fingerprint switch, Updates and Sign out. "Signed in as", the lock-again delay and Source and licences are deferred to follow-ups. Options were: (1) only what today's menu does plus the address, with no new API call or setting; (2) also the lock-again delay, pulling #8's timing (code says 1 min, design says 5, adjustable) into this ticket; (3) the full layout, including "signed in as", which needs a new current-user call.
  - **Q2, search trigger** (flags: ux): **option 1** taken. Search as you type, debounced 400 ms, and right away on the Search key; a newer request cancels the older one. Options were: (1) debounced as-you-type; (2) only on the Search key (fewest calls, results don't follow typing).
- Decided in planning (stand as written):
  - Paging is hand-rolled in the ViewModel from `DocumentPage.hasMore`, with no Paging library; the next page loads when the last five rows are visible.
  - Labels load with the first page; if either fails, the list shows load-failed. Pull to refresh reloads both.
  - The list lives in `DocumentsViewModel`, and uploads stay in their own ViewModel (today's `HomeViewModel`, renamed `UploadsViewModel`). An upload reaching Done refreshes the list from the top.
  - Navigation stays hand-rolled in `MainActivity` (Documents → Settings → Updates), with no navigation library.
  - The filter sheet's "Show N documents" count comes from a debounced one-row (`pageSize = 1`) query.
  - The sort sheet offers Newest first (the default), Oldest first, Recently added, Title A–Z and Correspondent A–Z. While searching it shows "Best match".
  - New shared components: document row (with a search-snippet variant), search field, chips, status card, skeleton, action button, tonal icon button, bottom sheet, option list, settings row.
- API: no new call; the calls used were checked in #7 against the server source (v3.3.4 / v2.20.15). `verify.env` is absent, so no live `/api/schema/` was read; self-verification must check list, search, filters, labels and thumb against a real server.
- Open risks:
  - No live server has ever answered these calls (from #7 as well). Search snippets and filter params are unproven against a running Paperless.
  - Sign out from Settings still hits #16 (it bounces back home) until that ticket lands.
  - A freshly uploaded document only appears at the top if its document date is the newest; under "Newest first" an old letter lands further down.

## Implementation

- Shipped:
  - `DocumentsViewModel` (`app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/ui/documents/DocumentsViewModel.kt:30`): hand-rolled paging (`loadMore` :55), one load job so a newer page-1 load cancels the older one (`loadFirstPage` :149), 400 ms debounced search, filters, sort, and the filter sheet's one-row count (`previewFilters` :123). Rows mapped by `Document.toRow` (`app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/ui/documents/DocumentsUiState.kt:36`); sort options in `SortOption.kt`.
  - Shared parts in `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/ui/components/`: document row with the snippet highlighter (`DocumentRow.kt:110`), search field, chips, icon and action buttons, status card (with an in-sheet variant), skeleton and list footers, bottom sheet and option rows, picker field, settings rows, dialog, back bar, and the canvas's icons in `AppIcons.kt`.
  - Documents is the home (`app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/ui/documents/DocumentsScreen.kt:106`): list states, pull to refresh, infinite scroll five rows ahead (:304), upload cards on top, the Add sheet (`AddSheet.kt`), searching mode, the chip row (:375), the sort sheet (`SortSheet.kt`) and the filters sheet with its correspondent picker (`FiltersSheet.kt:55`). `HomeScreen` is gone.
  - `HomeViewModel` became `UploadsViewModel` (`app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/ui/documents/UploadsViewModel.kt`), with `added` (:39, a buffered channel) refreshing the list when Paperless accepts an upload, and `clear()` (:68) for sign-out.
  - Settings (`app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/ui/settings/SettingsScreen.kt:43`, `SettingsViewModel.kt`): the address, the fingerprint switch, Updates with a "ready" pill, Sign out behind a confirm. `MainActivity` navigates Documents → Settings → Updates with a `Screen` enum (`app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/MainActivity.kt:57`), and a sign-out `session` counter (:146) keys the ViewModels so signing in again starts afresh; both survive a rotation (:175).
  - `docs/design/README.md` § Decisions: the new drawn icons, search rows dropping their tags, the failed-next-page banner.
- Tests: full CI command (`testDebugUnitTest koverVerify lintDebug assembleDebug`) green: 341 passed / 0 failed / 0 skipped; line coverage 90.4% (bound 80). New: `DocumentsViewModelTest` (41), `SortOptionTest` (2), `HighlightTest` (4), `SettingsViewModelTest` (5); `UploadsViewModelTest` (11, moved from `HomeViewModelTest`: the server-address and sign-out cases went with those features, `clear()` and three `added` cases came in).
- Outstanding: none unticked. Device-only checks from the test plan (thumbnails and their disk cache, pull-to-refresh and scroll feel, the Add sheet's scanner, camera and picker, a shared file landing as an upload card, search over the VPN) and every real-server call are for self-verification; `verify.env` was not read here.
- Contradicted the plan:
  - Copy: the canvas (which the manual puts above the checklist on anything visual) words several things differently, and the canvas won: "Title, A to Z" / "Correspondent, A to Z", "Couldn't load your documents", "Unlock with fingerprint", "Sign out?", "Clear all"; the chip row puts the removable filters before the sort chip. The checklist text was edited to match.
  - `added` is a buffered `Channel` flow, not a `SharedFlow`: a `SharedFlow` drops an upload accepted while Settings is open.
  - `SettingsViewModel` also takes the update store and the installed version code, so the "ready" pill is worked out (and tested) there instead of in the activity.
  - The ViewModel keys gained a sign-out counter: keyed on the address alone, signing out and back in to the same server would have shown the old session's list.
  - `SearchHit.highlighted` takes its match style as a parameter (`searchMatch` from the theme in the app) so it is testable without Compose.

## Review

- Fixed: none. Candidates checked and refuted: the debounce job cancelling itself inside `reload()` (it has no suspension left, and the load runs in its own job); sign-out composing fresh ViewModels against cleared settings (the gate goes to SignIn synchronously in the same handler, so `home` is null before recomposition); load-more racing a refresh or a page-1 reload (`loadJob` is shared, so they cancel each other, and the guards check `refreshing` and `Loaded`); `stopSearch` missing a reload while the debounce is pending (it checks `typingJob`); a failed load-more retriggering forever (the scroll trigger only fires on a change of `(nearEnd, rowCount)`, and the retry is the footer button).
- Raised: none.
- API: no endpoint touched; `data/` and `domain/` are unchanged on this branch. The list, search, filter, label and thumbnail calls go through #7's `DocumentLibrary` as they are; self-verification still has to try them against a real server.
- Layers: clean. No `ui/` import of `data/` and no Paperless field, path or query name in `ui/`; only `AppContainer` names `data/` classes.

## Doc drift

- Applied: README.md § intro features — new **Documents** bullet (browse, full-text search as you type, filters by tags/correspondent/type, sort, Add for uploads) (rung 3: using the app).
- Applied: README.md § features and Install — the fingerprint switch is "Unlock with fingerprint" in Settings, not "Lock with fingerprint" in the home screen's menu; Updates is **Settings → Updates**, not **⋮ → Updates**; "Scan document explains that" instead of "the Scan button" (rung 3; claims this branch made false).
- Applied: docs/design/README.md § Decisions (#9) — Documents replaced the upload-first home and the ⋮ menu (the #6 lines above it are now history), and Settings is partial: "signed in as", the lock-again delay (code: 1 minute in `AppLock`) and Source and licences from `layouts.md` § Settings are follow-ups (rung 4: a session building Settings would otherwise assume the manual's layout is shipped).
- Skipped: README architecture block — names only `data/` classes and layer roles; the new `ui/documents`, `ui/settings` and `ui/components` packages need no listing, `fd` finds them.
- Skipped: README use-case table — no use case added or changed (`domain/` untouched).
- Skipped: CLAUDE.md — no new cross-cutting rule; hand-rolled navigation and paging are visible in `MainActivity` and `DocumentsViewModel`.
- Skipped: `ArchitectureTest`'s `ui/home/HomeScreen.kt` strings — synthetic paths fed to the checker, not a doc claim.
- Stale, fixed separately: none.
