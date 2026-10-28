# Notes — #6 Design the document browsing app

Branch: `docs/6-design-document-browsing`

## Design

- **Canvas:** https://claude.ai/artifact/YPcrFxT1joGdxmBHfiMdf6 (private until shared from its
  Share menu; 45 boards, 412 × 915 each)
- **Manual:** `docs/design/README.md` (rules, tokens), `foundations.md`, `components.md`,
  `layouts.md`, `checklist.md`. The canvas template
  (`.claude/skills/design-canvas/assets/artboard.dc.html`) and `snippets.md` carry the same
  tokens.
- **Not done yet (waits for approval):** the tokens in `ui/theme/` and applying them to the
  login and home screens.

### Rows

1. Documents (home): view, loading more, empty, loading, load failed
2. Add a document: choose (sheet), uploading, refused, scanner needs camera
3. Search, filters and sort: searching, no results, filters sheet, filters applied, sort sheet
4. Document view: view, details, more menu, loading, load failed, photo original
5. Edit details: view, title error, save failed
6. Tag and correspondent pickers: tags, tags no match, correspondent, picker load failed
7. Delete with undo: confirm, undo, failed
8. Select several: selecting, bulk change (tags), bulk delete confirm, done (undo)
9. Settings: view, sign out confirm
10. Updates (#13): up to date, update ready, downloading, refused, check failed
11. Unlock (#8): asking (system prompt), cancelled
12. Sign in: view, error

### Open questions for the user

1. **Offline list.** The list keeps no copy of the documents, so away from the home network
   or VPN it shows "Couldn't load". Should it cache the last list (titles and thumbnails) so
   it opens offline? Drawn: no cache.
2. **Document view lands on the PDF**, with details in a pull-up panel. Or would you rather
   land on the details with the PDF below? Drawn: PDF first.
3. **Uploads in the list.** Uploads in progress show as cards at the top of the document list
   until they finish or are cleared; there's no separate Uploads screen. OK?
4. **Unlock delay.** The app locks again after 5 minutes in the background, changeable in
   Settings (#8 owns it). Right default?
5. **Tokens.** Slate `#1b2229`, green `#5cbf8b`, Figtree, corners 14 / 20 / 28. Approve or
   nudge.

### Choices made on the user's behalf

- **Navigation:** one stack, no bottom bar (only two top-level places). Settings is the icon
  button on the Documents title row; Updates sits under Settings. Upload is the "Add" action
  button, opening a sheet with Scan / Take photo / Choose file.
- **Sign in** is drawn as a row too, because #6's last step re-themes today's login screen.
- **Filters** live in one sheet (tags as multi-select chips, correspondent via the picker,
  document type as chips); applied filters show as removable chips in the bar and a
  "Filters · 2" chip in the selected style. Sort is its own short sheet: Newest first (by
  document date, the default), Oldest first, Recently added, Title A–Z, Correspondent A–Z.
  While searching, sort shows "Best match".
- **Search results** show a two-line snippet with the match highlighted in the green tint.
- **Document view:** PDF fills the screen, a collapsed details panel (title, meta, tags, Edit,
  Download) pulls up to the full details list; Share is in the bar; Download, Open in
  Paperless and Move to trash are in the More menu. Non-PDF originals show Paperless's PDF
  with a "Get photo" banner. The list's metadata shows at once while the PDF downloads with a
  determinate bar.
- **Editing** is a full screen (not a sheet) with Cancel / Save pinned at the bottom; pickers
  are list sheets with search. Single-choice pickers close on tap; tags use checkboxes and a
  Done. New tags/correspondents are not created in the app (#11 says web UI): the no-match
  state links to Paperless.
- **Delete:** confirm dialog with a red filled button, then an 8-second snackbar with Undo
  (green). Failures show a snackbar with Try again and keep the document.
- **Bulk:** long-press starts selection; a floating bar with Tags / From / Type / Trash. The
  bulk tag sheet is tri-state (all / some / none) with "adding" marked. Storage path is not in
  the bulk bar (#11 lists tags, correspondent, type, delete).
- **Tag colours from Paperless are ignored**: tags are neutral pills so the one-green rule
  holds.
- **Destructive red moved** from `#e4695e` to `#ec7166` (red text on cards was 4.4:1, now
  4.9:1). Added tokens `on-destructive #1f0d0b`, `border-muted #2c363f`, `scrim`, `r-thumb
  10`. Menus and the bulk bar sit on `surface`, not `raised`, so red text on them passes.
- **Network problems are amber, refusals are red**: "couldn't reach" uses the warning colour;
  Paperless refusing a document uses destructive.
- **Spinners are `mfg`**; only determinate progress bars use green.
- **Figtree is bundled** in the APK (GrapheneOS without Play can't fetch downloadable fonts).
- **Icons:** Material Symbols Rounded, outlined. The canvas draws a simplified gear for
  Settings; the app uses `Icons.Rounded.Settings`.
- **Updates (#13):** copy distinguishes it needs the internet (GitHub), not the home network;
  a refused update says nothing changed and offers Download again plus the release page.
- **Unlock (#8):** the system BiometricPrompt over a plain lock screen; after cancel, one
  green Unlock button. Nothing of the documents shows behind it.
- **No offline column** on the canvas: with no cache, "can't reach the server" is the same as
  load failed (see open question 1).
