# Notes — Move the build to Gradle 9, AGP 9 and the current libraries

Issue: #30 — Move the build to Gradle 9, AGP 9 and the current libraries (https://github.com/YasarMahomedAbbas/paperless-companion/issues/30)
Branch: `chore/30-gradle-9-agp-9`

## Planning

- Approach: three commits — toolchain (Gradle 9.8.2, AGP 9.4.1 with built-in Kotlin 2.4.21, Kover 0.9.11, Docker image, release apksigner path), then OkHttp 5.5.1, then the remaining #28 libraries; drop the `kotlin.android` plugin from the app module and keep it only as `apply false` at the root to pin KGP.
- Decided (the pipeline took these on the user's behalf; flag if wrong): compileSdk 35 → 37 because Compose UI 1.12.1 declares `minCompileSdk=37`; targetSdk stays 35 (a runtime-behaviour change, its own ticket); tests keep the `okhttp3.mockwebserver` API from the 5.x `mockwebserver` artifact rather than migrating to `mockwebserver3`; release.yml's apksigner moves to build-tools 36.0.0, nothing else in the release contract changes.
- Open risks: Kover 0.9.11 with AGP 9's new DSL and built-in Kotlin (if variant reports or `koverVerify` break, stop and record); stricter lint on the new toolchain under `abortOnError`; the Docker image's SDK is read-only so platform/build-tools names must match what AGP asks for exactly; the Release workflow's first run on AGP 9 happens only at merge-release; `verify.env` is absent, so the real-server check will be skipped.
- Supersedes Dependabot's #28: the PR body must carry `Closes #28` (alongside `Closes #30`), and merge-release must confirm #28 is closed after the merge (close it with a pointer to this PR if GitHub didn't).
- Plan approved by the pipeline as written.
