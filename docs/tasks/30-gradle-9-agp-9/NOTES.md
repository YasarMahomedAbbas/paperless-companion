# Notes — Move the build to Gradle 9, AGP 9 and the current libraries

Issue: #30 — Move the build to Gradle 9, AGP 9 and the current libraries (https://github.com/YasarMahomedAbbas/paperless-companion/issues/30)
Branch: `chore/30-gradle-9-agp-9`

## Planning

- Approach: three commits — toolchain (Gradle 9.8.2, AGP 9.4.1 with built-in Kotlin 2.4.21, Kover 0.9.11, Docker image, release apksigner path), then OkHttp 5.5.1, then the remaining #28 libraries; drop the `kotlin.android` plugin from the app module and keep it only as `apply false` at the root to pin KGP.
- Decided (the pipeline took these on the user's behalf; flag if wrong): compileSdk 35 → 37 because Compose UI 1.12.1 declares `minCompileSdk=37`; targetSdk stays 35 (a runtime-behaviour change, its own ticket); tests keep the `okhttp3.mockwebserver` API from the 5.x `mockwebserver` artifact rather than migrating to `mockwebserver3`; release.yml's apksigner moves to build-tools 36.0.0, nothing else in the release contract changes.
- Open risks: Kover 0.9.11 with AGP 9's new DSL and built-in Kotlin (if variant reports or `koverVerify` break, stop and record); stricter lint on the new toolchain under `abortOnError`; the Docker image's SDK is read-only so platform/build-tools names must match what AGP asks for exactly; the Release workflow's first run on AGP 9 happens only at merge-release; `verify.env` is absent, so the real-server check will be skipped.
- Supersedes Dependabot's #28: the PR body must carry `Closes #28` (alongside `Closes #30`), and merge-release must confirm #28 is closed after the merge (close it with a pointer to this PR if GitHub didn't).
- Plan approved by the pipeline as written.

## Implementation

- Shipped:
  - Toolchain: Gradle wrapper 9.8.2 (`gradle/wrapper/gradle-wrapper.properties:3`), AGP 9.4.1 / Kotlin 2.4.21 / Kover 0.9.11 (`gradle/libs.versions.toml:2-4`). Built-in Kotlin: `kotlin.android` dropped from `app/build.gradle.kts:1-5`, kept as `apply false` at the root; `buildEnvironment` resolves `kotlin-gradle-plugin:2.2.10 -> 2.4.21` (AGP accepted the `apply false` declaration, so no `buildscript` fallback was needed). `jvmTarget` moved to a top-level `kotlin { compilerOptions }` (`app/build.gradle.kts:61`); `compileSdk = 37`, targetSdk 35 / minSdk 26 unchanged.
  - `robolectricSdk` moved from `by configurations.creating` (deprecated in Gradle 9.6) to `configurations.create("robolectricSdk")` (`app/build.gradle.kts:69`). `gradle.properties` needed no change. The remaining `Configuration.setVisible` deprecation warning comes from AGP 9.4.1's own code, not ours.
  - Docker image: cmdline-tools 16111833, `platforms;android-37.0`, `build-tools;36.0.0`, system Gradle 9.8.2 (`docker/Dockerfile:4-8`). `release.yml:48` apksigner path → build-tools 36.0.0; nothing else in the release contract changed.
  - OkHttp + mockwebserver 5.5.1. Changelog items that touched the code: only `Response.body` becoming non-null, which made the `?.` / `?: throw` fallbacks dead; removed in `data/PaperlessHttp.kt:62,118-119`, `data/update/GitHubReleases.kt:41`, `data/update/HttpApkDownloader.kt:89`. Not touching it: the 4.x API kept on JVM (`toRequestBody`, `MultipartBody`, `HttpUrl`), the legacy `okhttp3.mockwebserver` package (still in the 5.x artifact, compiles without warnings here). The upload test's `Transfer-Encoding == null` / `Content-Length == bodySize` assertions and the v9/v10 tasks tests passed with no test edits.
  - Every other #28 version in `gradle/libs.versions.toml`: Compose BOM 2026.09.00 (ui 1.12.1, material3 1.4.0), appcompat 1.8.0, fragment 1.9.1, coroutines 1.11.0, serialization 1.12.0, Robolectric 4.17 with SDK jar `15-robolectric-13954326-i7`, Coil 3.6.3, ML Kit scanner 16.0.0. `releaseRuntimeClasspath` resolves each at the #28 version, no downgrades. Nothing held back.
  - Material 3 1.4 stopped pulling in `material-icons-core` transitively, which broke `Icons.*` in `ui/components/ViewerBar.kt` and `ui/updates/UpdatesScreen.kt`; declared explicitly (`gradle/libs.versions.toml` `compose-material-icons-core`, `app/build.gradle.kts:135`; the BOM still manages it at 1.7.8). No screen changed.
- Tests: full `testDebugUnitTest koverVerify lintDebug assembleDebug` green after each checkpoint; final run 384 passed / 0 failed / 0 skipped (including the 3 Robolectric `LoginScreenTest` tests offline on the new SDK jar); `koverVerify` passed; `lintDebug` clean with `abortOnError`. `assembleRelease` builds unsigned locally.
- Outstanding: none from the checklist. New deprecation warnings (not errors), left for a follow-up because each is a behaviour change, not a bump: `AnchoredDraggableState(...)` constructor in `ui/components/DetailsPanel.kt:66` (move thresholds to `AnchoredDraggableDefaults.flingBehavior`), and `createComposeRule` in `src/testDebug/.../LoginScreenTest.kt:35` (the v2 API switches to `StandardTestDispatcher`). Device-only checks (install over the current release, ML Kit scanner opening) stay with self-verification / the Release run.
- Risk noticed: `build.sh` tags its image `paperless-companion-build` for every worktree, so while this branch and an older-Dockerfile worktree build in parallel they retag the shared image back and forth; a parallel pipeline on the old image would run AGP 8.7 against the new SDK (or vice versa) for a moment. Gone once this merges and other branches merge main.
- Contradicted the plan: none.

## Review

- Fixed: none. Checked and kept: the dropped `body?` / `?:` fallbacks (OkHttp 5 gives a non-null, possibly empty body, so error paths still surface the status code and an empty download still fails on length/digest as before); `release.yml`'s apksigner at build-tools 36.0.0 exists on the runner because `assembleRelease` on AGP 9.4.1 needs it; Kover excludes and the Robolectric offline wiring unchanged in meaning; `robolectric.properties` still pins sdk 35, matching the new SDK jar; signing config, version-code formula and asset name untouched. Every Done-when item and ticked task is in the diff; nothing from Out of scope is.
- Raised: none
- API: no Paperless call changed (method, path, query, body, response parsing identical); only the transport moved to OkHttp 5. Upload `Content-Length` and v9/v10 `/api/tasks/` tests pass unedited. Live schema not read: `verify.env` absent.
- Layers: clean (no new imports; `okhttp3` still only in `data/`).

## Doc drift

- Applied: README.md:CI/CD (Release bullet) — `release.yml`'s apksigner path, `docker/Dockerfile`'s `BUILD_TOOLS` and AGP's expected build-tools must move together; a Dependabot AGP bump would otherwise pass CI and break only at the release tag (rung 3).
- Skipped: Gradle/AGP/Kotlin/library versions — no doc names a version; `libs.versions.toml` and the Dockerfile are the source.
- Skipped: built-in Kotlin (`kotlin.android` only `apply false` at the root) — AGP 9 errors clearly if the plugin is re-applied in the app module.
- Skipped: `material-icons-core` now declared explicitly — dropping it fails the compile; `docs/design/README.md`'s mention of `material-icons-core` stays true.
- Skipped: compileSdk 37 / targetSdk 35 — stated in `app/build.gradle.kts`; nothing in the docs names an SDK level.
- Skipped: OkHttp 5 non-null `Response.body` — enforced by the compiler; CLAUDE.md's MockWebServer and upload `Content-Length` claims are still true (tests unchanged and green).
- Skipped: `build.sh`'s shared image tag across parallel worktrees — transient, gone once this merges.
- Checked, still true: README Development commands and `build.sh` usage, CI/CD bullets against `ci.yml` / `release.yml`, CLAUDE.md's check command and `koverVerifyDebug` CI claim, "JDK, Android SDK and Gradle live in a Docker image".
- Stale, fixed separately: none

## Self verification

Result: partial

- CI build: `testDebugUnitTest koverVerify lintDebug assembleDebug` green in Docker on Gradle 9.8.2; 384 debug unit tests passed (0 failed, 0 skipped), line coverage 93.4 % (`koverVerify` ≥ 80 % passed), lint clean with `abortOnError`, debug APK built.
- Server: not run (`~/.config/paperless-companion/verify.env` absent). No Paperless call changed in method, path, query, body or parsing; only the transport moved to OkHttp 5.
- Verified: upload `Content-Length` present and no `Transfer-Encoding` on OkHttp 5 (`PaperlessApiTest`, unedited); v9 bare-list and v10 paginated `/api/tasks/` parsing (unedited tests); `ArchitectureTest` (`okhttp3` only in `data/`); Robolectric screen tests offline on the 4.17 SDK jar.
- Failed: none
- Not self-verifiable:
  - Installing the release APK over the current one: on the phone, the in-app update installs the new version without asking to uninstall (same key, higher version code). The Release workflow's `apksigner verify` (now build-tools 36.0.0) is the proxy.
  - The ML Kit document scanner (16.0.0) opens and returns a scan that uploads.
  - Uploads, document list and thumbnails against the real server (OkHttp 5 transport): scan or share a file, check it lands in Paperless and its thumbnail shows.
