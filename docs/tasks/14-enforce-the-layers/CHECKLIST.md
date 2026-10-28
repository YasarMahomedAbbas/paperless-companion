# Checklist — Enforce the layers: the server only behind data/

Issue: #14 — Enforce the layers: the server only behind data/ (https://github.com/YasarMahomedAbbas/paperless-companion/issues/14)
Branch: `refactor/14-enforce-the-layers`
Problem: The domain is named after Paperless, the home screen reaches into `data/` for its files and builds a Paperless link itself, and nothing in the build stops a layer from leaking again.

## Done when

- The domain's server types have backend-neutral names (`DocumentServer`, `ServerException`). `PaperlessApi` keeps its name.
- `HomeScreen` no longer imports `data.*`. Files come through a domain interface that `AppContainer` provides.
- `ui/` no longer builds the document link. The server implementation builds it.
- `ArchitectureTest` passes on the tree, and its rule tests show it failing for each forbidden import.
- No behaviour changes: user-visible text and URLs are identical, and existing tests pass apart from renames and the moved link test.
- CI passes: `./build.sh ./gradlew --console=plain testDebugUnitTest koverVerify lintDebug assembleDebug`.

## References

- Paperless API: no call is added or changed. `documentUrl` moves the existing UI link `<server>/documents/<id>/details` (a web UI page, not an API endpoint) into `PaperlessApi` unchanged.
- `CLAUDE.md` § Layers (the rules `ArchitectureTest` enforces).
- #7 builds on this.

## Context

The domain has `PaperlessRepository` (implemented by `data/PaperlessApi`) and `PaperlessException`. `HomeScreen` takes a `data.UriDocuments` and calls `from(uri, fallbackName)`, and `MainActivity` does the same through `container.documents`. `HomeUiState.documentUrl` builds the Paperless details path in `ui/`.

The pipeline settled these decisions:
- Rename the types and test fakes only. User-visible copy that says "Paperless" stays as it is (Q2).
- Add a domain interface `DocumentFiles` that takes the location as a `String`, so the domain stays Android-free (Q1). `UriDocuments` implements it and parses the string back to a `Uri`.
- Add `documentUrl` to `DocumentServer`, implemented in `PaperlessApi`. The ViewModel gets the link through a small use case (Q3).

`ArchitectureTest` is a pure rule function, tested on made-up inputs so every forbidden import is shown failing, plus one test that runs it over the real `src/main/java` tree.

## Test plan

- Unit: the existing `LoginUseCaseTest`, `UploadDocumentUseCaseTest`, `LoginViewModelTest`, `HomeViewModelTest` and `PaperlessApiTest` pass with only the renames. Add a new `DocumentLinkUseCaseTest` (signed in → link; signed out → null). `HomeViewModelTest`'s link assertion moves from `state.documentUrl(5)` to `viewModel.documentUrl(5)` and expects the same string. `PaperlessApiTest` gets a test that `documentUrl` returns `http://paperless:8000/documents/5/details`.
- `ArchitectureTest`, rule tests: a `ui/` file importing `…data.X` is a violation. A `domain/` file importing each of `…data.`, `…ui.`, `android.`, `androidx.`, `okhttp3.` and `kotlinx.serialization.` is a violation. A root file other than `AppContainer.kt` importing `…data.` is a violation. A fully-qualified `…paperlesscompanion.data.` reference in code outside `data/` is a violation. The allowed cases pass: `data/` importing `okhttp3`, `AppContainer.kt` importing `data`, and `ui/` importing `android`.
- `ArchitectureTest`, tree test: zero violations over `src/main/java`, and the scan must have found at least one file in each of `ui/`, `domain/` and `data/`, so a wrong path can't pass by finding nothing.
- MockWebServer: none new (no call changes).
- Real server: none needed, since no Paperless call changes. `verify.env` is also absent on this machine, so record it as skipped.
- Device only: open a finished upload's document link, and scan or share a file and see it upload. Both should behave as before.

---

## Checkpoint 1 — Neutral names for the server types · commit: `refactor(domain): name the server types after what they are, not Paperless`

Goal: the domain says `DocumentServer` and `ServerException`, and every test passes unchanged apart from the renames.

### [x] 1.1 — Rename the repository interface

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/domain/repository/Repositories.kt`
- `interface PaperlessRepository` → `interface DocumentServer`. Methods are unchanged (`login`, `upload`, `task`).
- New KDoc: "The document server the app signs in and uploads to. Implementations throw ServerException on any failure."

### [x] 1.2 — Rename the exception and make the model KDoc neutral

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/domain/model/Models.kt`
- `class PaperlessException(kind, message, cause)` → `class ServerException(...)`. `Kind` is unchanged.
- KDoc only: `ServerConfig` becomes "A document server and the API token that signs in to it". `UploadState` and `TaskInfo` comments say "the server" instead of "Paperless". `TaskInfo` and `TaskStatus` keep their names.

### [x] 1.3 — Update the users

Files: `domain/usecase/LoginUseCase.kt`, `domain/usecase/UploadDocumentUseCase.kt`, `ui/login/LoginViewModel.kt`, `data/PaperlessApi.kt`, `AppContainer.kt` (all under `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/`)
- Replace the type names. Rename the constructor parameters `paperless: PaperlessRepository` → `server: DocumentServer` in both use cases, and `private val paperless` → `private val server` in `AppContainer`.
- `PaperlessApi` KDoc: "DocumentServer over the paperless-ngx REST API (token auth)."
- **Leave every user-visible string as it is**, including `userMessage()`'s "Paperless" wording and "Paperless refused the document" (decision Q2). Only code comments change.

### [x] 1.4 — Rename in the tests

Files: `app/src/test/java/io/github/yasarmahomedabbas/paperlesscompanion/Fakes.kt` and every test that uses these names
- `FakePaperless` → `FakeServer : DocumentServer`, with KDoc "Scripted server: …". Test fields `paperless` → `server`.
- `PaperlessException` → `ServerException` in `PaperlessApiTest`, `LoginUseCaseTest`, `UploadDocumentUseCaseTest`, `LoginViewModelTest` and `HomeViewModelTest`. Change no assertions.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest`
- `rg -n 'PaperlessRepository|PaperlessException|FakePaperless' app/src` finds nothing.

---

## Checkpoint 2 — Files and links through the domain · commit: `refactor(ui): get files and document links through the domain`

Goal: no file under `ui/` imports `data.*` or builds a server URL. The link and the uploads behave exactly as before.

### [x] 2.1 — Domain interface for files to upload

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/domain/repository/Repositories.kt`
```kotlin
/** Turns a file the user picked, shot, scanned or shared (by its location, e.g. a content URI string) into a DocumentSource. */
interface DocumentFiles {
    fun from(location: String, fallbackName: String): DocumentSource
}
```

### [x] 2.2 — `UriDocuments` implements it

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/data/UriDocuments.kt`
- `class UriDocuments(resolver) : DocumentFiles`.
- `override fun from(location: String, fallbackName: String): DocumentSource`: `val uri = Uri.parse(location)`, then the current body. Use `location` in the `FileNotFoundException` message.
- Keep the class name. Kover already excludes `*.data.UriDocuments*`.

### [x] 2.3 — Document link on the server interface

Files: `domain/repository/Repositories.kt`, `data/PaperlessApi.kt`
- In `DocumentServer`, add `/** The address of a document's page in the server's own web interface. */ fun documentUrl(config: ServerConfig, documentId: Int): String`.
- `PaperlessApi.documentUrl` returns `"${config.baseUrl}/documents/$documentId/details"`, the exact string `HomeUiState` builds today. No normalisation.
- `FakeServer.documentUrl` returns `"${config.baseUrl}/documents/$documentId"` (any distinct shape will do).
- `PaperlessApiTest`: add `` `documentUrl points at the document's details page` ``, asserting `"http://paperless:8000/documents/5/details"` for `CONFIG`.

### [x] 2.4 — Use case for the link

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/domain/usecase/DocumentLinkUseCase.kt` (new)
```kotlin
/** The web address of an uploaded document on the signed-in server, or null when signed out. */
class DocumentLinkUseCase(private val server: DocumentServer, private val settings: SettingsRepository) {
    operator fun invoke(documentId: Int): String? = settings.load()?.let { server.documentUrl(it, documentId) }
}
```
Test: `app/src/test/java/io/github/yasarmahomedabbas/paperlesscompanion/domain/DocumentLinkUseCaseTest.kt`, covering signed in → `FakeServer`'s link and signed out → null.

### [x] 2.5 — ViewModel and screen

Files: `ui/home/HomeViewModel.kt`, `ui/home/HomeScreen.kt`, `AppContainer.kt`, `MainActivity.kt`
- `HomeUiState`: remove `documentUrl`. Keep `serverUrl`.
- `HomeViewModel(uploadDocument, documentLink: DocumentLinkUseCase, settings)`, with `fun documentUrl(documentId: Int): String? = documentLink(documentId)`.
- `HomeScreen`: change the parameter to `documents: DocumentFiles`, and remove the `data.UriDocuments` import. Call sites pass `uri.toString()`: `documents.from(uri.toString(), …)` for scan, photo and picked files. `onOpen` does `viewModel.documentUrl(id)?.let { context.startActivity(Intent(ACTION_VIEW, Uri.parse(it))) }`.
- `AppContainer`: `val documents: DocumentFiles = UriDocuments(context.contentResolver)`. The home factory builds `HomeViewModel(UploadDocumentUseCase(server, settings), DocumentLinkUseCase(server, settings), settings)`.
- `MainActivity.receive`: `container.documents.from(it.toString(), "Shared document")`.
- `HomeViewModelTest`: construct with `DocumentLinkUseCase(server, settings)`. Split the first test so that it checks `serverUrl` as before and checks `viewModel.documentUrl(5)` against the `FakeServer` link.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*HomeViewModelTest' --tests '*DocumentLinkUseCaseTest' --tests '*PaperlessApiTest'`
- `rg -n 'paperlesscompanion\.data' app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/ui app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/domain` finds nothing.
- `./build.sh ./gradlew --console=plain assembleDebug` compiles (the screen and activity have no unit tests).

---

## Checkpoint 3 — ArchitectureTest · commit: `test(build): fail the build when an import crosses the layers`

Goal: a plain unit test that reads the sources and fails on any import crossing `CLAUDE.md` § Layers.

### [x] 3.1 — The rule, as a pure function

File: `app/src/test/java/io/github/yasarmahomedabbas/paperlesscompanion/ArchitectureTest.kt` (new)
- `internal fun layerViolations(relativePath: String, source: String): List<String>`. `relativePath` is relative to the `paperlesscompanion` package directory (e.g. `ui/home/HomeScreen.kt`). It returns one human-readable message per offending line (`"<path>:<line> imports <what> (<rule>)"`).
- Imports: lines matching `^import\s+([\w.]+)`. Let `P = "io.github.yasarmahomedabbas.paperlesscompanion"`.
  - `ui/**`: forbid `P.data.`.
  - `domain/**`: forbid `P.data.`, `P.ui.`, `android.`, `androidx.`, `okhttp3.`, `kotlinx.serialization.`.
  - Anything not under `data/` and not `AppContainer.kt`: forbid `P.data.`.
- Fully-qualified use: outside `data/` and `AppContainer.kt`, any non-import, non-package line containing `P.data.` is a violation, so a data class can't be named inline to dodge the import rule.

### [x] 3.2 — Rule tests (the "fails when added back" proof)

Same file, one `@Test` each, asserting non-empty / empty results on made-up sources:
- `` `ui importing data is caught` ``
- `` `domain importing data, ui, android, androidx, okhttp or serialization is caught` ``. Loop over the six prefixes and assert each one is caught.
- `` `outside data and AppContainer, importing data is caught` `` (e.g. `MainActivity.kt`)
- `` `a fully qualified data reference is caught` ``
- `` `allowed imports pass` `` (`data/` → `okhttp3`/`kotlinx.serialization`, `AppContainer.kt` → `data`, `ui/` → `android`/`androidx`/`domain`, `domain/` → `java.io`/`kotlinx.coroutines`)

### [x] 3.3 — Tree test

Same file: `` `the app's sources keep to the layers` ``.
- Root: `File("src/main/java/io/github/yasarmahomedabbas/paperlesscompanion")`. Gradle runs unit tests from the module directory. Assert it `isDirectory`.
- Walk `*.kt` and compute the relative path with `/` separators.
- Assert at least one file was found under each of `ui/`, `domain/` and `data/`.
- Assert the collected violations are empty, and print them joined by newlines in the failure message.
- One KDoc line on the class points to `CLAUDE.md` § Layers and says "move the code, don't loosen the rule".

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*ArchitectureTest'`
- Temporarily put `import io.github.yasarmahomedabbas.paperlesscompanion.data.UriDocuments` back in `HomeScreen.kt`. The tree test should fail and name that line. Record the output in NOTES for the PR, then revert the import.
- The full CI command: `./build.sh ./gradlew --console=plain testDebugUnitTest koverVerify lintDebug assembleDebug`.

---

## Out of scope

- Rewording user-visible text that says "Paperless" (login copy, app title, upload status and error messages). It stays as it is (Q2). A neutral wording would be its own ticket.
- Renaming `PaperlessApi`, `UriDocuments`, `PaperlessTheme`, `PaperlessApp`, `TaskInfo` or `TaskStatus`.
- Any change to a Paperless call, its request or response handling, or the link's format.
- Splitting the code into Gradle modules, or bringing in an architecture-lint library.
