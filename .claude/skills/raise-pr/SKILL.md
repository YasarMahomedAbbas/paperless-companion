---
name: raise-pr
description: >-
  Use when a branch has been verified and needs its pull request — "raise the PR", "open a PR", or the pipeline's sixth stage. Re-runs the full CI command, merges origin/main in, pushes, and opens the PR against main with a body assembled from the ticket folder (what and why, verification, changes, testing, API check, docs, review, outstanding). Opens a draft when verification failed. Merging is merge-release's job.
---

# Raise PR

`start-ticket` → `implement-plan` → `self-review` → `doc-drift` → `self-verification` →
**`raise-pr`** → `merge-release`

The body is assembled from `docs/tasks/<n>-<slug>/`, not from memory.

## 1. Everything committed

```bash
git branch --show-current     # never main
git status --short
```

Uncommitted work belonging to the ticket gets committed via `commit-code`; anything else is
left out and named in the report.

## 2. Sync with main

```bash
git fetch -q origin main
git merge origin/main
```

Resolve conflicts keeping both sides' intent, then the full CI command:

```bash
./build.sh ./gradlew --console=plain testDebugUnitTest koverVerify lintDebug assembleDebug
```

Green before anything is pushed. Capture the numbers.

## 3. Push and open

```bash
git push -u origin HEAD
gh pr create --base main --head "$(git branch --show-current)" \
  --title "<type>(<scope>): <summary> (#<n>)" --body-file <scratchpad file>   # add --draft when Result: failed
```

The title becomes the squash commit on `main` and drives the release bump, so its type must
be honest: `feat` for something new a user can do, `fix` for a bug, `refactor` / `chore` /
`docs` / `test` otherwise.

Body:

```markdown
Closes #<n>

## What & why

<one paragraph from the checklist's Problem and Context>

## Verification

Result: <from NOTES>
<CI build, server, verified, failed and not-self-verifiable lines, verbatim>

## Changes

- <one line per checkpoint>

## Testing

- `testDebugUnitTest` — <N passed / N failed>
- `koverVerify` — <coverage>
- `lintDebug`, `assembleDebug` — <result>

## API check

<from NOTES `## Review` API line: each endpoint, docs + schema match>

## Docs

<from NOTES `## Doc drift`>

## Review

<from NOTES `## Review`; decisions the pipeline took on the user's behalf, from `## Planning`>

## Outstanding

<unticked tasks with reasons, raised issues, or nothing>

<the PR attribution line from the session's system prompt>
```

## 4. Report

The PR URL and one line on what it ships.

## Guardrails

- Base is always `main`.
- Never open a PR with red CI locally, suppressed checks or unexplained unticked tasks.
- Never merge here.
