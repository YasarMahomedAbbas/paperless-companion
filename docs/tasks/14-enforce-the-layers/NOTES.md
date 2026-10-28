# Notes — Enforce the layers: the server only behind data/

Issue: #14 — Enforce the layers: the server only behind data/ (https://github.com/YasarMahomedAbbas/paperless-companion/issues/14)
Branch: `refactor/14-enforce-the-layers`

## Planning

- Approach: the domain types get neutral names (`DocumentServer`, `ServerException`). Files and document links reach `ui/` only through domain interfaces that `AppContainer` provides. `ArchitectureTest`, a pure rule function plus a scan of the real source tree, fails the build on any import that crosses the layers.
- Decided (the pipeline took these on the user's behalf):
  - Q1 → option 1: a domain `DocumentFiles.from(location: String, fallbackName)`, with `uri.toString()` in the UI and `Uri.parse` in `UriDocuments`. This keeps the domain Android-free. Flags: none.
  - Q2 → option 1: keep all user-visible "Paperless" text unchanged and rename only types and comments, to honour "no behaviour changes". Flag: ux.
  - Q3 → option 1: move the document link out of `HomeUiState` into `DocumentServer.documentUrl` (built by `PaperlessApi`), reached through `DocumentLinkUseCase`. Flag: scope, because it goes beyond the ticket's list but changes nothing visible.
- Decided (defaults, accepted):
  - `PaperlessRepository` → `DocumentServer`, `PaperlessException` → `ServerException` (`Kind` unchanged), `FakePaperless` → `FakeServer`.
  - `TaskInfo`, `TaskStatus`, `PaperlessApi` and `UriDocuments` keep their names. `data/`'s own "Paperless sent no token" messages stay in `data/`.
  - `ArchitectureTest` checks import lines and fully-qualified `…paperlesscompanion.data.` references. Its tree scan must find files in `ui/`, `domain/` and `data/` so it can't pass on an empty walk.
- Open risks: `HomeScreen` and `MainActivity` have no unit tests, so the `uri.toString()` / `Uri.parse` round trip is covered only by compilation and a device check. Content URIs survive the round trip losslessly. `verify.env` is absent, so the real-server check is skipped, and no Paperless call changes anyway.

## Implementation

- Shipped:
  - `PaperlessRepository` → `DocumentServer`, `PaperlessException` → `ServerException`, `FakePaperless` → `FakeServer`; use-case and container fields `paperless` → `server`; domain KDoc says "the server" (`domain/repository/Repositories.kt:8`, `domain/model/Models.kt:33`). User-visible "Paperless" strings unchanged.
  - `DocumentFiles` domain interface (`domain/repository/Repositories.kt:22`), implemented by `UriDocuments.from(location: String, …)` (`data/UriDocuments.kt:13`); `HomeScreen` and `MainActivity` pass `uri.toString()`, and `HomeScreen` no longer imports `data.*`.
  - `DocumentServer.documentUrl` (`domain/repository/Repositories.kt:18`), built in `PaperlessApi.documentUrl` (`data/PaperlessApi.kt:101`) with the same `<base>/documents/<id>/details` string; reached through new `DocumentLinkUseCase` and `HomeViewModel.documentUrl`. `HomeUiState.documentUrl` removed.
  - `ArchitectureTest` (`app/src/test/java/io/github/yasarmahomedabbas/paperlesscompanion/ArchitectureTest.kt`): pure `layerViolations(path, source)` plus five rule tests and a tree scan of `src/main/java` that must find files in `ui/`, `domain/` and `data/`.
- Tests: full CI command green (testDebugUnitTest koverVerify lintDebug assembleDebug): 64 passed / 0 failed / 0 skipped. New: `DocumentLinkUseCaseTest` (2), `ArchitectureTest` (6), `PaperlessApiTest` documentUrl (1), `HomeViewModelTest` link test split out (1).
- Fails when added back: with `import io.github.yasarmahomedabbas.paperlesscompanion.data.UriDocuments` restored in `HomeScreen.kt`, the tree test fails with:
  ```
  java.lang.AssertionError: Imports cross the layers:
  ui/home/HomeScreen.kt:51 imports io.github.yasarmahomedabbas.paperlesscompanion.data.UriDocuments (ui sees only domain)
  ```
  The import was reverted afterwards.
- Outstanding: device-only checks (open a finished upload's document link; scan or share a file and see it upload) are left for self-verification. Real-server check not needed (no Paperless call changed) and `verify.env` is absent.
- Contradicted the plan: none. Small additions: the `UploadDocumentUseCase` KDoc also says "the server" instead of "Paperless" (comment only).

## Review

- Fixed: none
- Raised: none
- API: no Paperless call touched. `documentUrl` is the web-UI page `<server>/documents/<id>/details` (not an API endpoint), moved into `PaperlessApi` byte-for-byte; `PaperlessApiTest` pins the string. `verify.env` absent, live schema not checked (nothing to check).
- Layers: clean. No `data.*` import or fully-qualified use outside `data/` and `AppContainer`; no endpoint path, field name or status string in `ui/` or `domain/`. The domain's remaining "Paperless" text is user-visible copy in `userMessage()`/`notConfirmed`, kept by decision Q2. One small behaviour note: the link now comes from `settings.load()` at tap time instead of the server URL cached in `HomeUiState`, so a signed-out tap opens nothing instead of a relative `/documents/<id>/details` URL. That is safer, not a regression.

## Doc drift

- Applied: README.md:Architecture — `DocumentLinkUseCase` added to the use-case table, and one sentence that `AppContainer` is the only file outside `data` naming a `data` class and that `ArchitectureTest` fails the build on a crossing import (rung 3: building the app; the table is the README's inventory of use cases).
- Skipped: CLAUDE.md § Layers — already names `ArchitectureTest` and the rules it enforces; checked against the test's rule function, accurate.
- Skipped: the domain renames (`DocumentServer`, `ServerException`, `DocumentFiles`) — no doc named the old types; `rg` finds the new ones.
- Skipped: README's use-case table describing uploads by Paperless endpoint — it describes what each use case achieves for a reader of the app; the calls themselves live in `PaperlessApi`, which the diagram already places in `data/`.
- Stale, fixed separately: README.md:Architecture — said "each depending only on the one below it", but `data` depends on `domain` (it implements its interfaces); now "`ui` and `data` both depend on `domain`, never on each other" (commit 1e57edd).
