# Keyxif Export / File Pipeline Spec (Android → Web Port)

Source (Android 1.0.3):
- `app/src/main/java/com/keyxif/app/domain/export/ExportWorker.kt`
- `app/src/main/java/com/keyxif/app/domain/export/ImageExporter.kt`
- `app/src/main/java/com/keyxif/app/domain/export/ExportWorkPayload.kt`
- `app/src/main/java/com/keyxif/app/util/FileNameUtils.kt`
- `app/src/main/java/com/keyxif/app/util/BitmapUtils.kt`
- `app/src/main/java/com/keyxif/app/util/IntentShareUtils.kt`
- `app/src/main/java/com/keyxif/app/domain/model/BuildInfoDisplay.kt` (meaningful-text filter)

---

## 1. File naming (`FileNameUtils.outputName(buildInfo, index, settings)`)

Final name shape (all rules):

```
{prefix}_{NN}.{ext}
```

- `NN` = 1-based batch index, `index.toString().padStart(2, '0')` → `01`, `02`, … `10`, and `100` for 3-digit (padStart never truncates).
- `ext` by `OutputFormat`: `WEBP → "webp"`, `PNG → "png"`.
- `prefix` per `FileNameRule`, after sanitization; if the sanitized prefix is blank, fall back to `"Keyxif"`.

### Per-rule prefix (raw, before sanitize)

| FileNameRule | Raw prefix | Example |
|---|---|---|
| `KEYXIF_INDEX` (default) | Literal `Keyxif` | `Keyxif_01.webp` |
| `HOUSING_INDEX` | `buildInfo.housing` meaningful-or-empty | `QK65_01.webp` |
| `NICKNAME_INDEX` | `buildInfo.nickname` meaningful-or-empty | `My_Board_01.webp` |
| `HOUSING_KEYCAP_INDEX` | meaningful housing + meaningful keycap joined with `_` (either may be missing; both missing → empty → fallback `Keyxif`) | `QK65_GMK_Olivia_01.webp` |

### "Meaningful" text filter (`meaningfulBuildTextOrNull`)

Applied to housing/nickname/keycap before use:
1. `trim()`; blank → null.
2. Normalize for comparison only: lowercase + collapse whitespace to single space.
3. Return null if the normalized value is in the blocklist:
   `untitled keyboard`, `untitled`, `keyboard build`, `unknown`, `null`, `none`, `n/a`, `na`, `미입력`, `없음`, `정보 없음`, `빌드 정보 미입력`
4. Otherwise return the original trimmed value (original casing preserved).

### Sanitization (`FileNameUtils.sanitize`)

Applied to the assembled prefix (and to `saveDirectoryName`):
1. `trim()`
2. Remove all of: `\ / : * ? " < > |`  (regex `[\\/:*?"<>|]` → removed, not replaced)
3. Collapse any whitespace run (`\s+`) to a single `_`
4. Truncate to first **64 characters**
5. If result is blank → `"Keyxif"` (for directory: `"Keyxif"` too)

### Secondary overload (used elsewhere, e.g. re-export by build name)

```
{sanitize(buildName) or "Keyxif"}_Keyxif_{NN}.webp
```

### Collision handling

The app does **no explicit collision handling**. It hands `DISPLAY_NAME` to Android MediaStore, which auto-uniquifies duplicates itself (Android appends ` (1)` etc. to the base name). **Web port must implement its own uniquifier** when writing to a user-picked directory (File System Access API): if `name.ext` exists, try `name (1).ext`, `name (2).ext`, … For plain `<a download>` downloads the browser dedupes automatically — no action needed.

---

## 2. Resolution pipeline

### Constants (`BitmapUtils`)

```
SAVE_LONG_SIDE_LIMIT    = 4096
PREVIEW_LONG_SIDE_LIMIT = 720
```

### Effective save long side (`ExportWorker.saveLongSide`)

```
if (settings.keepOriginalResolution)  → Int.MAX_VALUE   // i.e. unlimited, no downscale
else                                  → settings.maxLongSidePx ?: 4096
```

- `keepOriginalResolution` default: `true`.
- `maxLongSidePx` is nullable; only positive values are honored (`optNullableInt` drops `<= 0`).
- The limit is passed to the renderer as `maxLongSide`, and to `decodeOrientedBitmap` when loading source photos.

### Decode + downscale algorithm (`decodeOrientedBitmap`)

1. Read EXIF orientation from the stream (default `ORIENTATION_NORMAL` on failure).
2. Bounds-only decode to get intrinsic width/height.
3. Compute power-of-two `inSampleSize`: start at 1, double while `longestSide / sample > maxLongSide * 1.25` (the 1.25 slack avoids over-sampling before the fine downscale).
4. Decode at that sample size, ARGB_8888.
5. Apply EXIF orientation via matrix: ROTATE_90/180/270, FLIP_HORIZONTAL (`scaleX -1`), FLIP_VERTICAL (`scaleY -1`), TRANSPOSE (rotate 90 + flipX), TRANSVERSE (rotate 270 + flipX).
6. Final exact downscale if still `longest > maxLongSide`: uniform scale `ratio = maxLongSide / longest`, bilinear filtering (`createScaledBitmap(..., filter = true)`), each dimension `roundToInt().coerceAtLeast(1)`.

### Web mapping

- EXIF orientation: `createImageBitmap(blob, { imageOrientation: 'from-image' })` applies EXIF for you — do **not** re-apply the matrix table above (double-rotation bug). Note `'from-image'` is the default in modern browsers, but pass it explicitly.
- Two-phase decode is unnecessary on web; `createImageBitmap` also accepts `resizeWidth`/`resizeHeight` + `resizeQuality: 'high'` to combine decode and downscale in one step. Otherwise decode full-size then draw to a canvas at target size (`ctx.imageSmoothingQuality = 'high'` ≈ bilinear/better).
- Replicate the rule exactly: if `keepOriginalResolution` → no resize; else clamp longest side to `maxLongSidePx ?? 4096`, preserving aspect ratio, `Math.round` each side, min 1px.
- Beware canvas max dimensions (~16384px, and iOS Safari total-area limits) when `keepOriginalResolution` is on.

---

## 3. Quality

- `settings.webpQuality`: Int, default **92** (`ImageExporter.DEFAULT_WEBP_QUALITY = 92`), clamped `coerceIn(1, 100)` at save time.
- WEBP: lossy WebP at that quality (API 30+: `WEBP_LOSSY`; older: legacy `WEBP`).
- PNG: `Bitmap.CompressFormat.PNG` — **quality parameter is ignored** (lossless).

### Web mapping (`canvas.toBlob` / `OffscreenCanvas.convertToBlob`)

```js
const quality = clamp(webpQuality, 1, 100) / 100;   // 92 → 0.92
canvas.toBlob(cb, 'image/webp', quality);           // WEBP
canvas.toBlob(cb, 'image/png');                     // PNG — omit quality, ignored anyway
```

- MIME types match Android: `image/webp`, `image/png`.
- Note: Safari < 17-ish cannot encode `image/webp` via toBlob — feature-detect and fall back to PNG (or a WASM webp encoder) with a UI notice.

---

## 4. Batch export flow (`ExportWorker.doWork`)

### Input

Payload is JSON on disk (`ExportWorkPayloadCodec`): `{ template: <CardTemplate name>, settings: <AppSettings JSON>, photos: [{ id, uri, displayName, buildInfo{housing, switchName, plate, mount, keycap, nickname, logoId, customLogoUri, logoDisabled}, analysisResult{paletteColors:int[], analyzedAt, errorMessage, analysisMode, analysisCenterCropRatio} }] }`. Empty photo list → immediate failure ("저장할 사진이 없습니다."). Web equivalent: an in-memory job object; persist to IndexedDB if you want resumability.

### Ordering & loop

- Photos are processed **strictly in payload order**, sequentially (index 1..N); `index` (1-based) is the `NN` in the filename.
- Per photo: render (template + settings + `maxLongSide` from §2) → build filename (§1) → encode+save (§3) → record metadata.

### Failure policy (`skipFailedOnBatchSave`, default `true`)

- Each photo's save is individually try/caught.
- Success → `success++`.
- Failure → `failure++`, photo id appended to `failedIds`, and:
  - `skipFailedOnBatchSave == true` → continue with next photo.
  - `skipFailedOnBatchSave == false` → **break out of the loop immediately** (remaining photos untouched).
- The overall job still completes as "success" either way — failures are reported in counts, not as job failure.

### Progress reporting shape (emit before and after each item)

```
{
  current: number,          // 1-based index of the item being processed
  total: number,            // payload.photos.length
  success_count: number,
  failure_count: number,
  message: string,          // "저장 준비 중" | "{current} / {total} 처리 중" | final summary
  current_photo_id: string? // id of the photo in flight (null on prep/final)
}
```

Final completion result adds:

```
{
  photo_ids: string[],      // all photo ids in the batch, in order
  failed_ids: string[],     // ids that failed, in order encountered
  saved_uri: string?        // URI of the LAST successfully saved image
}
```

Final message: `"저장 완료: 성공 {success}장, 실패 {failure}장"`. Android also posts a notification; web analog = optional Notification API + toast.

### Exported-gallery record (`ExportedImage`) — one per successful save

| Field | Value |
|---|---|
| `id` | `"{System.currentTimeMillis()}-{photo.id}-{index}"` (web: `` `${Date.now()}-${photo.id}-${index}` ``) |
| `uri` | saved content URI as string (web: object URL / OPFS path / file handle key) |
| `fileName` | the exact generated display name incl. extension |
| `createdAt` | epoch millis at record time |
| `width`, `height` | final saved bitmap dimensions (post-downscale) |
| `fileSizeBytes` | actual encoded file size (web: `blob.size`), min 0, 0 on failure to stat |
| `templateName` | `CardTemplate` enum name |
| `housing` | `buildInfo.housing` passed through meaningful-filter (nullable) |
| `switchName` | `buildInfo.switchName` meaningful-or-null |
| `keycap` | `buildInfo.keycap` meaningful-or-null |
| `nickname` | `buildInfo.nickname` meaningful-or-null |
| `paletteColors` | `analysisResult.paletteColors` (int ARGB list; zeros filtered out on decode) |

Cleanup: payload temp directory is deleted in `finally` regardless of outcome.

### Save destination

Android writes via MediaStore into `Pictures/{sanitize(saveDirectoryName) or "Keyxif"}` with `IS_PENDING` 1→0 (atomic publish), and **deletes the MediaStore entry if encoding fails** (no partial files). Web equivalents, in preference order:
1. File System Access API directory handle (user picks folder once) — write to temp name then rename, or write-then-close for atomicity; delete on failure.
2. Fallback: sequential `<a download>` blob downloads (no directory control, browser handles collisions).
3. OPFS as the "exported gallery" backing store for the in-app gallery view.

---

## 5. Share (`IntentShareUtils` → Web Share API)

Android side is **inbound** share handling (app as share target): accepts `ACTION_SEND` / `ACTION_SEND_MULTIPLE` with `type` starting `image/`; collects URIs from `EXTRA_STREAM` plus ClipData items; dedupes by URI string; filters each URI to resolved MIME `image/*` (or unresolvable → allowed).

Web mapping:

- **Inbound (share target)**: Web Share Target API (PWA manifest `share_target` with `method: "POST"`, `enctype: "multipart/form-data"`, `params.files: [{ name: "images", accept: ["image/*"] }]`). Apply the same filtering: accept only `image/*` files, dedupe (by name+size+lastModified since web has no URIs), ignore non-images silently.
- **Outbound (sharing an exported image)**: `navigator.share({ files: [new File([blob], fileName, { type: mimeType })] })`, gated by `navigator.canShare({ files })`. Fallback: download link + clipboard (`ClipboardItem` with the image blob).

---

## Quick reference

| Setting | Default | Effect |
|---|---|---|
| `outputFormat` | `WEBP` | ext + encoder + MIME |
| `webpQuality` | `92` | WebP quality 1–100 → toBlob 0.01–1.0; PNG ignores |
| `keepOriginalResolution` | `true` | disables downscale entirely |
| `maxLongSidePx` | `null` | null → 4096 (`SAVE_LONG_SIDE_LIMIT`) when downscaling |
| `fileNameRule` | `KEYXIF_INDEX` | prefix scheme |
| `saveDirectoryName` | `"Keyxif"` | Pictures subfolder (sanitized) |
| `skipFailedOnBatchSave` | `true` | continue vs. abort-on-first-failure |
