# Keyxif Android

Keyxif Android is the APK-distributed native app built with Kotlin and Jetpack Compose.

## Commands

```powershell
cd android
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```

Release signing uses these environment variables:

- `KEYSTORE_FILE`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

The app update checker reads the root repository metadata file at `docs/update.json` through `BuildConfig.UPDATE_JSON_URL`.
