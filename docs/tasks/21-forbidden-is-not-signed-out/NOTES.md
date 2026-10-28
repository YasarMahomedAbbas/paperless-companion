# Notes — A permission refusal (403) tells the user to sign in again

Issue: #21 — A permission refusal (403) tells the user to sign in again (https://github.com/YasarMahomedAbbas/paperless-companion/issues/21)
Branch: `fix/21-forbidden-is-not-signed-out`

## Planning

- Approach: a new domain `ServerException.Kind.FORBIDDEN` with its own `userMessage()` ("You don't have permission to do that in Paperless."). `PaperlessHttp` maps 403 to FORBIDDEN on every signed-in call and keeps 401 (and 400/403 on sign-in) as UNAUTHORIZED. `badRequestIsAuth` becomes `signIn`.
- Decided (the pipeline took this on the user's behalf; flags: api, ux): every signed-in call maps 403 to FORBIDDEN, meaning the document library plus upload and task status, not only the library calls the issue names. Sign-in keeps 400/401/403 as UNAUTHORIZED. Reason: Paperless's first authenticator (`PaperlessBasicAuthentication`) answers non-Basic requests with a `Token` auth header, so a missing or invalid token is 401. A 403 on a signed-in call therefore means the token works but the user lacks the permission, upload (`add_document`) included.
- Decided (routine, by the planner): keep the "Wrong username or password" text on UNAUTHORIZED; no ViewModel or screen changes, since they already show `userMessage()`; FORBIDDEN's exception message carries "Paperless returned HTTP 403: <body>" for logs.
- Open risks: on a server with remote-user API auth enabled (`PAPERLESS_ENABLE_HTTP_REMOTE_USER_API`), `PaperlessRemoteUserAuthentication` comes first and has no auth header, so DRF answers a bad token with 403. Such a user would see "no permission" instead of "sign in again". The Paperless docs site refused automated fetches, so the docs were read from the repo source, and `verify.env` is absent, so the live schema and real-server status codes weren't checked.

## Implementation

- Shipped:
  - `ServerException.Kind.FORBIDDEN` with its KDoc (`domain/model/Models.kt:51-54`) and its message "You don't have permission to do that in Paperless." (`domain/usecase/UploadDocumentUseCase.kt:88`).
  - `PaperlessHttp.httpError` (`data/PaperlessHttp.kt:192-203`): 401, and 400/403 on a sign-in, stay UNAUTHORIZED ("Wrong username or password"); any other 403 is FORBIDDEN ("Paperless returned HTTP 403: <body>"). `badRequestIsAuth` is now `signIn` in `call`/`callUnless`/`httpError` and `PaperlessApi`'s private `call`; `download` passes `signIn = false`. `PaperlessApi.login` passes `signIn = true` (`data/PaperlessApi.kt:42`).
  - `EditDocumentsUseCase.kt:10-11` comment names FORBIDDEN.
  - Tests: `PaperlessLibraryTest` 403 on documents/update/file (file leaves nothing behind), `PaperlessApiTest` 403 on upload/task → FORBIDDEN and 403/401 on login → UNAUTHORIZED, `UploadDocumentUseCaseTest` FORBIDDEN's message doesn't say to sign in.
- Failing-first: with the domain kind and the tests in place but the old mapping, the five new 403 → FORBIDDEN tests failed (they got UNAUTHORIZED): 91 tests, 5 failed. After the mapping change, all pass.
- Tests: full CI command (`testDebugUnitTest koverVerify lintDebug assembleDebug`) BUILD SUCCESSFUL; debug unit tests 330 passed / 0 failed.
- Outstanding: none.
- Contradicted the plan: none.
