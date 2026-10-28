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

## Implementation

- Shipped:
  - `data/PaperlessHttp.kt:35`: the shared plumbing (token header, error mapping with 404 → `NOT_FOUND`, `json`, a streamed `download` at :76 through a `.part` file, `pages` at :108). `PaperlessApi` delegates to it with its signature unchanged; `PaperlessApiTest` passes unedited.
  - `domain/repository/Repositories.kt:37`: `DocumentLibrary` (list/search, one document, the four label lists, files, update, delete, trash, restore, bulk edit), with its models in `domain/model/Documents.kt` and use cases in `domain/usecase/DocumentsUseCase.kt`, `LoadLabelsUseCase.kt`, `EditDocumentsUseCase.kt`.
  - `data/PaperlessLibrary.kt`: the Paperless implementation; bulk delete posts `/api/documents/delete/` and falls back to `bulk_edit` `delete` on 404 (`bulkDelete`, :157). `data/PaperlessJson.kt`: document/tag/label parsing, `parseHighlights` (:74), `fileName` (:130).
  - `data/Thumbnails.kt:25`: `ServerTokenInterceptor` plus the thumbnail URL and cache key; `data/ThumbnailImages.kt`: the Coil 3.0.4 glue (`ThumbnailFetcherFactory` :24, `ThumbnailKeyer`, `thumbnailImageLoader` :42). `AppContainer.kt:94`: `PaperlessApp` is Coil's `SingletonImageLoader.Factory`.
  - Coil 3.0.4 (`coil-compose`, `coil-network-okhttp`) added; it keeps OkHttp at 4.12.0. Kover excludes the Coil glue only.
- Tests: full CI command (`testDebugUnitTest koverVerify lintDebug assembleDebug`) green: 274 passed / 0 failed, line coverage 96.3%, `ArchitectureTest` green. New: `PaperlessHttpTest` 10, `PaperlessLibraryTest` 35, `PaperlessJsonTest` 21, `ThumbnailsTest` 8, `DocumentUseCasesTest` 28.
- Outstanding: the device-only check (thumbnails show and survive a restart from the disk cache) waits for #9. Every call still needs trying against a real server and its live `/api/schema/` (self-verification).
- Contradicted the plan:
  - `pages` does not follow `next` as an absolute URL: it takes only `next`'s query and applies it to the same path at the signed-in address, so a proxy-built `next` with an inner host or scheme never receives the token. It also stops after 1000 pages. Checklist 1.2 edited to match.
  - The file name is only known once the server answers, so `file` downloads to `<folder>/.download` and renames it; path separators in a name become `_` (not dropped), and leading dots are dropped too. Checklist 3.2 edited.
  - `ThumbnailFetcher.Factory` became `ThumbnailFetcherFactory` (the fetcher it returns is Coil's own network fetcher, so there is no `ThumbnailFetcher` class); the kover pattern still matches. Checklist 3.3 edited.
  - Coil 3.0.4 checked: `Options.copy(diskCacheKey = …)` compiles and `NetworkFetcher` reads `options.diskCacheKey`; its default cache strategy does not parse the response's `Cache-Control`, so the thumb endpoint's `no-cache` doesn't force a refetch. Confirm on a device with #9.
  - `restore` with an empty id list makes no call, like `bulkEdit`.

## Review

- Fixed: bulk delete on Paperless 2 never fell back (`data/PaperlessLibrary.kt` `bulkDelete`). 2.x has no `/api/documents/delete/`, and its DRF router matches the path as document-detail with pk `delete`, which has no POST, so an authed user gets **405**, not 404 (checked in the v2.20.15 `urls.py`: the explicit `^delete/` route exists only in v3.3.4). It now falls back on 405 or 404, through a new `PaperlessHttp.jsonUnless` that keeps the status code inside `data/`. New test: 405 → `bulk_edit` `delete`. Commit e52d1a1.
- Raised: #21 (a 403 on a document call reads "Wrong username or password / sign in again"; needs a permission-refused kind before the edit screens), #22 (flaky `PaperlessApiTest` timeout-message assertion, seen once in `testReleaseUnitTest`).
- API: checked against the server source at v3.3.4 and v2.20.15 (no live schema: `verify.env` absent, so self-verification must still read `/api/schema/`). `GET /api/documents/` (page, page_size, fields, ordering, query, tags__id__all, correspondent__id, document_type__id; v9 `all` ignored), `GET|PATCH|DELETE /api/documents/{id}/`, `/thumb/`, `/preview/`, `/download/[?original=true]`, the four label lists (paged via `next`, host pinned), `GET|POST /api/trash/`, `POST /api/documents/bulk_edit/` all match. `POST /api/documents/delete/` is v10-only; the 2.x answer was 405, not 404 (fixed above).
- Layers: clean. No Paperless names, paths or status codes in `domain/`; `NOT_FOUND` is in the app's own words.
- CI: full command green after the fix (275 tests, koverVerify, lint, assemble).
