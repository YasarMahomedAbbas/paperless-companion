# Checklist — Sign out bounces straight back to Home

Issue: #16 — Sign out bounces straight back to Home (https://github.com/YasarMahomedAbbas/paperless-companion/issues/16)
Branch: `fix/16-sign-out-bounces-home`
Problem: After tapping Sign out, the sign-in screen shows for a moment and then the app goes back to Home with the old config, on a settings store that sign-out has just cleared. Sign out looks like it didn't work, and the next launch asks to sign in.

## Done when

- After Sign out, the sign-in screen stays up until the user signs in again.
- `LoginViewModel` forgets a sign-in result once the screen has passed it on, and `LoginScreen` tells it so.
- The server address and username are still filled in on the sign-in screen after sign-out. The password stays cleared.
- Compose UI tests run on the JVM (Robolectric) inside `testDebugUnitTest`, with versions pinned to the app's targetSdk (35), and the Robolectric SDK jar comes from the Gradle cache, so the Docker build downloads it once and runs offline after that.
- A test through the real `LoginScreen` (a second LoginScreen after sign-out does not sign in on its own) fails at runtime without the fix and passes with it.
- CI passes: `./build.sh ./gradlew --console=plain testDebugUnitTest koverVerify lintDebug assembleDebug`.

## References

- Paperless API: no call is added or changed.
- #8 (fingerprint unlock), whose implementation notes first recorded this bug.
- Robolectric 4.14.1 (Maven Central): its `DefaultSdkProvider` maps SDK 35 to `org.robolectric:android-all-instrumented:15-robolectric-12650502-i7` (preinstrumented version 7; checked in the 4.14.1 jar, and the artifact exists on Maven Central). SDK 35 under Robolectric needs Java 21: the Docker image (`eclipse-temurin:21-jdk`) and CI (`setup-java` 21) both have it.
- Compose BOM 2024.12.01 resolves `ui-test-junit4` and `ui-test-manifest` to 1.7.6.

## Context

`MainActivity` creates `LoginViewModel` at activity scope with `viewModel<LoginViewModel>(factory = container.loginViewModelFactory)`, so the same instance comes back every time `GateState.SignIn` is shown. A successful `submit()` stores the config in `LoginUiState.signedIn`. `LoginScreen` runs `LaunchedEffect(state.signedIn) { state.signedIn?.let(onSignedIn) }`. After `GateViewModel.onSignedOut()` the sign-in screen is composed again, the effect sees the old non-null `signedIn`, and it calls `gate.onSignedIn(oldConfig)`. The fix consumes the result (`onSignedInHandled()`) rather than re-scoping the ViewModel (Q1-1, taken by the pipeline). The user chose to prove it through the real screen (Q2 revised to option 1 on the issue): the project gets Robolectric + Compose ui-test so `LoginScreen` can be composed on the JVM, shown, hidden (Home) and shown again (sign-out) around one activity-scoped ViewModel. Today all tests are plain JVM (junit, coroutines-test, MockWebServer) and Robolectric would fetch its SDK jar from Maven into `~/.m2`, which in the Docker container is `/tmp` and lost after every run. So the SDK jar is declared as a Gradle dependency instead (resolved into `GRADLE_USER_HOME`, which `build.sh` mounts from the host cache and `setup-gradle` caches on CI) and Robolectric runs in offline mode pointed at it. The fix touches no `domain/` or `data/` code and changes nothing visual.

## Test plan

- Unit (`LoginViewModelTest`, plain JVM as today):
  - After a successful sign-in and `onSignedInHandled()`, `state.signedIn` is null.
  - After sign-in and the handler, `serverUrl` and `username` keep their values and `password` stays `""`.
  - Calling `onSignedInHandled()` with nothing pending changes nothing.
- Compose UI on Robolectric (`LoginScreenTest`, through the real `LoginScreen`):
  - Smoke (checkpoint 1): the sign-in screen renders its fields and the Sign in button.
  - Fill in, tap Sign in: `onSignedIn` fires once with the config.
  - Then hide the screen (Home) and show it again with the same ViewModel (sign-out): `onSignedIn` is still at one call, the Sign in button is shown, and the address and username fields keep their text. Fails without the fix (two calls).
  - Sign in again after that: `onSignedIn` fires a second time.
- MockWebServer: none (no call changes).
- Real server: none needed (no Paperless call changes). Record it as skipped.
- Device only: sign in, tap Sign out, and confirm the sign-in screen stays with the address and username filled in. Sign in again and confirm Home appears. With the fingerprint lock on, repeat the check.

---

## Checkpoint 1 — Compose UI tests on the JVM · commit: `build(test): run Compose UI tests on the JVM with Robolectric`

Goal: `testDebugUnitTest` can compose a screen with `createComposeRule()` under Robolectric at SDK 35, offline, with the SDK jar taken from the Gradle cache.

### [x] 1.1 — Version catalog

File: `gradle/libs.versions.toml`
- `[versions]`: `robolectric = "4.14.1"` and `robolectricSdk = "15-robolectric-12650502-i7"`, with a comment above them: the SDK jar is the one Robolectric 4.14.1 uses for SDK 35 (the targetSdk); bumping Robolectric or the targetSdk means bumping this too (read the new version's `DefaultSdkProvider`), and tests fail offline until they match.
- `[libraries]`:
  - `robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }`
  - `robolectric-android-all = { group = "org.robolectric", name = "android-all-instrumented", version.ref = "robolectricSdk" }`
  - `compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }` (BOM-managed)
  - `compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }` (BOM-managed)

### [x] 1.2 — Gradle wiring

File: `app/build.gradle.kts`
- `dependencies`: `testImplementation(libs.robolectric)`, `testImplementation(libs.compose.ui.test.junit4)`, `debugImplementation(libs.compose.ui.test.manifest)` (it puts the `ComponentActivity` that `createComposeRule()` launches into the debug manifest; the BOM already applies through `implementation(platform(...))`, as for `compose.ui.tooling`).
- A configuration for the SDK jar, outside `dependencies` resolution of the app: `val robolectricSdk by configurations.creating { isCanBeConsumed = false; isTransitive = false }` and in `dependencies` `robolectricSdk(libs.robolectric.android.all)`.
- In `android { }`: `testOptions { unitTests { isIncludeAndroidResources = true } }` (Robolectric needs the merged resources and manifest for Compose and Material 3).
- After `android { }`: configure every unit-test task to run Robolectric offline against the resolved jar, lazily so the 100+ MB jar is resolved only when tests run and is a tracked input:
  ```kotlin
  // Robolectric would download its SDK jar into ~/.m2, which the Docker build loses after
  // every run. Gradle resolves it into GRADLE_USER_HOME instead (cached on the host by
  // build.sh and on CI by setup-gradle), and Robolectric runs offline against it.
  tasks.withType<Test>().configureEach {
      inputs.files(robolectricSdk).withPropertyName("robolectricSdk").withNormalizer(ClasspathNormalizer::class.java)
      jvmArgumentProviders += CommandLineArgumentProvider {
          listOf(
              "-Drobolectric.offline=true",
              "-Drobolectric.dependency.dir=${robolectricSdk.singleFile.parentFile}",
          )
      }
  }
  ```
  (Robolectric's offline resolver looks for `android-all-instrumented-<version>.jar` by name in that directory, which is the jar's own folder in the Gradle cache. If the implementer finds the lambda `CommandLineArgumentProvider` captures the configuration in a way the configuration cache rejects, an explicit `Sync` task into `layout.buildDirectory.dir("robolectric")` that the test tasks depend on is the fallback; record which in NOTES.)
- Leave the Kover filters unchanged: `*Screen*` classes stay excluded, so the screen test adds no coverage numbers and the 80% bound is unaffected.

### [x] 1.3 — Pin the SDK

File: `app/src/test/resources/robolectric.properties` (new)
`sdk=35` with a comment: matches `targetSdk`; the jar is pinned in the version catalog.

### [x] 1.4 — Keep Dependabot from moving the SDK jar on its own

File: `.github/dependabot.yml`
Under the gradle entry, add `ignore: [{ dependency-name: "org.robolectric:android-all-instrumented" }]` with a comment: it moves only together with Robolectric (see the catalog comment). A Robolectric bump then fails its PR's tests until the jar is bumped by hand, which is the intended signal.

### [x] 1.5 — Smoke test

File: `app/src/testDebug/java/io/github/yasarmahomedabbas/paperlesscompanion/ui/LoginScreenTest.kt` (new; in `testDebug`, not `test`, because `koverVerify` also runs `testReleaseUnitTest`, and the release manifest has no `ComponentActivity` since `ui-test-manifest` is debug-only)
`@RunWith(RobolectricTestRunner::class) @Config(application = Application::class) class LoginScreenTest` (a plain `Application`, so Robolectric doesn't run `PaperlessApp.onCreate`, which in release schedules WorkManager) with `@get:Rule val compose = createComposeRule()`, a `LoginViewModel(LoginUseCase(FakeServer(), FakeSettings()))` built in the test, and one test `the sign-in screen shows its fields and the Sign in button`: `setContent { LoginScreen(viewModel, notice = null, onSignedIn = {}) }`, then `onNodeWithText("Server address")`, `"Username"`, `"Password"` exist and `onNodeWithText("Sign in")` is displayed. Do not use `MainDispatcherRule` here: under Robolectric `viewModelScope` runs on the main looper, and the compose rule's `waitForIdle()` drives it.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*LoginScreenTest*'` (first run downloads the SDK jar into the host Gradle cache).
- Run it a second time with networking off for the container (`docker run --network none`, e.g. a one-off copy of the `build.sh` command line) and confirm it passes: the jar comes from the cache, not the network. Record the outcome in NOTES.
- Then the full CI command (koverVerify and lint included). Lint must stay clean with `ui-test-manifest` in the debug manifest.
- Record in NOTES the extra time the Robolectric class adds to `testDebugUnitTest`.

---

## Checkpoint 2 — Consume the sign-in result · commit: `fix(auth): stay signed out after Sign out`

Goal: a sign-in result reaches the gate once. The sign-in screen shown after Sign out does not sign in on its own, proven through the real screen.

### [x] 2.1 — The failing screen test first

File: `app/src/testDebug/java/io/github/yasarmahomedabbas/paperlesscompanion/ui/LoginScreenTest.kt`
Add, before touching the main code:
- `a second sign-in screen after sign-out does not sign in on its own`: hold `var showLogin by mutableStateOf(true)` in the test and a list of configs `onSignedIn` received. `setContent { if (showLogin) LoginScreen(viewModel, null) { signedIn += it } }`. Type into "Server address" (`paperless:8000`), "Username" (`anna`), "Password" (`pw`) with `performTextInput`, click "Sign in", `waitForIdle()`, assert one config `ServerConfig("http://paperless:8000", "token-123")`. Then `showLogin = false`, `waitForIdle()` (Home), `showLogin = true`, `waitForIdle()` (sign-out): assert still exactly one config, "Sign in" is displayed, and the address and username fields still show `paperless:8000` and `anna`.
- `signing in again after sign-out signs in once more`: the same sequence, assert one config, then type the password again, click "Sign in", `waitForIdle()`, and assert two configs. (The one-config check makes it fail on the old code too: there the re-delivery is the second call and the real second sign-in, an equal `ServerConfig`, doesn't re-run the effect.)
Run it and confirm the first test fails on the current code (two configs), and note the failure in NOTES.

### [x] 2.2 — Add the handler to `LoginViewModel`

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/ui/login/LoginViewModel.kt`
Add `fun onSignedInHandled() = _state.update { it.copy(signedIn = null) }` with a KDoc: the screen calls it after passing the config on, because the ViewModel outlives the screen (activity scope) and would otherwise re-deliver the result after Sign out. Leave `serverUrl` and `username` unchanged.

### [x] 2.3 — Call it from `LoginScreen`

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/ui/login/LoginScreen.kt`
Change the effect to:
`LaunchedEffect(state.signedIn) { state.signedIn?.let { onSignedIn(it); viewModel.onSignedInHandled() } }`
(Format it across lines as the file's style requires.) Leave `MainActivity` unchanged: the ViewModel stays at activity scope.

### [x] 2.4 — ViewModel tests

File: `app/src/test/java/io/github/yasarmahomedabbas/paperlesscompanion/ui/LoginViewModelTest.kt`
Add three tests with the existing `fillIn()`, `FakeServer` and `runTest(main.dispatcher)` + `advanceUntilIdle()` pattern:
- `a handled sign-in is not delivered again`
- `a handled sign-in keeps the address and username`
- `handling with nothing pending changes nothing`

### [x] 2.5 — README test sentence

File: `README.md`
The "Tests cover…" paragraph says Compose screens need a device. Change it to say screens can be tested on the JVM with Robolectric + Compose ui-test (the SDK jar comes from the Gradle cache, offline) and that screens are still left out of the coverage numbers.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*LoginScreenTest*' --tests '*LoginViewModelTest*'`
- Then the full CI command (koverVerify and lint included).
- Observable: the screen test that failed in 2.1 passes; `signedIn` is null once handled, and the address and username survive.

---

## Out of scope

- Re-scoping `LoginViewModel` per visit, or a one-shot event channel (rejected at planning).
- Compose UI tests for any screen other than `LoginScreen`, and counting screens in the coverage numbers (the Kover filters stay as they are).
- Instrumented (device/emulator) tests and `androidTest`.
- Changing CI caching beyond what `setup-gradle` already caches (the SDK jar lives in the Gradle cache it already handles).
- Any change to `GateViewModel`, `HomeViewModel.signOut()`, the settings store or the fingerprint lock.
- Any visual change to the sign-in screen.
