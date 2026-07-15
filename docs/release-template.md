## Keyxif Release

### Changes
- Check for updates once on every cold app launch so newly published versions are not missed.
- Improve package-installer compatibility and remove a restricted install intent flag.
- Resume APK installation automatically after the user grants unknown-source permission.
- Track the current download job and keep the verified APK in app-private storage until installation.
- Verify that release signing, package identity, presets, settings, and recent-search storage remain compatible across updates.

### Install Notes
- The APK must be signed with the same package name and signing key as the installed app.
- Android will show its package installer screen; users must approve installation manually.
