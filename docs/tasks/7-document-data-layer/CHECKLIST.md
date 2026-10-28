# Checklist — Document data layer: read, search and edit documents through the API

Issue: #7 — Document data layer: read, search and edit documents through the API (https://github.com/YasarMahomedAbbas/paperless-companion/issues/7)
Branch: `feat/7-document-data-layer`
Problem: the app can only upload. Nothing in it can list, search, show, edit, delete or restore a document on the server, so the document screens (#9, #10, #11) have nothing to build on.

## Done when

- Use cases exist for every call below, tested against MockWebServer, and each call has been tried against the real server:
  - read: the document list (paged, sorted, filtered by tag / correspondent / document type), full-text search (`query=`), one document, its thumbnail, preview and download, and the lists of tags, correspondents, document types and storage paths;
  - write: update a document's details (PATCH), delete (to trash), restore from trash, bulk edit.
- Thumbnails load through an image loader that sends the token (only to the signed-in server) and are cached.
- Every response shape that differs between API v9 (Paperless 2.x) and v10 (3.x) is handled, with a test for each.
- CI passes (`testDebugUnitTest koverVerify lintDebug assembleDebug`), coverage stays at 80% or more, `ArchitectureTest` green.

## References

Checked against the docs source (`docs/api.md`) and the server source (`src/documents/views.py`, `serialisers.py`, `filters.py`, `src/paperless/urls.py`, `src/paperless/views.py`) at **v3.3.4** (latest, default API v10, supports 9 and 10) and **v2.20.15** (API v9 only). The docs site and the public demo were behind a Cloudflare challenge and `verify.env` is absent, so **no live `/api/schema/` was read**: self-verification must check every call below against a live server.

- `GET /api/documents/`: paginated `{"count","next","previous","results":[…]}`. **v9 also sends `"all": [ids]`, v10 drops it** (`StandardPagination._should_include_all`). Params: `page`, `page_size` (default 25), `ordering` (`created`, `added`, `modified`, `title`, `correspondent__name`, `document_type__name`, `archive_serial_number`, with a `-` prefix for descending), `tags__id__all=1,2`, `correspondent__id=3`, `document_type__id=4`, `fields=` (sparse fieldset, same on 2.x and 3.x). Full text: `query=<text>` → each result gains `__search_hit__ {score, highlights, rank}`, where `highlights` is HTML with `<span class="match">…</span>` (added after `fields` filtering on both versions).
- Document fields used (same on v9 and v10): `id`, `title`, `content` (single document only), `tags` [ids], `correspondent`, `document_type`, `storage_path` (id or null), `created` (a date since v9), `added`, `modified` (ISO datetimes), `archive_serial_number`, `original_file_name`, `archived_file_name`, `page_count`, `mime_type`, `deleted_at` (trash).
- `GET /api/documents/{id}/`; `PATCH /api/documents/{id}/` (JSON, partial; returns the document); `DELETE /api/documents/{id}/` (soft delete into the trash, 204, on both).
- `GET /api/documents/{id}/thumb/` (`image/webp`, `Cache-Control: no-cache` + ETag), `/preview/` (inline, archived PDF if one exists), `/download/` (attachment; `?original=true` for the original). Filename from `Content-Disposition`.
- `GET /api/tags/`, `/api/correspondents/`, `/api/document_types/`, `/api/storage_paths/`: paginated like documents (v9 `all` too). Fields: `id`, `name`, `document_count`; tags also `color`, `text_color`, `is_inbox_tag`.
- `GET /api/trash/` (paginated document list); `POST /api/trash/` `{"documents":[ids],"action":"restore"}` → `{"result":"OK","doc_ids":[…]}`. Same on both.
- `POST /api/documents/bulk_edit/` `{"documents":[ids],"method":…,"parameters":{…}}`: `modify_tags` (`add_tags`, `remove_tags`, both required), `set_correspondent` (`correspondent`, null clears), `set_document_type` (`document_type`), `set_storage_path` (`storage_path`). The `delete` method is **legacy on v10** (moved to `POST /api/documents/delete/` `{"documents":[ids]}`, which **does not exist on 2.x**).
- Unknown document → 404 on both.
- Coil 3.0.4 (`io.coil-kt.coil3:coil-compose`, `coil-network-okhttp`; built on Kotlin 2.0.21 and OkHttp 4.12, so it matches this build).
- Blocks #9, #10, #11. Design (#6) is separate; nothing here is visible.

## Context

`DocumentServer` / `PaperlessApi` cover sign-in, upload and the consume task. All HTTP plumbing (token header, error mapping, URL building, network-error wording) is private to `PaperlessApi`. This ticket moves that plumbing into an internal `PaperlessHttp` helper. A new domain interface `DocumentLibrary` holds everything about documents already on the server, implemented by a new `data/PaperlessLibrary.kt` on the same helper and mirroring `PaperlessApi` + `PaperlessApiTest`. Use cases mirror `DocumentLinkUseCase`: they load the `ServerConfig` from `SettingsRepository` and throw `ServerException(UNAUTHORIZED, "Not signed in")` when there is none. Every other failure is a `ServerException` too. A new kind, `NOT_FOUND`, is added for a gone document; its message reads as before. Thumbnails go through Coil 3. The domain gives the UI a `DocumentThumbnail(documentId, version)`. A data-side Coil fetcher turns it into the authed thumb URL, with a disk-cache key that includes the document's `modified` time, so an edit or reprocess refreshes it without revalidating every time. A token interceptor sends the token only to the signed-in server's scheme, host and port. Bulk delete posts the v10 delete endpoint and falls back to legacy `bulk_edit` `delete` on 404 (2.x). The app sends no API version header (the server's default is used), and both shapes are parsed. Lists leave out `content` with `fields=`. Labels page through `next` with `page_size=100`, with no app-side cache (the screens decide). Downloads stream into a cache folder `data/` owns, so a large PDF never sits in memory.

## Test plan

- Unit: the use cases (signed in → passes config and arguments, signed out → `UNAUTHORIZED`, server error passes through); `LoadLabelsUseCase` loads all four lists; `DocumentChanges` rejects an empty change set; highlight parsing (`<span class="match">` → plain text + ranges, entities decoded, nested or unclosed tags tolerated); thumbnail URL and cache key; token interceptor (same server → header, other host / port / scheme → no header); `Content-Disposition` filename parsing (`filename*=utf-8''…`, quoted, missing → `document-<id>`, path separators stripped).
- MockWebServer (`PaperlessLibraryTest`): document list answered in the **v9 shape (with `all`)** and the **v10 shape (without)**; query params for each sort, each filter, paging and `fields`; search with `__search_hit__`; empty page; one document (full fields, nulls); 404 → `NOT_FOUND`; 401 → `UNAUTHORIZED`; labels across two pages (`next` followed) in both shapes; thumb/preview/download streamed to a file with the right path, query and name; PATCH body for set / clear / keep fields; DELETE; trash list and restore body; each bulk edit body; bulk delete via `/documents/delete/`, and the fallback to `bulk_edit` `delete` when that answers 404; a 400 validation error → `SERVER` with the server's text.
- Real server (self-verification; read-only unless `PAPERLESS_ALLOW_WRITES=1`): read `/api/schema/` and check each path, param and field above; list documents (first page, one sort, one tag filter); full-text search; one document; thumb, preview and download of one document (status, content type, a non-empty file); the four label lists; trash list; read `X-Api-Version`. With writes allowed, only on a document it uploaded itself: PATCH its title and back, bulk add and remove a tag, delete it, see it in the trash, restore it, then delete it again.
- Device only: thumbnails show and survive a restart from the disk cache. This waits for #9, which has the first screen that shows them.

---

## Checkpoint 1 — Shared HTTP plumbing · commit: `refactor(api): share the Paperless HTTP plumbing`

Goal: `PaperlessApi` runs on an internal `PaperlessHttp` that the new library can reuse; a 404 is its own error kind; nothing visible changes and every existing test passes unchanged.

### [x] 1.1 — `ServerException.Kind.NOT_FOUND`

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/domain/model/Models.kt`
Add `NOT_FOUND` to `Kind` (KDoc: "the server has no such item, or it is gone"). In `domain/usecase/UploadDocumentUseCase.kt` `userMessage()`, add `Kind.NOT_FOUND -> message ?: "Paperless couldn't find it"`, so an upload 404 still reads "Paperless returned HTTP 404: …" as today.

### [x] 1.2 — `PaperlessHttp`

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/data/PaperlessHttp.kt` (new)
`internal class PaperlessHttp(val client: OkHttpClient, val io: CoroutineDispatcher)`. It takes over `PaperlessApi`'s private `url(baseUrl, path)`, `authed(config, path|url)`, `call(request, client, badRequestIsAuth)`, `networkError`, `defaultClient()` and `JsonObject.string`, unchanged except that `call` maps **404 → `NOT_FOUND`** with the same "Paperless returned HTTP 404: …" message. Add:
- `suspend fun json(config, method, path, query: Map<String,String> = emptyMap(), body: JsonElement? = null): JsonElement` (JSON body as `application/json`).
- `suspend fun download(request: Request, into: File): Pair<String?, String?>`, which streams the body to `into` (write to `into.part`, rename on success, delete on any failure) and returns `Content-Disposition` and `Content-Type`. Same error mapping as `call`.
- `suspend fun pages(config, path, query): List<JsonObject>`, which reads `results` and follows `next` until null. Only `next`'s query is used, applied to `path` at the signed-in address (behind a proxy Paperless can build `next` with an inner host or scheme, and the token must never go there). It accepts a bare array as one page, and stops with `SERVER` after 1000 pages.
- `fun JsonObject.int(key)`, `fun JsonObject.intList(key)`.

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/data/PaperlessApi.kt`
Delegate to `PaperlessHttp`; constructor signature and `defaultClient()` (kept as a forwarding companion function) unchanged, so `AppContainer` and `PaperlessApiTest` don't change.

### [x] 1.3 — Tests

File: `app/src/test/java/io/github/yasarmahomedabbas/paperlesscompanion/data/PaperlessHttpTest.kt` (new)
404 → `NOT_FOUND`; `download` writes the body, returns the headers, leaves no `.part` on success and no file on a 500 or a dropped connection; `pages` follows `next` over two pages and accepts a bare array; `json` sends the token and an `application/json` body.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*PaperlessApiTest' --tests '*PaperlessHttpTest' --tests '*UploadDocumentUseCaseTest' --tests '*ArchitectureTest'`
- `PaperlessApiTest` passes without edits.

---

## Checkpoint 2 — Browse, search and labels · commit: `feat(documents): browse, search and read documents`

Goal: a use case can page through documents (sorted, filtered or searched), load one document and load the four label lists, against v9 and v10 answers.

### [x] 2.1 — Domain models

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/domain/model/Documents.kt` (new; `java.time` only)
- `data class Document(val id: Int, val title: String, val created: LocalDate?, val added: Instant?, val modified: Instant?, val correspondentId: Int?, val documentTypeId: Int?, val storagePathId: Int?, val tagIds: List<Int>, val archiveSerialNumber: Int?, val originalFileName: String?, val pageCount: Int?, val mimeType: String?, val content: String? = null, val searchHit: SearchHit? = null)`. `content` is null in lists. `val thumbnail get() = DocumentThumbnail(id, modified)`.
- `data class SearchHit(val score: Double?, val excerpt: String, val matches: List<IntRange>)`: `excerpt` is plain text, and `matches` are the highlighted ranges in it.
- `data class DocumentPage(val total: Int, val documents: List<Document>, val hasMore: Boolean)`.
- `enum class DocumentSort { CREATED, ADDED, MODIFIED, TITLE, CORRESPONDENT, DOCUMENT_TYPE, ARCHIVE_SERIAL_NUMBER }`.
- `data class DocumentQuery(val text: String? = null, val tagIds: Set<Int> = emptySet(), val correspondentId: Int? = null, val documentTypeId: Int? = null, val sort: DocumentSort = DocumentSort.CREATED, val descending: Boolean = true, val page: Int = 1, val pageSize: Int = 25)`. KDoc: a blank `text` counts as none. With `text`, results come in relevance order and `sort` is ignored. Tags must all match. Require `page >= 1`, `pageSize in 1..100`.
- `data class Tag(val id: Int, val name: String, val color: String?, val textColor: String?, val isInbox: Boolean, val documentCount: Int?)`.
- `data class Label(val id: Int, val name: String, val documentCount: Int?)`, used for correspondents, document types and storage paths.
- `data class Labels(val tags: List<Tag>, val correspondents: List<Label>, val documentTypes: List<Label>, val storagePaths: List<Label>)`.
- `data class DocumentThumbnail(val documentId: Int, val version: Instant?)` (KDoc: what the UI hands the image loader; `version` changes when the document does).

### [x] 2.2 — `DocumentLibrary` (read half)

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/domain/repository/Repositories.kt` (append)
`interface DocumentLibrary` (KDoc: documents already on the server; throws ServerException): `suspend fun documents(config: ServerConfig, query: DocumentQuery): DocumentPage`, `suspend fun document(config: ServerConfig, id: Int): Document`, `suspend fun tags(config): List<Tag>`, `suspend fun correspondents(config): List<Label>`, `suspend fun documentTypes(config): List<Label>`, `suspend fun storagePaths(config): List<Label>`.

### [x] 2.3 — `PaperlessLibrary` (read half) and parsing

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/data/PaperlessLibrary.kt` (new)
`class PaperlessLibrary(client: OkHttpClient = PaperlessApi.defaultClient(), io: CoroutineDispatcher = Dispatchers.IO, private val files: File) : DocumentLibrary`. `files` is the cache folder for checkpoint 3.
- `documents`: `GET api/documents/` with `page`, `page_size` and `fields=id,title,created,added,modified,correspondent,document_type,storage_path,tags,archive_serial_number,original_file_name,page_count,mime_type`. Text set (trimmed, non-blank) → `query=<text>` and no `ordering`. Otherwise `ordering=[-]<created|added|modified|title|correspondent__name|document_type__name|archive_serial_number>`. Non-empty tags → `tags__id__all=<comma ids, sorted>`, plus `correspondent__id`, `document_type__id`. `total` = `count`, `hasMore` = `next != null`. The v9 `all` field is ignored.
- `document`: `GET api/documents/{id}/` (all fields, `content` included).
- Labels: `pages()` over `api/tags/` etc. with `page_size=100`, `ordering=name`.

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/data/PaperlessJson.kt` (new, internal)
`fun JsonObject.toDocument(): Document`: `created` via `LocalDate.parse` of the first 10 characters (a v9 date, or an older datetime), `added`/`modified` via `OffsetDateTime.parse(...).toInstant()`, and any unparseable date becomes null, never an exception. Also `toTag()` and `toLabel()`, and `fun searchHit(json): SearchHit?` with `parseHighlights(html): Pair<String, List<IntRange>>`. That parser strips every tag, records the text inside `<span class="match">` as ranges, decodes `&amp; &lt; &gt; &quot; &#39;`, and treats an unclosed span as running to the end.

### [x] 2.4 — Use cases

Files under `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/domain/usecase/`:
- `DocumentsUseCase.kt`, holding `internal fun SettingsRepository.signedIn(): ServerConfig = load() ?: throw ServerException(Kind.UNAUTHORIZED, "Not signed in")` and `class BrowseDocumentsUseCase(library, settings) { suspend operator fun invoke(query: DocumentQuery): DocumentPage }`, plus `class GetDocumentUseCase { suspend operator fun invoke(id: Int): Document }`.
- `LoadLabelsUseCase.kt`: `suspend operator fun invoke(): Labels`, with the four lists loaded concurrently (`coroutineScope` + `async`). The first failure cancels the others and is rethrown.

### [x] 2.5 — Wiring and fakes

File: `AppContainer.kt`: `private val library: DocumentLibrary = PaperlessLibrary(files = File(context.cacheDir, "documents"))`, with a comment that the screens (#9–#11) build their use cases from it.
File: `app/src/test/java/.../Fakes.kt`: `class FakeLibrary : DocumentLibrary` with scripted answers, a recorded call list and an optional `failure`.

### [x] 2.6 — Tests

- `data/PaperlessLibraryTest.kt` (new): the read rows of the test plan, with **a v9 page (with `all`) and a v10 page (without)** for documents and for labels, each sort and filter param, search hits, 404/401.
- `data/PaperlessJsonTest.kt` (new): `toDocument` with nulls and odd dates; highlight parsing cases.
- `domain/DocumentUseCasesTest.kt` (new): the browse, get and labels use cases, signed in and signed out, with errors passed through.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*PaperlessLibraryTest' --tests '*PaperlessJsonTest' --tests '*DocumentUseCasesTest' --tests '*ArchitectureTest'`

---

## Checkpoint 3 — Files and thumbnails · commit: `feat(documents): fetch document files and load thumbnails`

Goal: a document's preview, archived or original file lands in a cache file. Thumbnails load through Coil with the token, sent only to the signed-in server, and are cached by document version.

### [ ] 3.1 — Domain

File: `domain/model/Documents.kt`: `enum class DocumentFileKind { PREVIEW, ARCHIVED, ORIGINAL }` and `data class DocumentFile(val name: String, val mimeType: String?, val file: java.io.File)`.
File: `domain/repository/Repositories.kt`: `DocumentLibrary.file(config: ServerConfig, id: Int, kind: DocumentFileKind): DocumentFile` (KDoc: overwrites the previous copy of the same document and kind).
File: `domain/usecase/DocumentsUseCase.kt`: `class OpenDocumentFileUseCase { suspend operator fun invoke(id: Int, kind: DocumentFileKind): DocumentFile }`.

### [ ] 3.2 — `PaperlessLibrary.file`

`PREVIEW` → `api/documents/{id}/preview/`, `ARCHIVED` → `/download/`, `ORIGINAL` → `/download/?original=true`, streamed with `PaperlessHttp.download` into `files/<id>-<kind lower-case>/<name>`. The folder is emptied first. `name` comes from a new internal `fun fileName(contentDisposition: String?, fallback: String): String` in `PaperlessJson.kt`: `filename*=UTF-8''…` (percent-decoded) wins over `filename="…"`. Path separators, `..` and control characters are stripped. If nothing is left, the name is `document-<id>` plus an extension from the content type when it is PDF.

### [ ] 3.3 — Coil

Files: `gradle/libs.versions.toml` (`coil = "3.0.4"`, libraries `coil-compose`, `coil-network-okhttp`), `app/build.gradle.kts` (`implementation` both).
File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/data/Thumbnails.kt` (new):
- `internal fun thumbnailUrl(config: ServerConfig, thumb: DocumentThumbnail): String` = `<base>/api/documents/<id>/thumb/`.
- `internal fun thumbnailCacheKey(config, thumb): String` = `thumb:<base>:<id>:<version epoch millis or 0>`.
- `class ServerTokenInterceptor(private val settings: SettingsRepository) : Interceptor`: adds `Authorization: Token …` only when the request's scheme, host and port equal the signed-in server's. It never overwrites an existing header.
File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/data/ThumbnailImages.kt` (new; the Coil glue, which needs Android):
- `class ThumbnailFetcher.Factory(settings, network: Fetcher.Factory<Uri>)`: a Coil `Fetcher.Factory<DocumentThumbnail>`. Signed out → null (no fetch). Otherwise it delegates to the OkHttp network fetcher for `thumbnailUrl`, with `options.copy(diskCacheKey = key)`, because Coil's disk key defaults to the URL and would ignore the version. `class ThumbnailKeyer : Keyer<DocumentThumbnail>` returns the same key for the memory cache. Check both against the Coil 3.0.4 API while implementing.
- `fun thumbnailImageLoader(context: Context, settings: SettingsRepository, client: OkHttpClient): ImageLoader`: the network fetcher on `client.newBuilder().addInterceptor(ServerTokenInterceptor(settings))`, the thumbnail fetcher and keyer, a memory cache, and a disk cache at `cacheDir/thumbnails` (64 MB).
- This file goes on the kover exclude list. The pure functions and the interceptor stay in `Thumbnails.kt` and stay measured.

File: `AppContainer.kt`: `val imageLoader by lazy { thumbnailImageLoader(context, settings, PaperlessApi.defaultClient()) }`. `PaperlessApp` implements `coil3.SingletonImageLoader.Factory` and returns `container.imageLoader`, so a screen only writes `AsyncImage(model = document.thumbnail, …)`.
File: `app/build.gradle.kts` kover excludes: `"*.data.ThumbnailImages*"`, `"*.data.ThumbnailFetcher*"`, `"*.data.ThumbnailKeyer*"`.

### [ ] 3.4 — Tests

- `PaperlessLibraryTest`: each kind's path and query, the file's content and name, and the folder replaced on a second fetch; 404 → `NOT_FOUND` and no file left behind.
- `data/ThumbnailsTest.kt` (new): URL and key (version change → new key, signed-in server change → new key). Interceptor with MockWebServer: the same server gets the header, another port gets none, and nobody signed in means no header.
- `PaperlessJsonTest`: the `fileName` cases.
- `DocumentUseCasesTest`: `OpenDocumentFileUseCase`.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*PaperlessLibraryTest' --tests '*ThumbnailsTest' --tests '*PaperlessJsonTest' --tests '*DocumentUseCasesTest' --tests '*ArchitectureTest' assembleDebug` (assembleDebug proves the Coil wiring compiles).

---

## Checkpoint 4 — Edit, delete, trash and bulk edit · commit: `feat(documents): edit, delete, restore and bulk edit documents`

Goal: a use case can change a document's details, move it to the trash, list and restore the trash, and bulk edit tags, correspondent, type, storage path or delete, with bulk delete working on 2.x and 3.x.

### [ ] 4.1 — Domain

File: `domain/model/Documents.kt`:
- `sealed interface Change<out T> { data object Keep : Change<Nothing>; data class To<T>(val value: T) : Change<T> }`.
- `data class DocumentChanges(val title: Change<String> = Keep, val created: Change<LocalDate> = Keep, val correspondentId: Change<Int?> = Keep, val documentTypeId: Change<Int?> = Keep, val storagePathId: Change<Int?> = Keep, val tagIds: Change<List<Int>> = Keep, val archiveSerialNumber: Change<Int?> = Keep)`, with `val isEmpty`. A `To(null)` clears the field.
- `sealed interface BulkEdit { data class ModifyTags(val add: Set<Int>, val remove: Set<Int>); data class SetCorrespondent(val id: Int?); data class SetDocumentType(val id: Int?); data class SetStoragePath(val id: Int?); data object Delete }`.

File: `domain/repository/Repositories.kt`, add to `DocumentLibrary`: `update(config, id, changes): Document`, `delete(config, id)`, `trash(config, page: Int, pageSize: Int): DocumentPage`, `restore(config, ids: List<Int>)`, `bulkEdit(config, ids: List<Int>, edit: BulkEdit)`.

### [ ] 4.2 — `PaperlessLibrary` (write half)

- `update`: `PATCH api/documents/{id}/` with only the changed keys: `title`, `created` (ISO date), `correspondent`, `document_type`, `storage_path`, `tags`, `archive_serial_number`, where `To(null)` → JSON null. The answer is parsed with `toDocument()`.
- `delete`: `DELETE api/documents/{id}/`.
- `trash`: `GET api/trash/?page=&page_size=`, parsed like documents (v9 `all` ignored).
- `restore`: `POST api/trash/` `{"documents":[…],"action":"restore"}`.
- `bulkEdit`: `POST api/documents/bulk_edit/` with `modify_tags {add_tags, remove_tags}` (both always sent), or `set_correspondent {correspondent}`, `set_document_type {document_type}`, `set_storage_path {storage_path}` (null clears). For `Delete`: `POST api/documents/delete/` `{"documents":[…]}`, and on `NOT_FOUND` (2.x has no such endpoint) the same ids via `bulk_edit` `{"method":"delete","parameters":{}}`. Comment why. An empty id list → returns without a call.

### [ ] 4.3 — Use cases

File: `domain/usecase/EditDocumentsUseCase.kt` (new): `UpdateDocumentUseCase(id, changes): Document` (an empty `changes` → `IllegalArgumentException`, no call), `DeleteDocumentUseCase(id)`, `TrashUseCase { suspend fun list(page = 1, pageSize = 25): DocumentPage; suspend fun restore(ids: List<Int>) }`, `BulkEditUseCase(ids, edit)`.

### [ ] 4.4 — Tests

- `PaperlessLibraryTest`: PATCH bodies (keep, set, clear, date format), the answer parsed, DELETE path and method, trash list in v9 and v10 shapes, restore body, each bulk edit body, delete via `/documents/delete/`, fallback to `bulk_edit` on 404 (two requests, in order), a 500 on the new endpoint → `SERVER` with **no** fallback, 400 → `SERVER` with the server's text.
- `DocumentUseCasesTest`: each write use case signed in and signed out; the empty-changes refusal.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest koverVerify lintDebug assembleDebug` (the full CI command; coverage at or above 80%).

---

## Out of scope

- Any screen, ViewModel or navigation (#9, #10, #11; design #6). Nothing visible changes.
- Notes, custom fields, permissions and owners, saved views, share links, document versions, `more_like_id`, autocomplete and the global search endpoint.
- The v10 document-editing endpoints (`merge`, `rotate`, `edit_pdf`, `reprocess`, `remove_password`) and bulk `set_permissions` / `modify_custom_fields` / `reprocess`.
- Emptying the trash, and creating or editing tags, correspondents, document types or storage paths.
- Pinning an API version with an `Accept` header, and caching label lists or documents in the app.
- Saving a downloaded file to shared storage or opening it in another app: that is the screens' job, from the cache file.
