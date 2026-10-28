---
name: merge-release
description: >-
  Use when a ticket's PR is open and should be merged and shipped — "merge and release", "ship #9", or the pipeline's last stage. Waits for CI, refuses drafts and failed verification (parks instead), merges main in again if it moved, squash-merges, then tags the next version when the app changed, watches the Release workflow and checks the signed APK is on the release. The user has said PRs merge and releases ship without them.
---

# Merge and Release

`start-ticket` → `implement-plan` → `self-review` → `doc-drift` → `self-verification` →
`raise-pr` → **`merge-release`**

The user reviews nothing before this: the gates below are the review. When one fails, the
ticket parks (DECISION NEEDED, kind `other`) rather than shipping.

## 1. Where is it?

```bash
gh pr list --head "$(git branch --show-current)" --state all --json number,state,isDraft,mergeCommit,title
```

- **Open** → step 2.
- **Merged** → find the merge commit's release: `git fetch -q --tags origin` then
  `git tag --contains <mergeCommit> 'v*'`. A tag → done. None → step 5.

## 2. Gates

All must hold, else park with the reason:

- The PR is not a draft, and NOTES' `Result:` is `passed`, `partial` or `not applicable`.
  A `partial` is fine when what's left is device-only or the server was unreachable; anything
  that failed is not.
- NOTES has every stage section (`## Planning`, `## Implementation`, `## Review`,
  `## Doc drift`, `## Self verification`).
- No `Outstanding` item that breaks a "Done when" criterion.
- The diff doesn't touch the signing setup (`release.yml`'s keystore steps, `signingConfigs`)
  unless the ticket asked for exactly that.

## 3. CI

```bash
gh pr checks <pr> --watch --fail-fast
```

Red → read the failing log (`gh run view <id> --log-failed`), fix it on the branch if it's this
branch's fault (commit via `commit-code`, push, watch again; at most two rounds), else park.

## 4. Merge

If `main` moved since the PR was raised (`git fetch -q origin main && git log HEAD..origin/main
--oneline` is non-empty): `git merge origin/main`, resolve, run the full CI command, push, and
go back to step 3. Another pipeline merging first is normal when tickets run in parallel.

```bash
gh pr merge <pr> --squash --delete-branch
```

The issue closes through `Closes #<n>`. Check it did (`gh issue view <n> --json state`); if
not, `gh issue close <n> --comment "Shipped in #<pr>"`.

## 5. Release

**Only when the app changed**: the squash commit touches `app/`, `gradle/`, `build.gradle.kts`,
`settings.gradle.kts` or `gradle.properties`. Docs, tasks, skills and CI-only changes ship no
release; say "no release: no app change" and finish.

Next version from the latest tag and the PR title's type:

```bash
git fetch -q --tags origin
LAST=$(git tag -l 'v*' --sort=-v:refname | head -1)    # e.g. v0.1.4
```

- `feat` → minor (`v0.1.4` → `v0.2.0`)
- anything else → patch (`v0.1.4` → `v0.1.5`)
- never a major bump without the user; never minor or patch past 99 (the version code is
  `major*10000 + minor*100 + patch`).

Tag the squash commit on `origin/main` and push:

```bash
git tag -a "$NEXT" <squash sha> -m "<PR title>"
git push origin "$NEXT"
```

A rejected push means another pipeline just tagged: delete the local tag, re-fetch, recompute,
retry (at most three times).

Then:

```bash
gh run list --workflow release.yml --limit 3      # find the run for $NEXT
gh run watch <id> --exit-status
gh release view "$NEXT" --json assets -q '.assets[].name'   # paperless-scan-<version>.apk must be there
```

Write the release notes (`gh release edit "$NEXT" --notes-file <file>`): what it brings in
our terms, one line per change since `$LAST` (`git log --oneline $LAST..$NEXT`), and a
**Check on your phone** list from NOTES' "Not self-verifiable" lines.

A failed Release workflow: don't re-tag or delete the tag. Comment on the issue with the
failing step and the log excerpt, `PushNotification` (title `Release $NEXT failed`), and end
with STAGE COMPLETE whose Outstanding names it.

## 6. Report

STAGE COMPLETE with the PR, the merge commit, the tag (or "no release") and the release URL.

## Guardrails

- **Never touch the signing key or its secrets**, never regenerate a keystore, never edit the
  signing steps to make a release pass.
- Never force-push, never merge with red CI, never `--admin` past protection.
- Never delete or move a published tag.
