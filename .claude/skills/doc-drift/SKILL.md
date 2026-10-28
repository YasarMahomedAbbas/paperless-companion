---
name: doc-drift
description: >-
  Use after self-review, before the PR — "check the docs", "doc drift", or the pipeline's fourth stage. Checks every falsifiable claim CLAUDE.md, README.md and docs/ make about what the branch touched, records the new facts a future session must know, decides itself where each goes (nowhere / CLAUDE.md / README / a docs/ file), commits the fixes into the same PR, and records every decision in NOTES. Asks nothing.
---

# Doc Drift

`start-ticket` → `implement-plan` → `self-review` → **`doc-drift`** → `self-verification` →
`raise-pr` → `merge-release`

Docs drift one merged branch at a time; the cheap moment to catch it is while the change is
still on the branch. Two directions:

1. **Claims the branch broke**: a doc says something the new code makes false.
2. **Facts the branch created**: a rule, an invariant or a command a future session must know.

In scope: `CLAUDE.md`, `README.md`, `docs/**` except `docs/tasks/` (other tickets' records;
only this branch's own folder is touched).

## 1. Scope the scan

```bash
git fetch -q origin main
git diff origin/main...HEAD --name-only
```

The diff is the blast radius. Don't audit everything.

## 2. Check claims against the code

Falsifiable claims get checked with `rg` / `fd`, never from memory. Drift that recurs here:

| Drift | How to catch it |
|---|---|
| README's architecture block and use-case table | `fd -e kt . app/src/main` against the doc |
| Commands (`./build.sh …`, the CI line) | `.github/workflows/*.yml` and `build.sh` |
| Server support ("Paperless 2.x or 3.x", API versions) | what `data/` actually reads |
| Named files and classes | `rg` for each one the doc names |
| Release and install steps | `release.yml`, and the update flow once #13 lands |

## 3. Find the undocumented new

Did the branch add something a future session would get wrong without being told? A new rule
("all X go through Y"), a new layer boundary, a new setting, a new screen in the navigation, a
new permission, a server version quirk.

## 4. Decide, without asking

Documentation is paid for in context: a line earns its place only if it saves a future
session more than it costs to read. First rung that answers it decides:

1. **Would a session without this plausibly do the wrong thing?** No (the code says it, a
   test enforces it, one `rg` finds it) → **skip**, one line in NOTES saying why. Skipping is
   the default.
2. **Does every session need it, on every ticket?** A cross-cutting rule → `CLAUDE.md`: the
   rule and a one-clause reason, mechanics elsewhere. `CLAUDE.md` should not only grow; look
   for a line the code now enforces by itself.
3. **Is it about using or building the app?** → `README.md` (features, install, server,
   architecture, development).
4. **Is it detail for one area?** → `docs/<area>.md`, linked from `CLAUDE.md` or the README
   where a session working on that area will see it. A doc nothing links to doesn't exist.

Drift the branch didn't cause (already wrong on `origin/main`) is fixed too, in its own commit
(`docs: correct stale <area> docs`) in the same PR, so the PR stays reviewable by commit.

Never edit the Paperless docs' claims into ours from memory: a server fact cites its doc page.

## 5. Apply and commit

Corrections state what the code shows, verified. Commit via `commit-code`:
`docs: update docs for #<n>`.

## 6. Record

Append to `docs/tasks/<n>-<slug>/NOTES.md`:

```markdown
## Doc drift

- Applied: <file:section — what changed, and the rung>
- Skipped: <finding — why>
- Stale, fixed separately: <file:section — doc said X, code shows Y> | none
```

Or `- No drift found.` A clean scan is a result; inventing edits is worse than silence.
