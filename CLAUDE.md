# CLAUDE.md

Paperless Scan: an Android app (Kotlin, Jetpack Compose) that uploads scans, photos and files to a self-hosted Paperless-ngx server. See `README.md` for the layout, build and release.

## Working on the app
- **Build and test only in Docker:** `./build.sh ./gradlew --console=plain testDebugUnitTest koverVerify lintDebug assembleDebug` is what CI runs. Nothing is installed on the desktop.
- **Release:** commit to `main`, push, then `git tag vX.Y.Z && git push origin vX.Y.Z`. The Release workflow builds the signed APK and publishes it; wait for the run with `gh run watch` and check the APK is on the release. Phones update from GitHub Releases or Obtainium.
- **Signing key:** kept outside the repo and given to the Release workflow as the `RELEASE_KEYSTORE_B64` and `RELEASE_KEYSTORE_PASSWORD` secrets. Never print, move or regenerate it without asking: a new key forces every installed copy to be uninstalled before it can update.
- A bug fix comes with a test that fails without it.

## How tickets get built
Tickets are GitHub issues; a `Needs: #a, #b` line names what must be closed first. The skills in `.claude/skills/` carry them from issue to release with nobody watching:

- **`pipeline #<n>`** runs one ticket in its own worktree (`../worktrees/paperless-companion/issue-<n>`) and tmux session (`paperless-<n>`): `start-ticket` → `implement-plan` → `self-review` → `doc-drift` → `self-verification` → `raise-pr` → `merge-release`. The ticket folder `docs/tasks/<n>-<slug>/` (`CHECKLIST.md`, `NOTES.md`) is its stage log.
- **`autopilot`** starts a pipeline for every open ticket whose needs are closed (three at a time) and restarts ones a reboot stopped. A finished pipeline wakes it.
- **The user has authorised merging and releasing without them.** `merge-release` squash-merges once CI is green and verification passed, then tags the next version when app code changed. Reviews happen inside the pipeline; nothing waits for a person.
- **At a judgment call the pipeline parks** instead of guessing: a research note on the issue, the `parked` label, a push notification. Removing the label hands it back.
- **`design` tickets** end with a design canvas the user reviews (`design-canvas`); every screen is built with `design-recipe` and the manual in `docs/design/`.
- Real-server checks read `~/.config/paperless-companion/verify.env` (`PAPERLESS_URL`, `PAPERLESS_TOKEN`, optional `PAPERLESS_ALLOW_WRITES=1`). Without it, verification against the server is skipped and recorded as such.

## Layers: the server is behind one door
`ui/` → `domain/` ← `data/`, and `AppContainer` is the only place that names a `data/` class. The goal: swapping Paperless for another backend means rewriting `data/` and nothing else.

- **`domain/`** is plain Kotlin in the app's own words: documents, tags, uploads, a server that can refuse. No Android, no OkHttp, no JSON, and nothing Paperless-shaped: no endpoint paths, API versions, field names like `related_document_ids`, or Paperless status strings. It defines the repository interfaces `data/` implements.
- **`data/`** is the only layer that talks to anything outside the process: HTTP, the Keystore, content URIs, files. Every Paperless call, every response shape and every v9/v10 difference stays here and is turned into domain models before it leaves.
- **`ui/`** sees only `domain/`: ViewModels call use cases, screens render state. A screen never imports `data/`, builds a URL or reads a Paperless field.
- New server work starts by asking: what does the app need, in domain terms? That's the interface. How Paperless gives it is the implementation.

`ArchitectureTest` (#14) fails the build when an import crosses these lines. Don't loosen it to get green; move the code to the right layer.

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
