# Notes — Flaky test: status-check timeout message is not always "timeout"

Issue: #22 — Flaky test: status-check timeout message is not always "timeout" (https://github.com/YasarMahomedAbbas/paperless-companion/issues/22)
Branch: `fix/22-flaky-timeout-test`

## Planning

- Approach: test-only fix. Assert the kind, the `SocketTimeoutException` cause and our own message prefix instead of the JDK's wording, and add a deterministic interceptor-driven test that pins the classification for `"timeout"`, `"Read timed out"` (NO_ANSWER) and connect timeouts (UNREACHABLE).
- Decided (the pipeline took these on the user's behalf; no real decision point): assert type and prefix rather than match `timed? ?out`, since a regex still depends on wording we don't own; no production change, since `networkError` already classifies correctly.
- Open risks: none. `verify.env` is absent and no Paperless call changes, so the real-server check is not applicable.

## Implementation

- Shipped: `PaperlessApiTest.kt:215-227` the no-answer timeout test now asserts `Kind.NO_ANSWER`, a `SocketTimeoutException` cause and the `"no answer from <host>: "` prefix instead of the JDK's wording; `PaperlessApiTest.kt:229-249` new interceptor-driven test pins `"timeout"`/`"Read timed out"` → NO_ANSWER and `"Connect timed out"`/`"connect timed out"` → UNREACHABLE, each with the exception as cause and the wording at the end of the message. No production code changed.
- Failing-first: the interceptor throwing `SocketTimeoutException("Read timed out")`, checked with the old `message.contains("timeout")` assertion, failed (`AssertionError`, 21 tests / 1 failed): the flake reproduced deterministically. The probe was then replaced by the final assertions.
- Tests: `PaperlessApiTest` 21 passed / 0 failed; full CI command (`testDebugUnitTest koverVerify lintDebug assembleDebug`) BUILD SUCCESSFUL, debug unit tests 184 passed / 0 failed / 0 skipped.
- Outstanding: none.
- Contradicted the plan: none.

## Review

- Fixed: none. Checked: `networkError` builds exactly the `"no answer from <host>: <detail>"` / `"can't connect to <host>: <detail>"` messages with the exception as cause; the interceptor survives `client.newBuilder()` into the poll client, so the new test really runs `networkError`; an OkHttp read timeout is a `SocketTimeoutException` ("timeout" from Okio or "Read timed out" from the JDK) and is not retried once the request is sent, so the reworked MockWebServer test can't flake on cause or kind. Every "Done when" item is in the diff; no production code changed (Out of scope respected).
- Raised: none
- API: none touched (`GET /api/tasks/?task_id=` only exercised by the test, unchanged)
- Layers: clean

## Doc drift

- No drift found. The branch only changes `PaperlessApiTest.kt`; `rg` over CLAUDE.md, README.md and docs/ (excluding docs/tasks/) finds no claim about timeouts, their wording or this test.
- Skipped: "assert a network error's kind and cause, not the JDK's message wording": rung 1, the reworked tests show the pattern and a session copying a neighbouring test picks it up; not worth a CLAUDE.md line.
- Stale, fixed separately: none
