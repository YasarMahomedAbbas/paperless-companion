# Notes — Documents list can skip rows after a change moves documents out of the filter or sort order

Issue: #32 — Documents list can skip rows after a change moves documents out of the filter or sort order (https://github.com/YasarMahomedAbbas/paperless-companion/issues/32)
Branch: `fix/32-list-skips-moved-rows`

## Planning

- Approach: when Edit details or a bulk change touches the list's active filter or sort field, or happens during a search, `DocumentsViewModel` re-fetches the window it has already shown (the first `consumed` positions, in pages of 100) and replaces its rows, keeping the scroll. Other changes stay in-place patches.
- Decided by the pipeline on the user's behalf (approach question):
  - **Q1, how the list catches up after a change that moves documents** (flags: ux): **option 1** taken: re-fetch the loaded window. Options were: (1) as taken; (2) count rows out locally the way trash does, falling back to a reload for sort and search changes; (3) reload from the top after any such change, losing the scroll.
- Decided in planning (stand as written):
  - A change that touches nothing the list filters or sorts by stays a plain in-place patch, with no request.
  - A failed re-fetch keeps the patched rows and shows the existing "couldn't load more" footer. Its Retry re-runs the re-fetch before paging on.
  - The re-fetch shows the existing refreshing indicator, which also blocks load more while it runs. A refresh or a new search cancels it.
  - No new Paperless call: the same document-list query with `page_size=100`. The server allows up to 100000 (`StandardPagination`, paperless-ngx main), and the app keeps its 100 cap.
- API: checked in the paperless-ngx source on GitHub (main). docs.paperless-ngx.com answered 403 (Cloudflare) and `verify.env` is absent, so the live `/api/schema/` wasn't read; that is left to self-verification.
- Open risks:
  - Paperless updates its search index for a bulk edit in a background task, so a re-fetch straight after a bulk change during a search may still see the old ranking. Paging stays consistent with whatever the server answers, so nothing is skipped relative to that answer.
  - A long session with many loaded rows re-fetches several pages of 100 after each such change.

## Implementation

- Shipped:
  - `DocumentsViewModel.moves` (`ui/documents/DocumentsViewModel.kt:215`): a change moves a document when a search is on (any difference but the search hit), when it changes the filtered tags, correspondent or type, or when it changes the field the list is sorted by (`ADDED` never, `MODIFIED` always).
  - `DocumentsViewModel.refetchWindow` (`DocumentsViewModel.kt:241`): asks for the first `consumed` positions again in pages of 100, replaces `loaded` and the rows, and resets `consumed`, `hasMore` and `total` from the answer. It shows the refreshing indicator; a failure keeps the patched rows and sets `loadMoreFailed`. It is skipped while a pull to refresh is already bringing page 1.
  - `patch` and `apply` (`DocumentsViewModel.kt:192`, `:205`) call it when `moves` says so; `loadMore` re-runs it first while the window is `stale` (`:85`), so Retry catches up before paging on; `loadFirstPage` clears `stale` (`:334`).
  - Eight tests in `DocumentsViewModelTest` (from `:800`) with a document-holding `serving(MutableMap<Int, Document>)` fake that filters by tags and search text and sorts by title or date.
- Tests: `DocumentsViewModelTest` 61 passed / 0 failed; seven of the eight new ones failed before the change (the no-request one passes either way, as it should). Full check (`testDebugUnitTest koverVerify lintDebug assembleDebug`) green: 457 passed / 0 failed / 0 skipped, Kover bound held.
- Outstanding: the device-only scroll feel check and the real-server `page_size=100` read are left to self-verification.
- Contradicted the plan: none. One guard added beyond 1.2: a change during a pull to refresh doesn't start a re-fetch (it would cancel the refresh and its label reload); the checklist text says so.

## Review

- Fixed: a re-fetch in flight could land an answer the server gave before a change made since, undoing it. A trash while it ran brought the trashed row back. An Undo while it ran dropped the restored document, and load more then skipped it. An edit that moves nothing reverted to the old values. `patch`, `apply`, `remove` and `putBack` now start the re-fetch again while one is on its way, and a re-fetch with nothing left shown cancels the one in flight (`DocumentsViewModel.kt`, `refetching`). Three tests in `DocumentsViewModelTest` (`… while the re-fetch runs isn't undone by its answer`) fail without it. Full check green: 460 passed / 0 failed / 0 skipped, Kover bound held.
- Raised: none.
- API: `GET /api/documents/` (no change but `page_size=100`). Matches the paperless-ngx source (`StandardPagination`, max 100000) as planning found. docs.paperless-ngx.com answered 403 at planning, and `verify.env` is absent, so the live schema wasn't read. That is left to self-verification.
- Layers: clean (a `ui/` change only, in domain terms).

## Doc drift

- No drift found. The branch changes only `ui/documents/DocumentsViewModel.kt` and its test; README's feature list, architecture block and use-case table (`BrowseDocumentsUseCase`: one page of `GET /api/documents/`) and `docs/design/layouts.md` (infinite scroll, pull to refresh, loading-more footer) still match the code.
- Skipped: the re-fetch of the shown window after a change that moves documents (`refetchWindow`, `page_size=100`). It's ViewModel-internal, explained in its KDoc and pinned by `DocumentsViewModelTest`; it reuses the existing refreshing indicator and load-more footer, so no screen state or rule changes for a future session to get wrong.
- Stale, fixed separately: none
