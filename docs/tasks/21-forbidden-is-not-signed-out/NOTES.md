# Notes — A permission refusal (403) tells the user to sign in again

Issue: #21 — A permission refusal (403) tells the user to sign in again (https://github.com/YasarMahomedAbbas/paperless-companion/issues/21)
Branch: `fix/21-forbidden-is-not-signed-out`

## Planning

- Approach: a new domain `ServerException.Kind.FORBIDDEN` with its own `userMessage()` ("You don't have permission to do that in Paperless."). `PaperlessHttp` maps 403 to FORBIDDEN on every signed-in call and keeps 401 (and 400/403 on sign-in) as UNAUTHORIZED. `badRequestIsAuth` becomes `signIn`.
- Decided (the pipeline took this on the user's behalf; flags: api, ux): every signed-in call maps 403 to FORBIDDEN, meaning the document library plus upload and task status, not only the library calls the issue names. Sign-in keeps 400/401/403 as UNAUTHORIZED. Reason: Paperless's first authenticator (`PaperlessBasicAuthentication`) answers non-Basic requests with a `Token` auth header, so a missing or invalid token is 401. A 403 on a signed-in call therefore means the token works but the user lacks the permission, upload (`add_document`) included.
- Decided (routine, by the planner): keep the "Wrong username or password" text on UNAUTHORIZED; no ViewModel or screen changes, since they already show `userMessage()`; FORBIDDEN's exception message carries "Paperless returned HTTP 403: <body>" for logs.
- Open risks: on a server with remote-user API auth enabled (`PAPERLESS_ENABLE_HTTP_REMOTE_USER_API`), `PaperlessRemoteUserAuthentication` comes first and has no auth header, so DRF answers a bad token with 403. Such a user would see "no permission" instead of "sign in again". The Paperless docs site refused automated fetches, so the docs were read from the repo source, and `verify.env` is absent, so the live schema and real-server status codes weren't checked.
