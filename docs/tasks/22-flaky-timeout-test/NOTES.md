# Notes — Flaky test: status-check timeout message is not always "timeout"

Issue: #22 — Flaky test: status-check timeout message is not always "timeout" (https://github.com/YasarMahomedAbbas/paperless-companion/issues/22)
Branch: `fix/22-flaky-timeout-test`

## Planning

- Approach: test-only fix. Assert the kind, the `SocketTimeoutException` cause and our own message prefix instead of the JDK's wording, and add a deterministic interceptor-driven test that pins the classification for `"timeout"`, `"Read timed out"` (NO_ANSWER) and connect timeouts (UNREACHABLE).
- Decided (the pipeline took these on the user's behalf; no real decision point): assert type and prefix rather than match `timed? ?out`, since a regex still depends on wording we don't own; no production change, since `networkError` already classifies correctly.
- Open risks: none. `verify.env` is absent and no Paperless call changes, so the real-server check is not applicable.
