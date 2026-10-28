# Paperless Scan

An Android app for scanning paper documents straight into a self-hosted [Paperless-ngx](https://docs.paperless-ngx.com/) server.

- **Scan document**: Google's ML Kit document scanner (edge detection, cropping, multiple pages) makes a PDF and uploads it.
- **Take photo** and **Choose file**: for phones without Google Play services, or for PDFs you already have.
- **Share to Paperless Scan**: send PDFs and images from any other app.
- Follows each upload until Paperless has accepted it, shows its reason when it refuses one, and links to the new document.
- **Lock with fingerprint**: when the phone has a screen lock, the app opens only after a fingerprint or the phone's PIN/pattern, and locks again after a minute in the background. The switch is in the home screen's menu.

The app talks only to your own server. It's built for a server reached over the home network or a VPN such as Tailscale, so plain `http://` addresses are allowed.

## Install

1. On the phone, open the [latest release](../../releases/latest) and download `paperless-scan-<version>.apk`.
2. Open it and allow installing from your browser when Android asks.
3. Sign in with the server address (for example `http://paperless:8000`) and your Paperless username and password. The app swaps them for an API token, which it keeps encrypted with the Android Keystore, behind the phone's lock while Lock with fingerprint is on. The password itself isn't stored. If the phone's screen lock is removed, the key goes with it and the app asks you to sign in again.

To get updates automatically, add this repository's URL to [Obtainium](https://github.com/ImranR98/Obtainium).

**Scanning needs Google Play services.** On GrapheneOS, that means sandboxed Google Play. Without it, the Scan button explains that, and Take photo and Choose file still work. The scanner runs inside Play services, so it's **Play services** that needs the camera permission, not this app. If it's missing, the app says so and its **Open settings** button goes straight to Play services' permissions.

## Server

Paperless-ngx 2.x or 3.x: the app reads both API v9 and v10 answers. Paperless only refuses a duplicate upload when the server sets `PAPERLESS_CONSUMER_DELETE_DUPLICATES=true`; otherwise it keeps it as a second document.

## Architecture

Three layers, each depending only on the one below it:

```
ui/       Compose screens + ViewModels (state as StateFlow)
  ↓
domain/   Models, repository interfaces, use cases. Plain Kotlin, no Android.
  ↑
data/     PaperlessApi (OkHttp + paperless-ngx REST), SealedSettingsRepository + KeystoreTokenVault, UriDocuments
```

`data` implements the interfaces `domain` defines, so the use cases never see HTTP or Android. `AppContainer` wires the layers together by hand, without a DI framework.

| Use case | What it does |
|---|---|
| `LoginUseCase` | Normalizes the server address, exchanges username and password for a token via `POST /api/token/`, saves it |
| `UploadDocumentUseCase` | `POST /api/documents/post_document/` (with a `Content-Length`: Paperless reads a chunked upload as empty), then polls `GET /api/tasks/?task_id=` until Paperless accepts or refuses the document |
| `AppLock` | Locks the app again (and forgets the unlocked token) after a minute or more in the background, while the lock is on |

Any change to a Paperless call follows the current [API docs](https://docs.paperless-ngx.com/api/) and the server's own schema at `<server>/api/schema/view/` (see `CLAUDE.md`).

## Development

There's no toolchain to install: the JDK, Android SDK and Gradle live in a Docker image (`docker/Dockerfile`).

```sh
./build.sh                                     # debug APK in app/build/outputs/apk/debug/
./build.sh ./gradlew testDebugUnitTest         # unit tests
./build.sh ./gradlew koverHtmlReportDebug      # coverage report in app/build/reports/kover/
./build.sh install                             # build and adb-install on a USB-connected phone
```

Tests cover the domain and data layers and the ViewModels, with the HTTP client tested against MockWebServer. CI fails when line coverage drops below 80%. Compose screens and the Keystore code need a device and aren't counted.

## CI/CD

- **CI** (`.github/workflows/ci.yml`) runs on every push and PR: unit tests, the coverage check, lint and a debug build. The job summary shows the coverage numbers.
- **Release** (`.github/workflows/release.yml`) runs when a `vX.Y.Z` tag is pushed. It tests, builds a signed APK and publishes it as a GitHub Release. The version code is derived from the tag (`v1.2.3` → 10203).

  ```sh
  git tag v0.2.0 && git push origin v0.2.0
  ```

- Signing uses the `RELEASE_KEYSTORE_B64` and `RELEASE_KEYSTORE_PASSWORD` repository secrets. Android only installs an update when it's signed with the same key, so **keep a backup of the keystore**.
- Dependabot opens weekly PRs for Gradle dependencies and monthly ones for Actions.
