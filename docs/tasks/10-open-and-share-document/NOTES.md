# Notes — Open and share a document

Issue: #10 — Open and share a document (https://github.com/YasarMahomedAbbas/paperless-companion/issues/10)
Branch: `feat/10-open-and-share-document`

## Planning

- Approach: a viewer screen (`ViewerViewModel` + `ViewerScreen`) on the #7 use cases.
  - The file fetch gains progress.
  - A domain `DocumentExports` port (share location, save to Downloads or a picked location) is implemented in `data/PhoneExports` with FileProvider and MediaStore.
  - PDF pages render with `PdfRenderer` in `ui/`, under a three-stop details panel.
- Decided (the pipeline took these on the user's behalf):
  - Q1 → option 1: a finished upload card's "Open" opens the in-app viewer (the entry point until #9's list); Open in Paperless moves to the viewer's More menu. Flag: ux.
  - Q2 → option 1: Download saves to Downloads in one tap via MediaStore on Android 10+ with a "Saved to Downloads" snackbar, and falls back to the system Save-as picker on Android 8–9 (no storage permission). Flag: ux.
  - Q3 → option 1: Edit and Move to trash are left out. Quick actions show Download alone, the More menu holds Download and Open in Paperless, and #11 adds the rest. Flag: scope.
- Decided (defaults, accepted):
  - (a) The file fetch reports progress (done, total or null) for the determinate MB bar, falling back to a spinner without a length.
  - (b) The viewer fetches the preview once; Share and Download reuse it. "Get photo"/"Get original" fetches the original.
  - (c) Sharing and saving go through a domain `DocumentExports` port implemented in `data/` (content URIs and MediaStore are outside the process).
  - (d) `PdfRenderer` rendering (a lazy column, pinch zoom 1–4×, a page pill) lives in `ui/`.
  - (e) The details panel is an `AnchoredDraggableState` with three stops: full screen, quick actions and all details.
  - (f) The document and the labels load in parallel; a label failure only drops the names.
  - (g) A preview that isn't a PDF gets a "Can't show this file here" status card with Download.
  - (h) Navigation is a nullable open-document id in `MainActivity`, like `showUpdates`.
- Open risks:
  - #9 runs in parallel and touches `MainActivity`, `HomeScreen` and maybe the same new shared components (status card, tag pill, progress). Expect a merge; reuse #9's parts if it lands first.
  - No live schema was read (`verify.env` absent). The calls are unchanged from #7 and checked against the server source at v3.3.4, but self-verification must still confirm `Content-Length` on preview behind granian/proxy; without it the bar falls back to a spinner.
  - PDF rendering, zoom, the panel gestures, share and MediaStore save can only be checked on a phone.

## Implementation

- Shipped:
  - File progress: `PaperlessHttp.download` reports (done, total) every 64 KiB with the total from Content-Length, null when chunked (`data/PaperlessHttp.kt:102`, copy loop at `:162`); passed through `DocumentLibrary.file`, `PaperlessLibrary.file` and `OpenDocumentFileUseCase`.
  - Exports: the domain port `DocumentExports` (`domain/repository/Repositories.kt:78`) and `ExportDocumentUseCase`; `data/PhoneExports.kt:23` implements it with FileProvider (new `documents/` cache path in `res/xml/file_paths.xml`), MediaStore Downloads on Android 10+ and a picked content URI otherwise.
  - `ui/viewer/ViewerViewModel.kt:84`: details (waiting for the labels, a label failure only drops the names), the preview file with progress, NotViewable for a non-PDF preview, share, download / get original / save-as, open in Paperless, retry of whichever failed.
  - Viewer screen: `ui/viewer/ViewerScreen.kt` (`ViewerContent` at `:125`, eight previews), `ui/viewer/PdfPages.kt` (PdfRenderer behind one mutex, pinch zoom 1–4×, double tap, page pill, broken-file card), and shared parts in `ui/components/` (`ViewerBar` + `MenuItem`, `TagPill`, `DetailsPanel` at `DetailsPanel.kt:88` + `DetailRow`, `StatusCard`, `DeterminateProgress`, `SkeletonBlock`, `AppSnackbarHost`, two icons) plus the `viewerTitle` and `statement` type slots. Design gaps recorded in `docs/design/README.md` § Decisions.
  - Wiring: `AppContainer.viewerViewModelFactory` (`AppContainer.kt:92`); `HomeViewModel` lost `documentUrl`; the upload card's Open calls `onOpenDocument`; `MainActivity` opens the viewer from a saveable id, shares through a chooser with read permission (`MainActivity.kt:188`), saves through the `SaveAs` contract (`:226`), and opens the web UI.
- Tests: 317 passed / 0 failed / 0 skipped on the full CI command (`testDebugUnitTest koverVerify lintDebug assembleDebug`), line coverage 92.0%. New: 25 in `ViewerViewModelTest`, 2 in `PaperlessHttpTest`, 1 in `PaperlessLibraryTest`, 3 in `DocumentUseCasesTest`; `HomeViewModelTest` dropped the `documentUrl` test.
- Outstanding: every task is ticked. The device-only checks in the test plan (rendering and zoom, the panel stops and Back, share, Save to Downloads, Get photo, Open in Paperless, home Wi-Fi and VPN) and the real-server checks are for self-verification.
- Contradicted the plan:
  - `openDocumentId` lives in `rememberSaveable` in `setContent`, not an activity field like `showUpdates`, so a rotation keeps the viewer open; the save-as launcher is a `rememberLauncherForActivityResult` with a small `SaveAs` contract carrying the MIME per launch.
  - Page bitmaps are capped at 2048 px wide (column width × 2 otherwise), so a long document doesn't exhaust memory.
  - `produceState` tripped lint's `ProduceStateDoesNotAssignValue` (a false positive under K2), so `PdfPages` uses `remember` + `LaunchedEffect` instead.
  - The viewer shows fixed failure copy rather than the view model's `message` (the design checklist forbids server text and HTTP codes); snackbars still carry `userMessage()` for a failed Get original.
  - The plan's extra parts were needed: a skeleton block, a snackbar host and the Download / Open external icons did not exist yet.
