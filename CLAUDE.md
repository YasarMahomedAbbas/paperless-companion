# CLAUDE.md

Paperless Scan: an Android app (Kotlin, Jetpack Compose) that uploads scans, photos and files to a self-hosted Paperless-ngx server. See `README.md` for the layout, build and release.

## Paperless API: always check the current docs
Every call to Paperless (`data/PaperlessApi.kt`) must follow the **latest** Paperless-ngx API docs. Don't write or change a call from memory or from older examples: check the docs first, and check the live schema of the server it actually runs against.

- **API docs:** https://docs.paperless-ngx.com/api/
- **Changelog** (breaking API changes, version bumps): https://docs.paperless-ngx.com/changelog/
- **Live schema of the server** (exactly what the running version accepts and returns; the address is the one the app signs in to):
  - OpenAPI YAML: `<server>/api/schema/`
  - Browsable: `<server>/api/schema/view/`

**Why:** both v0.1.x upload bugs came from assumptions that the docs or the schema would have caught:
- **Chunked uploads.** Paperless runs Django under granian (ASGI) and reads a multipart body without a `Content-Length` as empty, so it answered 400 "No file was submitted". Uploads must send their length.
- **API versions.** Paperless 3 defaults to API v10, where `/api/tasks/` is paginated (`{"count", "results": [...]}`) with lower-case statuses and `result_data` / `related_document_ids`. v9 returned a bare list with `result` / `related_document`. The app reads both. When a response shape changes, support both versions (the server may lag behind or run ahead) and add a test for each.

MockWebServer accepts things a real Paperless refuses. When an API change is involved, check the behaviour against the real server too, not only the unit tests.
