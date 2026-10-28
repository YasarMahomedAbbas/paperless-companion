# Layouts

The screen shapes the app is made of, and how the screens connect. Name the shape before
building a screen (`design-recipe` § 1); a screen that fits none of them is usually two
screens. Phone portrait only: 360–412dp wide, drawn at 412 × 915 on the canvas.

## Navigation

```
Unlock (#8) ──► Documents (home) ──► Document view ──► Edit details ──► pickers (sheets)
                  │   │   │               │
                  │   │   │               └─ More menu: Download · Open in Paperless · Move to trash
                  │   │   └─ Add (sheet): Scan · Take photo · Choose file
                  │   └─ long-press: Selecting ──► bulk change sheet / bulk trash dialog
                  └─ Settings ──► Updates (#13)
Sign in ──► Documents          Share to the app ──► (Unlock) ──► Documents, uploading
```

- **One stack, no bottom bar.** There are only two top-level places (Documents and Settings),
  so a navigation bar would be mostly empty; Settings is the icon button on the Documents
  title row. Back always goes up the stack.
- **Documents is home.** Upload stops being the whole home screen and becomes the Add action
  on it; uploads in progress show as cards at the top of the list.
- **Sheets for choices, screens for work.** Picking (add, sort, filter, tags, correspondent,
  bulk change) is a sheet over the screen it changes. Editing a document is its own screen,
  because it has several fields and a Save.
- **Shared files** go straight to upload: the app opens on Documents with the new upload card
  at the top (after the unlock prompt when #8 is on).
- Signed out, the app opens on Sign in; signed in with fingerprint unlock on, it opens on
  Unlock.

## List — Documents

```
status bar
Large title bar ............ "Documents"            (Settings)
Search field
Chip row ................... [Filters] [Newest first] [Bills ×] …
(Uploading section) ........ upload cards
Result line ................ "23 documents" — only when searching or filtering
Document rows, infinite scroll, pull to refresh
                                               [+ Add]  ← action button
```

- Infinite scroll: the next page loads when the last five rows come into view; a footer row
  (one skeleton row and "Loading more") shows while it does.
- Pull to refresh reloads from the top.
- **Searching** replaces the title row with a back arrow and the focused search field; the
  chip row stays, sort switches to "Best match", and rows gain their snippet.
- **Selecting** replaces the title row with the selection bar and the action button with the
  bulk action bar.
- States: view · loading more · empty (one action: Add a document) · loading (skeleton) ·
  load failed (Try again). Searching adds no results (Clear filters).

## Viewer — Document view

```
Viewer bar ................. (←) Title…                      (share) (⋮)
Rendered PDF page(s), pinch to zoom, page pill "Page 1 of 2"
Details panel (quick actions, 268) ... title / meta / tags / [Edit] [Download]
```

- The PDF fills the space between the bar and the panel; pages are white paper on `bg` with
  a small shadow.
- The details panel has three stops: swipe down for **full screen** (bar and panel gone, the
  pages edge to edge with a 16 gutter, a handle tab at the bottom), up for **quick actions**,
  up again for **all details**. Back from full screen returns to quick actions, then to the
  list.
- The title, meta and tags come from the list, so the panel shows at once; only the PDF
  loads (determinate bar with MB).
- A non-PDF original shows Paperless's PDF version with a notice banner and a "Get photo"
  button for the original.
- States: view · full screen · details expanded · more menu · loading · load failed (panel
  still shows) · photo original.

## Form — Edit details

```
Form bar ................... (×) Edit details
Fields, 18 apart: Title · Date · Correspondent · Document type · Tags · Storage path
Bottom action area ......... [Cancel] [      Save      ]
```

- The action area is pinned above the keyboard (`imePadding`).
- Save stays enabled; validation runs on Save and marks the field. A failed save keeps every
  change and shows a notice banner at the top; the button becomes "Save again".
- Closing with unsaved changes asks "Discard changes?" (a dialog, same recipe as delete but
  with Discard as a `Text` button in `destructive`).
- States: view · field error · save failed.

## Sheet

Opened over the screen that owns it, which stays drawn under the scrim.

- **Short sheet** (Add, Sort): hugs its content from the bottom.
- **List sheet** (Filters, Tags, Correspondent, bulk change): from 64–150 below the top,
  title, optional search field, a scrolling list, a pinned action bar.
- Every list sheet has a no-match state ("No tag called 'warranty'" with Open Paperless,
  because new tags are made in the web UI) and a load-failed state.

## Dialog

Centred card over the scrim, for a decision that can't be undone by Back: delete, bulk
delete, sign out, the Play-services camera notice. Never for information alone (use a notice
banner or a snackbar).

## Settings

```
Back bar ................... (←) Settings
Section heading + card ..... Server: address, signed in as
Section heading + card ..... Security: fingerprint switch, lock-again delay
Section heading + card ..... App: Updates (status pill), Source and licences
[Sign out] (secondary, centred)
```

## Status — Updates (#13)

A back bar and one status card or release card, with the one action in the bottom action
area. States: up to date (Check now) · update ready (release notes, Install) · downloading
(progress and the three steps) · refused (reason, Download again) · check failed (needs the
internet, Try again).

## Unlock (#8)

```
centred: 80 lock tile in `surface` · "Paperless Scan is locked" · one line
bottom: [  Unlock  ]   ← only after the prompt was cancelled
```

The Android `BiometricPrompt` is the system's own sheet; the app only draws the screen behind
it. Nothing of the documents shows behind the lock.

## Sign in

Logo tile, the app's name at 28/500, one line about the network, the three fields, a note
that only a token is kept, and the full-width Sign in in the bottom action area. A failure
shows a notice banner over the fields and marks the field it's about.
