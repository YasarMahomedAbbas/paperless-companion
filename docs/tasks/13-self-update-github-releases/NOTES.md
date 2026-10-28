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
