# Paperless Scan

An Android app for scanning paper documents straight into a self-hosted [Paperless-ngx](https://docs.paperless-ngx.com/) server.

- **Documents**: the app opens on your documents, newest first. Search their full text as you type, filter by tags, correspondent and document type, and sort by date, date added, title or correspondent. Uploads start from **Add**.
- **Scan document**: Google's ML Kit document scanner (edge detection, cropping, multiple pages) makes a PDF and uploads it.
- **Take photo** and **Choose file**: for phones without Google Play services, or for PDFs you already have.
- **Share to Paperless Scan**: send PDFs and images from any other app.
- Follows each upload until Paperless has accepted it, shows its reason when it refuses one, and opens the new document in the app.
- **Open a document**: its pages (pinch to zoom), title, tags and details in a panel you drag up or down, with Share, Download (straight to Downloads on Android 10+, a Save-as picker on 8–9), Get original and Open in Paperless.
- **Organise documents**: **Edit** in the document view changes its title, date, correspondent, document type, storage path and tags. **Move to trash** (in its More menu) sends it to the Paperless trash, with Undo on the list for 8 seconds. Long-press a document on the list to select several, then change their tags, correspondent (From) or type, or move them all to the trash. Tags and correspondents are created in Paperless itself: a picker with no match offers **Open Paperless**.
- **Saved views, notes and more like this**: the views saved in Paperless's web interface show the same documents in the app, the server's home views as chips at the start of the list and every view under **Views**; search and filters narrow a view. **Notes** in a document's details lists its notes, newest first, and adds one (hidden when the server has notes switched off). **More like this** (in its More menu) lists the documents Paperless finds similar.
- **Unlock with fingerprint**: when the phone has a screen lock, the app opens only after a fingerprint or the phone's PIN/pattern, and locks again after a minute in the background. The switch is in **Settings** (the gear on the Documents screen).

The app talks only to your own server. It's built for a server reached over the home network or a VPN such as Tailscale, so plain `http://` addresses are allowed.

## Install

1. On the phone, open the [latest release](../../releases/latest) and download `paperless-scan-<version>.apk`.
2. Open it and allow installing from your browser when Android asks.
3. Sign in with the server address (for example `http://paperless:8000`) and your Paperless username and password. The app swaps them for an API token, which it keeps encrypted with the Android Keystore, behind the phone's lock while Unlock with fingerprint is on. The password itself isn't stored. If the phone's screen lock is removed, the key goes with it and the app asks you to sign in again.

Install the first APK by hand; after that the app updates itself. Release builds check GitHub Releases once a day and each time the app opens, and post **Update ready** when there's a newer version. Tap it, or open **Settings → Updates**, and tap **Install**: Android confirms every update, and asks once to allow the app to install apps ("Install unknown apps"). Before handing the file to Android, the app checks its size and sha256 against the release's digest and that it's signed with the app's own key; anything else is refused with the reason. Debug builds only check when you tap **Check now** (their key differs, so a release can't replace them anyway). A copy installed before this feature needs one more update by hand (or Obtainium) to get it.

**Scanning needs Google Play services.** On GrapheneOS, that means sandboxed Google Play. Without it, Scan document explains that, and Take photo and Choose file still work. The scanner runs inside Play services, so it's **Play services** that needs the camera permission, not this app. If it's missing, the app says so and its **Open settings** button goes straight to Play services' permissions.

## Server

Paperless-ngx 2.x or 3.x: the app reads both API v9 and v10 answers. Paperless only refuses a duplicate upload when the server sets `PAPERLESS_CONSUMER_DELETE_DUPLICATES=true`; otherwise it keeps it as a second document.

## Architecture

Three layers; `ui` and `data` both depend on `domain`, never on each other:

```
ui/       Compose screens + ViewModels (state as StateFlow)
  ↓
domain/   Models, repository interfaces, use cases. Plain Kotlin, no Android.
  ↑
data/     PaperlessApi (uploads, login) and PaperlessLibrary (documents, labels, trash, bulk edit,
          saved views, notes), both on PaperlessHttp (OkHttp + paperless-ngx REST);
          PaperlessViews (a saved view's filter rules as the web UI's query parameters); Thumbnails + ThumbnailImages (Coil),
          SealedSettingsRepository + KeystoreTokenVault, UriDocuments,
          PhoneExports (share through the FileProvider, save to Downloads via MediaStore),
          update/ (GitHub Releases, APK download, PackageInstaller, WorkManager, the notification)
```

`data` implements the interfaces `domain` defines, so the use cases never see HTTP or Android. `AppContainer` wires the layers together by hand, without a DI framework, and is the only file outside `data` that names a `data` class. `ArchitectureTest` fails the build when an import crosses these lines.

Thumbnails go through the app-wide Coil loader that `PaperlessApp` provides: a screen writes `AsyncImage(model = document.thumbnail, …)` and never builds the URL or adds the token. The loader adds the token only for the signed-in server's scheme, host and port, and caches each thumbnail on disk keyed on the document's modified time.

| Use case | What it does |
|---|---|
| `LoginUseCase` | Normalizes the server address, exchanges username and password for a token via `POST /api/token/`, saves it |
| `UploadDocumentUseCase` | `POST /api/documents/post_document/` (with a `Content-Length`: Paperless reads a chunked upload as empty), then polls `GET /api/tasks/?task_id=` until Paperless accepts or refuses the document |
| `AppLock` | Locks the app again (and forgets the unlocked token) after a minute or more in the background, while the lock is on |
| `CheckForUpdateUseCase` | Asks GitHub for the latest release (`GET /repos/…/releases/latest`), keeps it as the offer when its version code is higher than the installed one, and announces each version once |
| `DocumentLinkUseCase`, `LabelsLinkUseCase` | Give the web address of an uploaded document on the signed-in server, for the viewer's Open in Paperless, and of a label list (tags, correspondents, …) for a picker's Open Paperless |
| `BrowseDocumentsUseCase`, `GetDocumentUseCase`, `OpenDocumentFileUseCase` | One page of documents (`GET /api/documents/`: sort, filter by tags, correspondent and type, full-text search, a saved view's rules, or `more_like_id`), one document with its text, and its preview, archived or original file streamed into the app's cache, reporting progress |
| `ExportDocumentUseCase` | Hands a fetched file to another app (Share) or saves a copy to Downloads or a picked location |
| `SavedViewsUseCase` | The server's saved views (`GET /api/saved_views/`), as the home views and as every view, quick-list (sidebar) ones first, in the server's order: v9 reads the view's own flags, v10 the ids in `GET /api/ui_settings/` |
| `NotesUseCase` | A document's notes, newest first, and adding one (`GET`/`POST /api/documents/{id}/notes/`; a 200 with `{"error"}` is a refusal), and whether `ui_settings` has notes switched on |
| `LoadLabelsUseCase` | Every tag, correspondent, document type and storage path, loaded side by side |
| `UpdateDocumentUseCase`, `DeleteDocumentUseCase`, `TrashUseCase`, `BulkEditUseCase` | Edit one document (`PATCH`), move it to the trash, list and restore the trash, and change or delete many at once (`bulk_edit`; delete posts v10's `/api/documents/delete/` and falls back to `bulk_edit` on Paperless 2) |

Any change to a Paperless call follows the current [API docs](https://docs.paperless-ngx.com/api/) and the server's own schema at `<server>/api/schema/view/` (see `CLAUDE.md`).

## Development

There's no toolchain to install: the JDK, Android SDK and Gradle live in a Docker image (`docker/Dockerfile`).

```sh
./build.sh                                     # debug APK in app/build/outputs/apk/debug/
./build.sh ./gradlew testDebugUnitTest         # unit tests
./build.sh ./gradlew koverHtmlReportDebug      # coverage report in app/build/reports/kover/
./build.sh install                             # build and adb-install on a USB-connected phone
```

Tests cover the domain and data layers and the ViewModels, with the HTTP client tested against MockWebServer. Compose screens can be tested on the JVM with Robolectric and Compose ui-test (in `app/src/testDebug/`; Robolectric runs offline against an SDK jar Gradle keeps in its cache). CI fails when line coverage drops below 80%. Screens, the Keystore code and the Android glue (PDF rendering, content URIs, MediaStore, preferences, the package installer, WorkManager, notifications, Coil) aren't counted in the coverage numbers (the excludes are in `app/build.gradle.kts`).

## CI/CD

- **CI** (`.github/workflows/ci.yml`) runs on every push and PR: unit tests, the coverage check, lint and a debug build. The job summary shows the coverage numbers.
- **Release** (`.github/workflows/release.yml`) runs when a `vX.Y.Z` tag is pushed. It tests, builds a signed APK and publishes it as a GitHub Release. The version code is derived from the tag (`v1.2.3` → 10203). Its signature check calls `apksigner` from a fixed build-tools version, the one AGP expects and `docker/Dockerfile` installs; an AGP bump that moves build-tools moves all three, or the release fails only at tag time.

  ```sh
  git tag v0.2.0 && git push origin v0.2.0
  ```

- Signing uses the `RELEASE_KEYSTORE_B64` and `RELEASE_KEYSTORE_PASSWORD` repository secrets. Android only installs an update when it's signed with the same key, so **keep a backup of the keystore**.
- Dependabot opens weekly PRs for Gradle dependencies and monthly ones for Actions.
