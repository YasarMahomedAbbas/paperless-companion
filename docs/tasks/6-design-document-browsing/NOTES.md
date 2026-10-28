# Notes — #6 Design the document browsing app

Branch: `docs/6-design-document-browsing`

## Design

- **Canvas:** https://claude.ai/artifact/YPcrFxT1joGdxmBHfiMdf6 (private until shared from its
  Share menu; 46 boards, 412 × 915 each)
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
4. Document view: view, full screen, details, more menu, loading, load failed, photo original
5. Edit details: view, title error, save failed
6. Tag and correspondent pickers: tags, tags no match, correspondent, picker load failed
7. Delete with undo: confirm, undo, failed
8. Select several: selecting, bulk change (tags), bulk delete confirm, done (undo)
9. Settings: view, sign out confirm
10. Updates (#13): up to date, update ready, downloading, refused, check failed
11. Unlock (#8): asking (system prompt), cancelled
12. Sign in: view, error

### Review (2026-10-28)

The user's answers to the open questions; the canvas sticky is now a green "Decided" note and
`docs/design/README.md` § Decisions records them.

1. **Offline list:** no cache, as drawn.
2. **Document view:** keep landing on the PDF with quick actions, and add full screen. The
   details panel now has three stops: swipe down for full screen (bar and panel gone, a handle
   tab left), up for quick actions, up again for all details. New board "Document — full
   screen"; the hint reads "Swipe up for all details, down for full screen".
3. **Uploads in the list:** as drawn.
4. **Unlock delay:** 5 minutes, as drawn.
5. **Tokens:** as drawn.

Also from the review: the Settings button's simplified gear read as a sun. The ten Documents
boards that show it now draw a proper gear.

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
- **Icons:** Material Symbols Rounded, outlined. Settings is `Icons.Rounded.Settings` (a
  gear; the canvas's first, simplified gear looked like a sun and was replaced).
- **Updates (#13):** copy distinguishes it needs the internet (GitHub), not the home network;
  a refused update says nothing changed and offers Download again plus the release page.
- **Unlock (#8):** the system BiometricPrompt over a plain lock screen; after cancel, one
  green Unlock button. Nothing of the documents shows behind it.
- **No offline column** on the canvas: with no cache, "can't reach the server" is the same as
  load failed (see open question 1).

## Implementation

After the canvas was approved, the theme was built and applied to today's two screens
(`design-recipe`, checked against `docs/design/checklist.md`). No CHECKLIST.md for a design
ticket; three checkpoints:

1. **Theme** — `8e1cd14` feat(ui): add the house theme: slate and green tokens, Figtree, corners.
   `ui/theme/`: `Color.kt` (the README tokens as one dark `ColorScheme` with every slot set,
   `ExtendedColors.warning`), `Type.kt` (the foundations.md scale plus `cardTitle`,
   `primaryButton`, `lead`), `Shape.kt` (r-thumb / ctl / card / sheet), `Spacing.kt`, and
   `Theme.kt` with no dark branch and no dynamic colour. Figtree 400 / 500 / 600 bundled in
   `res/font/` as static instances cut (fontTools instancer) from the Google Fonts variable
   font (`google/fonts` `ofl/figtree`); OFL in `assets/licenses/Figtree-OFL.txt`. The window
   background and system bars are dark whatever the system setting. Tests: `ThemeTest` (the
   foundations.md contrast table within 0.06, surfaces step up, Figtree 400/500/600 only) and
   `DesignRulesTest` (no dark/dynamic branch anywhere; no `Color(0x`, `RoundedCornerShape(n`,
   `fontSize =` or `.sp` outside `ui/theme` and `ui/components`).
2. **Components** — `7ff7973` feat(ui): add the shared components the sign-in and home screens need.
   `ui/components/`: `AppButton` (Primary / Secondary / Text / Destructive / DestructiveText,
   busy), `AppTextField` (label, focus / error rings, error line, password eye),
   `NoticeBanner` (Info / Unreachable / Refused), `UploadCard` (Working / Added / Refused /
   Unreachable), `LargeTitleBar`, `SectionHeading`, `AppIcons` (outlined glyphs core icons lack).
   Each has a `@Preview`; `ui-tooling-preview` added.
3. **Screens** — `964ae61` feat(ui): apply the house theme to the sign-in and home screens.
   Sign in per its canvas boards, with the failed-sign-in banner and the marked field
   (`LoginUiState.errorField`, `.unreachable`; tests in `LoginViewModelTest`). Home keeps its
   upload-first layout until #9, rebuilt from the components; `UploadState.Failed.unreachable`
   lets a "couldn't reach" upload show amber, not red (tests in `UploadDocumentUseCaseTest`).
   Previews for every state of both screens. Gaps the manual left are recorded in
   `docs/design/README.md` § Decisions.

**CI:** `./build.sh ./gradlew --console=plain testDebugUnitTest koverVerify lintDebug assembleDebug`
→ BUILD SUCCESSFUL (65 tests, 0 failures; lint has only the pre-existing dependency-version,
data-extraction and `mipmap-anydpi-v26` warnings). The APK carries the three Figtree files and
the licence.

**Deferred / not done:**

- The new Documents home, Add sheet, scanner-needs-camera dialog and the other canvas rows
  are #9, #10, #11, #13, #8; their components (search field, chips, rows, sheets, dialogs,
  snackbar, skeleton, status card, menu, switch) are built there, by the same recipes.
- Nobody has looked at the screens on a phone or in Android Studio's preview yet; the
  previews are there for that.
- `HomeScreen` still imports `data.UriDocuments` (it did before); the layer fix belongs to
  #14's `ArchitectureTest`.
- The launcher icon keeps its old `#17541F` background; the manual doesn't cover the icon.
