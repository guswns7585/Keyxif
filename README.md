# Keyxif

Keyxif is a monorepo for the native Android app and the browser-based Web version. Both apps make keyboard photos into share-ready build images, but they are built, released, and deployed independently.

## Projects

- `android/`: Kotlin + Jetpack Compose Android app for APK distribution.
- `web/`: static HTML/CSS/JavaScript Web app for browser use and Cloudflare Pages deployment.
- `shared/`: common schemas, template specs, brand notes, and source asset guidelines.
- `docs/`: release metadata, update JSON files, and release notes.
- `scripts/`: release/build entry points for Android and Web.

## Structure

```text
Keyxif/
  android/
    app/
    build.gradle.kts
    settings.gradle.kts
    gradlew
    gradlew.bat
  web/
    package.json
    index.html
    data/
    scripts/
    dist/
  shared/
    assets/
      logos/
      icons/
      sample-images/
    templates/
      template-specs.json
    schema/
      keyxif-build-info.schema.json
      exported-image.schema.json
    docs/
      brand-guidelines.md
  docs/
    update.json
    web-update.json
    release-template.md
  scripts/
    release-android.ps1
    release-android.sh
    release-web.ps1
    release-web.sh
  .github/
    workflows/
      android-release.yml
      web-deploy.yml
```

## Android

```powershell
cd android
.\gradlew.bat :app:assembleDebug
```

The Android app keeps its package name, signing setup, local update checker, template rendering, and finished image gallery under `android/app`.

Android release metadata stays in root `docs/update.json` because the installed app reads that public URL.

Release helper:

```powershell
.\scripts\release-android.ps1 -VersionName 1.0.4 -VersionCode 5 -OwnerRepo guswns7585/keyxif
```

GitHub Actions workflow:

- `.github/workflows/android-release.yml`
- Trigger: tag push matching `v*.*.*` or manual dispatch
- Runs Gradle inside `android/`
- Builds a signed APK, creates a GitHub Release, uploads the APK, and updates `docs/update.json`

Required Android release secrets:

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

## Web

```powershell
cd web
npm install
npm run dev
npm run build
npm run preview
```

The Web version is static and does not require Android to build. `npm run build` creates `web/dist`.

Web deploy helper:

```powershell
.\scripts\release-web.ps1 -VersionName 0.1.0
```

GitHub Actions workflow:

- `.github/workflows/web-deploy.yml`
- Trigger: `main` push touching `web/**`, `shared/**`, or manual dispatch
- Runs `npm ci` and `npm run build` inside `web/`
- Deploys `web/dist` to Cloudflare Pages with Wrangler when Cloudflare secrets are present

Required Web deploy secrets for GitHub Actions:

- `CLOUDFLARE_ACCOUNT_ID`
- `CLOUDFLARE_API_TOKEN`
- `CLOUDFLARE_PAGES_PROJECT_NAME`

## Cloudflare Pages

For the simplest Cloudflare setup, connect the GitHub repository directly in Cloudflare Pages and use:

- Root directory: `web`
- Build command: `npm run build`
- Build output directory: `dist`
- Production branch: `main`

Cloudflare Pages Git integration automatically builds and deploys when the connected branch changes. If you use the GitHub Actions workflow instead, create a Pages project first and add the three Cloudflare secrets listed above.

Official references:

- https://developers.cloudflare.com/pages/get-started/git-integration/
- https://developers.cloudflare.com/pages/configuration/build-configuration/
- https://developers.cloudflare.com/pages/how-to/use-direct-upload-with-continuous-integration/

## Shared Rules

- Android must not require a Web build.
- Web must not require an Android build.
- Keep shared code sharing conservative: common schemas, template specs, asset naming rules, and documentation only.
- Treat `shared/schema` as the reference for portable build/export metadata.
- Update `shared/templates/template-specs.json` before adding a public template to either platform.
- Keep platform-specific generated resources in each platform folder.

## Versioning

- Android version: `android/app/build.gradle.kts` `versionCode` / `versionName`
- Android release tag: keep the existing `v1.0.4` style
- Web version: `web/package.json` `version`
- Web release tag, if needed: `web-v0.1.0`
- Android update metadata: `docs/update.json`
- Optional Web metadata: `docs/web-update.json`

## Licenses And Notices

Open-source library notices should be shown inside each app's settings/about surface or documented in platform README files.

Third-party vendor names and logos can have trademark or copyright restrictions. Do not add redistributable brand marks to `shared/assets` or platform resources unless permission is clear.
