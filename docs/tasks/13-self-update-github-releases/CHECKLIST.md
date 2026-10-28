# Checklist — Update the app from GitHub Releases

Issue: #13 — Update the app from GitHub Releases (https://github.com/YasarMahomedAbbas/paperless-companion/issues/13)
Branch: `feat/13-self-update-github-releases`
Problem: a new release only reaches our phones if someone downloads the APK by hand or runs Obtainium; the app should find, verify and offer its own updates.

## Done when

- With an older build installed, a new tag reaches both phones as "Update ready" and installs with one confirmation.
- A tampered or differently signed APK is refused with a reason, before any install starts.
- Tested (check, version compare, digest and signer checks), CI passes.
- The README says: install once by hand, after that the app updates itself.

## References

- GitHub REST API, "Get the latest release": `GET https://api.github.com/repos/YasarMahomedAbbas/paperless-companion/releases/latest`,
  anonymous (60 requests/hour), `Accept: application/vnd.github+json`, `X-GitHub-Api-Version: 2022-11-28`.
  Excludes drafts and prereleases; 404 when the repo has no release. Fields used: `tag_name` (`v0.1.4`),
  `body` (release notes, Markdown), `assets[]`: `name` (`paperless-scan-0.1.4.apk`), `size` (bytes),
  `digest` (`sha256:<hex>`, set on every asset uploaded since GitHub added it; checked live on v0.1.4),
  `browser_download_url` (answers 302 to `objects.githubusercontent.com`, so the download follows redirects).
- Release workflow (`.github/workflows/release.yml`): version code = `major*10000 + minor*100 + patch`, asset `paperless-scan-<version>.apk`.
- Android: `PackageInstaller` sessions (`setRequireUserAction(USER_ACTION_REQUIRED)` on API 31+; below 31 an unattended update is never possible for this app anyway), `STATUS_PENDING_USER_ACTION`, `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES`, `POST_NOTIFICATIONS` (API 33+), signing certificates via `GET_SIGNING_CERTIFICATES` (API 28+) with `GET_SIGNATURES` below (minSdk is 26).
- Model: personal-assistant `android/shared/.../update/` (`UpdateCheck`, `ApkFetcher`, `Signature`, `Installer`, `UpdateWorker`, `UpdateViewModel`) and `android/app/.../update/UpdateScreen.kt`.
- #6 (design) owns navigation and settings and will move the Updates entry; #14 (layer test).

## Context

Nothing in the app knows about updates yet; the home top bar has only *Sign out*. This mirrors the
personal-assistant phone self-update, with GitHub Releases as the source instead of its own server, so
no token and redirects followed (the sha256 and the signer are the guard, not the host). Layering: the
domain names an `AppRelease` (version, version code, notes, `ApkFile`), the ports `ReleaseSource`,
`UpdateStore`, `ApkDownloader`, `AppInstaller`, and `CheckForUpdateUseCase`; `data/update/` implements
them (GitHub JSON, OkHttp download, `PackageManager`/`PackageInstaller`, SharedPreferences, WorkManager,
the notification); `ui/updates/` has the screen. Release builds only (`BuildConfig.DEBUG` skips scheduling
and the permission ask). Decided with the pipeline: Updates is reached from a new overflow menu on the
home top bar (with *Sign out* moved into it) and from the notification; the notification permission is
asked once on the first launch of a release build that has this feature. Download starts only when
*Install* is tapped. Missing digest, unparseable tag or missing asset → no offer.

## Test plan

- Unit: `versionCodeOf` (v1.2.3 → 10203, `1.2.3`, junk, out-of-range parts); `CheckForUpdateUseCase` (newer → offer saved + notified once, same/older → offer cleared, already notified → no second notification, offline/5xx → TryAgain with the old offer kept, 4xx/rate limit → Done); `sameSigners`; `UpdatesViewModel` (rows from installed + offer, Install → download progress → installing, checksum/not-ours/offline failures in words, needs permission → kept download → resumed installs, Stop, Check now ok/failed, events Installed/Failed).
- MockWebServer: `GitHubReleases` against a v0.1.4-shaped answer (taken from the live API), 404, no matching asset, asset without `digest`, `digest` not sha256, unparseable tag, 403 rate limit, 500, malformed JSON; `HttpApkDownloader`: good file, wrong size, wrong sha256, more bytes than announced (stops), 302 to a second path followed, 404, dropped connection, part file deleted on every failure, earlier downloads cleared.
- Real server: no Paperless call changes. Self-verification calls the live GitHub `releases/latest` (read-only), checks the parse gives the current tag's version code and asset, and streams the asset to confirm its size and sha256 match `digest`.
- Device only: the daily worker, "Update ready" notification and its tap, the notification permission prompt, "Install unknown apps", Android's install confirmation, the signer check against the installed app, and the update itself (older build on both phones, then a new tag).

---

## Checkpoint 1 — Find a newer release · commit: `feat(update): find a newer release on GitHub`

Goal: the app can ask GitHub for the latest release, tell whether it's newer than the installed version,
remember it and announce it once; all unit-tested.

### [x] 1.1 — Domain models

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/domain/model/Updates.kt`
- `data class ApkFile(val name: String, val size: Long, val sha256: String /* lower-case hex */, val location: String /* opaque download address */)`
- `data class AppRelease(val version: String, val versionCode: Long, val notes: String, val apk: ApkFile)` with `fun isNewerThan(installed: Long) = versionCode > installed`
- `class UpdateException(val kind: Kind, message: String, cause: Throwable? = null) : Exception(message, cause)` with
  `enum class Kind { OFFLINE, SERVER, CHECKSUM, NOT_OURS, NEEDS_PERMISSION, REFUSED, STORAGE }`; `UpdateException` carries `val status: Int? = null`, set on `SERVER` (for retry on ≥500 / no status).
- `sealed interface InstallEvent { data object Installed; data class Failed(val detail: String) }` (Android's confirmation itself never reaches the domain; see 3.3).

### [x] 1.2 — Ports

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/domain/repository/Repositories.kt` (append)
- `interface ReleaseSource { suspend fun latest(): AppRelease? }` — null when there's no usable release; throws `UpdateException(OFFLINE | SERVER)`.
- `interface UpdateStore { val offer: StateFlow<AppRelease?>; fun saveOffer(release: AppRelease?); fun notifiedVersionCode(): Long; fun markNotified(versionCode: Long); fun askedForNotifications(): Boolean; fun markAskedForNotifications() }`
- `interface ApkDownloader { suspend fun download(apk: ApkFile, onProgress: (done: Long, total: Long) -> Unit): File; fun clear() }` — throws `UpdateException(OFFLINE | SERVER | CHECKSUM | STORAGE)`.
- `interface AppInstaller { fun installedVersionCode(): Long; fun canInstall(): Boolean; suspend fun install(apk: File); val events: StateFlow<InstallEvent?>; fun clearEvents() }` — `install` throws `NEEDS_PERMISSION` (file kept), `NOT_OURS`, `STORAGE`, `REFUSED` (file deleted otherwise).

### [x] 1.3 — `CheckForUpdateUseCase`

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/domain/usecase/CheckForUpdateUseCase.kt`
`class CheckForUpdateUseCase(releases: ReleaseSource, store: UpdateStore, installed: () -> Long, announce: (AppRelease) -> Unit)`,
`enum class Outcome { Done, TryAgain }`, `suspend fun run(): Outcome`: newer release → `saveOffer`, and `announce` + `markNotified` only when its version code > `notifiedVersionCode()`; not newer or null → `saveOffer(null)`; `OFFLINE` or `SERVER` with status null/≥500 → `TryAgain`, offer untouched; other `SERVER` (403 rate limit, 404 handled as null already) → `Done`. Mirrors the assistant's `UpdateCheck`.

### [x] 1.4 — `GitHubReleases` and `versionCodeOf`

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/data/update/GitHubReleases.kt`
`class GitHubReleases(client: OkHttpClient = OkHttpClient(), io: CoroutineDispatcher = Dispatchers.IO, url: String = LATEST) : ReleaseSource`,
`LATEST = "https://api.github.com/repos/YasarMahomedAbbas/paperless-companion/releases/latest"`. Headers `Accept: application/vnd.github+json`, `X-GitHub-Api-Version: 2022-11-28`. 404 → null; other non-2xx → `SERVER(status)`; `IOException` → `OFFLINE`; malformed JSON → null (nothing usable, not worth retrying). Parse with `kotlinx.serialization.json` elements as `PaperlessApi` does. `tag_name` → `versionCodeOf`; asset name must equal `"paperless-scan-$version.apk"` where version is the tag without `v`; `digest` must start `sha256:` with 64 hex chars (lower-cased); any missing → null.
`internal fun versionCodeOf(tag: String): Long?` — optional `v`, exactly three non-negative integer parts, minor and patch < 100, else null.

### [x] 1.5 — Tests

Files: `app/src/test/java/io/github/yasarmahomedabbas/paperlesscompanion/data/GitHubReleasesTest.kt` (MockWebServer, mirror `PaperlessApiTest`),
`app/src/test/java/io/github/yasarmahomedabbas/paperlesscompanion/domain/CheckForUpdateUseCaseTest.kt`,
fakes `FakeReleases`, `FakeUpdateStore` added to `Fakes.kt`. Cases as in the test plan.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*GitHubReleasesTest' --tests '*CheckForUpdateUseCaseTest'`

---

## Checkpoint 2 — Download and verify · commit: `feat(update): download a release APK and check its digest and signer`

Goal: a release's APK is downloaded, refused unless its size and sha256 match, and the signer compare exists and is tested.

### [ ] 2.1 — `HttpApkDownloader`

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/data/update/HttpApkDownloader.kt`
`class HttpApkDownloader(dir: File /* cacheDir/updates */, client: OkHttpClient = OkHttpClient(), io: CoroutineDispatcher = Dispatchers.IO) : ApkDownloader`.
Mirror the assistant's `ApkFetcher`: clear `dir` first; stream to `<name>.part` while hashing; more bytes than `size` → `CHECKSUM` at once; size or sha256 mismatch → `CHECKSUM`; rename to `<name>`; any failure or cancel deletes both; call cancelled on coroutine cancel; progress every 256 KiB and at the end. Differences: redirects **followed** (GitHub's 302 to its file host); only `https://` locations accepted except in tests (constructor flag `allowHttp: Boolean = false`); the file name is `apk.name` checked against `^paperless-scan-[0-9.]+\.apk$` (else `CHECKSUM`) so nothing from the network chooses a path; non-200 → `SERVER(status)`; `IOException` on the wire → `OFFLINE`, on disk → `STORAGE`. Read timeout 60 s.

### [ ] 2.2 — Signer compare

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/data/update/ApkSigner.kt`
`fun sameSigners(ours: List<ByteArray>, theirs: List<ByteArray>): Boolean` (same set of sha256 digests, non-empty), as in the assistant. `fun sameSigner(context: Context, apk: File): Boolean` reads ours and the archive's: API 28+ `GET_SIGNING_CERTIFICATES` → `signingInfo.apkContentsSigners`; below, `GET_SIGNATURES` → `signatures` (deprecated, `@Suppress`). Use `getPackageInfo(name, flags: Int)` (not `PackageInfoFlags`, API 33).

### [ ] 2.3 — Tests

Files: `app/src/test/java/io/github/yasarmahomedabbas/paperlesscompanion/data/HttpApkDownloaderTest.kt` (MockWebServer, temp dir via `TemporaryFolder`), `app/src/test/java/io/github/yasarmahomedabbas/paperlesscompanion/data/ApkSignerTest.kt` (pure `sameSigners`: equal, different, either empty, order-insensitive). Downloader cases as in the test plan; a tampered body (same size, one byte flipped) → `CHECKSUM` and no file left.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*HttpApkDownloaderTest' --tests '*ApkSignerTest'`

---

## Checkpoint 3 — Check daily, announce, install · commit: `feat(update): check daily, announce and install through PackageInstaller`

Goal: release builds check daily and on launch, post "Update ready" once per version, and can install a verified APK with Android's confirmation.

### [ ] 3.1 — Build and manifest

- `gradle/libs.versions.toml`: `work = "2.10.0"` (check the current stable on Google Maven before pinning), `work-runtime-ktx`; `app/build.gradle.kts`: `implementation(libs.work.runtime)`, `buildFeatures { buildConfig = true }`, add to kover excludes: `"*.data.update.PackageInstallerUpdates*"`, `"*.data.update.InstallResultReceiver*"`, `"*.data.update.PrefsUpdateStore*"`, `"*.data.update.UpdateWorker*"`, `"*.data.update.UpdateSchedule*"`, `"*.data.update.UpdateNotifier*"`. `ApkSigner.kt` is not excluded (the pure `sameSigners` lives there); if its Android half drags coverage below 80%, move `sameSigner(context, apk)` into `AndroidSigner.kt` and exclude that file.
- `AndroidManifest.xml`: `REQUEST_INSTALL_PACKAGES`, `POST_NOTIFICATIONS`; `<receiver android:name=".data.update.InstallResultReceiver" android:exported="false" />`.

### [ ] 3.2 — `PrefsUpdateStore`

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/data/update/PrefsUpdateStore.kt`
SharedPreferences `updates`; the offer as a JSON string (`version`, `versionCode`, `notes`, `apk{name,size,sha256,location}`), unreadable → null; `offer` a `MutableStateFlow` loaded at construction and updated by `saveOffer`; `notified` long (default `Long.MIN_VALUE`); `asked_notifications` boolean.

### [ ] 3.3 — `PackageInstallerUpdates` and `InstallResultReceiver`

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/data/update/PackageInstallerUpdates.kt`
`class PackageInstallerUpdates(context: Context, io: CoroutineDispatcher = Dispatchers.IO) : AppInstaller`. Mirror the assistant's `Installer` for our own package only: `canInstall()` = `canRequestPackageInstalls()`; `permissionIntent()`; `install`: not allowed → `NEEDS_PERMISSION` (file kept); `!sameSigner` → `NOT_OURS`; session `MODE_FULL_INSTALL`, `setAppPackageName(packageName)`, `setSize`, `setRequireUserAction(USER_ACTION_REQUIRED)` on API 31+; commit with a mutable `PendingIntent` to the receiver (`FLAG_MUTABLE` on API 31+); `IOException` → `STORAGE`, other runtime → `REFUSED`; delete the file after. `installedVersionCode()` via `PackageInfoCompat.getLongVersionCode`.
`events: StateFlow<InstallEvent?>`, plus a data-side `val confirmation: StateFlow<Intent?>` with `consumeConfirmation(intent)`: the receiver posts `STATUS_PENDING_USER_ACTION`'s `EXTRA_INTENT` there, and `MainActivity` (app shell, gets it from `AppContainer`) starts it. Success → `Installed` and `store.saveOffer(null)`; anything else → `Failed(message)` (empty for `STATUS_FAILURE_ABORTED`).
`class InstallResultReceiver : BroadcastReceiver` finds the installer via `(context.applicationContext as PaperlessApp).container.installer`.

### [ ] 3.4 — Worker, schedule, notification

Files in `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/data/update/`:
- `UpdateSchedule.kt`: `object UpdateSchedule { fun daily(context); fun now(context) }` — unique periodic (24 h, `KEEP`) and one-time (`REPLACE`) work, both `NetworkType.CONNECTED`.
- `UpdateWorker.kt`: `CoroutineWorker` building `container.checkForUpdate` and running it; `TryAgain` → `Result.retry()` up to 2 attempts, then success.
- `UpdateNotifier.kt`: channel `updates` ("Updates", default importance), created in `PaperlessApp.onCreate`; `announce(release)` posts "Update ready" / "Version <version> is ready to install" (skipped without `POST_NOTIFICATIONS` granted on 33+), tap → `MainActivity` with extra `EXTRA_OPEN_UPDATES = "…OPEN_UPDATES"`, `PendingIntent.FLAG_IMMUTABLE`. Small icon: a new vector `res/drawable/ic_notification_update.xml`.
- `AppContainer`: `updateStore`, `releases`, `downloader`, `installer`, `notifier`, `checkForUpdate` (with `installer::installedVersionCode`); `PaperlessApp.onCreate`: create the channel; when `!BuildConfig.DEBUG`, `UpdateSchedule.daily(this)`.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest koverVerify lintDebug assembleDebug`
- Lint is clean for the new permissions, the receiver and the API-level guards (`NewApi`).

---

## Checkpoint 4 — The Updates screen · commit: `feat(ui): add the Updates screen with release notes and Install`

Goal: Updates is reachable from the home overflow menu and the notification, shows the installed version, the offer's version and notes, and installs it; the notification permission is asked once.

### [ ] 4.1 — `UpdatesViewModel`

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/ui/updates/UpdatesViewModel.kt`
`data class UpdatesUiState(val installedVersion: String, val offer: AppRelease?, val phase: Phase = Idle, val checking: Boolean = false, val checkFailed: Boolean = false)`;
`sealed interface Phase { Idle; Downloading(done, total); Installing; NeedsPermission; Failed(reason: String) }`.
`class UpdatesViewModel(installedVersion: String, store: UpdateStore, downloader: ApkDownloader, installer: AppInstaller, check: CheckForUpdateUseCase)`;
`install()`, `stop()`, `resumed()`, `checkNow()` with the assistant `UpdateViewModel`'s rules for one package: an offer counts only while newer than `installer.installedVersionCode()`; a kept download waits for the permission and installs on `resumed()` when allowed; `Installed` → Idle and offer gone; `Failed(detail)` → `Failed`. Reasons in words, by `UpdateException.Kind`: CHECKSUM "The download didn't match the release, so it wasn't installed.", NOT_OURS "This update isn't signed with the app's key, so it wasn't installed.", OFFLINE "Couldn't reach GitHub. Check the connection and try again.", STORAGE "Not enough space to download the update.", SERVER "GitHub didn't send the update. Try again later.", REFUSED "Android didn't install the update" + detail.

### [ ] 4.2 — `UpdatesScreen`

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/ui/updates/UpdatesScreen.kt`
Built with the `design-recipe` skill and `docs/design/README.md` (first pass; #6 may restyle): top bar with back and "Updates"; one card: "Paperless Scan", "Version <installed>" muted, then "Up to date" or "Version <new> is ready" + the release notes (plain text, scrollable) + one green *Install*; downloading: progress line, megabytes, *Stop*; "Installing…" while Android confirms; failure in words with *Install* again; needs permission: the sentence and *Allow* (opens `permissionIntent()`); *Check now* as a text button. Keeps the screen on while busy; `ON_RESUME` → `resumed()`.

### [ ] 4.3 — Home overflow menu, navigation, permission, confirmation

- `HomeScreen.kt`: replace the *Sign out* text button with an overflow icon button (content description "More") opening a `DropdownMenu` with "Updates" and "Sign out"; new parameter `onOpenUpdates: () -> Unit`.
- `MainActivity.kt`: `var showUpdates by mutableStateOf(false)` set by the menu and by `EXTRA_OPEN_UPDATES` in `receive(intent)` (onCreate and onNewIntent); back returns home (`BackHandler`). Collects `container.installer.confirmation` and starts it with `startActivity`, then consumes it. On create, release builds only: `UpdateSchedule.now(this)`; on API 33+, if `!updateStore.askedForNotifications()`, request `POST_NOTIFICATIONS` once via `registerForActivityResult(RequestPermission())` and mark asked. Updates is only reachable when signed in: the notification's extra is ignored on the login screen.
- `AppContainer`: `updatesViewModelFactory` (installed version name from `PackageManager`).

### [ ] 4.4 — Tests

File: `app/src/test/java/io/github/yasarmahomedabbas/paperlesscompanion/ui/UpdatesViewModelTest.kt` with `FakeDownloader`, `FakeInstaller` in `Fakes.kt`; cases as in the test plan. Mirror the assistant's `UpdateViewModelTest`.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest koverVerify lintDebug assembleDebug`

---

## Checkpoint 5 — README · commit: `docs: say the app updates itself from GitHub Releases`

### [ ] 5.1 — Install section

File: `README.md`: replace the Obtainium line: install the first APK by hand; after that the app checks GitHub Releases daily and on opening, posts "Update ready", and installs from *⋮ → Updates* with Android's confirmation (and "Install unknown apps" once). Mention it's release builds only, the size/sha256/signer checks, and that a copy installed earlier needs this one manual update to start. Add `CheckForUpdateUseCase` to the use-case table.

### Verify

- The README reads true against the code; `git diff --stat` touches only `README.md`.

---

## Out of scope

- A Settings screen or any navigation redesign (#6 decides; it will move the Updates entry).
- Downloading in the background before *Install*, silent or unattended installs, update channels/prereleases.
- Changing the Release workflow, the signing key or the version-code scheme.
- Any Paperless API change.
- Rendering the release notes as Markdown.
