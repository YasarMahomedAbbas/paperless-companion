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
