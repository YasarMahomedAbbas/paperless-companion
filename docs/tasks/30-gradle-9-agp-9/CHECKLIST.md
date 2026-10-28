# Checklist — Move the build to Gradle 9, AGP 9 and the current libraries

Issue: #30 — Move the build to Gradle 9, AGP 9 and the current libraries (https://github.com/YasarMahomedAbbas/paperless-companion/issues/30)
Branch: `chore/30-gradle-9-agp-9`
Problem: Dependabot's #28 can't land because AGP 9 builds Kotlin itself and refuses the `org.jetbrains.kotlin.android` plugin, so the app is stuck on 2024 tooling and libraries.

## Done when

- `./build.sh ./gradlew --console=plain testDebugUnitTest koverVerify lintDebug assembleDebug` passes and CI is green.
- The Release workflow builds and publishes the signed APK with the existing key (the signing config, the tag-to-version-code formula and the asset name are unchanged), so installed copies still update.
- Uploads and the document calls still work against the real server where verification can run.
- Every version in #28 is in `gradle/libs.versions.toml` / the wrapper, or NOTES says why one stayed behind.
- #28 is closed when this merges (the PR body says `Closes #28`).

## References

- #28 (Dependabot, the target versions): Gradle 9.8.2, AGP 9.4.1, Kotlin 2.4.21, Kover 0.9.11, Compose BOM 2026.09.00 (ui 1.12.1, material3 1.4.0), appcompat 1.8.0, fragment 1.9.1, coroutines 1.11.0, serialization 1.12.0, OkHttp + mockwebserver 5.5.1, Robolectric 4.17, Coil 3.6.3, ML Kit document scanner 16.0.0.
- AGP 9 migration (built-in Kotlin, new DSL): https://developer.android.com/build/releases/agp-9-0-0-release-notes and https://developer.android.com/build/migrate-to-built-in-kotlin
- Gradle 9 upgrade guide: https://docs.gradle.org/9.8.2/userguide/upgrading_version_9.html
- OkHttp 5 changelog / upgrade notes: https://square.github.io/okhttp/changelogs/changelog/ and https://square.github.io/okhttp/changelogs/upgrading_to_okhttp_5/
- Paperless API: no call changes. The calls whose transport changes with OkHttp 5 are `POST /api/documents/post_document/` (multipart, must send `Content-Length`), `GET /api/tasks/` (v9 bare list / v10 paginated), `GET /api/documents/` and the document download/thumbnail calls. Docs: https://docs.paperless-ngx.com/api/. No live schema was read: `~/.config/paperless-companion/verify.env` is absent.
- Facts checked while planning (Google Maven / Maven Central metadata):
  - Compose UI 1.12.1 (from BOM 2026.09.00) declares `minCompileSdk=37` and `minAndroidGradlePluginVersion=9.1.0`; material3 1.4.0 needs 35; appcompat 1.8.0 and fragment 1.9.1 need 34. So **compileSdk must go 35 → 37**.
  - AGP 9.4.1's default build-tools is **36.0.0**.
  - Robolectric 4.17's SDK 35 jar is `android-all-instrumented:15-robolectric-13954326-i7` (was `15-robolectric-12650502-i7`).
  - The current cmdline-tools is `commandlinetools-linux-16111833`; the 2023 one in the Dockerfile (11076708) predates the `platforms;android-37.0` naming. The SDK repository lists `platforms;android-37.0` and `build-tools;36.0.0`.
  - Coil 3.6.3's `coil-network-okhttp` is compiled against OkHttp 4.12.0; OkHttp 5 keeps the 4.x API on the JVM/Android, so Gradle resolving 5.5.1 is fine.

## Context

The build is `settings.gradle.kts` + root `build.gradle.kts` + `app/build.gradle.kts` with a version catalog, run through `./build.sh` in a Docker image (`docker/Dockerfile`: Temurin 21, Android SDK with `platforms;android-35` and `build-tools;34.0.0`, a system Gradle that `./gradlew` doesn't use). The SDK in the image is read-only to the build user, so AGP can't download a missing platform or build-tools: the image must preinstall exactly what AGP 9.4.1 + compileSdk 37 ask for. `release.yml` calls `apksigner` from a hard-coded `build-tools/34.0.0`. The work goes in three commits so a breakage points at one layer: toolchain first (old libraries, new Gradle/AGP/Kotlin/Kover), then OkHttp 5 (the only library that touches app code and the server), then the rest of the libraries. Decided: compileSdk goes to 37 because Compose demands it, **targetSdk stays 35** (changing it changes runtime behaviour on the phone and is its own ticket; Robolectric keeps running SDK 35); the tests keep the `okhttp3.mockwebserver` API from the 5.x `mockwebserver` artifact (same package and classes, deprecated in favour of `mockwebserver3`) so the test diff stays near zero; moving to `mockwebserver3` is out of scope.

## Test plan

- Unit: no new behaviour, so no new tests beyond what each checkpoint names. The whole existing suite is the regression net (184+ debug unit tests, the Robolectric screen tests in `src/testDebug`, `ArchitectureTest`), plus `koverVerify` ≥ 80 % and `lintDebug` with `abortOnError`.
- MockWebServer: the existing `PaperlessApiTest` upload test already asserts no `Transfer-Encoding` and `Content-Length == bodySize`; it must pass unchanged on OkHttp 5 (if it needs editing to pass, that is a finding to record, not to paper over). The v9 and v10 `/api/tasks/` tests in `PaperlessApiTest`/`PaperlessLibraryTest` must pass unchanged.
- Real server: self-verification, when `verify.env` exists: `GET /api/schema/`, `GET /api/documents/?page_size=1`, `GET /api/tasks/`, a thumbnail fetch, and (only with `PAPERLESS_ALLOW_WRITES=1`) one upload checked for `Content-Length` and its task. Absent today, so recorded as skipped.
- Device only: installing the release APK over the current one (same key, higher version code) and the ML Kit scanner opening. Not checkable here; the Release workflow's `apksigner verify` is the proxy for the key.

---

## Checkpoint 1 — Gradle 9, AGP 9, Kotlin 2.4, Kover 0.9.11 · commit: `build: move to Gradle 9.8, AGP 9.4 and built-in Kotlin 2.4`

Goal: the app builds and every check passes on the new toolchain, with the libraries still at their current versions (except what the toolchain itself forces).

### [ ] 1.1 — Docker image for AGP 9.4 and compileSdk 37

File: `docker/Dockerfile`
`CMDLINE_TOOLS=16111833`, `PLATFORM=android-37.0`, `BUILD_TOOLS=36.0.0` (comment: "the build-tools version AGP 9.4 expects by default"), `GRADLE_VERSION=9.8.2`. Confirm the platform package name with `sdkmanager --list` inside the image if `android-37.0` is refused; whatever name AGP's `compileSdk = 37` asks for is the one to install (the build error names it).

### [ ] 1.2 — Gradle wrapper 9.8.2

Files: `gradle/wrapper/gradle-wrapper.properties`, `gradle/wrapper/gradle-wrapper.jar`, `gradlew`, `gradlew.bat`
Run `./build.sh ./gradlew wrapper --gradle-version 9.8.2 --distribution-type bin` (on the old wrapper), then once more on the new one so the jar and scripts are 9.8.2's own. Keep `validateDistributionUrl=true` and `networkTimeout`.

### [ ] 1.3 — Versions in the catalog

File: `gradle/libs.versions.toml`
`agp = "9.4.1"`, `kotlin = "2.4.21"`, `kover = "0.9.11"`. Keep the `kotlin-android` plugin entry (see 1.4: it is how the KGP version is chosen, and how Dependabot keeps tracking it).

### [ ] 1.4 — Built-in Kotlin

Files: `build.gradle.kts`, `app/build.gradle.kts`
- Root: keep `alias(libs.plugins.kotlin.android) apply false`: it only puts KGP 2.4.21 on the build classpath, which is what AGP 9's built-in Kotlin compiles with (AGP 9 otherwise uses the KGP it depends on). Check with `./build.sh ./gradlew buildEnvironment` that `kotlin-gradle-plugin:2.4.21` is what resolves. If AGP rejects even the `apply false` declaration, use `buildscript { dependencies { classpath(...) } }` with a `kotlin-gradle-plugin` library entry instead, and say so in NOTES.
- App: remove `alias(libs.plugins.kotlin.android)` from `plugins {}`. Keep `kotlin.compose` and `kover`.
- Replace `kotlinOptions { jvmTarget = "17" }` with a top-level `kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }` (or `jvmToolchain`-free equivalent the AGP 9 docs show). `compileOptions` stays at Java 17.
- `compileSdk = 37` (AGP 9 also offers `compileSdk { version = release(37) }`; either is fine). `targetSdk` and `minSdk` unchanged.

### [ ] 1.5 — Whatever else AGP 9 / Gradle 9 removed

Files: `app/build.gradle.kts`, `gradle.properties`
Fix each error or removal the build reports, nothing more. Expected candidates: properties in `gradle.properties` that AGP 9 made defaults or errors (`android.useAndroidX`, `android.nonTransitiveRClass`) — drop only if the build warns or fails on them; Gradle 9 deprecations in the `robolectricSdk` configuration (`configurations.creating` → `configurations.register`/`dependencyScope` + resolvable if required) and the `Test` task's `jvmArgumentProviders`. Keep the Robolectric offline wiring and the Kover excludes exactly as they are in meaning. If AGP 9's new DSL breaks Kover 0.9.11 (no `koverVerify` / no variant reports), that is a stop: record it, don't rewrite coverage.

### [ ] 1.6 — Release workflow's apksigner path

File: `.github/workflows/release.yml`
`"$ANDROID_HOME"/build-tools/34.0.0/apksigner` → `36.0.0` (the version AGP 9.4.1 installs and builds with). Nothing else in `release.yml` changes: not the version formula, the asset name or the signing env vars.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest koverVerify lintDebug assembleDebug` passes.
- `./build.sh ./gradlew --console=plain assembleRelease` succeeds unsigned (no keystore env locally); the signing config block is unchanged in the diff.
- `./build.sh ./gradlew buildEnvironment` shows AGP 9.4.1 and KGP 2.4.21; `./gradlew --version` shows 9.8.2.
- New lint errors from the newer lint: fix them if small and in-scope; anything larger goes in NOTES (never `abortOnError = false`, never a new baseline).

---

## Checkpoint 2 — OkHttp 5 · commit: `build(deps): move OkHttp and MockWebServer to 5.5.1`

Goal: the app's HTTP runs on OkHttp 5.5.1, still only from `data/`, and the upload still sends its length.

### [ ] 2.1 — Bump

File: `gradle/libs.versions.toml`
`okhttp = "5.5.1"` (both `okhttp` and `okhttp-mockwebserver` use it; the artifact `com.squareup.okhttp3:mockwebserver` keeps the `okhttp3.mockwebserver` package in 5.x).

### [ ] 2.2 — Check the code against the OkHttp 5 changes

Files: `data/PaperlessHttp.kt`, `data/PaperlessApi.kt`, `data/PaperlessLibrary.kt`, `data/Thumbnails.kt`, `data/ThumbnailImages.kt`, `data/update/GitHubReleases.kt`, `data/update/HttpApkDownloader.kt` (all under `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/`), and the MockWebServer tests under `app/src/test/java/.../data/`.
Read the "Upgrading to OkHttp 5" notes and fix only what fails to compile or behaves differently (e.g. `MockResponse` / `SocketPolicy` / `RecordedRequest` API in the legacy package, `RecordedRequest.body` / `bodySize`, `Response.body` non-null, `toRequestBody` on a `ByteArray` still giving a known length). The upload body stays a `ByteArray` request body inside `MultipartBody`, so its `contentLength()` is known and OkHttp sends `Content-Length`. No `okhttp3` import outside `data/` (ArchitectureTest enforces it). Record in NOTES which changelog items touched this code and which didn't.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*PaperlessApiTest' --tests '*PaperlessHttpTest' --tests '*PaperlessLibraryTest' --tests '*ThumbnailsTest' --tests '*GitHubReleasesTest' --tests '*HttpApkDownloaderTest' --tests '*ArchitectureTest'`
- The upload test's `Transfer-Encoding == null` and `Content-Length == bodySize` assertions pass unedited.
- Then the full command from checkpoint 1.

---

## Checkpoint 3 — The other libraries · commit: `build(deps): bump Compose, AndroidX, coroutines, serialization, Robolectric, Coil and ML Kit`

Goal: every remaining version from #28 is in, and the suite (including the Robolectric screen tests) passes on them.

### [ ] 3.1 — Bump

File: `gradle/libs.versions.toml`
`composeBom = "2026.09.00"`, `appcompat = "1.8.0"`, `fragment = "1.9.1"`, `coroutines = "1.11.0"`, `serialization = "1.12.0"`, `robolectric = "4.17"`, `robolectricSdk = "15-robolectric-13954326-i7"` (update the comment to name Robolectric 4.17), `coil = "3.6.3"`, `mlkitScanner = "16.0.0"`. `activityCompose`, `lifecycle`, `biometric`, `work` aren't in #28 and stay (Gradle resolves the higher transitive versions Compose pulls in).

### [ ] 3.2 — Fix what the new versions break

Files: whatever the compiler, lint and the tests name, most likely Compose screens under `ui/` (Material 3 1.4 / Compose 1.12 deprecations-turned-errors, `ExperimentalMaterial3Api` changes) and the Robolectric tests in `app/src/testDebug/`. If a screen changes how it looks, apply `design-recipe`; the intent is no visual change. If one dependency can't move without a large rewrite, put it back to its current version and write why in NOTES (the ticket allows this), then keep going with the rest.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest koverVerify lintDebug assembleDebug` passes, including the `src/testDebug` Robolectric tests running offline against the new SDK jar.
- `./build.sh ./gradlew --console=plain :app:dependencies --configuration releaseRuntimeClasspath` shows each #28 version resolved (no unexpected downgrade).

---

## Out of scope

- Raising `targetSdk` (stays 35) or `minSdk` (stays 26).
- Migrating the tests from `okhttp3.mockwebserver` to `mockwebserver3`.
- Bumping libraries #28 doesn't list (activity, lifecycle, biometric, work), except as Gradle resolves them transitively.
- Any change to the signing key, the signing config, the tag format, the version-code formula or the APK asset name.
- New features, refactors or Paperless API changes.
