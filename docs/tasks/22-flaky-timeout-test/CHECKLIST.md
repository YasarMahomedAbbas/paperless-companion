# Checklist — Flaky test: status-check timeout message is not always "timeout"

Issue: #22 — Flaky test: status-check timeout message is not always "timeout" (https://github.com/YasarMahomedAbbas/paperless-companion/issues/22)
Branch: `fix/22-flaky-timeout-test`
Problem: one `PaperlessApiTest` case asserts the text of OkHttp's/the JDK's timeout exception, which is `"timeout"` or `"Read timed out"` depending on where the timeout fires, so it fails now and then and can block an unattended pipeline's CI.

## Done when

- `a status check that gets no answer is a no-answer error, not unreachable` no longer depends on the exception's wording: it asserts the kind, that the cause is a `SocketTimeoutException`, and our own message prefix.
- A deterministic test pins the classification for both wordings (`"timeout"`, `"Read timed out"` → NO_ANSWER) and for a connect timeout (`"Connect timed out"` / `"connect timed out"` → UNREACHABLE).
- `./build.sh ./gradlew --console=plain testDebugUnitTest koverVerify lintDebug assembleDebug` passes.

## References

- Paperless API: none. No call is added or changed; `GET /api/tasks/?task_id=` is only the call the test happens to exercise.
- Found in the self-review of #7.

## Context

`PaperlessApi.networkError` sorts an `IOException` into `UNREACHABLE` (couldn't connect, including a `SocketTimeoutException` whose message starts with "connect") or `NO_ANSWER` (connected, no complete answer), and builds `"no answer from <host>: <detail>"` with the exception as `cause`. The production code is right; only the test is brittle. Decided: assert the kind, `cause is SocketTimeoutException` and the `"no answer from ${server.hostName}: "` prefix instead of the JDK's text (the issue's first suggestion; a regex on `timed? ?out` would still couple us to wording we don't own). To make the classification itself testable without timing, a second test builds `PaperlessApi(client = …)` with an OkHttp interceptor that throws a `SocketTimeoutException` with each message, so every wording path runs deterministically. No production code changes.

## Test plan

- Unit: the reworked MockWebServer timeout test (kind + cause type + message prefix); a new interceptor-driven test covering `"timeout"` and `"Read timed out"` → NO_ANSWER and `"Connect timed out"`, `"connect timed out"` → UNREACHABLE, each with the exception as `cause`.
- MockWebServer: the existing `SocketPolicy.NO_RESPONSE` response with a 200 ms poll timeout; no v9/v10 difference involved.
- Real server: none; no Paperless call changes. Self-verification records it as not applicable.
- Device only: none.
- Failing-first: the interceptor test with `"Read timed out"` run against the old assertion (`message.contains("timeout")`) fails, which is the flake reproduced deterministically; record that in NOTES during implementation.

---

## Checkpoint 1 — Assert the timeout's kind, not its wording · commit: `test(api): assert the timeout's kind, not the JDK's wording`

Goal: the timeout tests pass regardless of which layer raises the timeout, and the classification is pinned for every wording.

### [x] 1.1 — Rework the flaky assertion

File: `app/src/test/java/io/github/yasarmahomedabbas/paperlesscompanion/data/PaperlessApiTest.kt`
In `a status check that gets no answer is a no-answer error, not unreachable`, replace `assertTrue(e.message!!.contains("timeout"))` with:
`assertTrue(e.cause is java.net.SocketTimeoutException)` and `assertTrue(e.message!!.startsWith("no answer from ${server.hostName}: "))`. Keep `assertEquals(Kind.NO_ANSWER, e.kind)`. Import `java.net.SocketTimeoutException`.

### [x] 1.2 — Pin the classification for every timeout wording

File: same.
Add `fun \`a timeout is sorted by where it happened, whatever its wording\`() = runTest { … }`. For each `(message, kind)` in `"timeout" to NO_ANSWER`, `"Read timed out" to NO_ANSWER`, `"Connect timed out" to UNREACHABLE`, `"connect timed out" to UNREACHABLE`: build `PaperlessApi(client = OkHttpClient.Builder().addInterceptor { throw SocketTimeoutException(message) }.build())`, call `task(config, "t")`, catch `ServerException`, assert the kind, `cause is SocketTimeoutException`, and that the message ends with `": $message"`. Use the message in assertion texts so a failure names the wording. Import `okhttp3.OkHttpClient`. Before adding the final assertions, temporarily run the `"Read timed out"` case against the old `contains("timeout")` check to confirm it fails; note the result in NOTES.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*PaperlessApiTest*'`
- Then the full CI command once: `./build.sh ./gradlew --console=plain testDebugUnitTest koverVerify lintDebug assembleDebug` (the flake showed in `testReleaseUnitTest` under `koverVerify`).

---

## Out of scope

- Any change to `PaperlessApi.networkError` or other production code.
- Hunting other flaky tests or adding retries to CI.
- Changing user-visible error texts.
