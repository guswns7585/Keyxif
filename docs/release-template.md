## Keyxif Release

### Changes
- Prevent completed WorkManager jobs from reusing an APK for an older release.
- Verify the downloaded APK package and version before opening the installer.
- Use a unique installer URI and disable HTTP cache reuse for every update download.

### Install Notes
- The APK must be signed with the same package name and signing key as the installed app.
- Android will show its package installer screen; users must approve installation manually.
