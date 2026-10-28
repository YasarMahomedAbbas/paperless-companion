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
