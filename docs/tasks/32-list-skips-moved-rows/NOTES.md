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
