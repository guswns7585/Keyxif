# Keyxif Web — 모듈 계약 (통합 규약)

모든 모듈은 브라우저 전역(window)에 붙는 plain JS (모듈 시스템 없음, 'use strict' IIFE).
로드 순서: assets.js → presets.js → renderers.js → palette.js → db.js → search.js → store.js → ui-*.js → main.js

## window.KeyxifPalette (palette.js)
PhotoPaletteAnalyzer.kt 1:1 포트.
- `analyze(image, mode, maxColors, centerCropRatio) -> number[]`
  - image: ImageBitmap | HTMLImageElement | HTMLCanvasElement (EXIF 보정 완료 상태)
  - mode: 'FullImage' | 'CenterCrop'
  - maxColors: 3..5 (기본 5), centerCropRatio: 0.35..1.0 (기본 0.75)
  - 반환: Android `Color.rgb()` int 배열 (0xFFRRGGBB 형태의 **signed 32-bit int** — Kotlin과 동일하게 `(0xFF<<24|r<<16|g<<8|b)|0`). 드래프트 JSON 호환 위해 int 유지.
  - 상수/알고리즘: ANALYSIS_LONG_SIDE=256, TARGET_LONG_SIDE=160, MAX_SAMPLES=12000,
    MIN_ALPHA=220, MIN_BUCKET_COUNT=2, QUANTIZE_STEP=16, MIN_DISTANCE_SQUARED=44²,
    HSL 노이즈 필터(sat<0.035 && (l<0.08||l>0.96)), 버킷 평균, score=count*(0.62+sat)*중립패널티*경계명도패널티,
    distinctByDistance. Kotlin 소스와 동일 결과 보장.

## window.KeyxifDB (db.js)
IndexedDB `keyxif` (v1): objectStore `sources` (keyPath 'id'), objectStore `exported` (keyPath 'id').
localStorage 키: `keyxif.settings`, `keyxif.draft`, `keyxif.recents`, `keyxif.buildPresets`.
- `init(): Promise<void>` — DB 오픈 (호출 전 다른 메서드 사용 금지)
- `putSource({id, name, type, blob}): Promise<void>` / `getSource(id): Promise<obj|null>` / `deleteSource(id): Promise<void>` / `listSourceIds(): Promise<string[]>` / `hasSource(id): Promise<boolean>`
- `putExported(record): Promise<void>` — record = ExportedImage 메타 + `thumbDataUrl: string` + `blob: Blob`
- `getExported(id)`, `listExported(): Promise<record[]>`(createdAt desc), `deleteExported(id)`, `clearExported()`, `hasExportedBlob(id): Promise<boolean>`
- localStorage JSON 헬퍼 (파싱 실패 → null): `loadJSON(key)`, `saveJSON(key, value)`, `removeKey(key)`

## window.KeyxifSearch (search.js) — presets.js 의존
PresetRepository.kt 검색/랭킹 포트. normalize = trim→lowercase→[a-z0-9가-힣] 외 제거.
- `searchHousings(query, recents: string[]) -> Choice[]`
- `searchSwitches(query, recents, includePresets: boolean) -> Choice[]`
- `searchKeycaps(query, recents) -> Choice[]`
- Choice = { title, subtitle, isRecent, preset|null }
  랭크 0정확→1접두→2정규정확→3정규접두→4부분→5정규부분, 최근 먼저, dedupe(정규 title|subtitle), 상한 80(빈쿼리)/160
- `logoForBuildInfo(buildInfo) -> LogoPreset|null` (logoId 직접 → housing 매칭 체인)
- `logoName(logoId) -> string|null`, `logoById(id) -> LogoPreset|null`
- `logoIdForHousing(housingPreset) -> string|null`
- `plates: string[]`, `mounts: string[]`, `logos: LogoPreset[]`

## window.KeyxifStore (store.js) — 내가 직접 작성
state-flow-spec.md의 KeyxifUiState + 57 actions. subscribe(fn) / getState() / actions.*

## UI (ui-*.js) — 컨테이너 규약
- `window.KeyxifUI.renderGallery(container, state, actions, helpers)` (ui-gallery.js)
- `window.KeyxifUI.renderSettings(container, state, actions, helpers)` (ui-settings.js)
- helpers = { text(ko,en), fmtDate(ms)->'yyyy.MM.dd HH:mm', colorToCss(int), esc(s)->HTML escape,
  getObjectURL(sourceId)->string|null (동기 캐시), VERSION }
- 두 함수는 매 상태 변경마다 전체 재렌더(innerHTML 재구성)되어도 무방하게 작성하되,
  입력 필드 포커스 유지가 필요한 곳은 data-preserve 속성 + helpers.preserveFocus 패턴 사용 가능(선택).
- 이벤트는 container에 위임 바인딩(onclick 데이터 속성) 또는 직접 addEventListener — 재렌더 안전해야 함.
