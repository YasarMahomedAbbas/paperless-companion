---
name: pipeline
description: >-
  Use when a ticket should be driven from issue to released APK with nobody watching — "run the pipeline on #9", "pipeline 9", "take #9 all the way", "resume the pipeline", or when autopilot launches it. Runs start-ticket → implement-plan → self-review → doc-drift → self-verification → raise-pr → merge-release, each stage as a fresh subagent in the ticket's own worktree and tmux session. Never asks the user anything: it takes the recommendation on routine questions and parks the ticket (a research note on the issue, label `parked`) at a judgment call. Ends by merging the PR and releasing, then tears the worktree down and wakes autopilot so dependent tickets start. Not for single stages (invoke the stage skill) or a design (design-canvas).
---

# Pipeline

`start-ticket` → `implement-plan` → `self-review` → `doc-drift` → `self-verification` →
`raise-pr` → `merge-release`

The stage skills know how to do their jobs and start cold from the ticket folder and the
branch. This skill only **sequences them and stands in for the user**. It always runs
unattended: the user has said PRs are merged and releases are tagged without them, so there is
nobody to ask. Every question is either decided here or parks the ticket.

Two ideas shape everything below:

- **The orchestrator stays ignorant on purpose.** It holds the ticket's intent, the stage
  reports and the decisions made. Never the diff, never the code, never the checklist body.
  That is what keeps it able to judge a plan brief the way the user would.
- **It is the user's proxy for the routine only.** It approves a plan whose brief covers the
  ticket, takes the recommended option on ordinary questions, and parks anything that changes
  what is being built.

Worktrees live at `<repo-parent>/worktrees/paperless-companion/issue-<n>`, each in a detached
tmux session named `paperless-<n>` (not `issue-<n>`: that name belongs to travel-smart's
autopilot, which counts those sessions).

## Entry: which phase are you in?

Invoked as `pipeline #<n>`. Work out where you are standing:

```bash
git rev-parse --show-toplevel
git branch --show-current          # empty on a detached HEAD
```

- **Not in `worktrees/paperless-companion/issue-<n>`** → **Bootstrap**, then stop. Subagents
  inherit this session's working directory, so a stage run from the wrong tree edits the
  wrong tree.
- **In it** → **Run**.

No number given: inside an issue worktree the folder name carries it. Otherwise there is
nobody to ask; print one line saying so and stop.

## Bootstrap

```bash
ROOT=$(git rev-parse --show-toplevel)
WT="$(dirname "$ROOT")/worktrees/paperless-companion/issue-<n>"
git -C "$ROOT" fetch -q origin main
if [ ! -d "$WT" ]; then
  mkdir -p "$(dirname "$WT")"
  git -C "$ROOT" worktree add --detach "$WT" origin/main
fi
tmux has-session -t "paperless-<n>" 2>/dev/null \
  || tmux new-session -d -s "paperless-<n>" -c "$WT" "claude '/pipeline #<n>'"
```

Detached at `origin/main`, because `main` is checked out in the main tree. Nothing to seed:
the build runs in Docker (`./build.sh`) and the Gradle cache is shared under `~/.cache`. If a
git command fails on a `.lock` file, another bootstrap is in the same second; retry once.

An existing worktree is reused, never recreated: the run resumes from the stage log.

Say "Continuing in tmux session `paperless-<n>`" and stop.

## Run

### 1. Hold the ticket's intent

```bash
gh issue view <n> --json title,body,comments,url,labels
```

Keep only what you need to judge a brief later: what is asked for, the "Done when" criteria,
anything the comments add or retract. Don't explore code.

If the issue is labelled `parked` or is closed, stop: a parked ticket waits for the user, a
closed one is done. If it is labelled `design`, follow **Design tickets** (below) instead of
the stage table.

### 2. Detect the next stage

The ticket folder is the stage log. The branch is `<type>/<n>-<slug>`; the folder is
`docs/tasks/<n>-<slug>/`. Read only the headings of `NOTES.md` (`rg -n '^## '`).

| Evidence | Next stage |
|---|---|
| Detached HEAD, or no `<type>/<n>-<slug>` branch | `start-ticket` |
| Branch, no `CHECKLIST.md` | `start-ticket` |
| No `## Implementation` in NOTES | `implement-plan` |
| No `## Review` | `self-review` |
| No `## Doc drift` | `doc-drift` |
| No `## Self verification` | `self-verification` |
| No PR for the branch (`gh pr list --head <branch> --state all`) | `raise-pr` |
| PR open, or merged without its release (see `merge-release` step 1) | `merge-release` |
| PR merged and released (or released not needed) | done → **Tear down** |

Say which stage you are resuming at and why, in one line. Announce every transition:

```bash
pipeline-status running <stage>
pipeline-status blocked <reason>
pipeline-status done    released
```

### 3. Run a stage as a fresh subagent

One `general-purpose` subagent per stage, never `fork`: the stage skills are built for a cold
start. The prompt is fixed; fill in the blanks:

```
You are running one stage of the pipeline: invoke the skill `<stage>` and follow it
completely. Working directory: <WT>. Ticket: #<n> (<url>).

You have no user. Wherever the skill would ask the user something, do not ask. Stop at that
point and end your turn with exactly one DECISION NEEDED report (format below). You will be
resumed with the answer and must continue from where you stopped, as if the user had said it.
Batch every question you would have asked at the same moment into one report. Make no
decision the skill reserves for the user.

Everything else in the skill (exploring, writing, committing via commit-code, running checks,
appending to NOTES) do exactly as the skill says. At the skill's hand-off, end your turn with a
STAGE COMPLETE report.

<stage-specific notes>

DECISION NEEDED
Stage: <stage>
Kind: design-question | plan-brief | scope-stop | other
Question: <what you would have asked, verbatim>
Options: <numbered, recommended first, one line each with the trade-off>
Recommendation: <option number and why, one line>
Flags: <any of: ux, scope, api, security, open-risk — or none>
Context: <≤10 lines; no file paths, no code>

STAGE COMPLETE
Stage: <stage>
Outcome: <≤8 lines, in the skill's own hand-off terms>
Commits: <hashes + subjects, or none>
Outstanding: <anything deferred or unresolved, or none>
```

Stage-specific notes:

- **`start-ticket`**: "The worktree is already at a fresh `origin/main`; cut the branch from
  there. The step-3 approach questions are `design-question` reports; the step-6 brief is a
  `plan-brief` report whose Question is the brief, verbatim."
- **`implement-plan`**: "A scope-creep stop or a checklist the code contradicts is a
  `scope-stop` report, with the proposed split as the options."
- **`doc-drift`**, **`self-verification`**: "This stage asks nothing; run it to the end."
  For self-verification add: "STAGE COMPLETE opens with a `Result:` line (passed / partial /
  failed / not applicable)."
- **`raise-pr`**: no notes.
- **`merge-release`**: "Skip the teardown; the orchestrator does it after your report."
- **`self-review`**: no notes.

Resume a parked subagent with `SendMessage`; a new `Agent` call would start the stage over.

### 4. Decide or park

| Kind | Decide | Otherwise |
|---|---|---|
| `design-question` | Take the recommended option, flagged or not. Tell the subagent: "Take option N. Record in NOTES that the pipeline took this on the user's behalf, with the option and the flag." That line reaches the PR body. | — |
| `plan-brief` | Approve when all three hold: (a) what is being built covers every "Done when" item; (b) the out-of-scope line drops nothing the ticket asks for; (c) no open question changes what is built. | **Park.** Don't fix the plan yourself. |
| `scope-stop` | Never. | **Park.** |
| `other`, a blocking Outstanding line, a subagent that ends without a report | Never. | **Park.** |

Answer in the user's voice ("Approved, proceed", "Take option 1") and say in chat, in one
line, what you decided and why.

### 5. Park

A parked ticket goes back to the user already researched.

1. `pipeline-status blocked "parked · <kind> · <stage>"`.
2. `SendMessage` the parked subagent:

   > This ticket is being parked here for the user. Don't continue the stage. Reply with one
   > RESEARCH SUMMARY for a developer reading it cold: what you explored (API docs and the
   > live schema, the files involved), the approach you would take and why, what you ruled
   > out, risks, and the open question with its options and your recommendation. Files by
   > path, no code, ≤40 lines. Commit and push anything already written to the ticket folder
   > via commit-code first.

3. Post the note: write it to a scratchpad file, `gh issue comment <n> --body-file <file>`.
   First line exactly `<!-- pipeline-parked -->`, then `## The pipeline parked this ticket`
   (one line: stage and why), `### Decision needed` (the report, verbatim), `### Research`
   (the summary, verbatim), `### Where the work is` (branch, pushed or not, ticket folder,
   worktree; and: "Answer here, remove the `parked` label, and `/pipeline #<n>` resumes from
   the stage log.").
4. `gh issue edit <n> --add-label parked`.
5. `PushNotification` (load it via ToolSearch): title `Pipeline #<n> parked`, body the stage
   and the kind.
6. Keep the branch and the worktree. Last action: `tmux kill-session -t "paperless-<n>"`.

### 6. Between stages

On STAGE COMPLETE print the Outcome, Commits and Outstanding lines, then detect the next stage
(step 2) and start it. Carry nothing else forward.

A `failed` self-verification is not a park by itself: `raise-pr` opens the PR as a draft and
`merge-release` refuses to merge a draft, which parks.

### 7. Tear down

After `merge-release`'s STAGE COMPLETE (merged and released, or released not needed):

1. `pipeline-status done released`, and `PushNotification`: title `#<n> shipped`, body the PR
   URL and the release tag (or "no release: docs only").
2. Wake autopilot, so tickets that needed this one start now:

   ```bash
   tmux has-session -t paperless-autopilot 2>/dev/null || \
     tmux new-session -d -s paperless-autopilot -c "$MAIN" "claude '/autopilot'"
   ```

3. Last action, one command (it deletes this session's working directory and ends it):

   ```bash
   MAIN=$(git worktree list --porcelain | head -1 | sed 's/^worktree //')
   git -C "$MAIN" worktree remove --force "$(git rev-parse --show-toplevel)" \
     && tmux kill-session -t "paperless-<n>"
   ```

## Design tickets

A `design`-labelled ticket produces a design the user reviews, not code, so it ends parked for
review instead of merged. Stages, all as fresh subagents with the same contract:

1. **Draw**, if NOTES has no `## Design`: one subagent runs `start-ticket` steps 1, 2 and 4
   only (read, explore, branch `docs/<n>-<slug>`), then `design-canvas` for every screen and
   flow the ticket names, then writes the manual files the ticket asks for under
   `docs/design/`, keeping the canvas and the manual's tokens in step. It seeds `NOTES.md`
   with `## Design` (the canvas URL, rows, open questions, every choice it made on the user's
   behalf), commits and pushes via `commit-code`, and opens a **draft** PR with the canvas
   link at the top.
2. **Park for review**: park as in step 5, kind `design-review`, with the canvas link and the
   open questions as the Decision needed, and no research request (the canvas is the
   research). The user reviews the canvas with Claude in a chat, and the changes land on the
   same branch and canvas.
3. **Resumed** (label removed): if the comments after the parked note ask for changes, one
   subagent applies them with design-canvas's revising rules and to the manual, then parks
   again. If they approve, one subagent builds whatever the ticket says follows approval
   (for #6: the tokens in `ui/theme/`, with `implement-plan`'s rules and the full CI command),
   then mark the PR ready (`gh pr ready`), run `merge-release` (it releases only if app code
   changed), and tear down.

## Guardrails

- **Never read the checklist body, the diff or the code.**
- **Never run a stage outside `worktrees/paperless-companion/issue-<n>`.**
- **Never restart a stage to get past a question**; resume it.
- **Never skip or reorder stages**; the detection table is the only way forward.
- **Never touch the signing key** or the `RELEASE_KEYSTORE_*` secrets.
- A report with missing fields (a plan brief with no checkpoints, a verification with no
  `Result:` line) goes back to the subagent; don't fill the gaps yourself.
