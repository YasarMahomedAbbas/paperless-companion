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
