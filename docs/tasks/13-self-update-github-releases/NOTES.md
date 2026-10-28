# Notes — Update the app from GitHub Releases

Issue: #13 — Update the app from GitHub Releases (https://github.com/YasarMahomedAbbas/paperless-companion/issues/13)
Branch: `feat/13-self-update-github-releases`

## Planning

- Approach: mirror the personal-assistant phone self-update with GitHub Releases (`/releases/latest`, anonymous) as the source: the domain has the release model, ports and `CheckForUpdateUseCase`; `data/update/` has GitHub, download + sha256, signer check, `PackageInstaller`, WorkManager and the notification; `ui/updates/` has the screen. Release builds only.
- Decided (pipeline, on the user's behalf; flags: ux, scope): the Updates screen is reached from a new overflow menu (⋮) on the home top bar holding "Updates" and "Sign out", and from the "Update ready" notification. Options were: (1) overflow menu [taken], (2) a minimal Settings screen now, (3) a home banner only when an update exists. #6 (design) owns navigation and settings and is expected to move the Updates entry later.
- Decided (pipeline, on the user's behalf; flags: ux, scope): the notification permission is asked once, on the first launch of a release build that has this feature. Options were: (1) once on first launch [taken], (2) when Updates first opens, (3) never ask.
- Decided (pipeline): redirects are followed on the download (GitHub's asset URL 302s to its file host); size, sha256 from the asset `digest` and the signer are the guard. A missing digest, unparseable tag or missing `paperless-scan-<version>.apk` asset means no offer.
- Decided (pipeline): WorkManager daily check with a network constraint, plus one check per launch; offer and last-notified version code in SharedPreferences; download only on *Install*; Android always confirms (`USER_ACTION_REQUIRED`); "Install unknown apps" asked for when missing; release notes shown as plain text.
- Decided (pipeline): Android's confirmation `Intent` stays in `data/` and is started by `MainActivity` (the app shell), so no Android type reaches `domain/` or a ViewModel.
- Live API check (2026-10-28): `releases/latest` → `v0.1.4`, asset `paperless-scan-0.1.4.apk`, `size` 8570413, `digest` `sha256:27b2…36a3`, anonymous limit 60/h.
- Open risks: the first update to this version must still be installed by hand or Obtainium (the feature isn't on the phones yet); minSdk 26 needs API guards (signing certificates below 28, `setRequireUserAction` from 31, notification permission from 33); the Updates screen is styled on the first-pass manual before #6's canvas exists.

## Implementation

- Shipped: domain release model and ports (`app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/domain/model/Updates.kt:13`, `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/domain/repository/Repositories.kt:31`) and `CheckForUpdateUseCase` (`app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/domain/usecase/CheckForUpdateUseCase.kt:13`); `GitHubReleases` + `versionCodeOf` (`app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/data/update/GitHubReleases.kt:28`, `:88`); `HttpApkDownloader` with size/sha256 check, redirects followed but never https→http (`app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/data/update/HttpApkDownloader.kt:34`); `sameSigners`/`sameSigner` (`app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/data/update/ApkSigner.kt:41`); `PrefsUpdateStore` (`app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/data/update/PrefsUpdateStore.kt:21`); `PackageInstallerUpdates` + `InstallResultReceiver` (`app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/data/update/PackageInstallerUpdates.kt:37`, `:144`); `UpdateSchedule`, `UpdateWorker`, `UpdateNotifier` (`app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/data/update/`); `UpdatesViewModel` and `UpdatesScreen` with a preview per state (`app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/ui/updates/UpdatesViewModel.kt:58`, `UpdatesScreen.kt:48`); home overflow menu (`app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/ui/home/HomeScreen.kt:149`); `MainActivity` shows Updates, starts Android's confirmation, checks on launch and asks for notifications once (`app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/MainActivity.kt:96`); WorkManager 2.12.0, BuildConfig, manifest permissions and receiver; README install section and use-case table; a design open decision in `docs/design/README.md`.
- Tests: full CI command (`testDebugUnitTest koverVerify lintDebug assembleDebug`) green: 119 passed / 0 failed / 0 skipped; line coverage 96.7%; lint shows no finding in the new code (only the earlier dependency-version, allowBackup and mipmap-v26 warnings). New suites: GitHubReleasesTest 14, CheckForUpdateUseCaseTest 9, HttpApkDownloaderTest 14, ApkSignerTest 6, UpdatesViewModelTest 22.
- Outstanding: none unticked. Device-only checks are left to self-verification as the test plan says: the daily worker, the notification and its tap, the notification-permission prompt, "Install unknown apps", Android's confirmation, the signer check against the installed app, and a real update on both phones. `PrefsUpdateStore` has no unit test (excluded from coverage like the other SharedPreferences/Keystore classes).
- Contradicted the plan: nothing in the code disproved it. Deviations recorded in the checklist text: WorkManager pinned at 2.12.0, not 2.10.0 (current stable; needs compileSdk 35 and AGP 8.6+, both met); `UpdateException.status` sits on the exception rather than on `SERVER` alone; `PackageInstallerUpdates` takes the `UpdateStore` so a finished install clears the offer; `UpdateNotifier` is handed the activity class by `AppContainer`, so `data/` never names `MainActivity`; a non-https download address is refused as `SERVER` before any request; the Updates screen needed `ui-tooling-preview` (and `ui-tooling` for debug) for its previews; the launch check and permission ask run only on a fresh create, not on rotation; debug builds still check on *Check now*.

## Review

- Merged origin/main (#14's `ArchitectureTest`) first so the branch is reviewed under the rule it merges into; conflicts were import lists and the README use-case table.
- Fixed: `MainActivity` imported `data.update.UpdateSchedule` and `UpdateNotifier`, which `ArchitectureTest` fails once main is in ("only data/ and AppContainer name a data class"); `AppContainer` now exposes `checkForUpdatesNow()` and `EXTRA_OPEN_UPDATES`.
- Fixed: Android's install confirmation was started from a Compose `LaunchedEffect`, which runs while the activity is stopped. Leaving the app during the download meant the confirmation arrived in the background, Android blocked the activity start, the intent was consumed anyway and the screen stayed on "Installing…" with no way out. It's now collected with `repeatOnLifecycle(STARTED)`, so it waits until the app is back on screen. No JVM test: `MainActivity` has none (device check in self-verification).
- Fixed: `PackageInstallerUpdates.commit` let `openSession`'s `IOException` and any `RuntimeException` from `createSession`/`openSession` escape as non-`UpdateException`, breaking the `AppInstaller` contract; the ViewModel catches only `UpdateException`, so the app would crash instead of showing a reason. Both are now mapped (STORAGE / REFUSED). No JVM test: the class needs the Android framework and is excluded from coverage like the other platform classes.
- Raised: none
- API: no Paperless call touched. GitHub `GET /repos/…/releases/latest` re-checked live (now `v0.1.5`, asset `paperless-scan-0.1.5.apk`, `size`, `digest` `sha256:…`, `browser_download_url`): matches the parser and the MockWebServer fixtures' shape.
- Layers: `MainActivity`'s two `data/` imports moved behind `AppContainer`; domain names only app terms (release, APK file, installer), no GitHub or Android types.
- CI after fixes: `testDebugUnitTest koverVerify lintDebug assembleDebug` green, 129 tests / 0 failed / 0 skipped (incl. `ArchitectureTest`).

## Doc drift

- Applied: CLAUDE.md:Working on the app (Release) — replaced "Phones update from GitHub Releases or Obtainium" with the self-update contract: tag format, asset name `paperless-scan-<version>.apk` and the version-code formula in `release.yml` must change together with `GitHubReleases.kt`, or every installed copy silently stops finding updates (rung 2: every ticket ends in a release, and nothing fails in CI if the workflow renames the asset).
- Skipped: README Install section and use-case table, architecture block — already updated by the branch; checked against `UpdateSchedule` (daily + per launch, network constraint), `MainActivity`/`AppContainer` (`!BuildConfig.DEBUG` gates), `GitHubReleases` (`releases/latest`, digest, asset name); all true.
- Skipped: docs/design/README.md open decision for the Updates screen — matches `HomeScreen` (⋮ → Updates, Sign out; MoreVert) and `UpdatesScreen` (ArrowBack); nothing else to add.
- Skipped: README CI/CD version-code line (`v1.2.3` → 10203) — still true, `versionCodeOf` uses the same formula.
- Skipped: the release-builds-only rule, the signer check and the "Android always confirms" behaviour — code and README state them; no future session is likely to get them wrong without a doc.
- Stale, fixed separately: none

## Self verification

Result: partial

- CI build: `testDebugUnitTest koverVerify lintDebug assembleDebug` green in Docker: 129 tests passed / 0 failed / 0 skipped; line coverage 96.7% (koverVerify passed); lint shows only the earlier GradleDependency, AndroidGradlePluginVersion, DataExtractionRules and ObsoleteSdkInt warnings, none in new code; debug APK built.
- Server: Paperless not run (the branch changes no Paperless call; nothing in `data/PaperlessApi.kt` touched). The branch's one remote source is GitHub, checked live instead (below).
- Verified: `GET https://api.github.com/repos/YasarMahomedAbbas/paperless-companion/releases/latest` with the code's headers (`Accept: application/vnd.github+json`, `X-GitHub-Api-Version: 2022-11-28`), anonymous → 200, `x-ratelimit-limit: 60`. Parsed by the code's rules: tag `v0.1.5` → version code 105, asset `paperless-scan-0.1.5.apk`, `size` 8570413, `digest` `sha256:57dbb664…05bb` (64 hex), `browser_download_url` present, `body` 840 chars. Streamed the asset: one 302 to `https://release-assets.githubusercontent.com/…` (https→https, allowed with `followSslRedirects(false)`; no host is pinned in code) → 200, 8570413 bytes, sha256 equal to `digest`. The MockWebServer fixtures already have this shape (checked in review), so no fixture change.
- Failed: none
- Not self-verifiable (check on the phone, on a release build older than the next tag):
  - First launch asks for the notification permission once (Android 13+), and not again on rotation or relaunch.
  - Opening the app with a newer release out posts "Update ready"; tapping it opens *⋮ → Updates* with the offer and release notes.
  - The daily WorkManager check finds a new tag without opening the app (network constraint).
  - *Install* with "Install unknown apps" off sends to Android's setting; coming back with it allowed continues the install.
  - Download progress shows; Android's install confirmation appears, including when the app was left during the download (it waits until the app is back on screen).
  - The update installs over the existing copy (signer check against the installed app passes) and the offer clears afterwards; on both phones.
  - The copy currently installed (without this feature) must be updated to this release by hand or Obtainium once.
