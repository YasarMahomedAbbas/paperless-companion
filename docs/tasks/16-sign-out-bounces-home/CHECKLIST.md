# Checklist — Sign out bounces straight back to Home

Issue: #16 — Sign out bounces straight back to Home (https://github.com/YasarMahomedAbbas/paperless-companion/issues/16)
Branch: `fix/16-sign-out-bounces-home`
Problem: After tapping Sign out, the sign-in screen shows for a moment and then the app goes back to Home with the old config, on a settings store that sign-out has just cleared. Sign out looks like it didn't work, and the next launch asks to sign in.

## Done when

- After Sign out, the sign-in screen stays up until the user signs in again.
- `LoginViewModel` forgets a sign-in result once the screen has passed it on, and `LoginScreen` tells it so.
- The server address and username are still filled in on the sign-in screen after sign-out. The password stays cleared.
- A ViewModel test fails without the fix and passes with it.
- CI passes: `./build.sh ./gradlew --console=plain testDebugUnitTest koverVerify lintDebug assembleDebug`.

## References

- Paperless API: no call is added or changed.
- #8 (fingerprint unlock), whose implementation notes first recorded this bug.

## Context

`MainActivity` creates `LoginViewModel` at activity scope with `viewModel<LoginViewModel>(factory = container.loginViewModelFactory)`, so the same instance comes back every time `GateState.SignIn` is shown. A successful `submit()` stores the config in `LoginUiState.signedIn`. `LoginScreen` runs `LaunchedEffect(state.signedIn) { state.signedIn?.let(onSignedIn) }`. After `GateViewModel.onSignedOut()` the sign-in screen is composed again, the effect sees the old non-null `signedIn`, and it calls `gate.onSignedIn(oldConfig)`. The pipeline settled the fix: consume the result rather than re-scoping the ViewModel (Q1-1). Tests stay at the ViewModel seam, with no Compose UI test infrastructure (Q2-1). This is a UI-only change: it touches no `domain/` or `data/` code and changes nothing visual.

## Test plan

- Unit (`LoginViewModelTest`):
  - After a successful sign-in and `onSignedInHandled()`, `state.signedIn` is null.
  - After sign-in and the handler, `serverUrl` and `username` keep their values and `password` stays `""`.
  - Simulated sign-out: a new collector of `state` after the handler sees `signedIn == null`, so a recomposed LoginScreen has nothing to fire.
  - A second sign-in after the handler exposes the new config again.
  - Calling `onSignedInHandled()` with nothing pending is harmless.
- MockWebServer: none (no call changes).
- Real server: none needed (no Paperless call changes). Record it as skipped.
- Device only: sign in, tap Sign out, and confirm the sign-in screen stays with the address and username filled in. Sign in again and confirm Home appears. With the fingerprint lock on, repeat the check.

---

## Checkpoint 1 — Consume the sign-in result · commit: `fix(auth): stay signed out after Sign out`

Goal: a sign-in result reaches the gate once. The sign-in screen shown after Sign out does not sign in on its own.

### [ ] 1.1 — Add the handler to `LoginViewModel`

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/ui/login/LoginViewModel.kt`
Add `fun onSignedInHandled() = _state.update { it.copy(signedIn = null) }` with a one-line KDoc: the screen calls it after passing the config on, because the ViewModel outlives the screen (activity scope) and would otherwise re-deliver the result after Sign out. Leave `serverUrl` and `username` unchanged.

### [ ] 1.2 — Call it from `LoginScreen`

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/ui/login/LoginScreen.kt`
Change the effect to:
`LaunchedEffect(state.signedIn) { state.signedIn?.let { onSignedIn(it); viewModel.onSignedInHandled() } }`
(Format it across lines as the file's style requires.) Leave `MainActivity` unchanged: the ViewModel stays at activity scope.

### [ ] 1.3 — Tests

File: `app/src/test/java/io/github/yasarmahomedabbas/paperlesscompanion/ui/LoginViewModelTest.kt`
Add the five tests from the test plan, using the existing `fillIn()`, `FakeServer` and `runTest(main.dispatcher)` + `advanceUntilIdle()` pattern. Suggested names:
- `a handled sign-in is not delivered again`
- `a handled sign-in keeps the address and username`
- `the sign-in screen after sign-out has nothing to sign in with`
- `signing in again after sign-out delivers the new config`
- `handling with nothing pending changes nothing`

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*LoginViewModelTest*'`
- Then the full CI command (koverVerify and lint included).
- Observable: `signedIn` is null once handled, and the address and username survive.

---

## Out of scope

- Re-scoping `LoginViewModel` per visit, or a one-shot event channel (rejected at planning).
- Compose UI test infrastructure (Robolectric, compose ui-test). The call from the screen's effect to the handler is covered only at the ViewModel seam.
- Any change to `GateViewModel`, `HomeViewModel.signOut()`, the settings store or the fingerprint lock.
- Any visual change to the sign-in screen.
