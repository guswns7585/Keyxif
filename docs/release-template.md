## Keyxif Release

### Changes
- Preserve the selected WEBP or PNG format while retrying encoder failures at memory-safe resolutions.
- Stage encoded output before publishing and recover from OEM-specific MediaStore pending-state behavior.
- Separate source access, rendering, encoding, temporary storage, and gallery failures with reportable `KX-SAVE` error codes.
- Avoid retaining Liquid Glass preview caches during final export and release duplicate blur bitmaps promptly.
- Keep batch exports running when individual photos fail and report the first actionable failure reason.
- Upgrade WorkManager to the current stable release for more reliable long-running foreground exports.
- Add MediaStore tests for public output, 40 sequential WEBP saves, and 4K WEBP format preservation.
- Synchronize Android and Web version metadata for 1.1.3.

### Install Notes
- The APK must be signed with the same package name and signing key as the installed app.
- Android will show its package installer screen; users must approve installation manually.
