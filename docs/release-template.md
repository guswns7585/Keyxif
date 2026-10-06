## Keyxif Release

### Changes
- Reject incomplete or damaged source images instead of producing horizontally corrupted output.
- Preserve the selected WEBP or PNG format and verify dimensions, MIME type, and decodability before gallery publication.
- Retry gallery publication through multiple MediaStore volumes, folders, pending modes, and a collision-safe filename fallback.
- Distinguish source decoding, gallery permission, insertion, writing, and publication failures with reportable `KX-SAVE` codes.
- Generate privacy-conscious diagnostic reports containing device, storage, and exception details without photo pixels or build information.
- Let users review and send an export diagnostic report to support from the save screen after a failure.
- Add device tests for truncated-image rejection, stable ARGB decoding, public output, 40 sequential saves, and 4K WEBP preservation.
- Synchronize Android and Web version metadata for 1.1.4.

### Install Notes
- The APK must be signed with the same package name and signing key as the installed app.
- Android will show its package installer screen; users must approve installation manually.
