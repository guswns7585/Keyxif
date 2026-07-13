# Keyxif — State & Flow Spec for 1:1 Web Port

Extracted from Keyxif 1.0.3 Android sources. This document is self-sufficient: the web port is written from this doc without re-reading Kotlin.

Source files:
- `app/src/main/java/com/keyxif/app/ui/KeyxifViewModel.kt` (state machine)
- `app/src/main/java/com/keyxif/app/ui/KeyxifApp.kt` (scaffold / step bar / dialogs / snackbar)
- `app/src/main/java/com/keyxif/app/ui/Localization.kt`
- `app/src/main/java/com/keyxif/app/ui/theme/Theme.kt`
- `app/src/main/java/com/keyxif/app/domain/model/Models.kt`, `BuildInfoDisplay.kt`
- `data/repository/AppSettingsRepository.kt`, `DraftSessionRepository.kt`, `RecentStore.kt`, `BuildPresetRepository.kt`, `data/exported/ExportedImageRepository.kt`

---

## 1. Domain model shapes (exact fields + defaults)

All types below must exist identically in the web port (TypeScript interfaces). Android `Uri` fields become web equivalents noted in §5.

### 1.1 Enums (member names are load-bearing — they are persisted as strings)

```
AppStep          = Photos | BuildInfo | Template | Export          // ordinal order matters (step bar, back nav)
CardTemplate     = PlainExport | ClassicFrame | MinimalCaption | BottomSpecBar | CornerMark
                 | PosterMargin | DarkGlassStrip | SideSpecRail | TopNameplate | MuseumMat
                 | CompactTicket | CleanSignature
RenderStatus     = Idle | Rendering | Saved | Error
AppLanguageMode  = System | Korean | English
AppThemeMode     = System | Light | Dark
OutputFormat     = WEBP | PNG
FileNameRule     = KEYXIF_INDEX | HOUSING_INDEX | NICKNAME_INDEX | HOUSING_KEYCAP_INDEX
QualityPreset    = HighCompression | Recommended | Balanced | HighQuality | Maximum | Custom
NicknameStyle    = Plain | AtPrefix | Credit
PaletteAnalysisMode = FullImage | CenterCrop
TemplateBackgroundTone = Light | Dark | Mixed
DEFAULT_PALETTE_CENTER_CROP_RATIO = 0.75
```

Korean display names (Korean-only, used in settings UI and template picker):

| Enum | Member → Label |
|---|---|
| CardTemplate | ClassicFrame→"클래식 프레임", MinimalCaption→"미니멀 캡션", BottomSpecBar→"하단 스펙 바", CornerMark→"코너 마크", PosterMargin→"포스터 마진", DarkGlassStrip→"다크 글래스 스트립", SideSpecRail→"사이드 스펙 레일", TopNameplate→"상단 네임플레이트", MuseumMat→"뮤지엄 매트", CompactTicket→"컴팩트 티켓", CleanSignature→"클린 시그니처", PlainExport→"Plain Export" |
| CardTemplate shortDescription | ClassicFrame→"사진 밖의 얇은 바에 전체 빌드 정보를 정돈합니다.", MinimalCaption→"밝은 하단 여백에 핵심 정보만 담습니다.", BottomSpecBar→"아주 얇은 하단 바에 주요 스펙을 배열합니다.", CornerMark→"사진 모서리에 로고와 하우징만 작게 표시합니다.", PosterMargin→"사진집 같은 프레임과 하단 여백을 만듭니다.", DarkGlassStrip→"하단 가장자리에 얇은 반투명 3열 정보를 표시합니다.", SideSpecRail→"오른쪽 외부 레일에 로고와 세부 스펙을 세로로 배치합니다.", TopNameplate→"사진 위쪽 여백에 큰 이름표와 로고를 올립니다.", MuseumMat→"넓은 매트 여백과 작품 라벨 같은 정보를 만듭니다.", CompactTicket→"하단 티켓형 라벨에 주요 정보를 작게 모읍니다.", CleanSignature→"하단 여백에 하우징, 키캡, 닉네임을 서명처럼 정리합니다.", PlainExport→"꾸밈 없이 원본 사진을 WEBP로 저장합니다." |
| AppStep | Photos→"사진", BuildInfo→"정보", Template→"템플릿", Export→"저장" |
| OutputFormat | WEBP→"WEBP", PNG→"PNG" |
| FileNameRule | KEYXIF_INDEX→"Keyxif_번호", HOUSING_INDEX→"Housing_번호", NICKNAME_INDEX→"Nickname_번호", HOUSING_KEYCAP_INDEX→"Housing_Keycap_번호" |
| QualityPreset | HighCompression→"고압축", Recommended→"권장", Balanced→"균형", HighQuality→"고화질", Maximum→"최고화질", Custom→"사용자 지정" |
| NicknameStyle | Plain→"그대로", AtPrefix→"@닉네임", Credit→"Credit" |
| PaletteAnalysisMode | FullImage→"전체 이미지", CenterCrop→"중앙 영역" |
| AppLanguageMode | System→"시스템", Korean→"한국어", English→"English" |
| AppThemeMode | System→"시스템", Light→"라이트", Dark→"다크" |

### 1.2 QualityPreset.applyTo(settings) — preset side effects

Selecting a quality preset mutates settings as:

| Preset | webpQuality | keepOriginalResolution | maxLongSidePx |
|---|---|---|---|
| HighCompression | 80 | false | 1920 |
| Recommended | 88 | false | 2048 |
| Balanced | 92 | true | null |
| HighQuality | 96 | true | null |
| Maximum | 100 | true | null |
| Custom | (unchanged) | (unchanged) | (unchanged) |

All also set `qualityPreset = <self>`.

### 1.3 Data classes

```ts
KeyboardBuildInfo {
  housing: string = ""
  switchName: string = ""
  plate: string = ""
  mount: string = ""
  keycap: string = ""
  nickname: string = ""
  logoId: string | null = null
  customLogoUri: string | null = null   // Android Uri → web: IDB blob key or dataURL
  logoDisabled: boolean = false
}

CropState { scale: number = 1, offsetX: number = 0, offsetY: number = 0 }
// NOTE: CropState exists on PhotoItem and is persisted in the draft, but the ViewModel
// exposes NO public crop mutation in 1.0.3. Keep the field + draft round-trip; a web
// crop editor (if any) mutates it via a photo-update action equivalent to the private
// updatePhoto(photoId, transform).

PhotoAnalysisResult {
  paletteColors: number[] = []          // ARGB ints; 0 entries filtered out on decode
  analyzedAt: number = 0                // epoch ms; 0 = never analyzed
  isAnalyzing: boolean = false          // always serialized as false in the draft
  errorMessage: string | null = null
  analysisMode: PaletteAnalysisMode | null = null
  analysisCenterCropRatio: number = 0.75
}

PhotoItem {
  id: string                            // UUID
  uri: string                           // Android: file:// copy in app storage → web: IDB blob key / object URL
  displayName: string                   // fallback "사진"
  cropState: CropState = default
  buildInfo: KeyboardBuildInfo = default
  analysisResult: PhotoAnalysisResult = default
  renderStatus: RenderStatus = Idle
  errorMessage: string | null = null
}

DraftSession {
  photoItems: PhotoItem[] = []
  selectedTemplate: CardTemplate = ClassicFrame
  currentStep: AppStep = Photos
  selectedPhotoId: string | null = null
  settings: AppSettings = default
  lastUpdatedAt: number = 0
}

BuildPreset { id: string, presetName: string, buildInfo: KeyboardBuildInfo, createdAt: number, updatedAt: number }

ExportedImage {
  id: string
  uri: string                           // Android MediaStore content:// → web: see §5.5
  fileName: string
  createdAt: number
  width: number
  height: number
  fileSizeBytes: number
  templateName: string                  // CardTemplate enum name
  housing?: string | null
  switchName?: string | null
  keycap?: string | null
  nickname?: string | null
  paletteColors: number[] = []
}

ExportProgress {
  isSaving: boolean = false
  current: number = 0
  total: number = 0
  successCount: number = 0
  failureCount: number = 0
  message: string | null = null
}

AppSettings {                            // defaults exactly as below
  webpQuality: 92                        // clamp 70..100
  outputFormat: OutputFormat.WEBP
  keepOriginalResolution: true
  maxLongSidePx: number | null = null    // null = original; persisted as -1 when null
  fileNameRule: FileNameRule.KEYXIF_INDEX
  saveDirectoryName: "Keyxif"            // blank → "Keyxif"
  openGalleryAfterSave: false
  showSaveToast: true
  skipFailedOnBatchSave: true
  rememberLastTemplate: true
  rememberLastNickname: true
  recentInputLimit: 20                   // normalized to 10 | 20 | 50 (≤10→10, ≤20→20, else 50)
  copyPreviousBuildInfoOnAdd: false
  defaultTemplate: CardTemplate.ClassicFrame
  useCurrentPhotoForTemplatePreview: true
  protectCenterAreaForOverlay: true
  textScale: 1.0                         // clamp 0.85..1.35
  qualityPreset: QualityPreset.Recommended
  autoRestoreDraftSession: true
  askBeforeRestoreDraft: true
  nicknameStyle: NicknameStyle.Plain
  nicknameEmphasis: 1.1                  // clamp 0.9..1.35
  showSwitchPresets: true
  enableExportPreviewZoom: true
  showBuildInfoInPlainExport: false
  updateJsonUrl: ""                      // WEB-OMIT (trimmed on normalize)
  showPaletteColors: true
  paletteColorCount: 4                   // clamp 3..5
  paletteAnalysisMode: PaletteAnalysisMode.CenterCrop
  paletteCenterCropRatio: 0.75           // clamp 0.35..1.0
  autoSelectLogoContrastVariant: true
  languageMode: AppLanguageMode.System
  themeMode: AppThemeMode.System
}
```

**Normalization** (apply on every settings decode AND on every write): clamp webpQuality 70..100; recentInputLimit → bucket {10,20,50}; maxLongSidePx → null unless > 0; saveDirectoryName blank → "Keyxif"; textScale 0.85..1.35; nicknameEmphasis 0.9..1.35; updateJsonUrl trim; paletteColorCount 3..5; paletteCenterCropRatio 0.35..1.0. Unknown enum strings decode to the field default.

### 1.4 "Meaningful build text" filter (used by recents, preset auto-name, display rows)

`meaningfulBuildTextOrNull(s)`: trim; return null if blank; lowercase + collapse whitespace to single spaces; return null if normalized value ∈ blocked set, else return the *trimmed original*:

```
"untitled keyboard", "untitled", "keyboard build", "unknown", "null",
"none", "n/a", "na", "미입력", "없음", "정보 없음", "빌드 정보 미입력"
```

`toDisplayRows(info, includeNickname=false)` → ordered rows with English labels for present, meaningful fields: Housing, Switch, Plate, Mount, Keycap, (Nickname).
`displayTitleOrNull(info)` = first meaningful of [nickname, housing].

---

## 2. UI state shape (KeyxifUiState) — single reactive store

```ts
KeyxifUiState {
  currentStep: AppStep = Photos
  photos: PhotoItem[] = []
  selectedPhotoId: string | null = null
  selectedExportPhotoIds: Set<string> = {}     // checkboxes on Export step
  expandedExportPhotoId: string | null = null  // fullscreen zoom preview on Export step
  selectedTemplate: CardTemplate = ClassicFrame
  settings: AppSettings = default
  isSettingsOpen: boolean = false
  settingsPageName: string | null = null       // sub-page within Settings; null = root list
  isGalleryOpen: boolean = false               // "완성 이미지" (finished images) screen
  exportedImages: ExportedImage[] = []         // reactive from repository, sorted createdAt desc
  buildPresets: BuildPreset[] = []             // sorted updatedAt desc
  presetQuery: string = ""                     // shared query text for preset search fields
  recentHousing: string[] = []
  recentSwitches: string[] = []
  recentKeycaps: string[] = []
  recentNicknames: string[] = []
  shareMessage: string | null = null           // banner for OS-share imports (web: Web Share Target, else omit)
  uiMessage: string | null = null              // one-shot snackbar text
  exportProgress: ExportProgress = default
  showDraftRestorePrompt: boolean = false
  draftLastUpdatedAt: number | null = null
  // ---- WEB-OMIT ----
  updateCheckState / updateDownloadState / showUpdateDialog / showUnknownSourcesDialog
}

// Computed:
selectedPhoto = photos.find(p => p.id === selectedPhotoId) ?? photos[0] ?? null
```

Non-state internals to replicate: `autoSaveReady: boolean` (gates draft autosave until initial load resolves), `pendingDraftSession` (held while restore prompt shows), `appliedInitialSettings` (first settings emission applies `defaultTemplate` to `selectedTemplate`), a palette-analysis job handle, and a preview-render concurrency limiter (semaphore of 2).

Static preset data exposed alongside state (from bundled `PresetRepository`, not persisted): `plates: string[]`, `mounts: string[]`, `logos: LogoPreset[]`, plus search functions (§3, group "Preset option queries").

---

## 3. Actions — complete list (64 public members; 57 web-relevant, 7 WEB-OMIT)

### Overlays (5)
1. **openSettings()** — `isSettingsOpen=true, isGalleryOpen=false`.
2. **closeSettings()** — if `settingsPageName != null` → clear it (go to settings root); else `isSettingsOpen=false`. (Two-level back.)
3. **selectSettingsPage(name: string|null)** — sets `settingsPageName` only if settings is open.
4. **openGallery()** — `isGalleryOpen=true, isSettingsOpen=false, settingsPageName=null`.
5. **closeGallery()** — `isGalleryOpen=false`.

### Settings (1)
6. **updateSettings(transform: (AppSettings)=>AppSettings)** — async: repository.update(transform) → normalized → persisted → flows back into state via subscription. Side effect on every settings emission: if this is the first emission ever, OR settings screen is open and `defaultTemplate` changed, set `selectedTemplate = settings.defaultTemplate`. Every emission also re-triggers palette analysis scheduling.

### Messages (3)
7. **consumeMessage()** — `uiMessage=null`. UI contract: an effect watches `uiMessage`; when non-null it immediately calls consumeMessage() then shows the snackbar with that text.
8. **clearShareMessage()** — `shareMessage=null`.
9. **showExportMessage(message)** — `exportProgress.message = message` (used by permission-denied path on Android; web keeps it for ad-hoc export banner text).

### Navigation (3)
10. **navigateToStep(step)** — guard: `navigationBlockMessage` = if `step != Photos && photos.length === 0` → set `uiMessage = "먼저 사진을 추가해 주세요."` and DO NOT navigate. Otherwise set `currentStep=step`, close settings+gallery (`isSettingsOpen=false, settingsPageName=null, isGalleryOpen=false`). (A warning-message hook exists but always returns null in 1.0.3.)
11. **navigateToPreviousStep()** — navigateToStep(previousStep(currentStep)); previousStep: Photos→null, BuildInfo→Photos, Template→BuildInfo, Export→Template.
12. **handleSystemBack(): boolean** — priority: settings open → closeSettings(), return true; gallery open → closeGallery(), return true; currentStep != Photos → navigateToPreviousStep(), return true; else return false (Android exits app; web: allow default browser back / do nothing).

### Photos (7)
13. **addPhotos(uris[])** — import flow (`fromShare=false`): show `uiMessage="사진을 가져오는 중입니다."`; compute `copiedInfo`: if `settings.copyPreviousBuildInfoOnAdd` → buildInfo of `photos[last]` else `selectedPhoto`'s; else if `settings.rememberLastNickname` → `KeyboardBuildInfo{ nickname: recentNicknames[0] ?? "" }`; else null. Dedupe input uris by string; for each, copy the bytes into app-owned storage (web: persist blob to IndexedDB `sources`, key = generated file-ish name) and create `PhotoItem{ id: uuid, uri: localRef, displayName, buildInfo: copiedInfo ?? {} }` (failures silently skipped). Append to `photos`; `selectedPhotoId = selectedPhotoId ?? firstImported.id`; final `uiMessage` = imported>0 ? `"사진 ${n}장을 추가했습니다."` : `"가져올 수 있는 새 사진이 없습니다."`. Then schedule palette analysis. displayName resolution: content-resolver display name → last path segment → "사진" (web: `File.name`).
14. **addSharedImages(uris[])** — same import with `fromShare=true`: forces `currentStep=Photos`; uses `shareMessage` instead of `uiMessage`: in-progress `"공유한 이미지를 가져오는 중입니다."`; success `"공유한 이미지 ${n}장을 사진 목록에 추가했습니다."`; none `"가져올 수 있는 새 이미지가 없습니다."`; called with empty list → `currentStep=Photos, shareMessage="공유한 이미지가 없습니다."`. (Web: only if implementing Web Share Target; otherwise WEB-OMIT.)
15. **removePhoto(id)** — delete the app-owned source file (only if it lives under the app source store; web: delete IDB blob), remove from `photos`, remove from `selectedExportPhotoIds`, clear `expandedExportPhotoId` if it was this photo, and fix selection: keep `selectedPhotoId` if still present else first remaining photo's id else null.
16. **clearPhotos()** — delete all local source files; `photos=[], selectedPhotoId=null, selectedExportPhotoIds={}, expandedExportPhotoId=null, currentStep=Photos, exportProgress=reset`, `uiMessage="사진 목록을 비웠습니다."`.
17. **movePhoto(id, direction: int)** — direction is a delta (−1 up / +1 down); newIndex clamped to [0, last]; no-op if unchanged; splice-reinsert.
18. **selectPhoto(id)** — set `selectedPhotoId` only if the photo exists.
19. **startNewSession()** — clear draft repository, then `currentStep=Photos, photos=[], selectedPhotoId=null, exportProgress=reset, showDraftRestorePrompt=false, draftLastUpdatedAt=null`. (Note: does NOT delete source files.)

### Export-step selection (3)
20. **setExportPhotoSelected(id, selected: boolean)** — add/remove from `selectedExportPhotoIds`; no-op if photo missing.
21. **clearExportSelection()** — `selectedExportPhotoIds = {}`.
22. **setExpandedExportPhoto(photoId | null)** — set fullscreen-preview target; non-null id must exist. UI contract: while (`!isSettingsOpen && !isGalleryOpen && currentStep==Export && expandedExportPhotoId!=null && settings.enableExportPreviewZoom`) the app content behind the preview gets a 20dp blur (animated from 0), i.e. web: CSS `filter: blur(20px)` with transition on the scaffold.

### Build info (per selected photo) (7)
23. **updateBuildInfo(buildInfo)** — replace `selectedPhoto.buildInfo` wholesale (no-op when no selected photo).
24. **clearSelectedBuildInfo()** — reset selected photo's buildInfo to defaults.
25. **applyBuildInfoToAll()** — copy selected photo's buildInfo to every photo; then record it into recents (see §3 recents rules).
26. **selectHousingPreset(preset: HousingPreset)** — on selected photo: `housing=preset.name`, `logoId = logoIdForHousing(preset) ?? existing logoId`, `customLogoUri=null`, `logoDisabled=false`.
27. **selectSwitchPreset(preset)** — `switchName=preset.name`.
28. **selectKeycapPreset(preset)** — `keycap=preset.name`.
29. **completeBuildInfo()** — for each DISTINCT buildInfo across all photos: record into recents (limit = settings.recentInputLimit); refresh recents/presets in state; then navigateToStep(Template). This is the "next" action from the BuildInfo step.

### Template (1)
30. **selectTemplate(template)** — `selectedTemplate=template`; if `settings.rememberLastTemplate` → also `updateSettings{ defaultTemplate: template }`.

### Build presets CRUD (4)
31. **updatePresetQuery(query)** — `presetQuery=query`.
32. **saveBuildPreset(name)** — using selected photo's buildInfo: repository.save(name, info) — trims name, blank → auto-name = join(" + ") of [meaningful housing, meaningful keycap ?? meaningful switchName], blank → "새 빌드"; new preset gets `id=uuid, createdAt=updatedAt=now`, PREPENDED. Also records info into recents. Then refresh state lists.
33. **applyBuildPreset(preset)** — updateBuildInfo(preset.buildInfo) + record into recents.
34. **deleteBuildPreset(id)** — repository.delete(id) + refresh.

### Recent inputs (4 removals + recording rules)
35. **removeRecentHousing(value)** / 36. **removeRecentSwitch(value)** / 37. **removeRecentKeycap(value)** / 38. **removeRecentNickname(value)** — remove (case-insensitive match on meaningful text) then refresh state lists.
- **Recording** (`addBuildInfo(info, limit)`): adds each of housing/switchName/keycap/nickname independently. Per field: skip non-meaningful values (§1.4); prepend trimmed value; dedupe case-insensitively (first occurrence wins); truncate to `limit.clamp(10,50)`. Triggered by: saveBuildPreset, applyBuildPreset, applyBuildInfoToAll, completeBuildInfo (each distinct info).

### Preset option queries (3, pure functions over static data + recents)
39. **housingOptions(query)** = presetSearch(housing, query, recentHousing).
40. **switchOptions(query)** = presetSearch(switch, query, recentSwitches, includePresets = settings.showSwitchPresets).
41. **keycapOptions(query)** = presetSearch(keycap, query, recentKeycaps).
(Implementation lives in bundled `PresetRepository` — port its dataset/search separately; contract here: returns ranked `PresetChoice<T>` lists mixing recents + presets.)

### Rendering (2, async)
42. **renderPreviewBitmap(photoId, maxLongSide = PREVIEW_LONG_SIDE_LIMIT)** — renders photo+template+settings composite; returns bitmap or null if photo missing. Concurrency capped at 2 simultaneous renders (semaphore) to avoid memory spikes — web: cap concurrent canvas renders similarly.
43. **renderSourcePreviewBitmap(photoId, maxLongSide)** — decodes the raw source (EXIF-oriented) without template.

### Export (4 + pipeline)
44. **savePhoto(photoId)** → enqueueExport([photoId]).
45. **savePhotos(photoIds[])** → enqueueExport(photoIds).
46. **saveAll()** → enqueueExport(all photo ids in order).
47. Pipeline **enqueueExport(ids)**:
   - ids empty → `uiMessage="저장할 사진이 없습니다."`, stop.
   - resolve PhotoItems; none found → `uiMessage="저장할 사진을 찾을 수 없습니다."`, stop.
   - set `exportProgress = { isSaving:true, total:n, message:"저장 작업을 준비하는 중입니다." }`; targeted photos → `renderStatus=Rendering, errorMessage=null`.
   - snapshot payload = { photos (with source bytes made durable), template, settings }. Payload prep failure → `exportProgress={ isSaving:false, total:n, failureCount:n, message: error.message ?? "저장 작업을 준비할 수 없습니다." }`, stop.
   - enqueue unique background job (REPLACE policy — a new export replaces a pending one); then `exportProgress.message = "${directoryLabel}에 백그라운드 저장을 시작했습니다."` where directoryLabel = `Pictures/${sanitize(settings.saveDirectoryName) || "Keyxif"}` (web: use the label text as-is or adapt to "다운로드").
   - **Progress observation** (Android polls WorkManager every 500ms active / 1500ms idle; web: direct callbacks from a Web Worker): active → `exportProgress = { isSaving:true, current, total, successCount, failureCount, message: msg ?? "Keyxif 저장 중" }` and the currently-processing photo → `renderStatus=Rendering`.
   - **Completion** (deduped by work id): read { total, current, successCount, failureCount, message, photoIds, failedIds, savedUri }. Default message: success → "저장이 완료되었습니다.", failure → "저장 작업이 실패했습니다.". Set `exportProgress = { isSaving:false, ..., message: settings.showSaveToast ? message : null }`. Photo statuses: id ∈ failedIds → `renderStatus=Error, errorMessage="백그라운드 저장 실패"`; else if photoIds empty or id ∈ photoIds → success ? (`Saved`, errorMessage=null) : (`Error`, errorMessage=message); others untouched. If `settings.openGalleryAfterSave` and savedUri present → open the saved image (web: open gallery view / object URL).
   - Worker behavior contract (per settings): encode `outputFormat` at `webpQuality`; resolution capped by `maxLongSidePx` unless `keepOriginalResolution`; file names per `fileNameRule` + index; `skipFailedOnBatchSave` continues past failures; each success appends an `ExportedImage` record (repository §5.5).
   - Android pre-export permission dance (WRITE_EXTERNAL_STORAGE on ≤ Android 9 with message "Android 9 이하에서는 저장 권한이 필요합니다.", POST_NOTIFICATIONS on 13+) — **WEB-OMIT** (browser downloads need no permission).

### Exported-image records (8)
48. **shareExportedImage(image)** — OS share sheet of the file (web: Web Share API with the stored blob; fallback message "공유할 앱을 열 수 없습니다.").
49. **openExportedImage(image)** — OS viewer (web: open object URL in new tab; failure "이미지를 열 수 없습니다.").
50. **removeExportedImageRecord(id)** — repository.remove(id) (record only, file untouched).
51. **deleteExportedImageFile(image)** — deleteExportedImageFiles([image]).
52. **deleteExportedImageFiles(images[])** — for each: try to delete the underlying file, always remove the record. Message: all deleted → `"이미지 ${n}개를 삭제했습니다."`; else `"이미지 ${n}개를 목록에서 제거했습니다. 일부 파일은 삭제 권한이 없었습니다."` (web: deleting IDB blobs always succeeds → first message).
53. **deleteAllExportedImages()** — deleteExportedImageFiles(all current records).
54. **pruneMissingExportedImages()** — remove records whose file can no longer be opened; `uiMessage="접근 불가 항목 ${removed}개를 정리했습니다."` (web: check IDB blob existence).
55. **clearExportedImageRecords()** — repository.clear(); `uiMessage="완성 이미지 목록 기록을 초기화했습니다."`.

### Draft session (2 public + machinery in §4)
56. **restoreDraftSession()** / 57. **discardDraftSession()** — see §4.

### Misc (1)
58. **openSupportEmail()** — mailto: `typenews902@gmail.com`, subject "Keyxif 문의"; if mail app can't open, copy address to clipboard + `uiMessage="메일 앱을 열 수 없어 이메일 주소를 복사했습니다."` (web: `mailto:` link + clipboard fallback).

### WEB-OMIT (7) — everything UpdateRepository-related
59. checkForUpdate() 60. dismissUpdateDialog() 61. openUpdateApk() 62. openReleaseNotes() 63. installDownloadedUpdate() 64. dismissUnknownSourcesDialog() + openUnknownSourcesSettings().
Also omit: `UpdateCheckState`, `UpdateDownloadState`, `UpdateInfo`, `showUpdateDialog`, `showUnknownSourcesDialog`, auto-check-on-launch, the UpdateAvailableDialog and UnknownSourcesDialog composables, `settings.updateJsonUrl` UI (keep the settings field for draft JSON compatibility, hide it). The web app ships as a web page; no APK update flow exists.

---

## 4. Step navigation, scaffold & dialogs

### 4.1 Steps and reachability
- Order: `Photos(1) → BuildInfo(2) → Template(3) → Export(4)`.
- Step bar: always visible on main flow (hidden when settings/gallery open). Every chip is clickable → `navigateToStep(step)`.
- **Only validation rule**: any step other than Photos requires `photos.length > 0`, else blocked with snackbar `"먼저 사진을 추가해 주세요."`. There is no forward-only gating — with photos present the user can jump to any step.
- Step chip visuals: selected chip = surface background + elevation, number badge = primary bg / onPrimary number; completed (ordinal < current) = secondaryContainer badge with check icon (onSecondaryContainer); future = surfaceContainerHighest badge, onSurfaceVariant number. Label: labelMedium; bold when selected. Container: surfaceContainer, large radius (22px), inner padding 6, gap 4.

### 4.2 Bottom action bar (hidden when settings or gallery open)
Surface color surfaceContainerLow, top divider outlineVariant, row padding 20h/12v, gap 10, button height 50.
- "이전 / Previous" outlined button shown iff previousStep != null; disabled while `exportProgress.isSaving`.
- Non-Export steps: one primary button (weight 2 when Previous exists, else full width), disabled while saving, label per step: Photos→"빌드 정보 입력 / Enter build info", BuildInfo→"템플릿 선택 / Choose template", Template→"미리보기 · 저장 / Preview & save". Click: Photos→navigateToStep(BuildInfo); BuildInfo→completeBuildInfo(); Template→navigateToStep(Export).
- Export step: two buttons — "선택 저장 N / Save selected N" (enabled iff N>0 && !saving; label without N when 0) → save selected ids; "전체 저장 / Save all" (enabled iff photos non-empty && !saving) → saveAll.

### 4.3 Top bar
- Main mode: title "Keyxif" (headlineSmall) + 7dp secondary-colored dot; right side: photo-count pill `"사진 N장" / "N photos"` (surfaceContainerHigh circle pill, labelMedium onSurfaceVariant, shown iff photos non-empty), gallery icon button ("완성 이미지"/"Finished Images"), settings icon button ("설정"/"Settings"). Below: step indicator.
- Settings/gallery mode: back arrow + bold headlineSmall title "설정"/"Settings" or "완성 이미지"/"Finished Images"; step indicator hidden.
- Padding: horizontal 18, vertical 14, vertical gap 12; background = colorScheme.background.

### 4.4 Snackbar
Single-shot: watch `uiMessage`; when set, consume immediately (set null) and display the text as a snackbar/toast.

### 4.5 Back handling (web)
Intercept back (History API) while settings open, gallery open, or step != Photos; delegate to `handleSystemBack()` order: settings → gallery → previous step → default.

### 4.6 Draft restore dialog (exact copy)
- Modal, NOT dismissable by outside click/ESC (onDismissRequest is a no-op).
- Title: `이전 작업을 복구할까요?`
- Body: `임시 저장된 Keyxif 작업이 있습니다.\n마지막 저장: {date}` where {date} = `lastUpdatedAt` formatted `yyyy.MM.dd HH:mm` (device locale), or `저장 시각 알 수 없음` when null/≤0.
- Confirm (filled button): `복구` → restoreDraftSession(). Dismiss (text button): `새로 시작` → discardDraftSession().
(Dialog copy is Korean-only in 1.0.3 — not localized.)

---

## 5. Draft session lifecycle

### 5.1 Save triggers (autosave)
Subscribe to the whole UI state, skip the initial emission, **debounce 700ms**; on each settled state:
- Guard: skip unless `autoSaveReady && !showDraftRestorePrompt && settings.autoRestoreDraftSession`.
- If `photos.length === 0` → clear the stored draft.
- Else save `DraftSession{ photoItems: photos, selectedTemplate, currentStep, selectedPhotoId, settings, lastUpdatedAt: now }` (repository stamps `lastUpdatedAt = now` again on write).
Explicit clears: discardDraftSession(), startNewSession().

### 5.2 Load on start
1. Read stored draft + current persisted settings.
2. If draft null OR draft.photoItems empty OR `!settings.autoRestoreDraftSession` → `autoSaveReady=true`, done (fresh session).
3. Else hold draft as pending. If `settings.askBeforeRestoreDraft` → `showDraftRestorePrompt=true, draftLastUpdatedAt=draft.lastUpdatedAt` (dialog §4.6). Else restore immediately.

### 5.3 restoreDraftSession()
- `autoSaveReady=false`; persist `draft.settings` as the live settings (repository.update to draft.settings).
- State: `photos = draft.photoItems.map(validatedRestoredPhoto)`, `selectedTemplate=draft.selectedTemplate`, `currentStep = normalizeStep(draft.currentStep)` (legacy stored value `"Adjust"` maps to Template; unknown → Photos), `selectedPhotoId=draft.selectedPhotoId`, `settings=draft.settings`, `isSettingsOpen=false`, `showDraftRestorePrompt=false`, `draftLastUpdatedAt=draft.lastUpdatedAt`, `uiMessage="이전 작업을 복구했습니다."`.
- `validatedRestoredPhoto`: try opening the photo's source (web: IDB blob lookup). Openable → `analysisResult.isAnalyzing=false, renderStatus=Idle, errorMessage=null`. Not openable → `renderStatus=Error, errorMessage="사진 접근 권한이 만료되었습니다."` (web wording may keep as-is: source blob missing).
- Clear pending; `autoSaveReady=true`; schedule palette analysis with the restored settings.

### 5.4 discardDraftSession()
Clear repo; `photos=[], selectedPhotoId=null, currentStep=Photos, showDraftRestorePrompt=false, draftLastUpdatedAt=null, uiMessage="이전 작업을 폐기했습니다."`; `autoSaveReady=true`.

### 5.5 Draft JSON wire format (keep identical for the web `keyxif.draft` value)
```json
{
  "selectedTemplate": "ClassicFrame",
  "currentStep": "Photos",
  "selectedPhotoId": "uuid-or-null",
  "lastUpdatedAt": 1720000000000,
  "settings": { /* all 36 AppSettings fields, enum names as strings */ },
  "photos": [
    {
      "id": "uuid", "uri": "…", "displayName": "…",
      "cropState": { "scale": 1.0, "offsetX": 0.0, "offsetY": 0.0 },
      "buildInfo": { "housing": "", "switchName": "", "plate": "", "mount": "", "keycap": "",
                     "nickname": "", "logoId": null, "customLogoUri": null, "logoDisabled": false },
      "analysisResult": { "paletteColors": [/* ARGB ints */], "analyzedAt": 0, "isAnalyzing": false,
                          "errorMessage": null, "analysisMode": "CenterCrop", "analysisCenterCropRatio": 0.75 }
    }
  ]
}
```
Decode rules: any parse failure → treat as no draft; missing photo displayName → "사진"; renderStatus always resets to Idle; `isAnalyzing` always false; paletteColors entries equal to 0 are dropped; unknown enum names fall back to defaults; `maxLongSidePx` nullable (null or ≤0 → null).

---

## 6. Palette analysis (automatic, sequential)

Trigger points: photo import, settings emission (any change), draft restore. `schedulePaletteAnalysis`:
- Skip entirely if `!settings.showPaletteColors`. Skip if a job is already running (single-flight).
- Loop: re-read live settings each iteration; break if showPaletteColors turned off. Find FIRST photo where `needsPaletteAnalysis`:
  `!isAnalyzing && (analyzedAt <= 0 || analysisMode !== settings.paletteAnalysisMode || (mode === CenterCrop && |analysisCenterCropRatio − settings.paletteCenterCropRatio| > 0.001))`
  — none → break.
- Mark that photo `analysisResult.isAnalyzing=true, errorMessage=null`. Run analyzer(uri, mode, **maxColors=5** (hardcoded; `settings.paletteColorCount` only limits display count), centerCropRatio). On result: `analysisResult = { paletteColors: colors|[], analyzedAt: now, isAnalyzing: false, errorMessage: error?.message ?? (colors.empty ? "대표 색상을 찾지 못했습니다." : null), analysisMode: mode, analysisCenterCropRatio: ratio }`. Sleep 40ms; continue loop.
- Web implementation: canvas downsample + k-means/median-cut; CenterCrop = analyze the centered square/rect of `ratio` × each dimension.

---

## 7. Persistence inventory → web equivalents

Android has 5 stores + 2 file dirs. Proposed web mapping (localStorage for small JSON, IndexedDB for binaries):

| # | Android store | Keys & shape | Web equivalent |
|---|---|---|---|
| 1 | DataStore(Preferences) `keyxif_settings` | 36 scalar keys (snake_case): `webp_quality`:int, `output_format`:str(enum), `keep_original_resolution`:bool, `max_long_side_px`:int(−1 = null sentinel), `file_name_rule`:str, `save_directory_name`:str, `open_gallery_after_save`:bool, `show_save_toast`:bool, `skip_failed_on_batch_save`:bool, `remember_last_template`:bool, `remember_last_nickname`:bool, `recent_input_limit`:int, `copy_previous_build_info_on_add`:bool, `default_template`:str, `use_current_photo_for_template_preview`:bool, `protect_center_area_for_overlay`:bool, `text_scale`:float, `quality_preset`:str, `auto_restore_draft_session`:bool, `ask_before_restore_draft`:bool, `nickname_style`:str, `nickname_emphasis`:float, `show_switch_presets`:bool, `enable_export_preview_zoom`:bool, `show_build_info_in_plain_export`:bool, `update_json_url`:str, `show_palette_colors`:bool, `palette_color_count`:int, `palette_analysis_mode`:str, `palette_center_crop_ratio`:float, `auto_select_logo_contrast_variant`:bool, `language_mode`:str, `theme_mode`:str | `localStorage["keyxif.settings"]` = single JSON object of the AppSettings shape (camelCase, enum names as strings). Apply §1.3 normalization on read AND write. Reactive: wrap in a store; also listen to `storage` events for multi-tab sync (optional). |
| 2 | DataStore `keyxif_draft_session` | one key `draft_json`: JSON string (§5.5) | `localStorage["keyxif.draft"]` = same JSON, EXCEPT `photos[].uri` / `buildInfo.customLogoUri` are IDB source keys (see #6). Debounced writes (700ms) can exceed localStorage quota with many photos of metadata only — metadata is small, blobs live in IDB, so localStorage is fine. |
| 3 | SharedPreferences `keyxif_recents` | keys `housing`,`switch`,`keycap`,`nickname` — each a single string of values joined by U+001F | `localStorage["keyxif.recents"]` = `{ housing: string[], switch: string[], keycap: string[], nickname: string[] }` (drop the separator hack; arrays JSON-encoded). Same semantics: prepend, case-insensitive dedupe, meaningful-text filter, cap 10/20/50. |
| 4 | SharedPreferences `keyxif_build_presets` | key `presets`: JSON array of BuildPreset (buildInfo nested; `customLogoUri` stringified) | `localStorage["keyxif.buildPresets"]` = same JSON array. Sort by `updatedAt` desc on read. |
| 5 | DataStore `keyxif_exported_images` | key `exported_images_json`: JSON array of ExportedImage (sorted createdAt desc; add() dedupes by id and prepends) | **IndexedDB** `keyxif` db, store `exported` keyed by `id`: record = ExportedImage metadata **+ `thumbDataUrl: string` (small JPEG/WebP dataURL for grid) + `blob: Blob` (the full exported file)**. Reason: web cannot persist a `content://`/download URI — the browser download leaves no re-openable handle, so the gallery must own its own copy. `uri` field becomes the IDB key reference (or omit and derive object URLs at runtime). `pruneMissing` = drop records whose blob is absent. Mirror the metadata list in memory as a reactive array. |
| 6 | Files: `filesDir/keyxif_sources/` (imported photo copies, name = `${now}_${uuid}_${sanitizedName}.${ext}`, fallback ext `img`) | raw image bytes | **IndexedDB** store `sources` keyed by generated id: `{ id, name, type, blob }`. `PhotoItem.uri` on web = this key; resolve to `URL.createObjectURL(blob)` per session. Delete on removePhoto/clearPhotos (same "only delete what we own" rule is automatic). |
| 7 | Files: `cacheDir/keyxif_export/{workId}/` (photo_N.bin, logo_N.bin, request.json payload for WorkManager) | export payload snapshot | Not needed on web — pass the payload (photo blobs + template + settings) directly to a Web Worker / OffscreenCanvas via structured clone. |
| 8 | UpdateRepository prefs (last checked at, etc.) + downloaded APK file | — | **WEB-OMIT** entirely. |

**Key web caveat (gallery)**: on Android, exported files land in `Pictures/<saveDirectoryName>` via MediaStore and records store the content URI. On web, export = trigger a download (`<a download>` of the blob) AND persist `{metadata, thumbnail dataURL, full blob}` into IDB store #5 so the "완성 이미지" gallery, share, open, delete, and prune features keep working. "Delete file" can only delete the IDB copy — the user's downloaded file is untouchable (message copy still applies).

---

## 8. Theme — exact palettes

Base identity: "잉크 + 웜 페이퍼 + 코퍼" — Ink `#1C1B17`, Paper `#F7F5F0`, Copper `#A85D2B`.

### 8.1 Light scheme (`KeyxifLightColors`)

| Role | Hex |
|---|---|
| primary | #1C1B17 (Ink) |
| onPrimary | #FBFAF7 |
| primaryContainer | #E9E5DC |
| onPrimaryContainer | #1C1B17 |
| secondary | #A85D2B (Copper) |
| onSecondary | #FFFFFF |
| secondaryContainer | #F6E5D4 |
| onSecondaryContainer | #4A2A0E |
| tertiary | #50604F |
| onTertiary | #FFFFFF |
| tertiaryContainer | #E0E7DC |
| onTertiaryContainer | #20301F |
| background | #F7F5F0 (Paper) |
| onBackground | #1C1B17 |
| surface | #FFFEFB |
| onSurface | #1C1B17 |
| surfaceVariant | #ECE9E1 |
| onSurfaceVariant | #5E5A50 |
| surfaceContainerLowest | #FFFFFF |
| surfaceContainerLow | #FDFCF8 |
| surfaceContainer | #F2F0EA |
| surfaceContainerHigh | #EDEAE3 |
| surfaceContainerHighest | #E7E4DC |
| surfaceTint | #8A8376 |
| outline | #CFCBC0 |
| outlineVariant | #E4E1D8 |
| error | #A83A32 |
| onError | #FFFFFF |
| errorContainer | #F7E0DD |
| onErrorContainer | #541F1A |

### 8.2 Dark scheme (`KeyxifDarkColors`)

| Role | Hex |
|---|---|
| primary | #F4EEE4 |
| onPrimary | #28241E |
| primaryContainer | #3C372F |
| onPrimaryContainer | #F4EEE4 |
| secondary | #E0A36B |
| onSecondary | #2B1607 |
| secondaryContainer | #5B371A |
| onSecondaryContainer | #FFDCC1 |
| tertiary | #B7C9AF |
| onTertiary | #22301F |
| tertiaryContainer | #394A35 |
| onTertiaryContainer | #E1EFD8 |
| background | #151411 |
| onBackground | #F4EEE4 |
| surface | #1D1B17 |
| onSurface | #F4EEE4 |
| surfaceVariant | #4F4A41 |
| onSurfaceVariant | #D2CABD |
| surfaceContainerLowest | #10100E |
| surfaceContainerLow | #191713 |
| surfaceContainer | #22201B |
| surfaceContainerHigh | #2C2923 |
| surfaceContainerHighest | #363229 |
| surfaceTint | #E0A36B |
| outline | #8B8377 |
| outlineVariant | #514C44 |
| error | #FFB4AB |
| onError | #690005 |
| errorContainer | #93000A |
| onErrorContainer | #FFDAD6 |

### 8.3 Shapes / typography / chrome
- Corner radii: extraSmall 8, small 12, medium 16, large 22, extraLarge 28 (dp → px).
- Typography deltas from Material3 defaults: headlineSmall Bold ls 0; titleLarge Bold ls 0; titleMedium SemiBold ls 0; titleSmall SemiBold; labelLarge SemiBold ls 0; bodyMedium lineHeight 21sp.
- Theme mode: `settings.themeMode` — System → `prefers-color-scheme`, Light/Dark forced. System chrome (Android status bar = background color, nav bar = surfaceContainerLow) → web: `<meta name="theme-color">` per scheme.

---

## 9. Language handling & string table

### 9.1 Resolution
`KeyxifLanguage = Korean | English`. From `settings.languageMode`: System → Korean iff device primary language is `ko` (case-insensitive) — web: `navigator.language.toLowerCase().startsWith("ko")`; Korean/English → forced. Helper: `text(ko, en)` picks by resolved language. **Strings are inline ko/en pairs at each call site — there is no central resource file.** Each screen composable carries its own pairs (screens under `ui/screens/*` have additional pairs not enumerated here; extract when porting each screen).

### 9.2 Localized pairs in the scaffold (KeyxifApp.kt) — complete

| Context | ko | en |
|---|---|---|
| Back a11y | 뒤로가기 | Back |
| Settings title/icon | 설정 | Settings |
| Gallery title/icon | 완성 이미지 | Finished Images |
| Photo-count pill | 사진 {N}장 | {N} photos |
| Step: Photos | 사진 | Photos |
| Step: BuildInfo | 정보 | Info |
| Step: Template | 템플릿 | Template |
| Step: Export | 저장 | Export |
| Bottom: previous | 이전 | Previous |
| Bottom next (Photos) | 빌드 정보 입력 | Enter build info |
| Bottom next (BuildInfo) | 템플릿 선택 | Choose template |
| Bottom next (Template) | 미리보기 · 저장 | Preview & save |
| Bottom next (Export, unused) | 저장 | Save |
| Save selected (N>0) | 선택 저장 {N} | Save selected {N} |
| Save selected (N=0) | 선택 저장 | Save selected |
| Save all | 전체 저장 | Save all |

### 9.3 Korean-only strings (ViewModel messages + dialogs — NOT localized in 1.0.3; port verbatim)

Snackbar/uiMessage: `먼저 사진을 추가해 주세요.` · `사진을 가져오는 중입니다.` · `사진 {N}장을 추가했습니다.` · `가져올 수 있는 새 사진이 없습니다.` · `사진 목록을 비웠습니다.` · `이전 작업을 복구했습니다.` · `이전 작업을 폐기했습니다.` · `저장할 사진이 없습니다.` · `저장할 사진을 찾을 수 없습니다.` · `저장 작업을 준비하는 중입니다.` · `저장 작업을 준비할 수 없습니다.` · `{Pictures/디렉터리}에 백그라운드 저장을 시작했습니다.` · `Keyxif 저장 중` (progress default) · `저장이 완료되었습니다.` · `저장 작업이 실패했습니다.` · `백그라운드 저장 실패` (per-photo error) · `이미지 {N}개를 삭제했습니다.` · `이미지 {N}개를 목록에서 제거했습니다. 일부 파일은 삭제 권한이 없었습니다.` · `접근 불가 항목 {N}개를 정리했습니다.` · `완성 이미지 목록 기록을 초기화했습니다.` · `공유할 앱을 열 수 없습니다.` · `이미지를 열 수 없습니다.` · `대표 색상을 찾지 못했습니다.` · `사진 접근 권한이 만료되었습니다.` · `메일 앱을 열 수 없어 이메일 주소를 복사했습니다.`

Share-import banners: `공유한 이미지가 없습니다.` · `공유한 이미지를 가져오는 중입니다.` · `공유한 이미지 {N}장을 사진 목록에 추가했습니다.` · `가져올 수 있는 새 이미지가 없습니다.`

Draft dialog: `이전 작업을 복구할까요?` / `임시 저장된 Keyxif 작업이 있습니다.\n마지막 저장: {yyyy.MM.dd HH:mm}` / `저장 시각 알 수 없음` / `복구` / `새로 시작`.

Share sheet title: `Keyxif 이미지 공유`. Clipboard label: `Keyxif 문의 이메일`. Support email: `typenews902@gmail.com`, subject `Keyxif 문의`.

WEB-OMIT strings (update/permission flows): `업데이트 APK 다운로드를 시작했습니다.`, `APK URL이 비어 있습니다.`, `릴리즈 노트를 열 수 없습니다.`, `설치할 업데이트 APK가 없습니다.`, `설치 권한 설정을 열 수 없습니다.`, `업데이트 APK 다운로드가 완료되었습니다.`, `업데이트 다운로드가 실패했습니다.`, `업데이트 JSON URL이 설정되지 않았습니다.`, `새 버전 {v} ({code})을 사용할 수 있습니다.`, `현재 최신 버전입니다. ({v})`, `현재 최신 버전입니다.`, `업데이트 확인 실패: {msg}`, `업데이트 확인에 실패했습니다.`, `다운로드된 APK 파일을 찾을 수 없습니다.`, `설치 화면을 열 수 없습니다: {msg}`, `설치 화면을 열 수 없어 브라우저 다운로드로 전환합니다.`, `Android 9 이하에서는 저장 권한이 필요합니다.`, update dialog copy (`새 버전이 있습니다`, `지금 업데이트`, `설치 계속`, `릴리즈 노트`, `나중에`, `현재 버전: …`, `최신 버전: …`, `APK 다운로드 중 · {p}%`, `이 버전은 업데이트 후 계속 사용할 수 있습니다.`), unknown-sources dialog copy (`설치 권한이 필요합니다`, body, `설정 열기`, `닫기`).

---

## 10. Port checklist deltas (Android → Web)

1. WorkManager background export → Web Worker + OffscreenCanvas; progress via postMessage (no polling); "unique work with REPLACE" = terminate the previous worker job before starting a new one; keep the same ExportProgress state transitions and messages.
2. Photo URIs → IDB blobs (`sources` store); object URLs are session-scoped — regenerate after reload; draft restore validation = blob existence check.
3. Exported gallery → IDB `exported` store with thumbnail dataURL + full blob + metadata (§7 row 5); download via anchor; share via `navigator.share({files})` when available.
4. Notifications/storage permissions, APK update pipeline, unknown-sources settings, intent-based open/share fallbacks → WEB-OMIT (7 actions, 2 dialogs, 2 state slices).
5. Preview render concurrency: cap at 2 concurrent canvas renders (matches semaphore).
6. Preserve exact debounce (700ms draft autosave), 40ms inter-photo palette delay, snackbar consume-then-show pattern, and the 20px blur behind the export zoom preview.
