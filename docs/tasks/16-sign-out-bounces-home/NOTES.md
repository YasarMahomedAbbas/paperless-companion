# Notes — Sign out bounces straight back to Home

Issue: #16 — Sign out bounces straight back to Home (https://github.com/YasarMahomedAbbas/paperless-companion/issues/16)
Branch: `fix/16-sign-out-bounces-home`

## Planning

- Approach: `LoginViewModel.onSignedInHandled()` clears `signedIn` once `LoginScreen` has passed it to the gate, so the activity-scoped ViewModel can't re-deliver an old sign-in after Sign out. Tested at the ViewModel seam.
- Decided (the pipeline took these on the user's behalf, recommended options):
  - Q1-1: consume the result with a handler that the screen's effect calls. Rejected: a fresh ViewModel per visit to the sign-in screen (Q1-2, form blank every time, the change sits in activity wiring that is hard to unit test), and a one-shot event channel (Q1-3, changes the state shape for little gain). Flag ux: the server address and username stay filled in after sign-out (the password is cleared).
  - Q2-1: ViewModel-level tests only. Rejected: adding Robolectric + compose ui-test for a real second-LoginScreen test (Q2-2). Flag scope: no Compose UI test infrastructure is added, so the call from the screen's effect to the handler is covered only at the ViewModel seam, not by a test that fails if `LoginScreen` stops calling it.
- Decided (by start-ticket; flag if you disagree): `LoginViewModel` stays at activity scope and `MainActivity` is unchanged. There are no `domain/` or `data/` changes and no visual change.
- Open risks: the "fails without the fix" test fails without the fix only because the handler doesn't exist yet (a compile failure). The behaviour assertions (null after handling, fields kept) guard the semantics from then on.
