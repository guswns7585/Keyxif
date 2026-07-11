# Keyxif

Keyxif is a native Android app built with Kotlin and Jetpack Compose. It turns keyboard photos into share-ready build cards, saves WEBP/PNG results to `Pictures/Keyxif`, and keeps a local metadata gallery of finished exports.

## Build And Run

1. Open this folder in Android Studio.
2. Sync Gradle dependencies.
3. Run the `app` configuration on Android API 26 or newer.

The app uses Kotlin, Jetpack Compose, Material 3, Coil, DataStore, WorkManager, and AndroidX EXIF.

## Release Flow

Keyxif can be distributed outside the Play Store through signed APKs on GitHub Releases.

Required GitHub Secrets:

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

The release APK must use the same `applicationId` and the same signing key as the installed app. Android will reject update installs if the signing key differs.

Release steps:

1. Bump `versionCode` and `versionName`.
2. Update release notes in `docs/release-template.md`.
3. Run `scripts/release.ps1 -VersionName 1.1.0 -VersionCode 2 -OwnerRepo owner/keyxif` on Windows, or `scripts/release.sh 1.1.0 2 owner/keyxif` on macOS/Linux.
4. Push the generated tag, for example `v1.1.0`.
5. GitHub Actions workflow `Release APK` builds `assembleRelease`, signs the APK from Secrets, creates a GitHub Release, uploads `keyxif-${versionName}.apk`, and regenerates `docs/update.json`.

`docs/update.json` fields:

- `latestVersionCode`
- `latestVersionName`
- `minRequiredVersionCode`
- `title`
- `message`
- `apkUrl`
- `releaseNoteUrl`
- `forceUpdate`

`BuildConfig.UPDATE_JSON_URL` points to the app's default update metadata URL. Replace the placeholder repository URL in `app/build.gradle.kts` before publishing.

## Self Update

The app checks `update.json` once per day on launch and any time the user presses "업데이트 확인" in Settings.

When a newer version is available:

1. The app shows the update dialog.
2. "지금 업데이트" downloads the APK with WorkManager into the app cache under `updates/`.
3. Progress is shown in the app and, when notification permission is available, in the `keyxif_update` notification channel.
4. After download, the app opens Android's package installer using `FileProvider`.
5. The user must approve installation manually.

Android 8.0+ may require "install unknown apps" permission for Keyxif. If permission is missing, the app opens the system settings screen for this source.

Fallback behavior:

- Network, JSON, or download failures are shown without blocking app use.
- If package installer launch fails, the app falls back to opening `apkUrl` in the browser.
- Silent/unattended APK installation is intentionally not implemented.

## Finished Image Gallery

The "완성 이미지" screen shows images that were successfully exported by Keyxif.

- Result files remain in `Pictures/Keyxif`.
- The app stores only metadata in DataStore: URI, file name, size, template, build info, and palette colors.
- Individual save and batch save record only successful exports.
- The gallery uses a lazy thumbnail grid and Coil image loading.
- Detail view supports Android Sharesheet sharing, opening in the system gallery, removing only the app record, or deleting the actual file after confirmation.
- If a file was deleted outside Keyxif, Settings can prune inaccessible records.

## Project Layout

```text
.github/workflows/        GitHub Release APK automation
docs/                     update.json and release note templates
scripts/                  local release helper scripts
app/src/main/java/com/keyxif/app/
  data/
    exported/             Finished export metadata repository
    presets/              Built-in preset and logo data
    repository/           App settings, draft, preset, recent data
    update/               update.json fetch and APK download worker
  domain/
    analysis/             Photo palette extraction
    export/               MediaStore export worker and payloads
    model/                App models and enums
    renderer/             Canvas template rendering
  ui/
    components/           Compose components and previews
    screens/              Main app, export, settings, gallery screens
  util/                   Bitmap, intent, and filename helpers
```
