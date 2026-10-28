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
