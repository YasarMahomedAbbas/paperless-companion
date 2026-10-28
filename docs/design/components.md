# Components

One recipe per part. Each lives once in `ui/components/` and screens pick a **variant**; a call
site never passes a colour, shape, elevation or font size (see `design-recipe` § 2). The
canvas board named after each recipe shows it drawn.

Values are tokens from `README.md`; sizes in dp.

## Buttons

| Variant | Look | Height | When |
| --- | --- | --- | --- |
| `Primary` | filled `primary`, label `on-primary` 600 | 52 (56 when it's the screen's only control) | the one main action: Save, Sign in, Install, Show 23 documents, Done |
| `Secondary` | 1dp `border` outline, label `fg` 500, transparent | 48–52 | the second choice: Cancel beside Save, Try again, Edit, Download |
| `Text` | no container, label `fg` 500 (600 when it closes a dialog's main path) | 40–48 | third choices and in-line actions: Retry, Open, Clear finished, Use PIN |
| `Destructive` | filled `destructive`, label `on-destructive` 600 | 48 | only as the confirm in a delete dialog |
| `DestructiveText` | no container, label `destructive` | 48 | "Move to trash" in a menu, Trash in the bulk bar |

All `r-ctl`. A full-width primary sits in the bottom action area; side by side, Cancel takes
its width and the primary takes the rest (`weight(1f)`). A busy primary keeps its size and
swaps its label for a 20dp spinner in `on-primary`.

## Icon button

48 × 48, fully round. `Plain` (transparent, icon `fg`) in top bars; `Tonal` (`surface`,
icon `mfg`) for the Settings button on the Documents title row. Active (menu open) gets a
`raised` circle. Always a content description.

## Action button (FAB)

Extended FAB, 56 high, `r-card`, `primary` / `on-primary`, a 22dp plus and the label "Add".
Bottom-right, 20 from the edge, 28 from the bottom; it moves up above a snackbar. One per
screen, only on Documents.

## Top bars

- **Large title** (top-level screen): 28/500 title left, tonal icon button right, padding
  12 / 20 / 8. Not collapsing.
- **Back bar** (pushed screen): back icon button, 22/500 title, optional actions right.
- **Viewer bar**: back, 18/500 title ellipsised to one line, Share and More icon buttons.
- **Selection bar**: close icon ("Stop selecting"), "3 selected" at 22/500, a "Select all"
  text button.
- **Form bar**: close icon ("Close without saving") and the title; the actions are at the
  bottom, not in the bar.

## Search field

52 high, fully round, `field` ground, a 20dp search icon in `mfg`, placeholder "Search your
documents". Focused: a 2dp `border` inner ring, the back arrow replaces the title row, and a
Clear icon button appears once there is text. Inside sheets it's 48 high on `raised`.

## Text field

Label above (14/500 `mfg`), then a 52-high `field` box, `r-ctl`, no outline, text 16 `fg`.

- Focused: 2dp `border` inner ring.
- Error: the label and a 2dp ring turn `destructive`, and an error line with a 16dp alert icon
  sits under it (13 `destructive`). The message says what to do: "A document needs a title."
- Password: an eye icon button at the end ("Show password").

## Picker field

Looks like a text field but is a button: the current value (or "None" in `mfg`) and a
chevron, or a calendar icon for dates. Tags show as pills inside it (`surface` pills on the
`field` ground), wrapping. Tapping opens the matching picker sheet or the M3 date picker.

## Chips

36 high, fully round, 14 text.

| Variant | Look | When |
| --- | --- | --- |
| `Action` | 1dp `border` outline, `fg` 500, optional 18dp leading icon | Filters, sort order |
| `Selected` | `primary-tint` ground, `primary` 500 text, a 16dp check | a chosen filter option, "Filters · 2" when filters are on |
| `Unselected` | 1dp `border` outline, `fg` 400 | an option you could choose |
| `Removable` | outline, `fg`, a trailing 16dp × in `mfg` | an applied filter in the bar (content description "Remove filter Bills") |

## Tags (display only)

Pills, not chips: 12–13 text, padding 3–4 × 9–10, fully round, `raised` ground with `mfg` text
in a list row; `fg` text in details. Up to two in a list row, then "+3". No per-tag colours:
Paperless tag colours are ignored so the one-green rule holds.

## Document row

The list's unit. `surface`, `r-card`, padding 12, 8 apart, 12 from the screen edge.

- 56 × 72 thumbnail (`r-thumb`), Paperless's own thumbnail image, cropped to fill.
- Title 16/500, one line, ellipsised.
- `Correspondent · 3 Oct 2026` in 14 `mfg`; just the date when there's no correspondent.
- Tag pills.
- **Search variant**: under the meta line, a two-line snippet (13 `mfg`) with each match in a
  `primary-tint` highlight on `fg` text.
- **Selectable variant**: a 24dp round check at the start, the thumbnail shrinks to 48 × 62.
  Selected: `primary-tint` ground, 2dp `primary` inner ring, filled `primary` check.
- Tap opens the document; long-press starts selection.

## Upload card

Same shell as a row without the thumbnail: a 24dp status mark, the file name (16/500, one
line), a status line, an optional text button.

| State | Mark | Status line | Button |
| --- | --- | --- | --- |
| Uploading / processing | spinner `mfg` | "Uploading…" / "Paperless is reading it…" | — |
| Added | `primary` tick in a `primary-tint` circle | "Added to Paperless" | Open |
| Refused by Paperless | alert in `destructive` | Paperless's reason, in `destructive` | Retry |
| Couldn't reach | triangle in `warning` | "Couldn't reach the server. Check you're on the home network or the VPN." in `mfg` | Retry |

Upload cards sit under an "Uploading" section heading at the top of the Documents list;
"Clear finished" clears the added and refused ones.

## Bottom sheet

`surface`, `r-sheet` top corners, 36 × 4 handle in `border`, title 20/500, padding 12 / 20 /
28, over the scrim. Short sheets (Add, Sort) hug their content; list sheets (pickers, filters,
bulk change) open from 64–150dp below the top and scroll inside, with their action bar pinned
to the bottom above a `border-muted` divider.

## Option list (in a sheet)

52–56 high rows, `r-ctl`, 16 text.

- **Single choice** (sort, correspondent, document type, storage path): the selected row gets
  `primary-tint` and a `primary` check at the end; tapping a row chooses and closes. "None"
  is the first row, in `mfg`.
- **Multiple choice** (tags): a 22dp checkbox, `r` 7. Checked: filled `primary` with an
  `on-primary` tick. Unchecked: 2dp `mfg` ring. Mixed (bulk only): `raised` with a `mfg` ring
  and a dash. Chosen items float to the top above a divider. A "Done" primary and "2 chosen"
  in the pinned bar.
- **Action rows** (Add sheet): a 48 round icon tile and a two-line label. The main option's
  tile is `primary-tint` with a `primary` icon; the rest `raised` with `mfg`.

## Dialog

`surface`, `r-sheet` on all corners, 24 from the screen sides, padding 24, title 20/500,
body 15 `mfg`. Buttons end-aligned: a text Cancel, then the confirm. A delete confirm uses the
`Destructive` button and names what goes ("'Electricity bill — October' goes to the
Paperless trash…"). Others use `Primary`.

## Menu

The More menu: `surface`, `r-card`, 244 wide, padding 8, anchored under its icon button.
Items 48 high, `r-ctl`, a 20dp `mfg` icon and 15 `fg` text. A divider in `border` before a
destructive item, which is `destructive` text and icon.

## Snackbar

`raised`, `r-ctl`, 12 from the sides, 28 from the bottom, text 15 `fg`, one text button at the
end. **Undo is `primary`**, because it's the one thing to do now; other actions ("Try again")
are `fg`. 8 seconds; Undo restores from the Paperless trash.

## Bulk action bar

Floats 24 above the bottom while selecting: `surface` with a 1dp `border` ring, `r-card`, four
equal 64-high buttons with a 22dp icon over a 13 label: Tags, From (correspondent), Type,
Trash (destructive). Replaces the action button.

## Details panel

A pull-up panel over the viewer: `surface`, `r-sheet` top corners. Collapsed (268 high): title
20/500, `Correspondent · date · type`, tags, then Edit and Download as two `Secondary`
buttons. Expanded: a definition list (label 14 `mfg` left, value 15 `fg` right, rows divided
by `border-muted`) with title, date, correspondent, document type, tags, storage path, added.

## Notice banner

In-flow, not floating: `raised`, `r-card`, padding 12–16, a 20dp icon, one or two lines and an
optional text button. Used for "This came in as a photo…" and "Couldn't save your changes".
Info icons `mfg`, problems `warning`.

## Status card (empty / error / done)

Centred in a `surface` card, `r-card`, 20 from the sides, padding 32 / 24:

1. A 64 circle in `raised` holding a 28dp icon: `mfg` for empty, `warning` for can't-reach,
   `destructive` for refused, `primary` on `primary-tint` for done.
2. One statement, 17/500.
3. One line of what to do, 14 `mfg`.
4. One `Secondary` button: the next action ("Add a document", "Try again", "Clear filters").

## Skeleton

The loaded screen with every shape replaced by a `.shim` block: `surface` → `raised` → `surface`
gradient sweeping every 1.8s, `r-thumb` corners (fully round for chips and tags). Same sizes,
same positions as the loaded content, so nothing jumps. No spinner with it.

## Progress

- **Indeterminate**: a 18–24dp circular spinner in `mfg` (list footer "Loading more", upload
  cards).
- **Determinate**: a 6–8 high bar, fully round, `raised` track, `primary` fill, with the
  amount underneath in 14 `mfg` ("4.4 of 6.8 MB").

## Switch

M3 switch: on is a `primary` track with an `on-primary` thumb; off is the M3 default with an
`mfg` outline. The whole row is the touch target and carries the label.

## Settings row

Inside a `surface` card, `r-card`: 64–72 high, 18 side padding, a 16/500 label and an
optional 14 `mfg` second line, ending in a chevron, a switch, an external-link icon or a
status pill (`primary-tint` / `primary`, "0.4.0 ready"). Rows in one card are divided by
`border-muted` inset 18.
