# Keyxif 1.0.3 — 화면 픽셀 스펙 (Compose → Web 1:1 이식용)

소스: `Keyxif-1.0.3/app/src/main/java/com/keyxif/app/ui/` (screens 4개 + components 4개 + PresetRepository/Models 발췌)
단위: Compose `dp` → CSS `px` 1:1 매핑 권장 (기준 뷰포트 ~412px 모바일).
Material3 shape 기본값: `shapes.small`=8dp, `shapes.medium`=12dp, `shapes.large`=16dp 라운드.
공통 카드(FormSection/리스트 카드): `surface` 색 + tonal/shadow elevation 1dp → 웹에서는 `background: var(--surface); box-shadow: 0 1px 2px rgba(0,0,0,.10); border-radius: 16px`.

앱 단계(하단 네비) 이름 — Models.kt `AppStep.displayName()`:
`사진` / `정보` / `템플릿` / `저장`

---

## 1. PhotoPickerScreen (사진 선택)

### 레이아웃 (위→아래)
루트 `Column`: `fillMaxSize`, padding `horizontal 20dp / vertical 16dp`, 자식 간 간격 `14dp`.

1. **상단 버튼 행** (`Row`, 가로 간격 8dp, 세로 중앙정렬)
   - **[사진 추가]** — filled Button, `weight(1f)` (남은 폭 전부), 높이 `48dp`. 내용: `+`(Add) 아이콘 + `8dp` 간격 + 텍스트 `사진 추가`. 클릭 → OS 사진 피커, **최대 60장** 멀티선택, 이미지 전용.
   - **[전체 삭제]** — OutlinedButton, 높이 `48dp`, 내용: 휴지통(Delete outlined) 아이콘 + `6dp` 간격 + `전체 삭제`. **enabled 조건: 사진이 1장 이상 있을 때** (`state.photos.isNotEmpty()`). 클릭 → 확인 다이얼로그.
2. **공유/알림 메시지 배너** (조건부: `state.shareMessage != null`)
   - Surface: `fillMaxWidth`, radius medium(12dp), 배경 `secondaryContainer`.
   - 내부 Row padding: start 14 / end 6 / top 4 / bottom 4dp, 양끝 정렬.
   - 좌: 메시지 텍스트(bodyMedium, `onSecondaryContainer`, weight 1). 우: TextButton `확인` → 메시지 dismiss.
3. **본문** — 분기:
   - 사진 0장 → 빈 상태(아래).
   - 사진 있음 → 세로 리스트(`LazyColumn`, 아이템 간격 `10dp`), 아이템 = 사진 카드.

### 빈 상태 (EmptyPhotoState)
화면 정중앙 배치. 세로 스택, 간격 10dp, 가운데 정렬:
- 원형 Surface `88dp`, 배경 `surfaceContainerHigh`, 중앙에 AddPhotoAlternate 아이콘 `38dp` (`onSurfaceVariant` 틴트)
- (6dp spacer)
- 제목: `키보드 사진으로 시작하세요` (titleLarge)
- 설명(bodyMedium, `onSurfaceVariant`, 중앙 정렬, 줄바꿈 포함):
  `사진을 고르고 빌드 정보를 입력하면\n스펙 카드가 함께 담긴 이미지로 저장됩니다.`
- (6dp spacer)
- **[사진 선택하기]** filled Button 높이 48dp, `+` 아이콘 + 8dp + 텍스트.

### 사진 리스트 카드 (PhotoListCard)
Surface: radius large(16dp), surface 색, elevation 1dp.
내부 Row: `fillMaxWidth`, padding `10dp`, 자식 간격 `12dp`, 세로 중앙정렬.
- **썸네일**: `82dp` 정사각(`width 82dp` + `aspectRatio 1`), radius medium(12dp), `object-fit: cover`.
  - 좌상단 오버레이: 원형 배지 `20dp`(썸네일 기준 margin 5dp), 배경 `scrim 55% 알파`(검정 반투명), 중앙에 순번 숫자(labelSmall, 흰색). 순번 = 리스트 인덱스+1.
- **텍스트 컬럼** (weight 1, 줄 간격 3dp):
  - 1줄: 파일명 `displayName` (titleSmall, 1줄 말줄임)
  - 2줄: 하우징 텍스트 또는 미입력 시 `빌드 정보 미입력` (bodySmall, `onSurfaceVariant`, 1줄 말줄임)
- **아이콘 버튼 3개** (M3 IconButton = 48dp 터치영역, 24dp 아이콘):
  - ↑ (`위로 이동`) — enabled: `index > 0`
  - ↓ (`아래로 이동`) — enabled: `index < last`
  - ✕ (`삭제`, `onSurfaceVariant` 틴트) — 항상 enabled, 해당 사진 제거
- 재정렬은 **드래그가 아니라 ↑/↓ 버튼**으로 1칸씩 이동 (`onMove(id, ±1)`).

### 전체 삭제 확인 다이얼로그
- 제목: `사진을 모두 지울까요?`
- 본문: `업로드한 사진 ${N}장을 Keyxif 작업 목록에서 모두 제거합니다.` (N = 현재 장수)
- 확인 버튼: `전체 삭제` — **error 색 배경**(빨강) filled Button
- 취소 버튼: `취소` — TextButton
- 바깥 클릭/뒤로가기 → 닫힘

### 웹 이식 메모
- OS 사진 피커 → `<input type="file" accept="image/*" multiple>` + 60장 상한 검증.
- Coil AsyncImage → `URL.createObjectURL(file)` + `<img>`.

---

## 2. BuildInfoScreen (빌드 정보 입력)

### 레이아웃
루트 `LazyColumn`(세로 스크롤): padding `20/16dp`, 섹션 간격 `14dp`.
모든 섹션 = **FormSection** 카드: radius large(16dp), surface, elevation 1dp, 내부 padding `16dp`, 내부 요소 간격 `10dp`, 첫 요소는 섹션 제목(titleMedium **Bold**).

### 섹션 1 — `편집 중인 사진`
1. (선택된 사진이 있으면) 현재 사진 Row (간격 12dp):
   - 썸네일 `72dp` 정사각, radius `6dp`, cover
   - 컬럼(weight 1): 파일명 (titleMedium SemiBold, 최대 2줄 말줄임) / `${index+1} / ${total}` (bodySmall, onSurfaceVariant)
2. **가로 썸네일 스트립** (`LazyRow`, 간격 8dp): 사진 전체를 `72dp` 정사각 radius 6dp 카드로 나열. **선택된 사진 = `2dp primary` 테두리**(radius 6dp). 클릭 → 그 사진으로 편집 대상 전환. (가로 스크롤; 웹은 `overflow-x:auto` flex.)
3. **[이 빌드 정보를 모든 사진에 적용]** OutlinedButton — enabled: 선택 사진 존재 **AND 사진 2장 이상**
4. **[빌드 정보 초기화]** OutlinedButton — enabled: 선택 사진 존재

### 섹션 2 — `빌드 프리셋`
1. 상태 텍스트 (bodyMedium, onSurfaceVariant): 현재 buildInfo와 완전 일치하는 저장 프리셋이 있으면 `현재 적용: {프리셋명}`, 없으면 `현재 적용된 프리셋 없음`
2. **[빌드 프리셋 불러오기]** filled Button, `fillMaxWidth` → 프리셋 피커 바텀시트 열기
3. (프리셋 1개 이상일 때) 라벨 `최근 프리셋` (labelMedium, onSurfaceVariant) + **최근 3개** PresetSummaryRow:
   - 좌 컬럼(weight 1): 프리셋명(titleSmall SemiBold, 1줄 말줄임) / 설명 = `하우징 · 스위치 · 키캡` 중 의미있는 값 `·` 조인, 전부 비면 `세부 정보 없음` (bodySmall)
   - 우: **[적용]** OutlinedButton + 휴지통 IconButton(error 틴트, aria: `{프리셋명} 삭제`) → 삭제 확인 다이얼로그
   - 하단 TextButton `전체 보기` → 바텀시트
4. (프리셋 0개) 텍스트: `저장된 프리셋이 없습니다.`
5. 저장 행 (Row 간격 8dp): ClearableTextField(weight 1) 라벨 `새 프리셋 이름`, placeholder `비우면 자동 생성` + **[저장]** filled Button — enabled: 선택 사진 존재. 저장 후 입력값 비움. (프리셋명 상태는 사진 전환 시 리셋)

#### 프리셋 피커 바텀시트 (BuildPresetPickerBottomSheet)
ModalBottomSheet (웹: 하단 시트/시트 모달, 배경 스크림 + 아래에서 슬라이드).
내부 Column: padding `horizontal 18 / vertical 10dp`, 간격 `12dp`:
- 제목 `빌드 프리셋 불러오기` (titleLarge Bold)
- ClearableSearchField 라벨 `프리셋 검색`
- 결과 0개면: `검색 결과가 없습니다.` (bodyMedium, onSurfaceVariant)
- 리스트 (`LazyColumn`, **max-height 460dp**, 스크롤): M3 ListItem
  - headline: 프리셋명 (SemiBold, 1줄 말줄임)
  - supporting: descriptionText (1줄 말줄임)
  - trailing (간격 12dp): TextButton `적용` (적용 후 시트 닫힘) + 휴지통 IconButton(error) → 삭제 확인
  - 각 행 아래 HorizontalDivider(`surfaceVariant` 색 1px)
- **필터링**: 쿼리 trim 후 공백이면 전체. 아니면 프리셋명/하우징/스위치명/키캡에 **부분일치(대소문자 무시)**.

#### 프리셋 삭제 확인 다이얼로그
- 제목: `프리셋을 삭제할까요?`
- 본문: `"{프리셋명}" 프리셋을 삭제합니다. 이 작업은 되돌릴 수 없습니다.`
- 확인: `삭제` (error 배경 filled) / 취소: `취소` (TextButton)

### 섹션 3 — `빌드 정보` (핵심 3필드)
순서대로, 각 필드 앞에 RecentChips(있을 때만):
1. RecentChips `최근 사용한 하우징` → PresetSearchField 라벨 `하우징`, placeholder `하우징 검색 또는 직접 입력`
2. RecentChips `최근 사용한 스위치` → PresetSearchField 라벨 `스위치`, placeholder `스위치 검색 또는 직접 입력`
3. RecentChips `최근 사용한 키캡` → PresetSearchField 라벨 `키캡`, placeholder `키캡 검색 또는 직접 입력`

- 필드에 직접 타이핑 → buildInfo 즉시 반영(제어 입력).
- 옵션 선택 시: preset이 붙은 항목이면 preset 적용 콜백(하우징이면 로고 자동 매칭 등 부수효과), 아니면 title 문자열만 대입.

#### RecentChips (components/PresetSearchField.kt 하단)
- 값이 없으면 **렌더링 자체 생략**.
- 세로 간격 6dp: 라벨(labelMedium, onSurfaceVariant) + 칩 Row(간격 8dp).
- **최대 3개** AssistChip(outlined 칩, 라벨 좌우 2dp 추가 패딩, 1줄 말줄임). 클릭 → 해당 값 필드에 대입.

### 섹션 4 — `보강판`
FlowRow(줄바꿈 되는 칩 그룹, 가로/세로 간격 8dp)의 **FilterChip** 목록 (`plates` 문자열들).
- 단일 선택 + **재클릭 시 해제** (`info.plate == plate ? "" : plate`).
- FilterChip: 선택 시 체크 아이콘 + secondaryContainer 배경, 미선택 시 outline.

### 섹션 5 — `마운트`
보강판과 동일 구조 (`mounts` 목록, `info.mount` 토글).

### 섹션 6 — `닉네임과 로고`
1. RecentChips `최근 사용한 닉네임`
2. ClearableTextField `fillMaxWidth`, 라벨 `닉네임`, placeholder `입력한 경우에만 표시합니다`
3. FlowRow(8dp 간격) 로고 FilterChip 그룹 — 상호배타 라디오처럼 동작:
   - `로고 없음` — 선택 시 logoId/customLogo 모두 해제 + logoDisabled 토글
   - `자동` — 선택 조건: `!logoDisabled && logoId==null && customLogoUri==null` (하우징명으로 로고 자동 매칭). 재클릭 시 `로고 없음` 상태로.
   - 내장 로고들 (`logos[].name` 각각 칩) — 재클릭 시 해제→logoDisabled
   - `로고 업로드` / (업로드 후) `사용자 로고` — leading 아이콘: Upload. 미업로드 상태 클릭 → 이미지 피커. 업로드 상태에서 재클릭 → 커스텀 로고 해제 + logoDisabled.
4. (커스텀 로고 있고 logoDisabled 아님) 로고 미리보기 이미지: `fillMaxWidth × 높이 72dp`, `object-fit: contain`, aria `사용자 로고 미리보기`

---

## 공통 컴포넌트

### ClearableTextField / ClearableSearchField (ClearableTextFields.kt)
- M3 **OutlinedTextField** (외곽선 + 플로팅 라벨). 웹: 테두리 1px `outline`, 포커스 시 2px primary + 라벨 축소 애니메이션.
- trailing: **값이 비어있지 않으면 ✕ 지우기 IconButton** (aria: `입력 내용 지우기`) → 값 `""`. 외부 trailingIcon과 병행 시 [✕][외부아이콘] 순서 Row.
- ClearableSearchField = 라벨 문자열만 받는 축약형, 단일행.

### PresetSearchField (autocomplete — 실제로는 "필드 + 검색 바텀시트" 패턴)
필드 자체는 ClearableTextField이며 trailing에 TextButton **`검색`** (`min-width 56dp`)이 항상 붙는다. 인라인 드롭다운이 아니라 **`검색` 버튼 → ModalBottomSheet**가 열리는 구조 (직접 타이핑은 자유 입력).

바텀시트 내부 (padding 18/10dp, 간격 12dp):
1. 제목 `{라벨} 선택` (예: `하우징 선택`) — titleLarge Bold
2. ClearableSearchField: 라벨 `{라벨} 검색`, placeholder는 필드와 동일. **초기값 = 현재 필드 값**, 시트 안에서 로컬 편집(라이브 필터).
3. **[직접 입력값 사용]** filled Button `fillMaxWidth` — enabled: 쿼리 비어있지 않을 때. 클릭 → 쿼리 문자열을 그대로 필드 값으로 확정 + 시트 닫기.
4. 결과 0개: `검색 결과가 없습니다.` / 1개 이상: 라벨 `최근 사용 / 내장 목록` (labelMedium)
5. 결과 리스트 (`LazyColumn`, **max-height 420dp**): ListItem
   - headline: 항목명 (1줄 말줄임; **최근 항목은 SemiBold**, 내장 항목은 Normal)
   - supporting: subtitle (최근 항목 = `최근 사용`, 하우징 = `벤더 / 디자이너`, 스위치 = 제조사 또는 `앱 지원`, 키캡 = 제조사)
   - trailing (간격 4dp): (최근 항목만) 휴지통 IconButton aria `최근 항목 삭제` — 클릭 시 **시트 로컬에서 즉시 숨김**(hiddenRecentTitles) + 영구 삭제 콜백. 그 다음 TextButton — preset 없는 항목(=최근/자유값)은 `사용`, preset 항목은 `선택`. 클릭 → 반영 + 시트 닫기.
   - 행 구분선 surfaceVariant.

### 제안 필터링·랭킹 (PresetRepository.searchHousing/Switch/Keycap)
- 정규화 키: `trim → lowercase → [a-z0-9가-힣] 외 문자 제거` — 공백/기호 무시 매칭.
- 매칭: 원문 부분일치(ignore case) **또는** 정규화 키 부분일치. 대상 필드: 이름 + 별칭(aliases) + (하우징) 벤더/디자이너/벤더 별칭, (스위치·키캡) 제조사.
- **정렬 랭크** (낮을수록 위): 0 정확일치 → 1 접두일치 → 2 정규화 정확 → 3 정규화 접두 → 4 부분일치 → 5 정규화 부분일치. 항목 랭크 = 이름/별칭/제조사 랭크 중 최소.
- **결과 합성 순서**: 최근 사용(매칭되는 것, 쿼리 비면 전부) 먼저 → 내장 프리셋(랭크 정렬). 이후 `정규화(title)|정규화(subtitle)` 기준 dedupe.
- **개수 상한**: 쿼리 공백이면 80개, 있으면 160개.
- 최근 항목의 subtitle은 항상 `최근 사용`, isRecent=true.

---

## 3. TemplateSelectScreen (템플릿 선택)

### 레이아웃
루트 Column: padding 20/16dp, 간격 14dp.
1. 헤더 컬럼(간격 4dp):
   - `템플릿` (titleLarge)
   - 부제(bodyMedium, onSurfaceVariant): `편집 중: {사진라벨}` + ` · ` + 하우징명 조인. 둘 다 비면 `사진을 가리지 않는 카드 스타일을 선택하세요.`
2. **그리드** (`LazyVerticalGrid`, weight 1 = 남은 높이 스크롤):
   - `GridCells.Adaptive(minSize = 156dp)` → CSS `grid-template-columns: repeat(auto-fill, minmax(156px, 1fr))`
   - 셀 간격: 가로/세로 `10dp`
   - 항목: `CardTemplate` enum 12종 전부.

### TemplatePreviewCard (썸네일은 **실제 렌더러가 아님** — 하드코딩 미니어처 벡터)
Surface(클릭 가능), radius medium(12dp), surface 배경:
- 테두리: 선택 시 `2dp primary`, 미선택 시 `1dp outlineVariant`. elevation: 선택 2dp / 미선택 0.
- 내부 Column padding 10dp, 간격 8dp:
  1. **미리보기 캔버스**: `fillMaxWidth`, **aspectRatio 1.25 (5:4 가로)**, radius 10dp, 기본 배경 `#E6E8E6`.
     - Canvas에 절차적 스케치: 공통 대각선 그라디언트 배경 `#BFC7C2 → #6C7675 → #292F31` (좌상→우하) 위에 템플릿별 도형(아래 표). 웹은 **SVG 또는 canvas로 동일 좌표 재현** 권장 (좌표는 w/h 비율 기반이라 그대로 이식 가능).
     - **팔레트 칩**: PlainExport·CornerMark 제외 10종에 원형 칩 4개 오버레이. 색: `#343A40 #E8E2D4 #B7C9BF #FF8E68`. 칩 지름 `h*0.029`, 간격 `h*0.010`, 검정 22% 알파 1.1px 스트로크. 오른쪽 끝 x: SideSpecRail `w*0.965`, 그 외 `w*0.94`. 중심 y: ClassicFrame .95h / MinimalCaption .96h / BottomSpecBar .91h / PosterMargin .94h / DarkGlassStrip .94h / SideSpecRail .22h / TopNameplate .10h / MuseumMat .93h / CompactTicket .93h / CleanSignature .96h.
     - 선택 시 우상단: 원형 배지 22dp(margin 6dp), primary 배경, 흰 체크 14dp (aria `선택된 템플릿`).
  2. 템플릿 이름 (titleSmall SemiBold, 1줄)
  3. 설명 (bodySmall, onSurfaceVariant, 최대 2줄 말줄임)

### 템플릿 12종 이름·설명 (Models.kt)
| enum | 이름 | 설명 |
|---|---|---|
| ClassicFrame | 클래식 프레임 | 사진 밖의 얇은 바에 전체 빌드 정보를 정돈합니다. |
| MinimalCaption | 미니멀 캡션 | 밝은 하단 여백에 핵심 정보만 담습니다. |
| BottomSpecBar | 하단 스펙 바 | 아주 얇은 하단 바에 주요 스펙을 배열합니다. |
| CornerMark | 코너 마크 | 사진 모서리에 로고와 하우징만 작게 표시합니다. |
| PosterMargin | 포스터 마진 | 사진집 같은 프레임과 하단 여백을 만듭니다. |
| DarkGlassStrip | 다크 글래스 스트립 | 하단 가장자리에 얇은 반투명 3열 정보를 표시합니다. |
| SideSpecRail | 사이드 스펙 레일 | 오른쪽 외부 레일에 로고와 세부 스펙을 세로로 배치합니다. |
| TopNameplate | 상단 네임플레이트 | 사진 위쪽 여백에 큰 이름표와 로고를 올립니다. |
| MuseumMat | 뮤지엄 매트 | 넓은 매트 여백과 작품 라벨 같은 정보를 만듭니다. |
| CompactTicket | 컴팩트 티켓 | 하단 티켓형 라벨에 주요 정보를 작게 모읍니다. |
| CleanSignature | 클린 시그니처 | 하단 여백에 하우징, 키캡, 닉네임을 서명처럼 정리합니다. |
| PlainExport | Plain Export | 꾸밈 없이 원본 사진을 WEBP로 저장합니다. |

### 미니어처 도형 좌표 (비율 기반, 웹 SVG 그대로 이식)
- **ClassicFrame**: 하단 밝은 바 `#F7F7F3` (y .84h, 높이 .16h); 검정 라운드사각 `#171717` (.06w,.88h, .1w×.07h, r4); 3열 텍스트 라인 x=.22/.45/.68w: 진한 줄 `#343434` (.15w×3px, y .89h) + 회색 줄 `#888` (.11w×2px, y .94h)
- **MinimalCaption**: 하단 바 `#FCFCF9` (y .86h, .14h); 줄 `#282828` (.08w,.9h,.34w×3px); `#929292` (.08w,.95h,.24w×2px)
- **BottomSpecBar**: 짙은 바 `#232626` (y .89h, .11h); 흰 90% 줄 3개 x=.08/.39/.70w (.18w×3px, y .94h)
- **CornerMark**: 우상단 검정 58% 라운드사각 (.64w,.06h,.3w×.09h,r5); 흰 원 r=.025h @(.7w,.105h); 흰 줄 (.75w,.1h,.13w×3px)
- **PosterMargin**: 전체 `#F8F8F5`; 내부 사진 그라디언트 `#BFC7C2→#303638` (.04w,.04h,.92w×.76h); 줄 `#171717`(.09w,.87h,.34w×4px), `#777`(.09w,.93h,.45w×3px)
- **DarkGlassStrip**: 검정 70% 바 (y .88h,.12h); 흰 라운드사각 (.05w,.91h,.11w×.06h,r4); 흰 줄 3개 x=.22/.46/.70w (.14w×3px, y .94h)
- **SideSpecRail**: 우측 레일 `#F3F4F1` (x .82w, 폭 .18w, 전체 높이); 구분선 `#D9DAD6` 2px; 검정 라운드사각 `#181818` (.855w,.095h,.11w×.035h,r6); 4쌍 줄 y=.3+.12i: `#343434` .1w×3px / `#8B8B8B` .08w×2px (x .85w)
- **TopNameplate**: 상단 바 `#FAFAF7` (높이 .13h); 검정 원 r=.035h @(.1w,.065h); 줄 `#242424`(.18w,.045h,.32w×3px), `#8A8A8A`(.18w,.085h,.42w×2px)
- **MuseumMat**: 전체 `#F6F5EF`; 사진 그라디언트 `#BDC5C1→#33383A` (.06w,.06h,.88w×.68h); 줄 `#232323`(.08w,.84h,.33w×4px), `#777`(.08w,.9h,.54w×3px); 원 `#202020` r=.035h @(.86w,.86h)
- **CompactTicket**: 바 `#EDEEEA`(y .86h,.14h); 티켓 `#FBFAF6`(.04w,.88h,.92w×.1h,r8); 원 `#1D1D1D` r=.026h @(.12w,.93h); 줄 `#282828`(.18w,.91h,.32w×3px), `#8D8D8D`(.18w,.95h,.48w×2px)
- **CleanSignature**: 바 `#FAFAF7`(y .84h,.16h); 줄 `#151515`(.08w,.88h,.34w×4px), `#797C76`(.08w,.93h,.25w×3px), `#2E2F2C`(.08w,.97h,.42w×3px); 원 `#222` r=.03h @(.87w,.91h)
- **PlainExport**: 그라디언트 전체 + 흰 20% 스트로크 2px 라운드 프레임 (.06w,.06h,.88w×.88h,r4)

---

## 4. ExportScreen (미리보기·저장)

### 레이아웃
루트 Column: padding 20/16dp, 간격 14dp.
1. 헤더 컬럼(간격 4dp):
   - `미리보기` (titleLarge)
   - `WEBP 품질 {N}% · Pictures/{저장폴더명}` (bodyMedium, onSurfaceVariant) — 웹은 `다운로드 폴더` 등으로 치환
2. (있으면) 진행 메시지 `state.exportProgress.message` (bodyMedium, onSurfaceVariant)
3. (total > 0) **LinearProgressIndicator** `fillMaxWidth` — progress = current/total(0..1 클램프) + 카운터 텍스트 (labelMedium):
   `${current} / ${total} · 성공 ${successCount} · 실패 ${failureCount}`
4. (선택 모드 = 선택된 export 사진 1장 이상) **선택 배너**: Surface radius medium, `secondaryContainer` 배경, 내부 Row padding 14/4dp:
   - `${N}장 선택됨` (labelLarge, weight 1)
   - TextButton `선택 해제` — enabled: `!isSaving`
5. **그리드** (weight 1, 스크롤): `GridCells.Adaptive(150dp)` → `repeat(auto-fill, minmax(150px,1fr))`, 셀 간격 12dp.

### ExportPreviewCard (사진별 카드)
Surface radius large(16dp), elevation 1dp. 내부 Column padding 10dp, 간격 8dp:
1. **RenderedPreview** (아래 참조) — 클릭/롱프레스 combinedClickable, `isSaving` 동안 비활성:
   - 일반 클릭: 선택 모드면 선택 토글; 아니면 (설정 `enableExportPreviewZoom` 켜짐 시) 풀스크린 확대 다이얼로그
   - **롱프레스**: 해당 사진 선택 → 선택 모드 진입 (웹: `contextmenu`/pointer 홀드 ~500ms)
   - 선택 모드 시 좌상단 오버레이: 원형 26dp(margin 8dp) — 선택됨: `secondary` 배경 + 흰 체크 16dp (aria `선택됨`) / 미선택: surface 92% 알파 빈 원
2. 텍스트 컬럼(간격 2dp): 파일명(titleSmall, 1줄 말줄임) + **상태 텍스트**(bodySmall, 최대 2줄; Error면 error 색, 그 외 onSurfaceVariant):
   - Idle → `저장 대기` / Rendering → `저장 중` / Saved → `저장 완료` / Error → errorMessage 또는 `실패`
3. **[이 사진 저장]** FilledTonalButton, `fillMaxWidth × 40dp` — enabled: `!isSaving`. (배치 저장 트리거는 이 화면 밖 — 상단 앱바/네비 쪽. 이 화면 내 저장은 사진 단건.)

### RenderedPreview (components/RenderedPreview.kt — 그리드 셀 미리보기)
- 컨테이너: `fillMaxWidth`, **aspectRatio 1(정사각)**, radius medium(12dp), 배경 `surfaceContainerHigh`. 내용 중앙 정렬, 이미지 `object-fit: contain`.
- **실제 템플릿 렌더러 사용**: `viewModel.renderPreviewBitmap(photoId, 768)` — 긴 변 768px로 실제 카드 합성. renderKey가 바뀌면 재렌더 (키 구성: 템플릿명, buildInfo 해시, 팔레트 해시, textScale, nicknameStyle, showPaletteColors, paletteColorCount, paletteAnalysisMode, paletteCenterCropRatio, autoSelectLogoContrastVariant).
- 상태 머신:
  - 로딩: CircularProgressIndicator 28dp / 두께 3dp
  - 성공: 비트맵 표시
  - 템플릿 렌더 실패 → **원본 사진 폴백** 표시 + 좌하단 배지(margin 8dp): radius small(8dp), scrim 55% 배경, padding 8/4dp, labelSmall 흰색 `원본 표시 중`
  - 완전 실패: BrokenImage 아이콘 28dp + `미리보기 생성 실패` (bodySmall) + TextButton [Refresh 16dp + ` 다시 시도`] → 재시도 카운터 증가로 재렌더
- 웹 구현: 렌더러를 `<canvas>`로 이식, OffscreenCanvas + blob URL 캐시 권장.

### 풀스크린 확대 다이얼로그 (FullscreenPreviewDialog + ZoomableRenderedPreview)
- 전체 화면 Dialog, 배경 투명 + **딤 0.45** (웹: `rgba(0,0,0,.45)` 오버레이; 원본은 앱 컨텐츠 전체 blur도 앱 레벨에서 적용).
- **HorizontalPager**: 사진 리스트 전체를 좌우 스와이프 페이지로 (초기 페이지 = 클릭한 사진). **줌 중(scale>1.01)에는 페이저 스와이프 잠금.** 웹: scroll-snap 캐러셀 or pointer 스와이프, zoomed 플래그로 비활성.
- 상단 오버레이 Row (status bar 패딩 + padding 12/8dp, 양끝):
  - `${page+1} / ${total} · ${파일명}` (titleMedium SemiBold, **흰색**, 1줄 말줄임)
  - ✕ IconButton (흰색, aria `닫기`) → 닫기
- 페이지 콘텐츠: 실제 렌더러로 **긴 변 2048px** 재렌더 (`renderPreviewBitmap(id, 2048)`), 실패 시 원본 폴백, 그것도 실패 시 흰 텍스트 `미리보기를 만들 수 없습니다.` (로딩: 흰색 스피너; 기타 실패 문구 `미리보기 실패`).

#### 줌/팬 제스처 수학 (웹 1:1 이식 필수)
- 상태: `scale`(기본 1), `offset`(px, 기본 0,0), 컨테이너 크기.
- **핀치(2포인터)**: `nextScale = clamp(scale × zoomChange, 1, 5)` (`MAX_PREVIEW_SCALE = 5`); 팬 동시 반영.
- **1포인터 드래그**: scale > 1.01일 때만 팬.
- **스냅백**: `scale ≤ 1.01`이면 scale=1, offset=(0,0) 강제.
- **팬 클램프**: `maxX = width×(scale−1)/2`, `maxY = height×(scale−1)/2`; offset을 ±max로 coerce.
- **더블탭**: 줌 상태면 (1, 0,0)으로 리셋, 아니면 **scale 2.4**, offset (0,0).
- 렌더 변환: `visualScale = scale × 1.003` (`PREVIEW_EDGE_COVER_SCALE` — 가장자리 미세 틈 방지), CSS `transform: translate(offsetX, offsetY) scale(visualScale)`; 이미지 자체는 컨테이너에 contain-fit.
- 사진/renderKey 변경 시 scale·offset 리셋.

---

## Android 전용 → 웹 대체안

| Android | 위치 | 웹 대체 |
|---|---|---|
| Photo Picker (`PickMultipleVisualMedia`, max 60) | PhotoPicker, BuildInfo 로고 업로드 | `<input type=file accept="image/*" multiple>` + 60장 상한 |
| Coil `AsyncImage` + content URI | 썸네일 전부 | `URL.createObjectURL` + `<img loading=lazy>` |
| MediaStore 저장 (`Pictures/{dir}`, WEBP 품질 N%) | Export 저장 | `canvas.toBlob('image/webp', q)` → `<a download>` (단건) / 다중은 순차 다운로드 또는 JSZip; 헤더 문구 `Pictures/…`는 `다운로드 폴더`로 치환 |
| Sharesheet(공유) → `state.shareMessage` 배너 | PhotoPicker 배너 | `navigator.share({files})` (지원 시), 미지원 폴백 = 다운로드 |
| ModalBottomSheet | 프리셋 피커, 검색 피커 | 하단 고정 시트 + 스크림, `<dialog>` 기반 |
| HorizontalPager | 풀스크린 미리보기 | scroll-snap 캐러셀 / pointer 스와이프 |
| combinedClickable 롱프레스 | Export 카드 다중선택 진입 | pointerdown 500ms 홀드 타이머 (+`contextmenu` 억제) |
| 창 딤 `dimAmount 0.45` | 풀스크린 다이얼로그 | 오버레이 `rgba(0,0,0,.45)` |
| Bitmap recycle / OOM 처리 | 미리보기 | blob URL revoke, 캐시 상한 |

---

## 한국어 UI 문자열 인벤토리 (112개)

PhotoPicker (13): `사진 추가` `전체 삭제` `취소` `확인` `사진을 모두 지울까요?` `업로드한 사진 {N}장을 Keyxif 작업 목록에서 모두 제거합니다.` `빌드 정보 미입력` `위로 이동` `아래로 이동` `삭제` `키보드 사진으로 시작하세요` `사진을 고르고 빌드 정보를 입력하면\n스펙 카드가 함께 담긴 이미지로 저장됩니다.` `사진 선택하기`

BuildInfo (41): `편집 중인 사진` `이 빌드 정보를 모든 사진에 적용` `빌드 정보 초기화` `빌드 프리셋` `현재 적용: {name}` `현재 적용된 프리셋 없음` `빌드 프리셋 불러오기` `최근 프리셋` `전체 보기` `저장된 프리셋이 없습니다.` `새 프리셋 이름` `비우면 자동 생성` `저장` `빌드 정보` `최근 사용한 하우징` `하우징` `하우징 검색 또는 직접 입력` `최근 사용한 스위치` `스위치` `스위치 검색 또는 직접 입력` `최근 사용한 키캡` `키캡` `키캡 검색 또는 직접 입력` `보강판` `마운트` `닉네임과 로고` `최근 사용한 닉네임` `닉네임` `입력한 경우에만 표시합니다` `로고 없음` `자동` `로고 업로드` `사용자 로고` `사용자 로고 미리보기` `프리셋 검색` `검색 결과가 없습니다.` `적용` `{name} 삭제` `프리셋을 삭제할까요?` `"{name}" 프리셋을 삭제합니다. 이 작업은 되돌릴 수 없습니다.` `세부 정보 없음`

Template 화면+카드 (28): `템플릿` `편집 중: {label}` `사진을 가리지 않는 카드 스타일을 선택하세요.` `선택된 템플릿` + 템플릿 이름 12개 + 설명 12개 (위 표)

Export (14): `미리보기` `WEBP 품질 {N}% · Pictures/{dir}` `{c} / {t} · 성공 {s} · 실패 {f}` `{N}장 선택됨` `선택 해제` `선택됨` `이 사진 저장` `저장 대기` `저장 중` `저장 완료` `실패` `미리보기를 만들 수 없습니다.` `미리보기 실패` `닫기`

공통 컴포넌트 (12): `입력 내용 지우기` `검색` `{label} 선택` `{label} 검색` `직접 입력값 사용` `최근 사용 / 내장 목록` `최근 항목 삭제` `사용` `선택` `원본 표시 중` `미리보기 생성 실패` `다시 시도`

데이터/부제 (2): `최근 사용` `앱 지원`
네비 단계 (2 신규): `사진` `정보` (+ `템플릿` `저장`은 중복)
