# Notes — Unlock the app with a fingerprint

Issue: #8 — Unlock the app with a fingerprint (https://github.com/YasarMahomedAbbas/paperless-companion/issues/8)
Branch: `feat/8-fingerprint-unlock`

## Planning

- Approach: a second, auth-bound Keystore key seals the token. It only works for 10 s after the app's own BiometricPrompt (fingerprint, or PIN/pattern), so the token is decrypted once per unlock and kept in memory until the app locks again. A gate ViewModel shows Sign in / Lock / Home. Every incoming document waits in a buffer until Home is unlocked.
- Decided (the pipeline took these on the user's behalf, recommended options):
  - 1a, a fixed 1-minute background timeout before re-locking. Rejected: lock on every background (1b), a timeout choice (1c). Flags: ux, security.
  - 2a, a "Lock with fingerprint" switch in a small overflow menu on the home top bar, next to Sign out. Rejected: a new Settings screen (2b, overlaps #6), asking right after sign-in (2c). Flags: ux.
  - 3a, a time-limited auth-bound key (10 s), with the token held in memory until the app locks. Rejected: a per-use key needing a fresh unlock for every decryption (3b; device-credential CryptoObject only on API 30+). Flags: security.
- Decided (by start-ticket; stood, no disagreement):
  - The lock is on by default when the phone has a screen lock. With no screen lock the switch is hidden and the plain key is used, as today.
  - The prompt is the androidx biometric library (1.1.0 stable) with a PIN/pattern fallback, and MainActivity becomes a FragmentActivity.
  - Turning the switch on or off asks for the unlock, then re-seals the token with the matching key. Existing installs move to the locked key on their first unlock.
  - When the phone voids the key (a new fingerprint, or the screen lock removed), the stored sign-in is cleared and the sign-in screen says why.
  - The timeout policy is plain-Kotlin domain code (`AppLock`), tested with a fake clock. The Keystore stays in data/, behind a fakeable vault, and the prompt stays in ui/.
- Decided (by start-ticket, in the checklist): a fresh sign-in with the lock active is sealed only after a "confirm it's you" prompt. On a re-lock, Home stays composed under an opaque overlay, so a scan result that arrives while locked isn't lost. The app never decrypts the token on a bare read(), because a recent screen unlock would otherwise open the time-limited key without the app's own prompt.
- Open risks:
  - Android 8–10: an unlock with a weak-class face or fingerprint may not release the auth-bound key (UserNotAuthenticatedException). The app then stays locked and asks for the PIN/pattern instead. Both test phones run newer Android, so this path is untested on hardware.
  - Device-only behaviour (the prompt, the key refusing without it, enrolment voiding the key) isn't covered by unit tests. It needs the manual check on both phones (GrapheneOS included), which the pipeline can't do unattended.
  - BiometricPrompt 1.1.0 may need an AppCompat theme for its pre-API-28 fingerprint dialog. Check this during implementation.
- Outstanding (for the user, after release): the on-device check on both our phones, GrapheneOS included. Covers launch → prompt, the PIN/pattern fallback, cancel, re-lock after more than 1 minute away, a share from another app while locked, a scan that returns after the re-lock, adding a fingerprint (sign in again, with the notice), the switch off and on, and a phone with no screen lock. "Tested on both our phones" is a Done-when criterion that the unattended pipeline can't meet itself.

## Implementation

- Shipped:
  - Domain: `StoredSignIn` (`app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/domain/model/Models.kt:9`), `SettingsRepository.read()` with `load()` as a default and `AppLockRepository` (`app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/domain/repository/Repositories.kt:36`), and the 1-minute policy `AppLock` (`app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/domain/usecase/AppLock.kt:23`).
  - Data: `TokenVault` / `KeyValueStore` seams (`app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/data/TokenVault.kt`, `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/data/KeyValueStore.kt`); `KeystoreTokenVault` with the auth-bound `paperless-token-locked` key, 10 s validity, API 30+ / 26–29 branches (`app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/data/KeystoreTokenVault.kt:23`); `SealedSettingsRepository` replaces `KeystoreSettingsRepository` and holds every decision: read without touching the vault (`app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/data/SealedSettingsRepository.kt:32`), unlock with migration (`:51`), unsaved save (`:88`), switch re-seal (`:103`).
  - UI: `GateViewModel` (`app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/ui/lock/GateViewModel.kt:40`), `askToUnlock` (`app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/ui/lock/UnlockPrompt.kt`), `LockScreen` (`app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/ui/lock/LockScreen.kt:28`), the gate and the `pending` buffer in `MainActivity` (now a `FragmentActivity`, lifecycle observer at `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/MainActivity.kt:51`), the overflow menu with the lock switch (`app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/ui/home/HomeScreen.kt:219`), the voided notice on sign-in (`app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/ui/login/LoginScreen.kt:52`).
  - Build: biometric 1.1.0, fragment-ktx 1.8.9, appcompat 1.6.1; theme parent `Theme.AppCompat.Light.NoActionBar`; kover excludes `KeystoreTokenVault` and `PrefsStore` instead of the old repository.
- Tests: full CI command green: 95 passed / 0 failed (new: SealedSettingsRepositoryTest 20, AppLockTest 6, GateViewModelTest 15); koverVerify passes at 95.4 % line coverage; lintDebug no errors (warnings only: newer dependency versions, the existing `mipmap-anydpi-v26` folder).
- Outstanding:
  - The device checks in checkpoint 3's Verify (prompt, PIN fallback, cancel, re-lock after 1 min, share while locked, scan returning after a re-lock, switch off/on, no screen lock, both phones incl. GrapheneOS) — not runnable unattended; left for the user after release.
  - Not fixed, found while wiring (pre-existing, not this ticket): `LoginViewModel` is activity-scoped and keeps `signedIn` after a sign-in, so after Sign out the sign-in screen's `LaunchedEffect` fires `onSignedIn` again with the old config and goes straight back to Home on a cleared repository. Same behaviour as before this branch; worth its own ticket with a test.
- Contradicted the plan:
  - The pre-API-28 AppCompat risk was real: biometric 1.1.0 depends on AppCompat and its fingerprint dialog needs an AppCompat theme. Resolved by declaring appcompat and switching the theme parent (checklist 3.1 updated).
  - Android docs: `setInvalidatedByBiometricEnrollment` only applies to keys with no positive validity duration, so the 10 s time-limited key is probably **not** voided by adding a fingerprint, only by removing the screen lock. The Voided path and its notice still cover that; the device check "adding a fingerprint voids the key" should expect the app to keep working instead. Not changed in code (option 3a stands).
  - Removing the screen lock leaves `token_locked` set with no way to prompt; `read()` now treats that as Voided (clear, sign in again) instead of showing a lock screen that can't unlock (checklist 1.4 updated).
  - `hasUnsavedSignIn()` moved from 2.2 to 1.4 because the save tests need it. `seal()` never reports a voided key: `KeystoreTokenVault` deletes it and seals with a fresh one.
  - The design manual has no warning colour or ui-tooling for previews yet: the notice uses the error colour and the lock screen has no `@Preview`; both recorded in `docs/design/README.md` § Open decisions.

## Review

- Fixed:
  - `SealedSettingsRepository.unlock()` only caught `VaultLockedException` / `VaultVoidedException`, so a seal the key could no longer open (e.g. the screen lock removed and set again between launches: the locked key is regenerated, and AES-GCM fails its tag check) escaped as a crash on every unlock, with the lock screen re-prompting at each launch. It now clears the sign-in and reports Voided, as `read()` already did for the plain key. Test: `a seal the key can no longer open clears the sign-in instead of crashing`.
  - The `pending` buffer lived in a `MainActivity` field, so a scan or share waiting on the lock screen was dropped by a rotation before the unlock (before this branch a scan went straight to the rotation-safe `HomeViewModel`). Moved into `GateViewModel` (`pending`, `addPending`, `onPendingConsumed`). Test: `documents wait in the gate until home takes them`.
- Raised: https://github.com/YasarMahomedAbbas/paperless-companion/issues/16 (Sign out bounces straight back to Home; pre-existing, noted during implementation).
- API: none touched (no Paperless call added or changed).
- Layers: clean. `data/` holds the Keystore and prefs; `domain/` has `StoredSignIn`, `AppLockRepository`, `AppLock` in the app's words; `ui/` only sees domain. Security: the token isn't logged or put in a URL; it's stored only AES-GCM-sealed by a Keystore key in private prefs; no manifest change.
