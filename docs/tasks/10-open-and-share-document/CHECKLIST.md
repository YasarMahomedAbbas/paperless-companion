# Checklist — Open and share a document

Issue: #10 — Open and share a document (https://github.com/YasarMahomedAbbas/paperless-companion/issues/10)
Branch: `feat/10-open-and-share-document`
Problem: the app can upload a document but can't show one. To read, share or save a document we have to open the Paperless web UI.

## Done when

- Any document opens in the app: its PDF page by page with zoom, and its details (title, date, correspondent, document type, tags, storage path, added).
- It can be shared to any app, saved to the phone, and opened in the Paperless web UI, both on the home network and over the VPN.
- A non-PDF original (a photo, say) shows Paperless's PDF version, and the original can be saved to the phone.
- `ViewerViewModel` is tested and CI passes (`testDebugUnitTest koverVerify lintDebug assembleDebug`). Coverage stays at 80% or more and `ArchitectureTest` stays green.

## References

- Paperless API: no new endpoint. Every call already exists in `PaperlessLibrary` (#7) and was checked against the docs source and the server source at v3.3.4 (default API v10) and v2.20.15 (v9). File responses are the same on both versions.
  - `GET /api/documents/{id}/`: one document.
  - `GET /api/tags/`, `/api/correspondents/`, `/api/document_types/`, `/api/storage_paths/`: the names behind the ids.
  - `GET /api/documents/{id}/preview/` (inline) and `/download/?original=true`. Both go through `serve_file`, a Django `FileResponse`, which sends **`Content-Length`** (so progress can be determinate) and `Content-Disposition: <inline|attachment>; filename="…"; filename*=utf-8''…`.
    - Without `original`, a document with an archive version is served as `application/pdf`. Images and office files get one when consumed.
    - Without an archive, the original comes back in its own type (a text file is served as `text/plain; charset=utf-8`). The viewer must handle a preview that isn't a PDF.
  - Paperless `main` (after v3.3.4) adds document versions, where these endpoints take an optional `?version=`. Without it they serve the latest version, so nothing changes here.
  - The web UI link is `<server>/documents/<id>/details`, from `DocumentServer.documentUrl` (already used by the upload card).
- Design: canvas row "Document view" (https://claude.ai/artifact/YPcrFxT1joGdxmBHfiMdf6). `docs/design/layouts.md` § Viewer; `components.md` § Viewer bar, § Details panel, § Tags (display only), § Notice banner, § Status card, § Progress, the More menu, and the Snackbar.
- #9 (the list, in progress in parallel) will open this screen on a row tap. #11 adds Edit and Move to trash to it.

## Context

#7 left everything server-side in place. `GetDocumentUseCase`, `LoadLabelsUseCase` and `OpenDocumentFileUseCase` (`PREVIEW` / `ARCHIVED` / `ORIGINAL`, streamed into `cacheDir/documents/<id>-<kind>/`) are all there, and so is `DocumentLinkUseCase`.

This ticket adds three things:
- a progress callback on the file fetch, for the design's "x of y MB" bar;
- a domain `DocumentExports` port (share location and save to the phone), implemented in `data/` because content URIs and MediaStore are outside the process;
- the viewer screen with its `ViewerViewModel`, mirroring `UpdatesViewModel` / `HomeViewModel` and their tests.

Decisions:
- **Entry point.** The viewer opens from a finished upload card's "Open" (Q1-1). Open in Paperless moves into the viewer's More menu.
- **Saving.** Download saves to Downloads in one tap through MediaStore on Android 10+, with a "Saved to Downloads" snackbar. On Android 8–9 the system Save-as picker opens instead, so the app needs no storage permission (Q2-1).
- **Scope against #11.** Edit and Move to trash are left out (Q3-1). Quick actions show Download alone; the More menu holds Download and Open in Paperless.
- **One fetch.** The viewer fetches `PREVIEW` once; Share and Download use that same cached file. "Get photo" / "Get original" fetches `ORIGINAL` and saves it.
- **Rendering.** PDFs are rendered in `ui/` with Android's `PdfRenderer`: a lazy column of pages, pinch zoom 1–4×, and a page pill.
- **Details panel.** Built on Compose's `AnchoredDraggableState` with three stops (full screen, quick actions, all details). It opens on quick actions.
- **Loading.** The document and the labels load in parallel. If the labels fail, the panel shows without the names; if the document fails, the panel shows an error with Try again. The file's progress or failure is independent of both.
- **Navigation.** A nullable open-document id in `MainActivity`, the same pattern as `showUpdates`. #9 may restructure this, so expect a small merge.

## Test plan

- Unit:
  - `ViewerViewModelTest`:
    - Loading: the document, labels and preview load in parallel. Labels resolve to names (correspondent, type, storage path, tag names). A label failure leaves the names null and the details shown.
    - Document failure: a NOT_FOUND or UNREACHABLE document gives `DetailsState.Failed` with the user message and the right tone, and `retry()` reloads it.
    - File states: progress (done, total) and an unknown total. A non-PDF preview → `FileState.NotViewable`. A failure (UNREACHABLE → unreachable tone) and retry.
    - Share: emits the share event with the exports' location, name and type, and does nothing until the file is ready.
    - Save:
      - when saving directly works, it saves to Downloads with a "Saved to Downloads" message;
      - when it doesn't, it emits a pick-location event, and `saveTo(location)` copies to it;
      - a save failure gives a message;
      - "Get original" fetches `ORIGINAL`, then saves it.
    - Open in Paperless: emits the URL; signed out → no event.
    - `isPhotoOriginal`: true when the original's type is `image/*`.
  - `DocumentUseCasesTest`:
    - `OpenDocumentFileUseCase` passes the progress callback through;
    - the new `ExportDocumentUseCase` delegates to its port.
  - `HomeViewModelTest`: drop the `documentUrl` test, since that moved to the viewer.
- MockWebServer:
  - `PaperlessHttpTest`: `download` reports progress, ending at the full size, with the total from `Content-Length`. Without a length (chunked), the total is null.
  - `PaperlessLibraryTest`: `file` passes progress through.
- Real server (self-verification, read-only):
  - `GET /api/documents/{id}/` for one document;
  - the four label lists;
  - `GET /preview/` on a PDF document and on an image-original document: status, `Content-Type: application/pdf`, a `Content-Length` header present, the `Content-Disposition` filename;
  - `GET /download/?original=true` on the image document (`image/*`);
  - the web UI `/documents/<id>/details` answers 200 (or a login redirect).
- Device only:
  - PDF rendering and pinch zoom;
  - the three panel stops, and Back from full screen;
  - Share to another app (the receiving app reads the file);
  - Save to Downloads, where the file shows in Files;
  - Get photo for an image original;
  - Open in Paperless opens the browser;
  - all of it on home Wi-Fi and over the VPN.

---

## Checkpoint 1 — Progress and exports · commit: `feat(documents): report file progress and export files to the phone`

Goal: a file fetch reports its progress, and the domain can share a cached document file and save it to the phone, without anything visible yet.

### [x] 1.1 — Progress through the file fetch

- `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/data/PaperlessHttp.kt`: `download(request, into, onProgress: (done: Long, total: Long?) -> Unit = { _, _ -> })`.
  - Total is `body.contentLength()`, null when it is -1.
  - Copy with an 8 KiB buffer and report after each 64 KiB and once at the end. Report `(0, total)` first.
- `domain/repository/Repositories.kt`: `DocumentLibrary.file(config, id, kind, onProgress: (done: Long, total: Long?) -> Unit = { _, _ -> })`. KDoc: total is null when the server doesn't say.
- `data/PaperlessLibrary.kt`: pass it through.
- `domain/usecase/DocumentsUseCase.kt`: `OpenDocumentFileUseCase.invoke(id, kind, onProgress = { _, _ -> })`.
- `app/src/test/.../Fakes.kt`: `FakeLibrary.file` records the call, calls `onProgress` with a scripted `progress: List<Pair<Long, Long?>>`, and answers per kind (`files: Map<DocumentFileKind, DocumentFile>`, falling back to `file`).

### [x] 1.2 — `DocumentExports` port and use case

- `domain/repository/Repositories.kt`: `interface DocumentExports`. KDoc: puts a fetched document file where the user and other apps can reach it; failures throw `ServerException(UNREADABLE, …)`. Members:
  - `fun shareLocation(file: DocumentFile): String`: a location another app may be granted to read.
  - `val canSaveDirectly: Boolean`: true when `saveToDownloads` works without asking (Android 10+).
  - `suspend fun saveToDownloads(file: DocumentFile): String`: returns the name it was saved under.
  - `suspend fun saveTo(file: DocumentFile, location: String)`: copies to a location the user picked.
- `domain/usecase/ExportDocumentUseCase.kt` (new): `class ExportDocumentUseCase(private val exports: DocumentExports)` with `fun shareLocation(file)`, `val canSaveDirectly`, `suspend fun saveToDownloads(file)` and `suspend fun saveTo(file, location)`.
- `Fakes.kt`: `FakeExports` with a recorded call list, `canSaveDirectly` and `failure`.

### [x] 1.3 — Android implementation

- `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/data/PhoneExports.kt` (new): `class PhoneExports(private val context: Context, private val authority: String, private val io: CoroutineDispatcher = Dispatchers.IO) : DocumentExports`.
  - `shareLocation`: `FileProvider.getUriForFile(context, authority, file.file).toString()`.
  - `canSaveDirectly`: `Build.VERSION.SDK_INT >= Q`.
  - `saveToDownloads`: `MediaStore.Downloads.EXTERNAL_CONTENT_URI` with `DISPLAY_NAME`, `MIME_TYPE` and `IS_PENDING` = 1. Copy the file, then clear `IS_PENDING`. On failure, delete the row and throw `ServerException(Kind.UNREADABLE, "Couldn't save ${file.name}")`. Returns the display name MediaStore settled on.
  - `saveTo`: copy into `contentResolver.openOutputStream(Uri.parse(location), "w")`, with the same error.
- `app/src/main/res/xml/file_paths.xml`: add `<cache-path name="documents" path="documents/" />`, matching `AppContainer`'s `File(context.cacheDir, "documents")`.
- `app/build.gradle.kts`: kover excludes `"*.data.PhoneExports*"`, since it needs a device, like `UriDocuments`.

### [x] 1.4 — Tests

- `PaperlessHttpTest`: progress with `Content-Length`, ending at the full size; chunked → total null.
- `PaperlessLibraryTest`: `file` forwards progress.
- `DocumentUseCasesTest`: `OpenDocumentFileUseCase` forwards progress; `ExportDocumentUseCase` delegates each member.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*PaperlessHttpTest' --tests '*PaperlessLibraryTest' --tests '*DocumentUseCasesTest' --tests '*ArchitectureTest'`

---

## Checkpoint 2 — Viewer state · commit: `feat(documents): load a document, its file and its labels for the viewer`

Goal: `ViewerViewModel` drives everything the viewer shows and does, and is fully tested without a screen.

### [x] 2.1 — `ViewerViewModel`

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/ui/viewer/ViewerViewModel.kt` (new)

```kotlin
data class DocumentDetails(
    val title: String, val created: LocalDate?, val added: Instant?,
    val correspondent: String?, val documentType: String?, val storagePath: String?,
    val tags: List<String>, val isPhotoOriginal: Boolean, val originalIsPdf: Boolean,
)
sealed interface DetailsState {
    data object Loading : DetailsState
    data class Ready(val details: DocumentDetails) : DetailsState
    data class Failed(val message: String, val unreachable: Boolean) : DetailsState
}
sealed interface FileState {
    data class Loading(val done: Long, val total: Long?) : FileState
    data class Ready(val file: DocumentFile) : FileState          // a PDF
    data class NotViewable(val file: DocumentFile) : FileState    // preview isn't a PDF: offer Download
    data class Failed(val message: String, val unreachable: Boolean) : FileState
}
data class ViewerUiState(
    val documentId: Int, val details: DetailsState = DetailsState.Loading,
    val file: FileState = FileState.Loading(0, null), val saving: Boolean = false,
    val message: String? = null,   // snackbar text, cleared by messageShown()
)
sealed interface ViewerEvent {
    data class Share(val location: String, val name: String, val mimeType: String?) : ViewerEvent
    data class PickSaveLocation(val name: String, val mimeType: String?) : ViewerEvent
    data class OpenUrl(val url: String) : ViewerEvent
}
class ViewerViewModel(
    private val documentId: Int,
    private val getDocument: GetDocumentUseCase,
    private val loadLabels: LoadLabelsUseCase,
    private val openFile: OpenDocumentFileUseCase,
    private val documentLink: DocumentLinkUseCase,
    private val export: ExportDocumentUseCase,
) : ViewModel()
```

- `val state: StateFlow<ViewerUiState>`.
- `val events: Flow<ViewerEvent>`, a `Channel(BUFFERED).receiveAsFlow()`.
- `init` launches three coroutines: the document, the labels (errors swallowed to null names) and the `PREVIEW` file with progress.
- Names are joined when both have arrived: unknown ids → null, and tag names are kept in the document's tag order.
- `isPhotoOriginal = mimeType?.startsWith("image/") == true`.
- A file is a PDF when its `mimeType == "application/pdf"` or its name ends in `.pdf`.
- `retry()` reloads whichever of details and file failed.
- `share()`: `Share(export.shareLocation(file), file.name, file.mimeType)` from `FileState.Ready` / `NotViewable`; otherwise a no-op.
- `download()` saves the preview file, and `downloadOriginal()` fetches `ORIGINAL` first; `saving` is true while either runs.
  - With `canSaveDirectly`, it calls `saveToDownloads` → message "Saved to Downloads".
  - Otherwise it keeps the file pending and emits `PickSaveLocation`. `saveTo(location: String?)` copies it (null = cancelled), then shows "Saved".
  - A failure gives the message "Couldn't save <name>", or the `userMessage()` for a server error.
- `openInPaperless()` emits `OpenUrl` when `documentLink(documentId)` isn't null.
- `messageShown()`.
- Errors use `ServerException.userMessage()`; `unreachable = kind == UNREACHABLE || kind == NO_ANSWER`.

### [x] 2.2 — Tests

File: `app/src/test/java/io/github/yasarmahomedabbas/paperlesscompanion/ui/ViewerViewModelTest.kt` (new). Mirror `UpdatesViewModelTest` (the `Dispatchers.setMain` rule / `runTest`), using `FakeLibrary`, `FakeExports` and a fake settings. Cover every Unit row of the test plan for `ViewerViewModel`.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*ViewerViewModelTest' --tests '*ArchitectureTest'`

---

## Checkpoint 3 — Viewer screen · commit: `feat(ui): show a document with its pages and details panel`

Goal: the viewer screen and its parts exist in the house style, with a `@Preview` for every state in `layouts.md` § Viewer. Use the `design-recipe` skill; shape: **viewer**.

### [x] 3.1 — Shared parts

All under `ui/components/`. If #9 has already merged a part with the same recipe, reuse it instead.
- `ViewerBar.kt`: `ViewerBar(title, onBack, onShare, menuOpen, onMenuOpenChange, shareEnabled, menu: @Composable ColumnScope.() -> Unit)`, plus `MenuItem(text, icon, onClick, enabled)`. Back, a title at 18/500 ellipsised to one line, then Share and More icon buttons with content descriptions "Back", "Share" and "More options". The More menu follows the recipe: `surface`, `r-card`, 244 wide.
- `TagPill.kt`: `TagPill(name, inDetails: Boolean)`, display only, per § Tags. Paperless tag colours are ignored.
- `DetailsPanel.kt`:
  - `enum class PanelStop { FULL_SCREEN, QUICK, ALL }`.
  - `DetailsPanel(state: AnchoredDraggableState<PanelStop>, quick: @Composable () -> Unit, all: @Composable () -> Unit)`. Anchors: FULL_SCREEN = only the 120 × 48 "Show details" handle tab showing, QUICK = 268 high, ALL = 180 below the top. Top corners are `r-sheet`; a swipe moves one stop.
  - `DetailRow(label, value)` for the definition list.
- `StatusCard.kt`: `StatusCard(icon, tone, statement, hint, actionLabel, onAction)` per § Status card.
- `Progress.kt`: `DeterminateProgress(done: Long, total: Long?)`, a fully round bar with "x.x of y.y MB" under it at 14 `mfg`. It falls back to the 24dp spinner when the total is unknown.
- Also needed and added: `Skeleton.kt` (`SkeletonBlock`), `Snackbar.kt` (`AppSnackbarHost`), the `Download` / `OpenExternal` icons, and the `viewerTitle` / `statement` type slots.
- Add each to `docs/design/README.md` decisions only where the recipe left a gap.

### [x] 3.2 — PDF pages

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/ui/viewer/PdfPages.kt` (new)
- `@Composable fun PdfPages(file: java.io.File, fullScreen: Boolean, onTap: () -> Unit, modifier: Modifier)`.
- Opens `ParcelFileDescriptor` + `PdfRenderer` in a `DisposableEffect`, closed on dispose. All rendering goes through one `Mutex`, because `PdfRenderer` allows one page open at a time. Pages render on `Dispatchers.IO` into `ARGB_8888` bitmaps at the column width × 2 (capped at 2048 px wide, so a page stays a few MB), filled white first.
- Pages sit in a `LazyColumn` as white paper on `bg` with a small shadow, 12 apart. The gutter is 16 when full screen.
- Pinch zoom 1–4× with pan via `Modifier.transformable` on the column, and a double tap toggles 1× / 2.5×.
- The pill reads "Page n of N", from `firstVisibleItemIndex`.
- A renderer that throws (a corrupt PDF) gives the "Can't show this file" status card with Download.
- Kover excludes `"*.ui.viewer.PdfPages*"` and `"*.ui.viewer.PdfDocument*"`, since they need a device.

### [x] 3.3 — `ViewerScreen`

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/ui/viewer/ViewerScreen.kt` (new)
- `ViewerScreen(viewModel, onBack, onShare: (ViewerEvent.Share) -> Unit, onPickSaveLocation: (ViewerEvent.PickSaveLocation) -> Unit, onOpenUrl: (String) -> Unit)`. Events are collected in a `LaunchedEffect`. It delegates to a stateless `ViewerContent(state, …)` for previews.
- Bar: `ViewerBar`, whose More menu holds Download and Open in Paperless.
- Body by `FileState`:
  - `Loading` → `DeterminateProgress` centred;
  - `Ready` → `PdfPages`;
  - `NotViewable` → `StatusCard` "Can't show this file here" with Download;
  - `Failed` → `StatusCard` with the message and Try again, in the warning tone when unreachable and destructive otherwise. The panel still shows.
- Panel, quick actions:
  - title at 20/500, `Correspondent · date · type` (only the parts present), and tag pills;
  - a Download `Secondary` button, busy while `saving`;
  - the hint "Swipe up for all details, down for full screen" at 13 `mfg`;
  - when `isPhotoOriginal`, a `NoticeBanner` (Info) "This came in as a photo. You're seeing Paperless's PDF of it." with the "Get photo" action → `downloadOriginal()`. A non-image, non-PDF original gets "Get original".
- Panel, all details: `DetailRow`s for title, date, correspondent, document type, tags, storage path and added, with Download pinned at the bottom. `DetailsState.Loading` shows a skeleton of the quick actions, and `Failed` shows a compact error with Try again.
- Full screen: the bar and the panel slide away and the status bar hides (`WindowInsetsControllerCompat`). A tap on the page shows the bar for 3 s.
- Back: from full screen or all details it goes to QUICK; from QUICK, `onBack`.
- `message` → a `SnackbarHost` styled per § Snackbar, then `messageShown()`.
- Previews: view, full screen, details expanded, more menu, loading, load failed, photo original, not viewable.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest lintDebug assembleDebug`
- Run `docs/design/checklist.md` against the new code.

---

## Checkpoint 4 — Open, share and save from the app · commit: `feat(documents): open documents in the app and share or save them`

Goal: tapping Open on a finished upload shows the document in the viewer, and Share, Download, Get photo and Open in Paperless work end to end.

### [x] 4.1 — Wiring

- `AppContainer.kt`:
  - `private val exports: DocumentExports = PhoneExports(context, "${context.packageName}.files")`. The authority must equal the manifest's `${applicationId}.files`; `applicationId == packageName` here.
  - `fun viewerViewModelFactory(documentId: Int) = viewModelFactory { initializer { ViewerViewModel(documentId, GetDocumentUseCase(library, settings), LoadLabelsUseCase(library, settings), OpenDocumentFileUseCase(library, settings), DocumentLinkUseCase(server, settings), ExportDocumentUseCase(exports)) } }`.
  - `homeViewModelFactory` drops `DocumentLinkUseCase`.
- `ui/home/HomeViewModel.kt`: remove `documentLink` and `documentUrl` (the viewer owns the web link now). Update `HomeViewModelTest` to match.
- `ui/home/HomeScreen.kt`: new parameter `onOpenDocument: (Int) -> Unit`. The upload card's "Open" calls it in place of `ACTION_VIEW`.

### [x] 4.2 — `MainActivity`

- `var openDocumentId by rememberSaveable { mutableStateOf<Int?>(null) }` inside `setContent` (not an activity field like `showUpdates`, so a rotation keeps the viewer open).
- While it is set, the home branch renders `ViewerScreen(viewModel = viewModel<ViewerViewModel>(key = "viewer-$id", factory = container.viewerViewModelFactory(id)), …)` with `BackHandler(enabled = locked == null)`, the same pattern as `showUpdates`.
- `onShare`: `Intent(ACTION_SEND)` with type `mimeType ?: "application/octet-stream"`, `EXTRA_STREAM = Uri.parse(location)`, `ClipData` set and `FLAG_GRANT_READ_URI_PERMISSION`, wrapped in `Intent.createChooser(…, null)`.
- `onPickSaveLocation`: a `rememberLauncherForActivityResult(SaveAs())` launcher, where `SaveAs` is a small `ActivityResultContract<PickSaveLocation, Uri?>` building `ACTION_CREATE_DOCUMENT` with the name and the MIME per launch. Its result goes to `viewModel.saveTo(uri?.toString())`.
- `onOpenUrl`: `ACTION_VIEW`, catching `ActivityNotFoundException` → no-op.
- Signing out (the gate going to SignIn) clears `openDocumentId`.

### [x] 4.3 — Tests

- `HomeViewModelTest` and `GateViewModelTest` still pass after the `documentUrl` removal.
- `ArchitectureTest`: no `ui/` file imports `data/`. `PhoneExports` is named only in `AppContainer`.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest koverVerify lintDebug assembleDebug` (the full CI command).

---

## Out of scope

- Edit details and Move to trash (#11). The Edit button and the trash menu item are not shown.
- The document list and the tap from it (#9). Making Documents the home screen.
- Document versions (`?version=`), notes, and "more like this" (#12).
- Rendering non-PDF files in the app (text, office originals without an archive): they get "Can't show this file here" with Download.
- An offline copy of opened documents. The cache keeps only the latest copy per document and kind, and nothing survives as a feature.
- Printing, and text selection or search inside the PDF.
