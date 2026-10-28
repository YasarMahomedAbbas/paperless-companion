---
name: implement-plan
description: >-
  Use to build an approved checklist from start-ticket — "implement the plan", "work through the checklist", "do checkpoint 2", or the pipeline's second stage. Works checkpoint by checkpoint: ticks tasks as they're done, runs the checkpoint's tests in Docker, commits each checkpoint via commit-code with its drafted message, and stops on scope creep or when the code contradicts the checklist.
---

# Implement Plan

`start-ticket` → **`implement-plan`** → `self-review` → `doc-drift` → `self-verification` →
`raise-pr` → `merge-release`

The checklist is the spec; the code is the ground truth. When they disagree, stop and say so
(with `file:line`) rather than build something else.

## 1. Find the checklist

```bash
git branch --show-current     # <type>/<n>-<slug>, never main
git status --short
```

Read `docs/tasks/<n>-<slug>/CHECKLIST.md` in full. Work from the file, not from memory of
the planning: this stage runs cold. Start at the first unticked task and say which.

Check it was committed (`git log origin/main..HEAD --oneline -- docs/tasks/<n>-<slug>/`). If
not, commit it on its own first.

## 2. Work each checkpoint

Implement each task as written: the file it names, the signature it gives, the file it says to
mirror. Match the surrounding code's naming, idiom, comment density and test structure.

Rules that bite here:

- **Layers** (`CLAUDE.md` § Layers). A screen sees only `domain/`. `domain/` names things in
  the app's words and imports no Android, HTTP or JSON. Every Paperless detail (paths,
  versions, field names, statuses) stays in `data/` and is mapped to a domain model there.
  `ArchitectureTest` checks the imports; don't loosen it.
- **Paperless calls** follow the current docs and the live schema, which the checklist cites.
  If the code needs a shape the checklist didn't cite, read the docs and the schema before
  writing it. Uploads send a `Content-Length`. A shape that differs between API v9 and v10 is
  read in both forms, with a test for each.
- **Secrets**: the token lives only in `KeystoreSettingsRepository`; never log it or put it in
  a URL.
- **No suppressions** to get green: no `@Suppress` dodging a lint, no `@Ignore`d test.

Tick each task `[x]` as it's done, with the Edit tool, one edit per task. If what you built
differs from the task's text, edit the text to match. Don't tick what you didn't verify: a
device-only step stays unticked for self-verification.

## 3. Verify the checkpoint

```bash
./build.sh ./gradlew --console=plain testDebugUnitTest --tests '<this checkpoint's tests>'
```

Fix failures before moving on.

## 4. Commit the checkpoint

`commit-code` with the checkpoint's drafted message (adjusted if the work drifted). It pushes.

## 5. Close out

After the last checkpoint, the full CI command:

```bash
./build.sh ./gradlew --console=plain testDebugUnitTest koverVerify lintDebug assembleDebug
```

Then append to `docs/tasks/<n>-<slug>/NOTES.md`:

```markdown
## Implementation

- Shipped: <substantive changes, with file:line>
- Tests: <actual numbers: N passed / N failed>
- Outstanding: <unticked tasks with reasons, or none>
- Contradicted the plan: <what the code disproved and how it was resolved, or none>
```

Commit it with the last checkpoint or as `docs(tasks): record #<n> implementation`.

## Guardrails

- Never implement on `main`, never an unapproved checklist.
- Scope creep stops work: explain, propose a split, wait.
- Report faithfully: failing output shown, skipped steps named.
