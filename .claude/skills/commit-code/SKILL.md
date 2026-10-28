---
name: commit-code
description: >-
  Use whenever code or docs on a ticket branch need committing — the pipeline stages call it at every checkpoint and fix, and it runs standalone for "commit this". Conventional commits with a why-focused body, staging by path so unrelated changes never ride along, a file-based message, then a push of the branch (never main, never forced).
---

# Commit Code

A commit is a coherent, verified unit whose message says **why**: the diff already shows what.

## 1. Survey

```bash
git status --short
git diff --stat
git diff --stat --cached
```

Exactly one logical unit per commit. Separate a stray dependency bump, an unrelated doc fix or
debug leftovers. Never commit secrets, `local.properties`, keystores (`*.jks`, `*.keystore`),
`verify.env` contents or build output; stop and flag them.

## 2. Verify

If the caller just ran the checkpoint's tests green, don't repeat them. Otherwise run what the
change touches:

```bash
./build.sh ./gradlew --console=plain testDebugUnitTest
```

Knowingly red work isn't committed.

## 3. Stage by path

`git add <paths>`. `git add -A` only when the whole tree is the one unit.

## 4. Message

`<type>(<scope>): <summary>`, types `feat` `fix` `docs` `refactor` `test` `chore` `perf`,
scopes by area (`api`, `upload`, `documents`, `auth`, `ui`, `update`, `build`, `tasks`).
Summary imperative, ≤72 characters, no full stop. The body explains the problem, the
approach, and any trade-off a future reader would otherwise reverse-engineer. Footer: `Refs
#<n>`, then the attribution lines the session's system prompt gives for commits.

Write it to a file and commit from it (multi-line `-m` and heredocs break in fish):

```bash
git commit -F .git/COMMIT_MSG.txt && rm .git/COMMIT_MSG.txt
```

## 5. Push

```bash
git push -u origin HEAD
```

Not on `main`, not on a detached HEAD. A rejected push is reported, never forced, rebased or
amended past.

Report the short hash, subject, file count and whether it was pushed.
