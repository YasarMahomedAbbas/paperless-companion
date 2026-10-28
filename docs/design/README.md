# Design manual

The house style, written as recipes, so every screen built without a designer in the room
still looks like one app. The `design-recipe` skill applies it in code; `design-canvas` draws
it as mockups.

**Status: approved on the #6 canvas review (2026-10-28).** The rules and tokens below are what
the #6 design canvas is drawn with and what `ui/theme/` implements; the shared parts live in
`ui/components/`. #9, #10 and #11 build on that theme.

| File | What it holds |
| --- | --- |
| `README.md` (this) | the rules and the tokens |
| [`foundations.md`](foundations.md) | colour, type, shape, space, elevation, icons, motion, copy: the values and how to use them |
| [`components.md`](components.md) | one recipe per part: buttons, fields, rows, chips, sheets, dialogs, snackbars, states |
| [`layouts.md`](layouts.md) | the screen shapes and the navigation between screens |
| [`checklist.md`](checklist.md) | what to check before a screen is done |

Canvas: https://claude.ai/artifact/YPcrFxT1joGdxmBHfiMdf6 (one row per screen, one board per
state).

## The style in one paragraph

A calm slate ground with **one** accent, a soft green, spent sparingly: the main action, the
selected thing, the "it worked". Corners are round everywhere: soft on what you press, rounder
on the panels around it, fully round on chips and the search field. Surfaces are flat and step
up in one order (page → card → field), separated by tone rather than lines or shadows. One
typeface, Figtree, in three weights. Space is generous, so a list of documents reads at a
glance.

## The rules

1. **One theme.** No light and dark variants, no `isSystemInDarkTheme()` branch, no Material
   You dynamic colour. The app always looks the same.
2. **Tokens only.** Every colour is a theme colour; no `Color(0x…)` outside `ui/theme/`. The
   one exception is document imagery (thumbnails, rendered pages), which is the document's own.
3. **One green.** The accent marks the one main action on a screen, the selected chip, row or
   option, and success. Not body text, not icons at rest, not decoration. Two filled green
   buttons on one screen means one is wrong.
4. **Surfaces step up in one order**: page (`bg`) < card (`surface`) < field and raised parts
   (`raised`). Never a card on a card at the same tone.
5. **Round corners**: controls (buttons, fields, menu items) `r-ctl`; cards, rows and the
   action button `r-card`; sheets `r-sheet` on their top corners, dialogs on all four; chips,
   tags, the search field and icon buttons fully round. Nothing sharp.
6. **Flat.** Separation is by tone. Shadows only on what floats: the action button, sheets,
   dialogs, menus, snackbars.
7. **One button weight per role**: one filled green primary; secondary is an outline in the
   border colour; anything further is a text button. Destructive is red and always confirmed
   (with Undo where the server can restore it).
8. **Every data screen ships three more states**: a skeleton shaped like the loaded screen,
   an empty state with one next action, and an error with Try again that says to check the
   home network or VPN.
9. **Touch targets are at least 48dp**, and every icon-only button has a content description.

## Tokens

| Token | Value | Compose (`ColorScheme` / theme) | Use |
| --- | --- | --- | --- |
| `bg` | `#1b2229` | `background`, `surface` | the page ground (slate) |
| `surface` | `#222b33` | `surfaceContainer` | cards, rows, sheets, dialogs, menus |
| `raised` / `field` | `#2a343d` | `surfaceContainerHigh` | fields, tags, snackbars, the skeleton highlight |
| `fg` | `#e6ebe8` | `onBackground`, `onSurface` | text, icons you can press |
| `mfg` | `#9aa6a1` | `onSurfaceVariant` | secondary text, icons at rest, spinners |
| `primary` | `#5cbf8b` | `primary` | the accent (green) |
| `on-primary` | `#0e1a13` | `onPrimary` | text and icons on the accent |
| `primary-tint` | `primary` at 14% (`#5cbf8b24`) | `primaryContainer` | selected chip, row, option; search match |
| `border` | `#36424c` | `outline` | outlines, the sheet handle, focus ring |
| `border-muted` | `#2c363f` | `outlineVariant` | dividers inside a card |
| `destructive` | `#ec7166` | `error` | delete, errors at the field |
| `on-destructive` | `#1f0d0b` | `onError` | text on a filled red button |
| `warning` | `#e2b65c` | `ExtendedColors.warning` | can't reach the server, unsaved, warning icons |
| `scrim` | `#080c0f` at 60% | `scrim` | behind sheets and dialogs |
| `r-ctl` / `r-card` / `r-sheet` | 14 / 20 / 28 dp | `Shapes.small` / `.medium` / `.large` | corners |
| `r-thumb` | 10 dp | `Shapes.extraSmall` | thumbnails, skeleton blocks |
| Type | Figtree 400 / 500 / 600, bundled | `Typography` | one family; scale in `foundations.md` |

Changes from the first proposal: `destructive` moved from `#e4695e` to `#ec7166` so red text
passes 4.5:1 on `surface` (it was 4.4:1); `on-destructive`, `border-muted`, `scrim` and
`r-thumb` were added because the canvas needed them.

These live in `.claude/skills/design-canvas/assets/artboard.dc.html` for the canvas and in
`ui/theme/` for the app; change both together.

## Decisions

Settled by the user on the #6 canvas review (2026-10-28); the canvas's green sticky lists them.
A screen that can't follow the manual records why here.

- **Offline list.** The list keeps no copy of the documents, so away from the home network or
  VPN it shows the load-failed state. No cache.
- **Document view lands on the PDF** with quick actions in the details panel. The panel has
  three stops: swipe down for full screen, up for quick actions, up again for all details
  (`components.md` § Details panel).
- **Uploads live in the list**, as cards at the top until they finish or are cleared, with no
  separate Uploads screen.
- **Unlock again after 5 minutes** in the background (#8 owns the behaviour), changeable in
  Settings.
- **Token values** (slate, green, Figtree, corners) as in the table above.

Settled while building the theme (#6), where the manual left a range or a gap:

- **Type slots.** `bodyMedium` is 14 / 21 (secondary lines); explanations under a screen title
  use the theme's `lead` (15 / 22). `labelLarge` is 15 / 500 for button labels and the filled
  buttons use `primaryButton` (16 / 600); `labelMedium` (14 / 500) is field labels and section
  headings. `cardTitle` is 16 / 22 at 500. Every other M3 slot is Figtree at 400 or 500 so
  nothing falls back to Roboto.
- **Notice banner tones** follow the colour rules rather than "problems are amber": `Info`
  (info icon, `mfg`), `Unreachable` (no-connection icon, `warning`), `Refused` (alert,
  `destructive`). A wrong password is a refusal; a server the phone can't reach is the network.
- **Icons** that `material-icons-core` lacks (eye, no-connection, alert, document, triangle)
  are drawn in `ui/components/AppIcons.kt` from the canvas's strokes; the extended icon set
  would add megabytes to an unminified APK.
- **Today's home** keeps its upload-first layout until #9 makes Documents the home: Scan
  document is its one primary, Take photo and Choose file are secondaries, the uploads sit
  under an "Uploads" heading, and the scanner's camera message is an `Info` notice banner.
- **Sign in** uses the 20 page gutter (the canvas drew 24) and its field's own label is drawn
  inside the field's decoration so TalkBack reads it with the field.

Settled when #6 met #8 and #13 on `main`:

- The sign-in screen's "sign in again" notice (#8) is an `Info` notice banner, not error text:
  it explains why the app is back at sign-in and refuses nothing.
- The home's ⋮ menu (Updates, Lock with fingerprint, Sign out) moved into the large title bar
  as its only action, with the stock Material 3 dropdown until the manual has a menu recipe.
- The Updates screen (#13) and the lock screen (#8) are still stock Material 3 parts on the new
  theme; restyling them with `ui/components/` is follow-up work. The Updates screen has no
  skeleton or separate empty state: the offer is stored on the phone, so nothing loads, and
  "Up to date" is the empty state. A failed *Check now* is its error, with *Check now* as Try
  again; it says to check the connection rather than the home network or VPN, since GitHub
  isn't on the home network.

Settled while building the Documents list (#9):

- **More drawn icons.** Search, close, back, plus, chevron, the Settings gear, filters, sort,
  scan, camera, file, person and server are drawn in `AppIcons.kt` from the canvas's strokes,
  for the same reason as the first set.
- **The search row drops its tags.** A row's search variant shows the two-line snippet in the
  tags' place, as the "Search — searching" board draws it.
- **A failed next page** is an `Unreachable` notice banner at the foot of the list with Try
  again, so the rows already loaded stay put.
- **Documents is the home**, replacing the upload-first home above: Scan document, Take photo
  and Choose file moved into the Add sheet, and the ⋮ menu is gone (its Updates, fingerprint
  switch and Sign out are in Settings).
- **Settings is partial.** It shows the server address, the fingerprint switch, Updates and
  Sign out. "Signed in as", the lock-again delay (the code still locks after 1 minute) and
  Source and licences from `layouts.md` § Settings are follow-ups.

Settled while building the viewer (#10):

- **New type slots.** The viewer bar's 18/500 title is `viewerTitle` (18 / 24) and a status
  card's 17/500 statement is `statement` (17 / 24), both in `ui/theme/Type.kt`.
- **Quick actions showed Download alone** until #11 added Edit beside it (below); the More menu holds Download and
  Open in Paperless. The menu is the stock M3 `DropdownMenu` on `surface`, `r-card`, 244 wide,
  with `MenuItem` rows (48 high, 20dp `mfg` icon, 15 `fg` text).
- **The photo-original notice** sits in the quick actions under the hint, and the quick actions
  scroll when it doesn't fit in 268. An original that is neither a PDF nor an image gets the
  same banner with "Get original". When the preview itself isn't a PDF (no archive version),
  the page area shows a "Can't show this file here" status card with Download instead.
- **Viewer failures** use fixed copy, never the server's text: "Can't reach Paperless" / "Check
  you're on the home network or the VPN." (warning) or "Couldn't load this document" (refused),
  each with Try again. A details failure is a compact notice banner inside the panel.
- **The full-screen tab** is the panel's own handle shrunk to 120 × 48; the rest of the panel
  slides below the screen edge. The page pill ("Page 1 of 3") is a `raised` fully round pill at
  the top centre of the pages, 13 `fg`.

Settled while building Organise documents (#11):

- **Picker field** gained what the Edit details form needs: an optional label above it (read
  with the value by TalkBack as one button), tags as wrapping pills (`TagPill` with `onRaised`,
  a `surface` pill on the `field` ground), and a `Date` variant with a calendar icon in place of
  the chevron. While the labels load, a set field reads "Loading…"; if they fail, "Unknown".
- **Edit and Download** sit side by side as two `Secondary` buttons in the quick actions and at
  the foot of all details; Edit is disabled until the document has loaded.
- **Picker sheets** share one load-failed card ("Couldn't load the choices", Try again) and one
  no-match card ("No tag called '…'", with Open Paperless as its `Secondary` button). A picker
  with nothing in it at all says "No tags yet". The tags picker pins the tags chosen when it
  opened above the divider, so a tick never moves the row under the finger.
- **Mixed checkbox** (`MixedChoiceRow`) is a `raised` box with a 2dp `mfg` ring and a 10 × 2
  dash, exposed to TalkBack as a tri-state checkbox.
- **The date picker** is Material's own `DatePickerDialog` on `surface`, `r-sheet`, with text
  OK and Cancel.
- **Discard changes?** reads "Your changes to this document haven't been saved." with Keep
  editing and a `DestructiveText` Discard.
- **Move to trash** is the More menu's last item, after a `border` divider, in `destructive`;
  it is disabled until the document has loaded. Its confirm is an `AppDialog` with the
  `Destructive` button. A trash that fails stays on the viewer as a snackbar.
- **The Undo snackbar** shows on the list, not the viewer (the viewer closes on success). It has
  no time-out of its own: the app's 8-second offer decides when it goes, and swiping it away
  ends the offer. The Add button rises 64 above it while it shows.
- **Selecting** keeps the search field and chips under the selection bar; only the title row
  gives way. A tap toggles a row, a long-press on a row while selecting toggles it too, and
  Back stops selecting before it stops searching. The bulk bar sits 12 from the sides (the
  card gutter), 24 above the bottom, with a 6dp shadow, and is disabled while a change is on
  its way.
- **Bulk sheets.** The bulk Tags sheet is titled "Tags for 3 documents", has no chosen-first
  divider (the marks are mixed), and its pinned bar counts the tags changed ("1 tag changed")
  beside Done. From and Type reuse the single-choice picker with nothing marked, since the
  selected documents differ; "None" clears it on all of them.
- **Bulk trash** confirms with "Move 3 documents to trash?" and "They go to the Paperless trash.
  You can bring them back from there." (the titles aren't listed). Its Undo is the same
  snackbar, "3 moved to trash".
- **Snackbars** can be swiped away sideways (`AppSnackbarHost`).

Settled while building saved views, notes and more like this (#12):

- **View chips come first** in the Documents chip row: each home (dashboard) view is an
  `Unselected` chip, `Selected` while it's the chosen one; tapping the chosen one clears it. A
  chosen view that isn't a home view shows as a `Selected` chip right after them ("Stop showing
  …" to TalkBack). Then a **Views** `Action` chip, then Filters, the filter chips and the sort.
- **The Views chip** has a bookmark icon drawn in `AppIcons.kt` (`Views`), and shows only when
  the server has saved views. It opens the short **Saved views** sheet: single-choice rows, the
  quick-list (sidebar) views first in the server's order, then a `border-muted` divider and an
  "Other views" section heading over the rest by name. The views load with the labels; when
  they fail the list still works, just without view chips.
- **A view's own order** reads "View's order" on the sort chip until a sort is picked. Choosing
  a view clears the filters; filters and search then narrow it.
- **An empty view** says "Nothing in <view>" / "No document is in this saved view right now."
  with "Show all documents", which clears the view.
