# Notes — Saved views, notes and "more like this"

Issue: #12 — Saved views, notes and "more like this" (https://github.com/YasarMahomedAbbas/paperless-companion/issues/12)
Branch: `feat/12-saved-views-and-notes`

## Planning

- Approach:
  - Saved views come from `api/saved_views/` plus `api/ui_settings/`. `data/` turns a view's filter rules into exactly the query parameters the web UI sends, and the domain only knows "view N".
  - Home views are chips at the start of the Documents chip row. A Views sheet lists all views.
  - Notes are read and added on a Notes screen opened from the viewer's all-details panel.
  - More like this (`more_like_id`) opens the list in a "similar to" mode from the viewer's More menu.
  - Everything is built from the existing parts with `design-recipe`.
- Decided by the pipeline on the user's behalf (approach questions):
  - **Q1, design canvas first or not** (flags: ux, api, scope): **option 1** taken. Build with design-recipe from the existing parts and record the new choices in `docs/design/README.md` § Decisions; no canvas review. The other option was (2) park for a design-canvas review of the three new surfaces first.
  - **Q2, where saved views appear** (flags: ux, api, scope): **option 1** taken. Views flagged "show on dashboard" are chips at the start of the chip row. A Views chip opens a sheet with all views, sidebar ones first. The other options were (2) dashboard-style sections above the list with each view's first documents, and (3) a Views sheet only, ignoring both flags.
  - **Q3, how a view's filter is applied** (flags: ux, api, scope): **option 1** taken. The data layer translates the rules as the web UI's `queryParamsFromFilterRules` does (v3.3.4), so every view matches the web, and the user's search and filters narrow it. The other option was (2) map only the rules the app's filters already know and disable the other views with "Open in Paperless".
  - **Q4, where notes live** (flags: ux, api, scope): **option 1** taken. A Notes row in all details opens a Notes screen (newest first, pinned "Add a note" field). The other option was (2) notes inline at the foot of all details.
  - **Q5, how More like this shows** (flags: ux, api, scope): **option 1** taken. A More menu item opens the Documents list with a removable "Like: <title>" chip, reusing list, paging and selection; search ends it. The other option was (2) a separate read-only "Similar documents" screen.
- Decided in planning; the pipeline accepted them on the user's behalf (flags: ux, api, scope):
  - Notes are read and add only (no delete, no edit).
  - Choosing a view replaces (clears) the user's filters. The view's own order applies until the user picks a sort ("View's order" on the sort chip), and a picked sort is kept.
  - Views follow the server's order (`ui_settings` `dashboard_views_sort_order` / `sidebar_views_sort_order`), and the rest go by name.
  - Notes are hidden when `ui_settings.notes_enabled` is false. A failure to read it counts as enabled.
  - Both shapes are read: on v9 the view carries `show_on_dashboard` / `show_in_sidebar`, and on v10 they are in `ui_settings`.
- API:
  - Checked against the Paperless-ngx source at v3.3.4 (latest): `serialisers.py`, `views.py`, `docs/api.md` (version history), `src-ui` `filter-rule-type.ts`, `query-params.ts`, `ui-settings.ts` and `settings.service.ts`, and `search/_backend.py`.
  - **No live `/api/schema/` was read.** `~/.config/paperless-companion/verify.env` is absent, and docs.paperless-ngx.com answered 403. Self-verification must read the schema and check every call.
- Open risks:
  - The rule table is copied from v3.3.4's web UI. A rule type added later is dropped, so such a view shows more documents than the web UI. Older servers (2.x) may lack some rule types; that is harmless, because their views can't contain them.
  - The note endpoints answer some failures with HTTP 200 and `{"error": …}`; the client treats that as a failure. MockWebServer can't show what a real server sends on other edge cases.
  - On 2.x (Whoosh), `more_like_id` may include the source document itself in the results; v3 excludes it. Self-verification should check on the real server.
  - The note added during write verification stays on the self-uploaded document, because delete is out of scope.

## Implementation

- Shipped:
  - Notes and more like this, data and domain: `DocumentLibrary.notes` / `addNote` / `notesEnabled` (`data/PaperlessLibrary.kt:257-271`, 200 + `{"error"}` read as SERVER), `JsonObject.toNote()` (`data/PaperlessJson.kt:49`), `Document.noteCount`, `DocumentQuery.similarTo` → `more_like_id` with no `query`/`ordering` (`data/PaperlessLibrary.kt:59`), `NotesUseCase` (`domain/usecase/NotesUseCase.kt`).
  - Saved views, data and domain: `SavedView` / `SavedViews`, `DocumentQuery.view` / `viewOrder`, `SavedViewsUseCase`; the v3.3.4 rule table and `queryParamsFromFilterRules` port (`data/PaperlessViews.kt:23`, table at `:73`); `savedViews()` reading v9 flags on the view or v10 ids from `ui_settings`, both ordered by the `*_sort_order` arrays (`data/PaperlessLibrary.kt:105`); `documents()` merging a view's parameters with the user's tags, correspondent, type, text and sort, the view filter cached per server and id or fetched by id (`data/PaperlessLibrary.kt:59-100`).
  - Saved views in the list: view chips first in the chip row, a Views chip and the "Saved views" sheet (`ui/documents/ViewsSheet.kt:28`), `chooseView` / `clearView` and "View's order" (`ui/documents/DocumentsViewModel.kt:145`), an empty-view state with "Show all documents".
  - More like this: the viewer's More menu item (`ui/viewer/ViewerScreen.kt:264`), `showSimilar` / `clearSimilar` (`ui/documents/DocumentsViewModel.kt:161-180`), the removable "Like: <title>" chip, the sort chip disabled, a "Nothing like …" empty state; `MainActivity.kt:237` closes the viewer and starts it.
  - Notes: the Notes row in all details (`ui/viewer/ViewerScreen.kt:385`), `ViewerViewModel` `notesEnabled` / `noteCount` / `notesChanged`, `NotesViewModel` (`ui/notes/NotesViewModel.kt:36`) and `NotesScreen` (`ui/notes/NotesScreen.kt:62`), opened over the viewer from `MainActivity.kt:233`.
  - Component variants: `DetailRow(onClick, onClickLabel)`, `BackBar(subtitle)`, `AppTextField(multiLine)`; icons `Views`, `Similar`, `Note` in `AppIcons.kt`. The manual's § Decisions has the "#12" entries.
- Tests: full CI command (`testDebugUnitTest koverVerify lintDebug assembleDebug`) passed: 520 debug unit tests, 0 failed, 0 skipped; koverVerify, lintDebug (warnings only) and assembleDebug green. The Gradle run executed no `testReleaseUnitTest` task in this build.
- Outstanding: none of the checklist's tasks. The device-only checks (chip row scrolling with view chips, the Notes screen with the keyboard open, TalkBack on the Views sheet and notes) and every real-server check are for self-verification; no live `/api/schema/` has been read yet.
- Contradicted the plan:
  - The planning notes gave the simple title/text rules as 40 and 41. At v3.3.4 (`filter-rule-type.ts`) they are 48 (`title_search`) and 49 (`text`); 40/41 are `custom_fields__id__none` and `has_custom_fields`. The table follows the source, and the checklist's References and test plan were corrected.
  - The web UI leaves out any parameter whose value is null (`abstract-paperless-service.ts` `list`), so a null value on a rule without an `isnull` parameter is dropped rather than sent.
  - Small shape changes, written back into the checklist: `NotesViewModel.count` is `Int?` (null before the notes load); `clearSimilar(alsoFilters)` serves the "Show all documents" empty state; the Notes row reads "Open" when the server gave no count; an empty saved view has its own state ("Nothing in <view>").

## Review

- Fixed:
  - Notes came out in the server's order, but Paperless 3.3.4 answers `GET`/`POST api/documents/{id}/notes/` from the document serializer, whose notes follow the note model's order (`created`, oldest first); earlier versions sent newest first. A document with notes 1, 2, 3 showed the oldest at the top, against "newest first". `notesFrom` now sorts by `created` descending (then id), with a test that sends them oldest first (`PaperlessLibraryTest`).
  - A saved view with no `sort_field` and `sort_reverse` false was listed with `ordering=created` (oldest first). The web UI sends no ordering then, and Paperless's default document order is `-created`, so the app showed the view upside down. A null sort field now always gives `-created` (`PaperlessViewsTest`).
- Raised: none
- API:
  - `GET api/saved_views/`, `GET api/saved_views/{id}/`, `GET api/ui_settings/`: match the v3.3.4 source (serializer, `query-params.ts`, `filter-rule-type.ts`, `abstract-paperless-service.ts` for the null-ordering case). Live schema not read here (`verify.env` absent); self-verification must.
  - `GET api/documents/` with a view's parameters and with `more_like_id`: match the source; the rule translation was re-checked line by line against `queryParamsFromFilterRules` at v3.3.4.
  - `GET` / `POST api/documents/{id}/notes/`: bare list, 200 + `{"error"}` on failure, 403 without permission, as in `views.py` v3.3.4; order differs by version (fixed above).
- Layers: clean
