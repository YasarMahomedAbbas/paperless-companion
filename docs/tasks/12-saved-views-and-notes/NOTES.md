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
