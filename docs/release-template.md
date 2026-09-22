## Keyxif Release

### Changes
- Add the Liquid Glass Frame template on Android and Web with adaptive refraction, blur, highlights, readable text, and uncropped photo rendering.
- Improve Liquid Glass rendering quality with smoother anti-aliased edges and bilinear fallback sampling.
- Reuse blur and glass layers to speed up repeated previews without lowering final image quality.
- Make background exports more reliable with staged failure reporting and memory-aware retries.
- Preserve shared images safely when restoring or restarting a previous editing session.
- Copy Photo Picker images and custom logos into app-owned storage before background export.
- Continue batch saves when an older photo URI is no longer accessible, and safely fall back to the Keyxif logo when a custom logo permission has expired.
- Synchronize Android and Web version metadata for 1.1.2.

### Install Notes
- The APK must be signed with the same package name and signing key as the installed app.
- Android will show its package installer screen; users must approve installation manually.
