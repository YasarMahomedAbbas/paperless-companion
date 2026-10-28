# Checklist — A permission refusal (403) tells the user to sign in again

Issue: #21 — A permission refusal (403) tells the user to sign in again (https://github.com/YasarMahomedAbbas/paperless-companion/issues/21)
Branch: `fix/21-forbidden-is-not-signed-out`
Problem: when Paperless refuses an action because the user lacks a permission (for example, editing a document they can only view), the app tells them to sign out and sign in again, which can't help.

## Done when

- A 403 on any signed-in call (document library: list, get, labels, files, update, delete, trash, restore, bulk edit; upload; task status) is a `ServerException` of the new kind `FORBIDDEN`. Its `userMessage()` is "You don't have permission to do that in Paperless."
- A 401 on any call is still `UNAUTHORIZED` ("Paperless rejected the login. Sign out and sign in again.").
- Sign-in (`POST /api/token/`) still maps 400, 401 and 403 to `UNAUTHORIZED`, and the login screen still says "Wrong username or password".
- Each mapping has a test that fails without the fix.
- `./build.sh ./gradlew --console=plain testDebugUnitTest koverVerify lintDebug assembleDebug` passes.

## References

- Paperless API: no endpoint, field or request changes. Only the meaning of HTTP status codes changes.
  - Authentication (docs `api.md` § Authentication): Basic, session and token auth. `DEFAULT_AUTHENTICATION_CLASSES` is `PaperlessBasicAuthentication`, `TokenAuthentication`, `SessionAuthentication`. `PaperlessBasicAuthentication.authenticate_header` returns `Token` for non-Basic requests, so DRF answers a missing, invalid or expired token with **401**.
  - Permissions (docs `api.md` § Permissions): object-level permissions and owners. A signed-in user without view, change or delete rights on an object, or without the model permission (for example `add_document` for `post_document`), gets **403**.
  - `POST /api/token/` with wrong credentials answers **400** (`non_field_errors`).
  - Same in API v9 and v10.
- The docs site refuses automated fetches (403), so the docs were read from the repo source (`docs/api.md`, `src/paperless/settings/__init__.py`, `src/paperless/auth.py` on `dev`). The live schema couldn't be checked because `verify.env` is absent.
- Found in the self-review of #7. Must land before the edit screens (#10, #11) show the message.

## Context

`PaperlessHttp.httpError` maps 401 and 403 (and 400 when `badRequestIsAuth`) to `UNAUTHORIZED`. Every call goes through `PaperlessHttp.callUnless` or `download`. Only `PaperlessApi.login` passes `badRequestIsAuth = true`. Decision (the pipeline took it on the user's behalf): 403 means FORBIDDEN on every signed-in call, upload and task status included, because a working-but-unprivileged token is the only way Paperless answers 403 there. Sign-in keeps 400/401/403 as UNAUTHORIZED. To express that, `badRequestIsAuth` becomes `signIn: Boolean` with the same call sites. The domain gains `Kind.FORBIDDEN` and a message in `userMessage()`. The only exhaustive `when (kind)` is `userMessage()` in `UploadDocumentUseCase.kt`. ViewModels already show `userMessage()` and need no change. `LoginViewModel` only special-cases `UNAUTHORIZED`, which sign-in still produces.

## Test plan

- Unit: `userMessage()` for FORBIDDEN is the new text and doesn't say to sign in. The existing "every error kind has a user message" test covers the new kind automatically.
- MockWebServer:
  - `PaperlessLibraryTest`: 403 on `documents` → FORBIDDEN; 403 on `update` → FORBIDDEN; 403 on `file` → FORBIDDEN (this goes through `download`, a separate error path), with nothing left on disk.
  - `PaperlessApiTest`: 403 on `upload` → FORBIDDEN; 403 on `task` → FORBIDDEN; 403 on `login` → UNAUTHORIZED; 401 on `login` → UNAUTHORIZED. The existing 400-on-login and 401-on-upload/documents tests stay.
  - No v9/v10 difference: status codes are the same in both.
- Real server: none possible without `verify.env`. If it exists at verification time, a read-only check that a bogus token on `GET /api/documents/?page_size=1` answers 401 (not 403) and a bad sign-in on `/api/token/` answers 400.
- Device only: none.
- Failing-first: the new 403 → FORBIDDEN tests fail against the old mapping, which answers UNAUTHORIZED. Record that in NOTES.

---

## Checkpoint 1 — Tell a permission refusal apart from a rejected login · commit: `fix(api): tell a permission refusal apart from a rejected login`

Goal: every signed-in 403 surfaces as FORBIDDEN with its own message, while sign-in failures and 401s are unchanged.

### [ ] 1.1 — Domain kind and message

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/domain/model/Models.kt`
Add `FORBIDDEN` to `ServerException.Kind`, after `UNAUTHORIZED`: `enum class Kind { UNREACHABLE, NO_ANSWER, UNAUTHORIZED, FORBIDDEN, UNREADABLE, NOT_FOUND, SERVER }`. Extend the KDoc with "UNAUTHORIZED: the server doesn't accept the login. FORBIDDEN: signed in, but the server refused this action for this user."

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/domain/usecase/UploadDocumentUseCase.kt`
In `userMessage()`, add `Kind.FORBIDDEN -> "You don't have permission to do that in Paperless."`.

### [ ] 1.2 — HTTP mapping

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/data/PaperlessHttp.kt`
Rename the parameter `badRequestIsAuth` to `signIn` in `call`, `callUnless` and `httpError` (KDoc on `call`: "[signIn]: the request is a sign-in, so 400 and 403 mean wrong credentials"). New mapping:
- `code == 401 || (signIn && (code == 400 || code == 403))` → `ServerException(Kind.UNAUTHORIZED, "Wrong username or password")`. Keep the text: the login screen and existing tests rely on it.
- `code == 403` → `ServerException(Kind.FORBIDDEN, "Paperless returned HTTP 403: ${text.take(200)}")`
- 404 and the rest unchanged.
`download` passes `signIn = false`.

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/data/PaperlessApi.kt`
`login` calls `call(request, signIn = true)`. Rename the private `call` helper's parameter to match.

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/domain/usecase/EditDocumentsUseCase.kt`
Line 10 comment: "Every one throws ServerException: UNAUTHORIZED when signed out, FORBIDDEN when the user may not change it."

### [ ] 1.3 — Tests

File: `app/src/test/java/io/github/yasarmahomedabbas/paperlesscompanion/data/PaperlessLibraryTest.kt`
Next to `an expired token is unauthorized`, add:
- `` `a permission refusal is forbidden, not a rejected login` ``: `respond(403, """{"detail":"You do not have permission to perform this action."}""")`, then `expectError(Kind.FORBIDDEN) { library.documents(config, DocumentQuery()) }`.
- `` `a refused update for lack of permission is forbidden` ``: same 403, `library.update(config, 7, <a minimal DocumentChanges as in the existing update tests>)` → FORBIDDEN.
- `` `a refused file download for lack of permission is forbidden and leaves nothing behind` ``: 403 on `library.file(...)` (mirror `a gone document's file is not found and leaves nothing behind`) → FORBIDDEN, and the files dir has no file and no `.part`.

File: `app/src/test/java/io/github/yasarmahomedabbas/paperlesscompanion/data/PaperlessApiTest.kt`
- `` `an upload refused for lack of permission is forbidden` ``: 403 → `api.upload(config, document())` → FORBIDDEN.
- `` `a status check refused for lack of permission is forbidden` ``: 403 → `api.task(config, "t")` → FORBIDDEN.
- `` `login refused with 403 or 401 is unauthorized` ``: for each of 403 and 401, `respond(code, ...)`, then `api.login(...)` → UNAUTHORIZED.

File: `app/src/test/java/io/github/yasarmahomedabbas/paperlesscompanion/domain/UploadDocumentUseCaseTest.kt`
- `` `a permission refusal does not tell the user to sign in again` ``: `ServerException(Kind.FORBIDDEN, "x").userMessage()` equals "You don't have permission to do that in Paperless." and doesn't contain "sign in".

Before 1.2 lands, run the new 403 tests against the old mapping to confirm they fail (UNAUTHORIZED), and note it in NOTES.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*PaperlessApiTest*' --tests '*PaperlessLibraryTest*' --tests '*UploadDocumentUseCaseTest*' --tests '*LoginViewModelTest*'`
- Then the full CI command: `./build.sh ./gradlew --console=plain testDebugUnitTest koverVerify lintDebug assembleDebug`

---

## Out of scope

- Hiding or disabling actions the user can't perform (for example reading `user_can_change` / `permissions`). That is UI work for #10/#11 or a later ticket.
- Any screen or layout change. The new text shows wherever `userMessage()` is shown already.
- Detecting remote-user API auth (where a bad token can answer 403). It's recorded as an open risk.
- Changing `GitHubReleases`' own 403 handling (rate limit), which is a separate exception type.
