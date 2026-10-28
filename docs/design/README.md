# Design manual

The house style, written as recipes, so every screen built without a designer in the room
still looks like one app. The `design-recipe` skill applies it in code; `design-canvas` draws
it as mockups.

**Status: first pass.** The rules below are settled with the user. The token values are a
first proposal; #6 settles them on the design canvas and adds `foundations.md`,
`components.md`, `layouts.md` and `checklist.md`.

## The style in one paragraph

A calm slate ground with **one** accent, a soft green, spent sparingly: the main action, the
selected thing, the "it worked". Corners are round everywhere: soft on what you press, rounder
on the panels around it, fully round on chips and the search field. Surfaces are flat and step
up in one order (page → card → field), separated by tone rather than lines or shadows. One
typeface, in two or three weights. Space is generous, so a list of documents reads at a glance.

## The rules

1. **One theme.** No light and dark variants, no `isSystemInDarkTheme()` branch. The app
   always looks the same.
2. **Tokens only.** Every colour is a theme colour; no `Color(0x…)` outside `ui/theme/`.
3. **One green.** The accent marks the one main action on a screen, the selected chip or tab,
   success. Not body text, not icons at rest, not decoration. Two filled green buttons on one
   screen means one is wrong.
4. **Surfaces step up in one order**: page (`bg`) < card (`surface`) < field and raised parts
   (`raised`). Never a card on a card at the same tone.
5. **Round corners**: controls (buttons, fields) `r-ctl`; cards and rows `r-card`; sheets
   `r-sheet` on their top corners; chips, the search field and icon buttons fully round.
   Nothing sharp.
6. **Flat.** Separation is by tone. Shadows only on what floats: the action button, sheets,
   dialogs, menus.
7. **One button weight per role**: one filled green primary; secondary is an outline in the
   border colour; anything further is a text button. Destructive is red and always confirmed
   (with Undo where the server can restore it).
8. **Every data screen ships three more states**: a skeleton shaped like the loaded screen,
   an empty state with one next action, and an error with Try again that says to check the
   home network or VPN.
9. **Touch targets are at least 48dp**, and every icon-only button has a content description.

## Tokens (first proposal)

| Token | Value | Use |
| --- | --- | --- |
| `bg` | `#1b2229` | the page ground (slate) |
| `surface` | `#222b33` | cards, rows, sheets |
| `raised` / `field` | `#2a343d` | fields, tags, the skeleton highlight |
| `fg` | `#e6ebe8` | text |
| `mfg` | `#9aa6a1` | secondary text, icons at rest |
| `primary` | `#5cbf8b` | the accent (green) |
| `on-primary` | `#0e1a13` | text on the accent |
| `primary-tint` | `primary` at 14% | selected chip, selected row |
| `border` | `#36424c` | outlines, the sheet handle |
| `destructive` | `#e4695e` | delete, errors at the field |
| `warning` | `#e2b65c` | can't reach the server, unsaved |
| `r-ctl` / `r-card` / `r-sheet` | 14 / 20 / 28 dp | corners |
| Type | Figtree 400 / 500 / 600 | one family |

These live in `.claude/skills/design-canvas/assets/artboard.dc.html` for the canvas and in
`ui/theme/` for the app; change both together.

## Open decisions

- The token values, the typeface and the screen shapes: settled by #6.
