# Notes — Sign out bounces straight back to Home

Issue: #16 — Sign out bounces straight back to Home (https://github.com/YasarMahomedAbbas/paperless-companion/issues/16)
Branch: `fix/16-sign-out-bounces-home`

## Planning

- Approach: `LoginViewModel.onSignedInHandled()` clears `signedIn` once `LoginScreen` has passed it to the gate, so the activity-scoped ViewModel can't re-deliver an old sign-in after Sign out. Proven through the real screen with a Robolectric + Compose ui-test test (new test infrastructure, checkpoint 1), plus ViewModel tests.
- Decided (the pipeline took this on the user's behalf, recommended option):
  - Q1-1: consume the result with a handler that the screen's effect calls. Rejected: a fresh ViewModel per visit to the sign-in screen (Q1-2, form blank every time, the change sits in activity wiring that is hard to unit test), and a one-shot event channel (Q1-3, changes the state shape for little gain). Flag ux: the server address and username stay filled in after sign-out (the password is cleared).
- Decided by the user (on the issue, after the plan was parked at the brief): option 1. Add Robolectric + Compose UI test infrastructure and write the test the ticket asks for (a second LoginScreen after sign-out doesn't sign in on its own) through the real screen. Pin versions against the targetSdk, make sure the Docker build caches the android-all jar, keep the planned `onSignedInHandled()` fix. This replaces the earlier pipeline decision Q2-1 (ViewModel-level tests only); the plan was revised to match.
- Decided by start-ticket while revising (flag if you disagree):
  - Robolectric 4.14.1 with `sdk=35` (the targetSdk). Its `DefaultSdkProvider` maps SDK 35 to `android-all-instrumented:15-robolectric-12650502-i7`, checked in the 4.14.1 jar and on Maven Central. Newer Robolectric (4.16.x) adds SDK 36, which the app doesn't target; 4.14.1 is contemporary with the AGP 8.7 / Compose BOM 2024.12.01 stack. SDK 35 needs Java 21, which the Docker image and CI have.
  - The SDK jar is a Gradle dependency (its own configuration), and Robolectric runs with `robolectric.offline=true` pointed at the jar's folder in the Gradle cache. Reason: Robolectric's own download goes to `~/.m2`, which is `/tmp` in the container and lost per run; `GRADLE_USER_HOME` is the host cache that `build.sh` mounts and that `setup-gradle` caches on CI, so nothing extra is needed in the Docker image or CI.
  - Dependabot ignores `android-all-instrumented`; it moves only with Robolectric, by hand.
  - `ui-test-junit4` / `ui-test-manifest` come from the existing Compose BOM (1.7.6). The Kover filters are unchanged (`*Screen*` stays excluded).
  - `LoginViewModel` stays at activity scope and `MainActivity` is unchanged. There are no `domain/` or `data/` changes and no visual change.
- Open risks:
  - First run downloads the SDK jar (100+ MB) into the host Gradle cache; the offline re-run in checkpoint 1 is the proof it is cached.
  - Robolectric adds seconds per test class to `testDebugUnitTest`; checkpoint 1 records how much.
  - Kover's agent alongside Robolectric's sandbox class loader, and lint with `ui-test-manifest` in the debug manifest, are untested here until checkpoint 1 runs the full CI command.
  - Device check (sign out stays signed out, with and without the fingerprint lock) stays manual.

## Implementation

- Shipped:
  - Robolectric 4.14.1 + Compose ui-test (BOM 1.7.6) for JVM screen tests: catalog `gradle/libs.versions.toml:17-21,42-45`; wiring `app/build.gradle.kts:57-83,140-144` (`isIncludeAndroidResources`, the `robolectricSdk` configuration, offline mode pointed at the jar's folder in the Gradle cache via a lambda `CommandLineArgumentProvider`; no `Sync` fallback needed, the build doesn't use the configuration cache); `app/src/test/resources/robolectric.properties` (`sdk=35`); Dependabot ignores `android-all-instrumented` (`.github/dependabot.yml`).
  - The fix: `LoginViewModel.onSignedInHandled()` (`ui/login/LoginViewModel.kt:36`) clears `signedIn`; `LoginScreen`'s effect calls it after `onSignedIn` (`ui/login/LoginScreen.kt:35-40`).
  - Tests: `app/src/testDebug/.../ui/LoginScreenTest.kt` (3 tests through the real screen), three new `LoginViewModelTest` cases. README "Tests cover" paragraph updated (`README.md:63`).
- Tests: full CI command green. `testDebugUnitTest` 178 passed / 0 failed; `testReleaseUnitTest` 175 passed / 0 failed; koverVerify, lintDebug (lint clean with `ui-test-manifest` in the debug manifest) and assembleDebug pass.
  - Before the fix, `a second sign-in screen after sign-out does not sign in on its own` failed with `expected:<1> but was:<2>` (the old sign-in re-delivered); it passes with the fix.
  - Offline proof: after the first run put `android-all-instrumented-15-robolectric-12650502-i7.jar` into the host Gradle cache, `LoginScreenTest` re-ran green in the build image with `docker run --network none ... ./gradlew --offline ... --rerun-tasks`.
  - Cost: `LoginScreenTest` takes about 4.1–4.6 s (mostly Robolectric's first sandbox start), against about 1.6 s for every other test class together; wall time of a cold `testDebugUnitTest` run grew by roughly that.
- Outstanding: device check (sign in, Sign out stays on the sign-in screen with address and username filled in, sign in again reaches Home; again with the fingerprint lock on) is for self-verification. Real-server check: skipped, no Paperless call changed.
- Contradicted the plan:
  - `koverVerify` also runs `testReleaseUnitTest`, which the plan didn't account for. There the screen test failed twice over: Robolectric booted `PaperlessApp`, whose release `onCreate` schedules WorkManager (not initialized under Robolectric), and the release manifest has no `ComponentActivity` (`ui-test-manifest` is `debugImplementation`). Resolved by putting `LoginScreenTest` in `app/src/testDebug/` (compiled with `src/test/` for the debug unit tests only) and giving it `@Config(application = Application::class)` so the app's own wiring doesn't start. Checklist 1.5/2.1 updated to match.
  - `signing in again after sign-out signs in once more` as written passed on the old code by coincidence (re-delivery made the second call; the real second sign-in produced an equal `ServerConfig`, so the keyed effect didn't re-run). Added a one-config check before signing in again so it also fails without the fix; checklist 2.1 updated.

## Review

- Fixed: none. Checked: the effect calls `onSignedIn` then `onSignedInHandled()` with no suspension between them, so leaving composition can't cancel one without the other; `gate.onSignedIn` is synchronous; a sign-in that completes while the screen is hidden is still delivered when it returns (unchanged behaviour). Every "Done when" item and ticked task is in the diff; nothing from Out of scope. `LoginScreenTest` would fail without the fix (recorded in Implementation), and its second test checks one config before re-signing in so it can't pass by coincidence.
- Raised: none
- API: none touched (no Paperless call added or changed).
- Layers: clean (changes only in `ui/login/` and test/build wiring; no `data/` or Paperless names).

## Doc drift

- Applied: `app/build.gradle.kts`: Kover comment. It said Compose screens "need a device", but this branch lets them run on the JVM under Robolectric. It now says some screens have tests in `src/testDebug` and screens stay out of coverage. A code comment, not a doc: fixed because the branch made it false and a session reading it would skip writing screen tests.
- Applied (during implementation, verified here): `README.md:Development`: the "Tests cover" paragraph now names Robolectric + Compose ui-test, `app/src/testDebug/` and the offline SDK jar. Checked against `app/build.gradle.kts`, `robolectric.properties` and the test's location.
- Skipped: why screen tests go in `testDebug` with `@Config(application = Application::class)` (`koverVerify` also runs `testReleaseUnitTest`; the release manifest has no `ComponentActivity`). The header comment of `LoginScreenTest`, the obvious template, already says so, and the README points to the folder.
- Skipped: bumping Robolectric means bumping `robolectricSdk` by hand. `gradle/libs.versions.toml` and `.github/dependabot.yml` say so right where the bump happens, and the offline test run fails loudly if they don't match.
- Skipped: the `onSignedInHandled()` pattern (an activity-scoped ViewModel consumes a one-shot result). Its KDoc explains it, and it applies to one screen, not a cross-cutting rule.
- Checked, no drift: README architecture and use-case table are unchanged by this branch (no `domain/` or `data/` changes); CLAUDE.md "a bug fix comes with a test" is met.
- Stale, fixed separately: `CLAUDE.md:Working on the app`. It called the local check command "what CI runs", but `ci.yml` (and `release.yml`) run `koverVerifyDebug`, not `koverVerify`. `koverVerify` also runs `testReleaseUnitTest`, which made the local command stricter, and that is how this branch found the release-variant failure. The line now says so; the command is unchanged.
