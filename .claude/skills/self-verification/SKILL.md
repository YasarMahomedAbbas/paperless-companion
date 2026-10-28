---
name: self-verification
description: >-
  Use when a branch is built and reviewed and needs proof it works beyond the unit tests — "verify this", "self-verify", or the pipeline's fifth stage. Fully autonomous: runs the full CI command in Docker, checks every Paperless call the branch touches against the real server (live schema, read-only calls; writes only on a document it uploaded itself, and only when allowed), fixes in-scope bugs and re-runs, and records a Result line in NOTES. No screenshots. Never asks.
---

# Self Verification

`start-ticket` → `implement-plan` → `self-review` → `doc-drift` → **`self-verification`** →
`raise-pr` → `merge-release`

Turns "the unit tests are green" into "it builds as CI builds it, and the server it runs
against answers the way the code expects". MockWebServer accepts things a real Paperless
refuses (both v0.1.x upload bugs were that), so the real server is the check that matters.

Nobody looks at screenshots on these PRs, so none are taken.

## Principles

- **Never ask, never wait.** A question becomes a recorded finding.
- **Never claim a step you didn't run.** "Not self-verifiable" is a legitimate outcome.
- **Fix what's in scope, record what isn't.**
- **Real data is the household's.** Read freely; write only where step 3 allows it.

## 1. Scope

```bash
git fetch -q origin main
git diff --stat origin/main...HEAD
```

Read `CHECKLIST.md` (the "Done when" items and the test plan's *Real server* and *Device only*
lines) and NOTES `## Implementation`.

## 2. The CI build

```bash
./build.sh ./gradlew --console=plain testDebugUnitTest koverVerify lintDebug assembleDebug
```

Record the real test counts and coverage. Red → fix (in scope) and re-run.

## 3. The real server

Only when the diff touches `data/` calls to Paperless. Credentials come from
`~/.config/paperless-companion/verify.env`:

```bash
set -a; . ~/.config/paperless-companion/verify.env; set +a   # PAPERLESS_URL, PAPERLESS_TOKEN, PAPERLESS_ALLOW_WRITES
```

No file, or the server unreachable → record `Real server: not run (<why>)` and go on; the
Result is at best `partial`.

1. **Schema**: fetch `$PAPERLESS_URL/api/schema/` (with `Authorization: Token $PAPERLESS_TOKEN`
   and `Accept: application/json`) and check each path, method, parameter and field the code
   uses exists with the type the code expects. Note the server's version
   (`/api/ui_settings/` or the response's `X-Version` header) and which API version the app
   gets by default.
2. **Read calls**: make each GET the branch added, with `curl`, exactly as the code builds it
   (same path, query, headers), and check the response parses into what the code reads. Save
   a trimmed response as a MockWebServer fixture if the tests used an invented one.
3. **Write calls** (PATCH, DELETE, bulk edit, upload) only when `PAPERLESS_ALLOW_WRITES=1`,
   and only on a document this run uploaded itself: a one-page PDF titled
   `pipeline-test-<n>-<timestamp>`, deleted and emptied from the trash at the end, pass or
   fail. Never touch any other document, tag or correspondent. Writes not allowed → those
   calls are `checked against the schema only`.

A mismatch is a bug in this branch: fix it in `data/`, add the test that would have caught
it, re-run step 2.

## 4. Device only

What only a phone can show (fingerprint prompt, installing an update, the camera) is
`not self-verifiable`. List each with what to check by hand; `merge-release` puts the list in
the release notes.

## 5. Record

Append to NOTES:

```markdown
## Self verification

Result: passed | partial | failed | not applicable

- CI build: <N tests passed, coverage X%, lint clean, APK built>
- Server: <Paperless version, default API version> | not run (<why>)
- Verified: <each call or step, and what proved it>
- Failed: <step — what happened> | none
- Not self-verifiable: <step — what to check on the phone> | none
```

- `passed`: everything in scope ran and passed.
- `partial`: everything that ran passed; some steps couldn't run (server unreachable, device
  only).
- `failed`: something ran and failed and couldn't be fixed in scope. `raise-pr` opens a
  draft and `merge-release` won't merge it.
- `not applicable`: docs or tooling only.

Commit via `commit-code`: `docs(tasks): record #<n> verification`.
