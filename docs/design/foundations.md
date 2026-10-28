# Foundations

The values every screen is built from. Token names are the ones in `README.md` § Tokens; in
Compose they come from `MaterialTheme` (colour scheme, typography, shapes) plus the app's own
`Spacing` and `ExtendedColors` in `ui/theme/`.

## Colour

One theme, a dark slate ground. Contrast, measured on the token values:

| Text | On | Ratio | Use it for |
| --- | --- | --- | --- |
| `fg` | `bg` / `surface` / `raised` | 13.3 / 11.9 / 10.5 | all body text |
| `mfg` | `bg` / `surface` / `raised` | 6.4 / 5.7 / 5.0 | secondary text, at any size |
| `primary` | `bg` / `surface` | 7.1 / 6.4 | green text: selected chip, Undo, "adding" |
| `on-primary` | `primary` | 7.9 | the filled button's label |
| `destructive` | `bg` / `surface` | 5.4 / 4.9 | red text: "Move to trash", field errors (not on `raised`: 4.3) |
| `on-destructive` | `destructive` | 6.4 | the filled red button's label |
| `warning` | `surface` | 7.6 | warning icons only, never body text |

Rules of thumb:

- **Green means "this one" or "go".** The single filled button, the selected chip / option /
  row, a checked box, a switch that's on, a success tick, the Undo in a snackbar, a progress bar
  you started. Nothing else.
- **Red means "this destroys something"** or "this field is wrong". A red icon at rest is
  wrong.
- **Amber (`warning`) is the network.** The no-connection icon on error cards, and the triangle
  on an upload that couldn't reach the server. Paperless refusing a document is red; the phone
  not reaching Paperless is amber.
- **Selected things use the tint, not a fill**: `primary-tint` behind `primary` text, so a
  selected chip never competes with the filled button.
- **Document imagery is not themed.** Thumbnails and rendered pages are shown as they are, on
  a light page; that is the only light area in the app and it should be.

## Type

One family, **Figtree**, bundled in the APK (`res/font/`), not a downloadable font: GrapheneOS
phones without Google Play can't fetch downloadable fonts. Weights 400, 500 and 600 only.

| Role | M3 slot | Size / line | Weight | Used for |
| --- | --- | --- | --- | --- |
| Screen title | `headlineSmall` | 28 / 34 | 500 | "Documents", the large title of a top-level screen; −0.01em tracking |
| Version, big number | `titleLarge` | 24 / 30 | 500 | "Version 0.4.0", "Paperless Scan is locked" |
| Bar title | `titleMedium` (custom 22) | 22 / 28 | 500 | the title in a back-arrow top bar |
| Sheet / dialog title | `titleSmall` (custom 20) | 20 / 26 | 500 | "Add a document", "Move to trash?" |
| Viewer bar title | — | 18 / 24 | 500 | the document title over the PDF, one line, ellipsised |
| Card title | `bodyLarge` + 500 | 16–17 / 22 | 500 | row titles, card headings, empty-state statement |
| Body | `bodyLarge` | 16 / 24 | 400 | field values, option labels |
| Body small | `bodyMedium` | 14–15 / 21 | 400 | secondary lines, explanations, dialog text |
| Label | `labelLarge` | 14–15 / 20 | 500 | button labels (600 on the filled button), section headings in `mfg` |
| Caption | `bodySmall` | 13 / 19 | 400 | hints under a sheet, search snippets |
| Tag | `labelSmall` | 12–13 / 16 | 400 | tag pills in rows and details |

- Sentence case everywhere, including buttons and titles. No all-caps.
- Section headings ("Tags", "Security", "Uploading") are 14 / 500 in `mfg`, not bold `fg`.
- Numbers that change (counts, sizes) use the same face; no tabular figures needed.

## Shape

| Token | dp | M3 slot | On |
| --- | --- | --- | --- |
| `r-thumb` | 10 | `extraSmall` | thumbnails, skeleton blocks, the checkbox (7) |
| `r-ctl` | 14 | `small` | buttons, fields, menu items, options, snackbars |
| `r-card` | 20 | `medium` | cards, document rows, menus, the action button, notice banners |
| `r-sheet` | 28 | `large` / `extraLarge` | bottom sheets (top corners), dialogs (all corners) |
| full | 999 | `CircleShape` / `RoundedCornerShape(50)` | chips, tags, the search field, icon buttons, switches, avatars |

Rendered PDF pages keep a near-square 6dp corner: they are paper, not app chrome.

## Space

A 4dp grid. The steps in use: **4, 8, 12, 16, 20, 24, 32, 40**.

- **Page gutter 20** for text, headers and fields; **12** for cards and rows, so a card's
  content lines up close to the text above it.
- Between rows in a list: **8**. Inside a row or card: **12–16** padding.
- Between a field's label and the field: **6**; between fields: **18**.
- Section heading to its card: **8**; between sections: **24**.
- Bottom action area: **16** top, **28** bottom (40 on screens with one big button, so it
  clears the gesture bar).
- Status-bar inset and the IME are handled with `safeDrawingPadding()` / `imePadding()`, never
  hard-coded.

## Elevation

Flat by default. Separation is the `bg` → `surface` → `raised` step.

| Thing | Shadow |
| --- | --- |
| Action button | `0 6 20 / 35%` (M3 level 3) |
| Bottom sheet | `0 -8 30 / 35%` |
| Dialog, menu | `0 12 40 / 45%` |
| Snackbar, bulk action bar | `0 8 24 / 40%` |
| A rendered PDF page | `0 4 18 / 30%`, so white paper sits on slate |

Nothing else has a shadow, and nothing has a tonal-elevation tint: set `tonalElevation = 0` and
pick the container colour explicitly.

## Icons

Material Symbols **Rounded**, outlined (fill 0), weight 400, from
`androidx.compose.material.icons` (`Icons.Rounded.*`) or the Symbols font.

- **22–24dp** in bars and buttons, **20dp** in fields and rows, **16–18dp** inside chips and
  small buttons, **28dp** in the 64dp circle on empty / error cards.
- Icons at rest are `mfg`; icons that are the button (back, share, more) are `fg`.
- Every icon-only button has a `contentDescription` that says what it does ("Back", "Share",
  "Clear", "Stop selecting"); decorative icons pass `null`.

## Motion

Material's defaults, nothing bespoke: sheets slide up, dialogs fade and scale, the list
animates item removal on delete. The skeleton shimmer runs 1.8s linear and stops when the
system's "remove animations" setting is on.

## Copy

- Say what happened and what to do, in the user's words: "Couldn't load your documents. Check
  you're on the home network or the VPN, then try again." Never an HTTP status or exception.
- Name the server as "Paperless" and the phone as "this phone". "Trash", not "delete", for what
  Paperless can restore.
- Buttons are verbs on the thing: "Move to trash", "Install 0.4.0", "Show 23 documents".
- Errors that come from Paperless quote its reason when it gives one ("it's a duplicate of
  'Electricity bill — October'").
