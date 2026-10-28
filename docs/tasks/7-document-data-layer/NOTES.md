# Notes — Document data layer: read, search and edit documents through the API

Issue: #7 — Document data layer: read, search and edit documents through the API (https://github.com/YasarMahomedAbbas/paperless-companion/issues/7)
Branch: `feat/7-document-data-layer`

## Planning

- Approach: a new domain interface `DocumentLibrary`, implemented by `data/PaperlessLibrary` on an internal `PaperlessHttp` extracted from `PaperlessApi`. Use cases load the signed-in config. Thumbnails go through Coil 3: a data-side fetcher keyed on document id and modified time, with a token interceptor limited to the signed-in server.
- Decided (the pipeline took these on the user's behalf):
  - Q1 → option 1: a separate `DocumentLibrary` interface and `PaperlessLibrary` class sharing the extracted `PaperlessHttp` helper, instead of growing `DocumentServer` / `PaperlessApi`. Flag: none.
  - Q2 → option 1: Coil 3 (3.0.4, `coil-compose` + `coil-network-okhttp`) with a custom fetcher for a domain `DocumentThumbnail`, and a disk-cache key that includes the modified time. The token is attached by an interceptor only for the signed-in server's scheme, host and port. Flags: security (the host-limited token interceptor) and scope (the new Coil 3 dependency).
  - Q3 → option 1: bulk delete posts the v10 `/api/documents/delete/` and falls back to legacy `bulk_edit` `delete` on 404 (Paperless 2.x), with a test for each. Flag: api.
- Decided (defaults, accepted):
  - `ServerConfig` is passed per call, as today. Use cases throw `ServerException(UNAUTHORIZED, "Not signed in")` when signed out. A new `Kind.NOT_FOUND` exists for 404, and its message is unchanged.
  - No `Accept` version pin: the server's default is used, and both v9 and v10 shapes are parsed (v9 lists carry `all`, v10 lists don't).
  - One `DocumentQuery`: text (relevance order when set), tags must all match (`tags__id__all`), correspondent, document type, sort and direction, page, page size. It returns a page with total and hasMore. Lists leave out `content` with `fields=`.
  - Search highlights become plain text plus match ranges in the domain.
  - Preview and download stream into a cache file `data/` names (filename from `Content-Disposition`, sanitised). The kinds are preview, archived and original.
  - PATCH takes a `DocumentChanges` that keeps, sets or clears each field.
  - Bulk edit covers modify tags, set correspondent, set document type, set storage path and delete. Permissions, custom fields and reprocess are out.
  - Label lists follow `next` with `page_size=100`, with no app-side cache (the screens decide).
- Open risks:
  - No live schema was available: the docs site and public demo were behind a Cloudflare challenge, and `verify.env` is absent. Every call was checked against the docs source and the server source at v3.3.4 and v2.20.15 instead. Self-verification must read the live `/api/schema/` and try each call against a real server.
  - The Coil 3.0.4 disk-cache key override (`options.copy(diskCacheKey=…)` via a delegating fetcher) is planned from the library's design and must be confirmed against its API while implementing.
  - Thumbnails on a device can only be seen once #9 adds a screen.
