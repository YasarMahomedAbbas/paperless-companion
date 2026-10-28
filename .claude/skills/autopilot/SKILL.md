---
name: autopilot
description: >-
  Use when tickets should be picked up and shipped with nobody watching — "/autopilot", "run autopilot", "start everything that's ready", a timer, or a finished pipeline waking it. Finds open issues whose "Needs:" tickets are all closed, relaunches pipelines whose tmux session died, and starts up to three unattended `pipeline` runs, each in its own worktree and `paperless-<n>` session. Silent when nothing qualifies. Not for driving one chosen ticket (use pipeline).
---

# Autopilot

A picker and a launcher, nothing more. Everything after the launch is `pipeline`, which plans,
builds, verifies, merges and releases on its own, and parks a ticket at a judgment call.

Launched in its own detached session, by hand, by a timer, or by a pipeline that just shipped:

```bash
tmux has-session -t paperless-autopilot 2>/dev/null || \
  tmux new-session -d -s paperless-autopilot -c ~/projects/personal/paperless-companion \
  "claude '/autopilot'"
```

**Silence is the contract.** No text until something is launched. Tool calls only.

## 1. Capacity

```bash
running=$(tmux list-sessions -F '#{session_name}' 2>/dev/null | rg -c '^paperless-[0-9]+$' || true)
slots=$((3 - ${running:-0}))
```

At most three pipelines at once. `slots` ≤ 0 → step 5.

## 2. Orphans first

A worktree under `<repo-parent>/worktrees/paperless-companion/issue-<n>` with no
`paperless-<n>` session is a pipeline a reboot or a crash stopped. If its issue is open and not
labelled `parked`, it gets a slot before any new ticket: run `pipeline`'s Bootstrap for it (it
reuses the worktree and resumes from the stage log).

## 3. Candidates

```bash
gh issue list --state open --limit 100 --json number,title,labels,body
```

A ticket qualifies only if all of these hold. When unsure, it doesn't.

- **Not labelled `design`, `parked` or `manual`.** A design is drawn with the user; a parked
  ticket waits for their answer; a manual one is a check only a person can do.
- **No worktree and no `paperless-<n>` session** for it, and no open PR that closes it.
- **Everything it needs is closed.** Read the body's `Needs:` line(s): every `#<m>` named
  there must be closed (`gh issue view <m> --json state -q .state` is `CLOSED`). No `Needs:`
  line means it needs nothing. A comment that adds or drops a dependency overrides the body.
- **It's code.** A ticket with nothing to diff can't end in a PR.

## 4. Launch

Lowest issue number first. Take the first `slots`. For each, run `pipeline`'s **Bootstrap**
exactly as written there, one after another (the `git worktree add` calls share a lock). If a
bootstrap fails, remove the half-made worktree with `git worktree remove --force` and carry on.

Two tickets that touch the same files may run at once: the later PR merges `main` in and
resolves the conflict in `merge-release`. Don't hold one back for it.

## 5. Exit

If anything launched: `PushNotification` (load via ToolSearch), title `Autopilot`, body
`Started #a, #b — tmux paperless-<n>`, and print the same line.

Last action, only in the timer's own session:

```bash
[ -n "${TMUX:-}" ] && [ "$(tmux display-message -p -t "$TMUX_PANE" '#S')" = paperless-autopilot ] \
  && tmux kill-session -t paperless-autopilot
```

Run by hand anywhere else: end without killing anything.

## Guardrails

- Never run a stage here, read source, or edit a ticket.
- Never pick a `parked` ticket; the user removes the label when they've answered.
- Never exceed three pipelines.
