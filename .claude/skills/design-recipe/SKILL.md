---
name: design-recipe
description: >-
  The app's house style, applied. Use whenever you build, add, reshape, restyle or review anything a user sees in the app — a screen, a list, a row, a sheet, a dialog, a field, a button, an empty/loading/error state, or "make this look right". Use it even when the request doesn't mention design ("show the documents in a list", "add a delete confirm"), because every screen has to look like it belongs to the manual in docs/design/. Not for data-layer or domain work.
---

# Design recipe

The manual is `docs/design/` (README first: the rules). This skill is the workflow that
applies it in Compose. There is no wireframe step: the shapes are decided in the manual, so the
work is to name which shape a screen is and compose it from the app's own components.

Read `docs/design/README.md` once per session. Load other manual files only when a step asks.

## 1. Name the shape

In one line, before code: which screen shape this is (list, detail, viewer, form, sheet,
dialog, unlock) per `docs/design/layouts.md`. None of them → say so and propose the nearest;
it usually means the ticket wants two screens.

## 2. Inventory the parts

List the components the screen needs and, for each, its recipe in
`docs/design/components.md` and the composable in `ui/components/` that implements it. A call
site picks a variant and passes nothing that changes the look.

**About to pass a colour, shape, elevation, font size or padding that sizes a control into a
shared component? Stop.** That's a missing variant: add it to the component, record it in the
manual, then use it. Layout on the outside (`Modifier.fillMaxWidth()`, spacing between items)
is fine.

## 3. Build with the values

Everything from the theme (`ui/theme/`): colours from the colour scheme, never a `Color(0x…)`
outside the theme; shapes from the shape scale (the house corners are round, per the manual);
type from the typography scale; spacing from the manual's steps; Material Symbols icons at the
manual's sizes. One theme only: no `isSystemInDarkTheme()` branches, no light/dark variants.

Ship the three states with the happy path: a skeleton that mirrors the loaded layout, an empty
state with one next action, an error state with Try again.

## 4. Check it

Run `docs/design/checklist.md` against the code and fix what fails. Check `@Preview`s exist
for each state, so the screen can be looked at in Android Studio.

## 5. Record

A decision the manual doesn't cover, or a component that doesn't match its recipe → a line in
`docs/design/README.md` § Open decisions, in the same commit.
