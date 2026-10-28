---
name: start-ticket
description: >-
  Use to plan a ticket before any code — "start #9", "plan this ticket", "write the checklist for #9", or the pipeline's first stage. Reads the issue and its comments, explores the code, the Paperless API docs and the server's live schema, settles the approach (asking the user, or the pipeline), cuts the `<type>/<n>-<slug>` branch off origin/main, writes a checkpoint checklist with its test plan plus a NOTES stub into docs/tasks/<n>-<slug>/, commits and pushes them, and ends with a short brief for approval. Implements nothing.
---

# Start Ticket

First stage: **`start-ticket`** → `implement-plan` → `self-review` → `doc-drift` →
`self-verification` → `raise-pr` → `merge-release`

Produces the **checklist** and a **NOTES stub** on disk and a **brief** in chat. Paths are
relative to the repository root.

## 1. Read the ticket

```bash
gh issue view <n> --json number,title,body,comments,labels,url
```

Read the body and **every comment**: comments carry scope changes that supersede the body.
Pull out what is asked and why, the "Done when" criteria, what it `Needs:`, and anything
ambiguous. Ambiguity becomes an open question, never a guess.

If the branch already exists (re-planning), read `docs/tasks/<n>-<slug>/CHECKLIST.md` instead.

## 2. Explore

Enough to plan precisely, not the whole codebase.

- **`CLAUDE.md`**, in full: the layer rules (§ Layers), the Paperless API rule, build and test
  in Docker only, a bug fix comes with a failing test.
- **The Paperless API**, for every call the ticket adds or changes: the current docs
  (https://docs.paperless-ngx.com/api/), the changelog for version differences, and the live
  schema of the server (`<server>/api/schema/`; the address and a token are in
  `~/.config/paperless-companion/verify.env` as `PAPERLESS_URL` / `PAPERLESS_TOKEN`, if that
  file exists). Note each endpoint, method, field and the API version where a shape differs.
  Never plan a call from memory.
- **The code**: the files likely to change and the nearest analogue to mirror
  (`PaperlessApi.kt` + `PaperlessApiTest.kt` for a call, `UploadDocumentUseCase` for a use
  case, `HomeViewModel` + its test for a screen).
- **Anything the user sees**: read `docs/design/README.md` and name the screen's shape; the
  `design-recipe` skill is the full workflow. Visual decisions are already made there, so they
  are not questions for step 3.
- **Prior work**: `docs/tasks/` for an earlier checklist in the same area.

## 3. Settle the approach

Present, compactly: what you found (2–4 sentences), the **decision points** (where more than
one reasonable design exists: which layer owns what, the domain interface's shape, how state
flows to the screen, caching), each with options, the trade-off and a recommendation, a
**checkpoint sketch** (one line each) and the **test approach**. Ask with AskUserQuestion, one
question per real decision, recommended option first. Decide obvious ones yourself and list
them as "decided, flag if you disagree". Under the pipeline these become a DECISION NEEDED
report.

**The layering is not a decision point.** Server calls live in `data/`, the interface and the
models in `domain/` in the app's own words, the screen in `ui/`.

## 4. Branch

Slug: 3–5 kebab words. Branch `<type>/<n>-<slug>` (`feat`, `fix`, `chore`, `refactor`,
`docs`), e.g. `feat/9-browse-and-search`. The same `<n>-<slug>` names the ticket folder.

```bash
git fetch -q origin main
git switch -c "<type>/<n>-<slug>" origin/main
git push -u origin HEAD
```

Never base it on a local `main` that may lag.

## 5. Write the checklist and the notes

`docs/tasks/<n>-<slug>/CHECKLIST.md`. Checkpoints are the top-level units, each ending in a
commit:

```markdown
# Checklist — <title>

Issue: #<n> — <title> (<url>)
Branch: `<type>/<n>-<slug>`
Problem: <one sentence: what is wrong or missing today, in our terms as users>

## Done when

- <verifiable outcome, from the ticket>

## References

- Paperless API: <endpoints, with the docs section and the API version(s) whose shape differs>
- <other tickets, design canvas rows>

## Context

<One paragraph: what exists, the analogue being mirrored, the decisions and why.>

## Test plan

- Unit: <which use cases, ViewModels and API calls; happy path, edges, failures>
- MockWebServer: <the responses each new call is tested against, for v9 and v10 where they differ>
- Real server: <the read-only calls self-verification makes against the live server>
- Device only: <what only a phone can show (fingerprint, install, camera), or none>

---

## Checkpoint 1 — <name> · commit: `<type>(<scope>): <summary>`

Goal: <what exists and is verifiable when this is done>

### [ ] 1.1 — <atomic step>

File: `app/src/main/java/.../data/PaperlessApi.kt`
<What to change: names, signatures, fields, nullability. The file to mirror.>

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '<this checkpoint's tests>'`
- <the observable that proves it>

---

## Out of scope

- <binding>
```

Rules:

- **Checkpoints are commit-sized**, testable on their own, at most about a day of work.
- **Tests belong to the checkpoint that makes them writable.** No trailing "write the tests".
- **Exact paths and signatures.** The implementer never re-derives a decision.
- **Layer by layer inside a checkpoint**: the domain interface and model, the `data/`
  implementation with its MockWebServer test, then the use case and the ViewModel.
- **Commit messages are drafted now**, conventional-commit style, scoped by area (`api`,
  `upload`, `documents`, `auth`, `ui`, `build`, `docs`).
- **Out of scope is binding.**

Seed `docs/tasks/<n>-<slug>/NOTES.md`:

```markdown
# Notes — <title>

Issue: #<n> — <title> (<url>)
Branch: `<type>/<n>-<slug>`

## Planning

- Approach: <the agreed design in 1–2 lines>
- Decided: <each decision, one line; mark those the pipeline took on the user's behalf>
- Open risks: <or none>
```

No empty later headings: an empty heading reads to the pipeline as a finished stage.

Commit and push both through `commit-code`: `docs(tasks): plan #<n> <title>`.

## 6. The brief, then stop

In chat, at most ~15 lines: what is being built and why; the key decisions; the checkpoints,
one line each; the test approach; out of scope; open risks. No paths. Then:

> Review the brief; the checklist is at `<path>`. Approve to continue with `implement-plan`.

Changes asked for go in a follow-up commit (`docs(tasks): revise #<n> plan`), never a
force-push.

## Guardrails

- No code here.
- No call is planned without the docs and the schema behind it.
- No design decision in the checklist that wasn't settled or flagged.
- Branch slug and folder slug are the same string.
