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
