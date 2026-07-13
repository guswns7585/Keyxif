# Keyxif 웹 이식 스펙 — 완성 이미지 갤러리 + 설정

원본 소스 (Android, Jetpack Compose / Keyxif 1.0.3):
- `app/src/main/java/com/keyxif/app/ui/screens/ExportedGalleryScreen.kt`
- `app/src/main/java/com/keyxif/app/ui/screens/SettingsScreen.kt`
- 데이터 모델/기본값/라벨: `app/src/main/java/com/keyxif/app/domain/model/Models.kt`
- 로컬라이제이션: `app/src/main/java/com/keyxif/app/ui/Localization.kt`

날짜 포맷은 양쪽 화면 공통 `yyyy.MM.dd HH:mm` (SimpleDateFormat, 로컬 타임존).

---

# 1. GALLERY — "완성 이미지" (ExportedGalleryScreen)

## 1.1 데이터 모델 `ExportedImage`

| 필드 | 타입 | 비고 |
|---|---|---|
| id | String | 고유 키 (grid item key) |
| uri | String | 저장 파일 위치 (Android content URI) |
| fileName | String | |
| createdAt | Long (epoch ms) | 표시: `yyyy.MM.dd HH:mm` |
| width / height | Int | 상세에 `${width} x ${height}` 로 표시 |
| fileSizeBytes | Long | (이 화면에는 미표시, 레코드에는 존재) |
| templateName | String | |
| housing / switchName / keycap / nickname | String? | "의미 있는 텍스트"만 표시 (아래 1.9) |
| paletteColors | List<Int> (ARGB) | 색상 그룹핑에만 사용 |

## 1.2 화면 헤더

세로 배치, 화면 패딩 h20/v16dp, 섹션 간격 14dp, 헤더 내부 간격 8dp.

1. 타이틀: **`완성 이미지`** — titleLarge Bold
2. 서브타이틀 (bodyMedium, onSurfaceVariant):
   - 그룹 미진입: `Keyxif로 저장한 결과 이미지 ${images.size}개`
   - 그룹 진입 시: `${그룹제목} 그룹 이미지 ${그룹아이템수}개`
3. **그룹핑 FilterChip 행** (간격 8dp): `전체` / `하우징` / `브랜드` / `색상`
   - 칩 클릭 → 모드 변경 + 선택된 그룹 해제 + 다중선택 초기화
4. 그룹 진입 상태일 때만: OutlinedButton **`${모드라벨} 그룹으로 돌아가기`** (예: "하우징 그룹으로 돌아가기") → 그룹 목록으로 복귀
5. 이미지가 1개 이상일 때 액션 행 (간격 8dp):
   - 다중선택 중일 때만: Button **`선택 삭제 ${선택수}`** → 현재 보이는 목록 중 선택된 항목으로 삭제 확인 다이얼로그 열기 / TextButton **`선택 해제`** → 선택 비우기
   - 항상: OutlinedButton **`전체 삭제`** → **모든** 이미지(현재 그룹 아님)로 삭제 확인 다이얼로그 열기. 선택 중일 때는 `weight(1f)`로 늘어남.

## 1.3 그리드 (사진 목록)

- LazyVerticalGrid, `GridCells.Adaptive(156dp)` — 웹: `grid-template-columns: repeat(auto-fill, minmax(156px, 1fr))`
- 가로 간격 10dp / 세로 간격 12dp
- 아이템 = Card:
  - 썸네일: 정사각형(1:1), `ContentScale.Crop`(`object-fit: cover`), 상단 모서리만 radius 8
  - 선택 모드일 때 좌상단 오버레이: 26dp 사각 Surface(radius 6, padding 6) — 선택됨: primary 배경 + 흰색 체크 아이콘(접근성 라벨 "선택됨"), 미선택: surface 86% 투명 배경
  - 텍스트 블록 (padding h10/v8, 줄간격 3):
    - 1줄: `displayTitle()` — labelLarge SemiBold, 1줄 ellipsis
    - 2줄: 저장일시 `yyyy.MM.dd HH:mm` — bodySmall, onSurfaceVariant
- **displayTitle() 우선순위**: nickname → housing → fileName → 저장일시 (각 단계에서 "의미 있는 텍스트"인 것만, 1.9 참조)

### 인터랙션
- 탭: 선택 모드 아님 → 상세 다이얼로그 열기 / 선택 모드 → 해당 항목 선택 토글
- **롱프레스(웹: long-press 또는 우클릭/체크박스 hover 진입)**: 선택 집합에 추가 → 선택 모드 진입
- 이미지 목록이 바뀌면 사라진 id를 선택 집합에서 자동 제거
- 그룹핑 모드 변경 시 선택 집합 초기화

## 1.4 정렬 / 필터

- 명시적 정렬 UI **없음**. 필터 = 그룹핑 칩 4종이 전부.
- `전체` 모드: 전달받은 목록 순서 그대로 (상위에서 최신순으로 공급됨)
- 그룹 내부: `createdAt` 내림차순 고정
- 그룹 카드 정렬: ① "정보 없음"(unknown) 그룹을 맨 뒤로 → ② 개수 내림차순 → ③ 제목 오름차순

## 1.5 그룹핑 로직

정규화: `trim().lowercase()` 후 `[^a-z0-9가-힣]+` 전부 제거.

| 모드 | 그룹 키 | 그룹 제목 |
|---|---|---|
| 하우징 | `housing-{정규화된 하우징}` | 하우징 원문 (없으면 `정보 없음` / 키 `unknown`) |
| 브랜드 | `brand-{정규화된 브랜드}` | 하우징 텍스트를 내장 프리셋(PresetData.housings)의 name/aliases/id와 정규화 매칭 → preset.vendor 또는 vendorId→vendors 조회로 벤더명. 실패 시 `정보 없음` |
| 색상 | `color-*` (아래 표) | 아래 표 |

그룹 제외 텍스트(하우징/브랜드): 정규화 결과가 공백, `untitledkeyboard`, `unknown`, `none`, `null` 이면 "정보 없음" 처리.

브랜드명 표기 정규화 사전: qwertykeys→`Qwertykeys`, geonworks·geon→`Geonworks`, modedesigns·mode→`Mode`, owlab→`Owlab`, singakbd·singa→`SingaKBD`, matrixlab·matrix→`Matrix Lab`, kbdfans→`KBDfans`, jjwconcepts·jjw→`JJW`, 그 외 원문 유지.

### 색상 그룹 분류 (paletteColors 중 0 아닌 앞 3개 사용)

- 팔레트 비어있음 → 키 `unknown`, 제목 `정보 없음`, 칩 없음
- 각 색을 HSV 변환. "유채색" = 채도 ≥ 0.18 && 명도 ≥ 0.18
- 유채색 없음 → 평균 luminance로: ≤0.18 → `color-dark` / **Black / Dark** / 칩 `#1F1F1F`; ≥0.78 → `color-white` / **White / Silver** / 칩 `#E8E4DA`; 그 외 → `color-gray` / **Gray** / 칩 `#8C8C86`
- 유채색 있음 → hue 그룹 최빈값이 지배 그룹. 단, 유채색 ≥3개 && 지배 비율 <0.67 && hue 스프레드 >85° → `color-mixed` / **Multi / Mixed** / 칩 `#9B8BD9`

| hue 범위 | 키 | 제목 | 칩 색 |
|---|---|---|---|
| <18° 또는 ≥342° | color-red | Red / Pink | #E45B67 |
| 18–45° | color-orange | Orange | #E58A3A |
| 45–72° | color-yellow | Yellow / Gold | #E0B93A |
| 72–155° | color-green | Green | #4FA66A |
| 155–195° | color-cyan | Cyan | #4CB6B7 |
| 195–250° | color-blue | Blue | #537ECF |
| 250–292° | color-purple | Purple | #8B69CC |
| 292–342° | color-red-pink | Red / Pink | #D9689D |

hue 스프레드 = 360° − (정렬된 hue들 사이 최대 원형 간격).

## 1.6 그룹 카드 그리드

- LazyVerticalGrid `Adaptive(172dp)`, 간격 h10/v12
- 그룹이 0개면 중앙 텍스트: **`그룹으로 묶을 이미지가 없습니다.`**
- 그룹 카드 (클릭 → 그룹 진입):
  - 프리뷰 박스: 가로폭 100%, 비율 1.28:1. 최신 3장을 좌/중/우 정렬로 겹쳐 배치, 각각 정사각형·radius 8·padding 6, 폭은 3장일 때 58%(1장이면 100%), cover 크롭
  - 색상 모드일 때 우하단 28dp 원형 색상 칩(그룹 칩 색), padding 8
  - 텍스트 (padding h12/v10, 줄간격 3): 그룹 제목 titleSmall SemiBold 1줄 ellipsis / `${count}개` bodySmall onSurfaceVariant

## 1.7 상세 다이얼로그 (AlertDialog)

- 제목: `displayTitle()` 1줄 ellipsis
- 본문 (세로 간격 10):
  - 이미지: 가로 100%, 최대 높이 360dp, `ContentScale.Fit`(contain), radius 8
  - InfoLine 목록 (라벨 labelMedium onSurfaceVariant / 값 bodyMedium):
    1. `파일` = fileName
    2. `저장` = createdAt `yyyy.MM.dd HH:mm`
    3. `템플릿` = templateName
    4. `크기` = `${width} x ${height}`
    5. 조건부(의미 있는 값만, 이 순서): `Housing`, `Switch`, `Keycap`, `Nickname`
  - HorizontalDivider
  - 아이콘 버튼 행 (간격 8):
    - 공유 (Share 아이콘, 라벨 `공유`) → onShare
    - 갤러리에서 열기 (OpenInNew 아이콘, 라벨 `갤러리에서 열기`) → onOpen
    - 파일 삭제 (Delete 아이콘, **error 색 틴트**, 라벨 `파일 삭제`) → 상세 닫고 삭제 확인 다이얼로그 열기
- confirm 위치 버튼: TextButton **`닫기`** (단순 닫기)
- dismiss 위치 버튼: OutlinedButton **`목록에서만 제거`** → 확인 없이 즉시 레코드만 제거(파일 유지), 다이얼로그 닫힘

> 원본 앱의 상세 다이얼로그에는 팔레트 색상 칩이 **표시되지 않는다** (paletteColors는 색상 그룹핑 전용). 웹판에서는 레코드에 데이터가 이미 있으므로 `크기` 아래에 팔레트 칩 행(원형 스와치 나열)을 추가하는 것을 권장 — WEB-ADD로 표기해 구현.

## 1.8 파일 삭제 확인 다이얼로그 — 정확한 문구

- 제목: **`이미지를 삭제할까요?`**
- 본문:
  - 1개: **`"${fileName}" 파일을 기기에서 삭제하고 갤러리 목록에서도 제거합니다.`** (파일명은 큰따옴표로 감쌈)
  - 여러 개: **`선택한 이미지 ${n}개를 기기에서 삭제하고 갤러리 목록에서도 제거합니다.`**
- 확인: Button **`삭제`** — error 컨테이너 색(빨강 배경/onError 글자) → `onDeleteFiles(targets)` 실행, 삭제된 id 선택 집합에서 제거, 다이얼로그 닫기
- 취소: TextButton **`취소`** → 닫기만

진입 경로 3곳 모두 동일 다이얼로그: ① 상세의 파일 삭제 아이콘(1개) ② `선택 삭제 n`(현재 보이는 목록 중 선택분) ③ `전체 삭제`(전체 목록).

## 1.9 "의미 있는 텍스트" 규칙 (`meaningfulBuildTextOrNull`)

trim 후 공백이거나, 소문자·공백정규화 결과가 차단 목록(blockedBuildTexts)에 있으면 null 처리 → 해당 필드 미표시/그룹 제외. (차단 목록은 `BuildInfoDisplay.kt`의 `blockedBuildTexts` 참조 — 웹 포팅 시 동일 상수 복사.)

## 1.10 빈 상태 (이미지 0개)

중앙 정렬 세로 스택 (간격 12):
1. **`아직 완성된 이미지가 없습니다.`** — titleMedium SemiBold
2. **`사진을 선택하고 Keyxif 카드로 저장해보세요.`** — bodyMedium onSurfaceVariant
3. Button **`사진 선택하기`** → 만들기(사진 선택) 화면으로 이동

## 1.11 접근 불가 항목 정리 (prune) 플로우

갤러리 화면 자체가 아니라 **설정 > 완성 이미지 관리** 페이지에 있음 (2.8 참조):
- `파일 접근 불가 항목 정리` 버튼 → 각 레코드의 uri 접근성 검사 후 열 수 없는 레코드만 목록에서 제거 (파일 삭제 아님)
- `목록 기록 초기화` 버튼 → 레코드 전체 삭제. 안내문: "목록 기록 초기화는 실제 이미지 파일은 삭제하지 않습니다." 확인 다이얼로그 없이 즉시 실행(원본 기준) — 웹에서는 확인 다이얼로그 추가 권장(WEB-ADD).

## 1.12 웹 구현 제안 (WEB-EQUIV)

- **저장소**: IndexedDB `exportedImages` 오브젝트 스토어
  ```
  { id, fileName, createdAt, width, height, fileSizeBytes, templateName,
    housing, switchName, keycap, nickname,
    paletteColors: string[]  // '#RRGGBB' 배열로 변환 저장,
    thumbnailDataURL: string, // 목록/그룹 프리뷰용 축소본 (예: 긴변 320px WEBP dataURL)
    fileBlob: Blob            // 원본 결과물 — '다시 받기'/공유에 필요 }
  ```
  그리드/그룹 프리뷰는 thumbnailDataURL만 사용해 로딩 비용 절감 (Android의 Coil AsyncImage 대응).
- **갤러리에서 열기 (onOpen)** → **재다운로드**: `URL.createObjectURL(fileBlob)` + `<a download="${fileName}">` 클릭. (웹은 시스템 갤러리 열기 불가.) 버튼 라벨은 `다시 받기` 또는 `파일 다운로드`로 교체.
- **공유 (onShare)** → Web Share API: `navigator.canShare({ files: [new File([fileBlob], fileName, { type: 'image/webp' })] })` 지원 시 `navigator.share({ files, title: fileName })`, 미지원 시 재다운로드로 폴백.
- **파일 삭제** = IndexedDB 레코드(blob 포함) 삭제 — 확인 다이얼로그 문구 원본 그대로 사용, 단 "기기에서 삭제하고" → 웹 맥락상 "브라우저 저장소에서 삭제하고"로 조정 가능. **목록에서만 제거** = 레코드 삭제하되 이미 다운로드된 사본은 사용자 디스크에 남는다는 점에서 의미 유지 (또는 blob만 지우고 메타 유지하는 변형).
- **접근 불가 항목 정리** = fileBlob이 없거나 읽기 실패(스토리지 축출)한 레코드 제거.
- **롱프레스** = 포인터 long-press(≈500ms) + 데스크톱은 썸네일 hover 시 체크박스 노출로 대체 가능.

---

# 2. SETTINGS — "설정" (SettingsScreen)

## 2.1 전체 구조

**2단계 내비게이션**: 루트 메뉴 목록 → 페이지 상세. 상태는 `selectedPageName: String?` 하나 (null=루트). 웹: 해시 라우팅 `#/settings` / `#/settings/output` 등 권장.

- 컨테이너: LazyColumn, contentPadding 18dp, 항목 간격 16dp
- 루트 헤더: **`설정`** / `Settings` (titleLarge Bold) + 서브 **`필요한 항목만 열어 조정하세요.`** / `Open only the groups you want to adjust.`
- 루트 메뉴 카드(OutlinedCard, padding 14, 클릭 전체 영역): 제목 titleMedium SemiBold + 부제 bodySmall onSurfaceVariant
- 페이지 상세: 상단 TextButton **`< 설정`** / `< Settings` (뒤로) → 아래 OutlinedCard 섹션(제목 titleMedium Bold, 내부 간격 12, padding 14)

**로컬라이제이션 주의**: 루트 메뉴 제목/부제, 헤더, 뒤로 버튼, 그리고 표시 설정의 `언어`/`화면 모드` 라벨만 한/영 전환(`language.text(ko, en)`). **그 외 모든 행 라벨·설명은 한국어 하드코딩** (원본 그대로). 언어 판정: System 모드 → 시스템 로케일이 `ko`면 한국어, 아니면 영어.

공용 컴포넌트:
- **ToggleRow**: 좌측 제목(bodyLarge)+설명(bodySmall onSurfaceVariant), 우측 Material Switch
- **InfoRow**: 라벨(labelMedium onSurfaceVariant) 위, 값(bodyMedium) 아래 — 읽기 전용
- **FilterChip 그룹**: 단일 선택 칩(FlowRow, 간격 8) — 웹: 세그먼트/칩 버튼
- **Slider**: Material 슬라이더

## 2.2 루트 메뉴 (SettingsPage — 이 순서 고정, 10개)

| # | enum | 제목 (ko / en) | 부제 (ko / en) |
|---|---|---|---|
| 1 | Output | 출력 설정 / Output | 품질, 해상도, 파일명 / Quality, resolution, file names |
| 2 | Display | 표시 설정 / Display | 언어, 테마, 텍스트, 색상칩 / Language, theme, text, palette |
| 3 | Save | 저장 설정 / Save | 저장 후 동작과 미리보기 / After-save behavior and preview |
| 4 | Edit | 편집 설정 / Editing | 최근값, 기본 입력 동작 / Recent values and defaults |
| 5 | Session | 세션 설정 / Session | 작업 임시 저장과 복구 / Draft save and restore |
| 6 | Template | 템플릿 설정 / Templates | 기본 템플릿과 오버레이 / Defaults and overlays |
| 7 | Update | 업데이트 / Updates | 버전 확인과 설치 / Version checks and install — **WEB-OMIT** |
| 8 | Gallery | 완성 이미지 관리 / Finished Images | 저장 목록 정리 / Saved image list |
| 9 | About | 앱 정보 / About | 버전과 개인정보 처리 / Version and privacy |
| 10 | Developer | 개발 정보 / Developer | 문의와 기술 정보 / Contact and technical info |

## 2.3 출력 설정 (Output) — 6행

| # | 라벨 | 컨트롤 | 값 | 기본값 | 의존성/동작 |
|---|---|---|---|---|---|
| 1 | `추천 품질` | FilterChip 5개 | `고압축` `권장` `균형` `고화질` `최고화질` | 권장(Recommended) | 선택 시 아래 프리셋 표에 따라 webpQuality·keepOriginalResolution·maxLongSidePx **일괄 적용**. `사용자 지정(Custom)`은 칩으로 노출 안 됨(어느 칩도 선택 안 된 상태로 표현) |
| 2 | `WEBP 품질 ${q}%` | Slider | 70–100, 정수 스텝(steps=29) | 92 | 조작 시 qualityPreset=Custom |
| 3 | `출력 형식` | FilterChip | `WEBP` `PNG` | WEBP | **PNG 칩은 disabled** (WEBP만 선택 가능) |
| 4 | `원본 해상도 유지` | Switch | on/off — 설명: `켜면 가능한 원본 크기로 저장합니다.` | true | 조작 시 qualityPreset=Custom; on이면 5번 비활성 |
| 5 | `최대 긴 변 제한` | FilterChip | `원본`(null) `2048px` `2400px` `3000px` `4000px` | null(원본) | `원본 해상도 유지`가 on이면 **전체 disabled**; 조작 시 Custom |
| 6 | `저장 파일명 규칙` | FilterChip | `Keyxif_번호` `Housing_번호` `Nickname_번호` `Housing_Keycap_번호` | Keyxif_번호 | 프리셋과 무관 |

### 품질 프리셋 → 값 매핑 (`QualityPreset.applyTo`)

| 프리셋 | webpQuality | keepOriginalResolution | maxLongSidePx |
|---|---|---|---|
| 고압축 HighCompression | 80 | false | 1920 |
| 권장 Recommended | 88 | false | 2048 |
| 균형 Balanced | 92 | true | null |
| 고화질 HighQuality | 96 | true | null |
| 최고화질 Maximum | 100 | true | null |
| 사용자 지정 Custom | (변경 없음 — 개별 조작의 결과 상태) | | |

주의: 프리셋 `고압축`의 maxLongSidePx=1920은 5번 칩 옵션 목록에 없음 → 프리셋 적용 직후에는 어떤 칩도 선택 안 된 상태가 될 수 있음(원본 동작 그대로 유지).

## 2.4 표시 설정 (Display) — 10행 (+구분선 2개)

| # | 라벨 | 컨트롤 | 값 | 기본값 | 의존성 |
|---|---|---|---|---|---|
| 1 | `언어` / `Language` | FilterChip | `시스템` `한국어` `English` | 시스템 | UI 전체 언어 즉시 전환 |
| 2 | `화면 모드` / `Appearance` | FilterChip | `시스템` `라이트` `다크` | 시스템 | 테마 즉시 전환 |
| — | HorizontalDivider | | | | |
| 3 | `텍스트 크기 ${%.2f}x` | FilterChip 프리셋 + Slider | 칩: `작게`0.9 `기본`1.0 `크게`1.15 `매우 크게`1.3 / 슬라이더: 0.85–1.35 연속 | 1.0 | 칩은 정확히 일치하는 값일 때만 선택 표시 |
| 4 | `닉네임 표시 스타일` | FilterChip | `그대로`(Plain) `@닉네임`(AtPrefix) `Credit` | 그대로 | |
| 5 | `닉네임 강조 ${%.2f}x` | Slider | 0.9–1.35 연속 | 1.1 | |
| — | HorizontalDivider | | | | |
| 6 | `대표 색상 표시` | Switch — 설명: `사진에서 추출한 색상 팔레트를 템플릿에 표시합니다.` | on/off | true | |
| 7 | `대표 색상 개수` | FilterChip | `3개` `4개` `5개` | 4개 | |
| 8 | `색상 분석 영역` | FilterChip | `전체 이미지`(FullImage) `중앙 영역`(CenterCrop) | 중앙 영역 | 선택값이 9번 표시 여부 결정 |
| 9 | `중앙 영역 범위 ${%.0f}%` | Slider | 0.35–1.0 (표시 35–100%) | 0.75 (75%) | **`중앙 영역` 선택 시에만 표시** |
| 10 | `로고 자동 대비 선택` | Switch — 설명: `템플릿 배경에 따라 밝은/어두운 내장 로고를 자동으로 선택합니다.` | on/off | true | |

## 2.5 저장 설정 (Save) — 5행

| # | 라벨 | 컨트롤 | 설명 | 기본값 |
|---|---|---|---|---|
| 1 | `저장 위치` | InfoRow(읽기 전용) | 값: `Pictures/Keyxif` (`Pictures/${saveDirectoryName}`) | Keyxif |
| 2 | `저장 완료 후 갤러리 열기` | Switch | `저장이 끝나면 마지막 저장 결과를 이미지 앱으로 엽니다.` | false |
| 3 | `저장 완료 메시지 표시` | Switch | `저장 결과를 화면에 표시합니다.` | true |
| 4 | `전체 저장 중 실패 항목 건너뛰기` | Switch | `끄면 첫 실패에서 전체 저장을 멈춥니다.` | true |
| 5 | `저장 미리보기 확대 허용` | Switch | `저장 화면에서 렌더 미리보기를 크게 볼 수 있습니다.` | true |

웹 비고: 1번은 웹에선 브라우저 다운로드 폴더/IndexedDB 안내 문구로 대체. 2번 "갤러리 열기"는 저장 후 완성 이미지 탭으로 이동으로 대응.

## 2.6 편집 설정 (Edit) — 5행

| # | 라벨 | 컨트롤 | 설명/값 | 기본값 |
|---|---|---|---|---|
| 1 | `마지막 사용 템플릿 기억` | Switch | `템플릿을 선택하면 기본 템플릿도 함께 갱신합니다.` | true |
| 2 | `마지막 닉네임 기억` | Switch | `새 사진 추가 때 최근 닉네임을 제안합니다.` | true |
| 3 | `최근 사용 입력값 개수` | FilterChip | `10개` `20개` `50개` | 20개 |
| 4 | `사진 추가 시 이전 빌드 정보 복사` | Switch | `새 사진에 직전 사진의 빌드 정보를 자동으로 넣습니다.` | false |
| 5 | `스위치 추천 목록 표시` | Switch | `스위치 입력창에서 내장 프리셋을 함께 보여줍니다.` | true |

## 2.7 세션 설정 (Session) — 2행

| # | 라벨 | 컨트롤 | 설명 | 기본값 |
|---|---|---|---|---|
| 1 | `자동 세션 임시 저장` | Switch | `사진 목록, 템플릿, 빌드 정보를 임시 저장합니다.` | true |
| 2 | `앱 시작 시 복구 여부 묻기` | Switch | `끄면 임시 저장된 작업을 자동 복구합니다.` | true |

## 2.8 템플릿 설정 (Template) — 4행

| # | 라벨 | 컨트롤 | 값/설명 | 기본값 |
|---|---|---|---|---|
| 1 | `기본 템플릿` | FilterChip 12개 (enum 순서) | `Plain Export` `클래식 프레임` `미니멀 캡션` `하단 스펙 바` `코너 마크` `포스터 마진` `다크 글래스 스트립` `사이드 스펙 레일` `상단 네임플레이트` `뮤지엄 매트` `컴팩트 티켓` `클린 시그니처` | 클래식 프레임 |
| 2 | `템플릿 미리보기에서 실제 사진 사용` | Switch | `끄면 화면 샘플 미리보기 중심으로 봅니다.` | true |
| 3 | `이미지 오버레이 중앙 영역 보호` | Switch | `로고와 정보가 사진 중앙을 덮지 않도록 제한합니다.` | true |
| 4 | `Plain Export에 빌드 정보 표시` | Switch | `켜면 원본형 템플릿에도 입력한 빌드 정보를 함께 표시합니다.` | false |

## 2.9 업데이트 (Update) — 7행 — **WEB-OMIT (전체 섹션 웹 제외)**

APK 자체 업데이트 체크/다운로드/설치 플로우. 기록용 원본 구성:
1. InfoRow `현재 버전` = `${VERSION_NAME} (${VERSION_CODE})`
2. InfoRow `최신 버전` = 최신 버전명 또는 `확인 전`
3. InfoRow `마지막 확인` = `yyyy.MM.dd HH:mm` 또는 `확인 전`
4. 상태 메시지(primary색) / 오류 메시지(error색) 조건부 텍스트
5. 다운로드 중: `APK 다운로드 중 · ${n}%` + LinearProgressIndicator; 실패 시 `업데이트 다운로드 실패: ${msg}`
6. Button 전체폭 `업데이트 확인` (확인 중이면 `확인 중...`+disabled)
7. 다운로드 완료 시 Button `설치 계속`; DEBUG 빌드 한정 `update.json URL` 텍스트필드(placeholder `https://example.com/keyxif/update.json`)

웹 대체: 루트 메뉴에서 카드 자체를 제거하거나, About에 웹앱 버전 문자열만 표시.

## 2.10 완성 이미지 관리 (Gallery) — 4행 (데이터 관리)

| # | 요소 | 내용 |
|---|---|---|
| 1 | InfoRow `저장된 항목` | `${exportedImageCount}개` |
| 2 | Button(전체폭) **`파일 접근 불가 항목 정리`** | 접근 불가(URI 열기 실패) 레코드만 목록에서 제거 — 웹: blob 소실 레코드 정리 |
| 3 | 안내 텍스트 (bodySmall onSurfaceVariant) | **`목록 기록 초기화는 실제 이미지 파일은 삭제하지 않습니다.`** |
| 4 | Button(전체폭) **`목록 기록 초기화`** | 레코드 전량 삭제(파일 미삭제). 원본에는 확인 다이얼로그 없음 → 웹에서 확인 다이얼로그 추가 권장(WEB-ADD) |

## 2.11 앱 정보 (About) — 4행

1. **`Keyxif`** — headlineSmall Bold
2. InfoRow `버전명` = VERSION_NAME (웹: 웹앱 버전 문자열)
3. InfoRow `오픈소스 라이선스` = `준비 중`
4. Divider + 개인정보 문단 (bodyMedium onSurfaceVariant): **`사진과 빌드 정보는 기본적으로 기기 안에서 처리됩니다. 서버 업로드나 외부 전송 없이, 사용자가 직접 저장한 결과물만 갤러리에 저장됩니다.`** — 웹판에서도 그대로 유효(전부 클라이언트 처리)하므로 유지, "기기 안" → "브라우저 안" 표현 조정 가능

## 2.12 개발 정보 (Developer) — 6행 (+DEBUG 1)

1. InfoRow `개발자` = `KGJun`
2. **ActionInfoRow** `문의` = `typenews902@gmail.com` — 값이 클릭 가능한 TextButton → 웹: `mailto:` 링크
3. InfoRow `GitHub` = `guswns7585/keyxif` (원본은 비클릭 — 웹은 링크화 가능)
4. InfoRow `기술 정보` = `Kotlin · Jetpack Compose · Android SDK 26+` → 웹판 문자열로 교체 권장 (예: `TypeScript · Canvas · IndexedDB`)
5. InfoRow `이미지 처리` = `On-device rendering · WEBP Export · MediaStore Save` → 웹: `In-browser rendering · WEBP Export · Local download`
6. InfoRow `현재 기기` = `Android ${SDK_INT}` → 웹: `navigator.userAgent` 요약 또는 생략
7. (DEBUG 빌드 한정) Divider + `디버그 빌드 전용 개발자 옵션 영역` — WEB-OMIT

## 2.13 리셋 동작 정리

- 화면 어디에도 "전체 초기화/기본값 복원" 버튼 **없음**.
- 유일한 리셋성 동작 = 출력 설정의 **품질 프리셋 칩** (webpQuality/keepOriginalResolution/maxLongSidePx 3개를 일괄 세팅).
- webpQuality·keepOriginalResolution·maxLongSidePx 중 하나라도 개별 조작 → qualityPreset이 `Custom`으로 강등되어 5개 칩 모두 비선택 상태.
- 데이터 리셋 = 2.10의 `목록 기록 초기화`(갤러리 레코드만).
- 웹 저장: AppSettings 전체를 localStorage(또는 IndexedDB `settings` 키) JSON 하나로 저장, 기본값은 위 표의 기본값 열 사용.

## 2.14 AppSettings 기본값 요약 (웹 스토어 초기값)

```json
{
  "webpQuality": 92, "outputFormat": "WEBP", "keepOriginalResolution": true,
  "maxLongSidePx": null, "fileNameRule": "KEYXIF_INDEX", "saveDirectoryName": "Keyxif",
  "openGalleryAfterSave": false, "showSaveToast": true, "skipFailedOnBatchSave": true,
  "rememberLastTemplate": true, "rememberLastNickname": true, "recentInputLimit": 20,
  "copyPreviousBuildInfoOnAdd": false, "defaultTemplate": "ClassicFrame",
  "useCurrentPhotoForTemplatePreview": true, "protectCenterAreaForOverlay": true,
  "textScale": 1.0, "qualityPreset": "Recommended",
  "autoRestoreDraftSession": true, "askBeforeRestoreDraft": true,
  "nicknameStyle": "Plain", "nicknameEmphasis": 1.1,
  "showSwitchPresets": true, "enableExportPreviewZoom": true,
  "showBuildInfoInPlainExport": false,
  "showPaletteColors": true, "paletteColorCount": 4,
  "paletteAnalysisMode": "CenterCrop", "paletteCenterCropRatio": 0.75,
  "autoSelectLogoContrastVariant": true,
  "languageMode": "System", "themeMode": "System"
}
```
