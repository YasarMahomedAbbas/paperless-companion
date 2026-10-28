# Checklist

Run this against the code before a screen is done (`design-recipe` § 4). Every line is a yes;
a no is fixed or recorded in `README.md` § Open decisions in the same commit.

## Theme and tokens

- [ ] No `Color(0x…)`, `RoundedCornerShape(n.dp)`, `fontSize =` or `.sp` outside `ui/theme/`
      and `ui/components/` (document thumbnails and rendered pages excepted).
- [ ] No `isSystemInDarkTheme()`, no `dynamic*ColorScheme`, no light/dark branch.
- [ ] Screens use `ui/components/` parts by variant; nothing passes a colour, shape, elevation
      or text size into a shared component.
- [ ] Containers set their colour explicitly; `tonalElevation` is 0.

## Colour

- [ ] At most **one** filled green button on the screen (the action button counts).
- [ ] Green appears only on: the primary action, selected chips / options / rows, checked
      boxes and switches, success marks, a snackbar's Undo, a determinate progress fill.
- [ ] Red appears only on destructive actions and field errors; amber only on network
      problems.
- [ ] No red text on `raised`; secondary text is `mfg`, never a lower alpha of `fg`.

## Shape and surface

- [ ] Buttons and fields `r-ctl`; cards, rows, menus and the action button `r-card`; sheets and
      dialogs `r-sheet`; chips, tags, the search field and icon buttons fully round.
- [ ] Surfaces step `bg` → `surface` → `raised`; no card on a card of the same tone.
- [ ] Shadows only on the action button, sheets, dialogs, menus, snackbars and the bulk bar.

## Type and space

- [ ] Figtree only, weights 400 / 500 / 600; sizes from the scale in `foundations.md`.
- [ ] Sentence case; no all-caps.
- [ ] Spacing on the 4dp grid; 20 page gutter for text, 12 for cards and rows.
- [ ] Long titles ellipsise on one line; nothing overflows at 360dp wide or at 1.3× font
      scale.

## States

- [ ] Loading is a skeleton with the loaded layout's shapes, not a centred spinner.
- [ ] Empty has one statement, one line, one action.
- [ ] Failure says what happened and what to do ("Check you're on the home network or the
      VPN") and has Try again; it never shows an HTTP code or exception text.
- [ ] Anything that writes (save, delete, bulk change) has a failure state that keeps the
      user's input.
- [ ] Delete is confirmed and offers Undo.
- [ ] A `@Preview` exists for every state, using the app theme.

## Access

- [ ] Every touch target is at least 48 × 48.
- [ ] Every icon-only button has a `contentDescription` that says the action.
- [ ] Decorative icons and thumbnails have `contentDescription = null`; the document row reads
      as one item ("Electricity bill — October, City Power, 3 October 2026, tags Bills, House").
- [ ] Selected and checked states are exposed to TalkBack (`selected`, `toggleableState`), not
      only drawn green.
- [ ] Text contrast is at least 4.5:1 (the token pairs in `foundations.md` already pass).
- [ ] Works with the keyboard up: forms scroll, the action area sits above the IME.

## Layers

- [ ] The screen imports `domain/` only; no `data/` class, URL, or Paperless field name
      (`ArchitectureTest` fails the build otherwise).
