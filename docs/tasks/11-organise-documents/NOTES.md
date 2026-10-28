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
