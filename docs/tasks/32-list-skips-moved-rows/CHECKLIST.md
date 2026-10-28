# Checklist — Documents list can skip rows after a change moves documents out of the filter or sort order

Issue: #32 — Documents list can skip rows after a change moves documents out of the filter or sort order (https://github.com/YasarMahomedAbbas/paperless-companion/issues/32)
Branch: `fix/32-list-skips-moved-rows`
Problem: after Edit details or a bulk change moves documents out of the list's filter or to a new place in its sort order, the next "load more" starts at the wrong place on the server and skips documents until a pull to refresh.

## Done when

- Filtered by a tag with more than 25 matches, taking that tag off 3 loaded documents with the bulk Tags sheet and then scrolling to the end shows every document that still has the tag, each once.
- Edit details changing the field the list is sorted by (title, date, or correspondent when sorted by correspondent) and then scrolling on skips no document and shows none twice.
- A change that touches nothing the list filters or sorts by still patches the row in place, with no request.
- The scroll position is kept (rows stay keyed by id). Documents that no longer match leave the list, and moved ones appear in their new place.
- Each fix comes with a test that fails without it.

## References

- Paperless API: no new call. The existing `GET /api/documents/` list (`page`, `page_size`, `ordering`, `tags__id__all`, `correspondent__id`, `document_type__id`, `query`) is asked again with `page_size=100`. The server's `StandardPagination` allows `page_size` up to 100000 (paperless-ngx `src/paperless/views.py`, main). The app's own `DocumentQuery` cap is 100. The shape is the same on v9 and v10.
- #11 (decision Q3: patch in place, keep the scroll). #11's review added the `consumed` offset for trash and Undo.

## Context

`DocumentsViewModel` pages by hand. `consumed` counts the server positions it has shown (trash subtracts, Undo adds), and `loadMore` fetches from the page holding the next unseen one, dropping ids it already shows. `patch` (Edit details, called from `MainActivity`) and `apply` (bulk changes, from `DocumentsScreen`) only rewrite loaded rows, so a change that moves documents across the server's page boundary shifts the pages under `consumed`. The fix, taken by the pipeline (option 1, flag ux): when a change touches what the list is filtered or sorted by, or happens during a search, the ViewModel asks the server again for the window it has already shown (the first `consumed` positions, in pages of 100). It replaces `loaded` and the rows with the answer and resets `consumed` and `hasMore` from it. Other changes stay plain in-place patches. A failed re-fetch keeps the patched rows and shows the existing "couldn't load more" footer, and its Retry runs the re-fetch again before paging on. The tests mirror #11's `serving(...)` fake server in `DocumentsViewModelTest`.

## Test plan

- Unit (`DocumentsViewModelTest`, `serving(...)` fake server):
  - the issue's steps: 60 documents tagged 7, filtered by tag 7, with 3 loaded ones losing it through `apply(ModifyTags(remove = {7}))`. The server drops them, and load more shows all 57 once each (fails without the fix).
  - sorted by title: `patch` renames a loaded document so the server moves it past page 1, and load more skips none and repeats none (fails without the fix).
  - sorted by date: `patch` changes `created`, and the same holds.
  - a change touching neither filter nor sort (e.g. storage path, or a tag that isn't filtered on) makes no `documents()` call.
  - a change during a search re-fetches.
  - the re-fetch asks pages of 100 until it covers `consumed`, then load more continues from the right position (a window above 100).
  - a failed re-fetch keeps the patched rows and sets `loadMoreFailed`, and `loadMore` (Retry) re-runs the re-fetch, then pages on.
  - a refresh or a new search while the re-fetch runs cancels it, so the older answer never lands.
- MockWebServer: none; no `data/` change.
- Real server: self-verification reads `GET /api/documents/?page=1&page_size=100&tags__id__all=<id>&ordering=-created` and checks that `count`, `next` and `results` look the same as at `page_size=25` (read-only). It needs `verify.env`, which is absent today.
- Device only: that the scroll position holds while rows leave the list after a bulk change (a feel check).

---

## Checkpoint 1 — Re-fetch the loaded window after a change that moves rows · commit: `fix(documents): re-fetch the shown rows when a change moves documents in the list`

Goal: `patch` and `apply` re-fetch the shown window when the change touches the active filter or sort, or a search is active, so load more skips nothing.

### [ ] 1.1 — Decide whether a change moves the document

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/ui/documents/DocumentsViewModel.kt`
Add `private fun moves(before: Document, after: Document): Boolean`, reading `_state.value.filters`, `_state.value.sort` and `loadedText`:
- `loadedText != null` (searching; the search index covers title, tags, correspondent and type): `before.copy(searchHit = null) != after.copy(searchHit = null)`.
- `filters.tagIds` not empty and `before.tagIds.toSet().intersect(filters.tagIds) != after.tagIds.toSet().intersect(filters.tagIds)`.
- `filters.correspondentId != null` and the correspondent changed; the same for `documentTypeId`.
- The sort field changed: `DocumentSort.CREATED` → `created`, `TITLE` → `title`, `CORRESPONDENT` → `correspondentId`, `ADDED` → never (no edit changes it). Other `DocumentSort` values → the matching field if there is one (`DOCUMENT_TYPE` → `documentTypeId`, `ARCHIVE_SERIAL_NUMBER` → `archiveSerialNumber`, `MODIFIED` → always true), for safety, though `SortOption` doesn't offer them.

### [ ] 1.2 — The window re-fetch

Same file. Add `private const val WINDOW_PAGE_SIZE = 100` and `private var stale = false` (true while the shown window is out of step with the server) and `private fun refetchWindow()`:
- Return when `_state.value.labels` is null or `consumed == 0`. Otherwise set `stale = true`, cancel `loadJob`, and set the state to `refreshing = true, loadingMore = false, loadMoreFailed = null`, keeping the rows.
- In `loadJob = viewModelScope.launch`: fetch `query(page = p).copy(pageSize = WINDOW_PAGE_SIZE)` for p = 1, 2, … while the collected documents number fewer than `count` (= `consumed` at the start) and the last page `hasMore`. Take the first `count` documents, then `distinctBy { it.id }`.
- On success: `consumed` = the number of positions taken (before distinct), `hasMore = last.hasMore || collected.size > consumed`, `loaded` = the window, `stale = false`, rows from `loaded`, `total = last.total`, `refreshing = false`, and status `Empty` when the window is empty, else `Loaded`. Leave `removed` as it is: `putBack` already falls back to the end for an index past the list.
- On `CancellationException`, rethrow. On another failure: `refreshing = false`, `loadMoreFailed = e.failure().message`; `stale` stays true.

### [ ] 1.3 — Wire `patch`, `apply` and `loadMore`

Same file.
- `patch(document)`: keep the in-place patch, then `if (moves(old, document)) refetchWindow()`.
- `apply(ids, edit)`: work out the changed documents. If any `moves(it, it.with(edit))`, patch locally, then `refetchWindow()`; otherwise only patch as now.
- `loadMore()`: after the existing guards, `if (stale) { refetchWindow(); return }`. The guard on `!hasMore` must not block a stale window: check `stale` before `!hasMore`.
- `loadFirstPage` sets `stale = false` (a fresh page 1 is in step).
- Update the class KDoc and the `consumed` KDoc to say that edits re-fetch the window.

### [ ] 1.4 — Tests

File: `app/src/test/java/io/github/yasarmahomedabbas/paperlesscompanion/ui/DocumentsViewModelTest.kt`
Extend `serving(...)` (or add a sibling) so the fake server can serve `doc(...)` values with tags, title and created that the test changes, and filters by `q.tagIds`. Add the unit cases from the test plan, one `@Test` each. Write the issue-steps and title-sort cases first and watch them fail before 1.1–1.3.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*DocumentsViewModelTest*'`
- The issue-steps and sort tests fail on `origin/main`'s ViewModel and pass with the change.
- Full check: `./build.sh ./gradlew --console=plain testDebugUnitTest koverVerify lintDebug assembleDebug` green, Kover bound held.

---

## Out of scope

- Changing paging to cursors or reloading from the top after every change (#11 Q3 stands for changes that move nothing).
- Re-ordering or removing rows locally from the sort or filter rules (option 2, not taken).
- Changes made elsewhere (web UI, other devices) while the list is open; pull to refresh covers them.
- Any new Paperless call or `data/` change.
- The trash list and the filter preview count.
