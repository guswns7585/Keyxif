# Keyxif 커스텀 템플릿 에디터 단계 0 분석 보고서

작성일: 2026-07-17
범위: Android 앱과 동기화 대상 Web 앱
결론: 단계 1 착수에 필요한 구조 분석 완료. 커스텀 에디터 구현 코드는 변경하지 않음.

## 1. 템플릿 선택 화면과 관련 컴포넌트

### Android

- `ui/screens/TemplateSelectScreen.kt`
  - `CardTemplate.entries`를 `LazyColumn`으로 순회한다.
  - 선택 결과는 `(CardTemplate) -> Unit`으로 `KeyxifViewModel.selectTemplate`에 전달한다.
- `ui/components/TemplatePreviewCard.kt`
  - 실제 사진 렌더러가 아니라 Compose `Canvas`로 템플릿별 축약 도형을 직접 그리는 선택 카드다.
  - 따라서 커스텀 템플릿 썸네일은 이 컴포넌트의 `when (CardTemplate)`에 추가하는 방식보다 별도 썸네일 경로가 필요하다.
- `ui/KeyxifApp.kt`
  - `AppStep.Template`에서 `TemplateSelectScreen`을 열고, 다음 단계는 `Export`다.

### Web

- `ui-main.js`가 템플릿 선택 단계와 카드 UI를 구성한다.
- 템플릿 목록과 이름은 `store.js`의 `CARD_TEMPLATES`, `TEMPLATE_NAME`, `TEMPLATE_DESC` 상수에 고정되어 있다.
- Android와 마찬가지로 기본 템플릿 ID 목록에 의존하므로 커스텀 템플릿 목록은 별도 데이터 컬렉션으로 합성해야 한다.

## 2. 현재 템플릿 데이터 모델

### Android

- `domain/model/Models.kt`의 `CardTemplate` enum이 기본 템플릿 ID다.
- 선택 상태는 `KeyxifUiState.selectedTemplate: CardTemplate`다.
- 설정의 기본 템플릿, 드래프트, 내보내기 payload도 모두 enum 이름을 문자열로 저장하고 다시 enum으로 복원한다.
- `domain/renderer/KeyxifCanvasRenderer.kt`는 `Map<CardTemplate, TemplateRenderer>`로 구현체를 찾는다.
- `TemplateRenderer`는 레이아웃 사양, 사진 배치 방식, 배경색, 로고 배경 톤과 `draw`를 제공한다.

### Web

- 문자열 템플릿 ID와 `data/renderers.js`의 렌더러 목록을 사용한다.
- 정적 기본 템플릿이라는 전제는 Android와 동일하다.

### 판단

`CardTemplate`에 사용자 템플릿을 동적으로 추가할 수는 없다. 기본 enum은 그대로 유지하고 다음 두 계층을 분리해야 한다.

- 기본 템플릿: 기존 `CardTemplate` + 기존 렌더러 맵
- 커스텀 템플릿: 버전이 있는 `CustomTemplate` + 전용 공통 렌더러

단계 8에서 목록을 통합할 때만 `BuiltIn(CardTemplate)` 또는 `Custom(id)` 형태의 선택 참조를 도입하는 것이 안전하다.

## 3. 사진 미리보기 렌더링 방식

### Android

- 저장 단계의 실제 미리보기는 `RenderedPreview.kt`가 `KeyxifViewModel.renderPreviewBitmap`을 비동기로 호출한다.
- ViewModel은 최종 저장과 같은 `KeyxifCanvasRenderer.render`를 호출하되 긴 변 길이만 미리보기 제한값으로 낮춘다.
- 실패 시 원본 사진 디코딩 결과를 fallback으로 보여준다.
- 템플릿 선택 카드의 그림은 실제 렌더 결과가 아닌 별도 축약 Canvas다.

### Web

- `store.js`의 `renderPreviewBitmap`과 저장 작업이 모두 `renderPhotoCanvas`를 사용한다.
- `renderPhotoCanvas`는 원본 Blob, 빌드 정보, 색상, 로고 자산을 모은 뒤 `data/renderers.js`의 공통 Canvas 렌더러를 호출한다.

### 통합 원칙

커스텀 에디터 미리보기와 최종 출력은 별도 레이아웃 계산을 가지면 안 된다. 정규화 모델을 픽셀 배치로 바꾸는 순수 계산 결과를 에디터 Canvas와 최종 렌더러가 함께 사용해야 한다.

## 4. 최종 이미지 생성 및 저장 방식

### Android

1. `KeyxifViewModel.enqueueExport`가 선택 사진, 템플릿, 설정의 스냅샷을 `ExportWorkPayload` JSON으로 앱 캐시에 기록한다.
2. `WorkManager`의 `ExportWorker`가 payload를 읽고 사진별 원본 URI를 디코딩한다.
3. `KeyxifCanvasRenderer.render`가 `RenderLayout`을 계산하고 Bitmap Canvas에 배경, 사진, 로고와 템플릿 콘텐츠를 그린다.
4. `ImageExporter`가 WEBP 또는 PNG를 `MediaStore`의 `Pictures/Keyxif` 계열 폴더에 저장한다.
5. `ExportedImageRepository`가 결과 URI와 메타데이터를 DataStore에 기록한다.

### Web

1. `enqueueExport`가 작업 시점의 사진, 빌드 정보, 렌더 스타일을 복사한다.
2. `renderPhotoCanvas`가 Canvas를 만든다.
3. Canvas를 WEBP/PNG Blob으로 변환해 다운로드하고 IndexedDB `exported` 저장소에 Blob과 메타데이터를 기록한다.

## 5. 색상 단계가 템플릿에 적용되는 방식

- 색상 분석 결과는 사진별 `PhotoAnalysisResult.paletteColors`에 저장된다.
- 사용자의 사진별 선택은 `PhotoRenderStyle`에 저장된다.
  - 카드 배경 사용 여부, 팔레트 인덱스, 사용자 지정 카드색
  - 텍스트색 사용 여부, 팔레트 인덱스, 사용자 지정 텍스트색
- Android `KeyxifCanvasRenderer`와 Web `render`가 최종 배경색과 콘텐츠색을 계산해 `RenderAssets`로 템플릿 렌더러에 전달한다.
- 색상칩은 템플릿 렌더러가 `visiblePaletteColors`를 사용해 그린다.
- 커스텀 템플릿은 단계 3에서 `colorSlotId`를 모델에만 준비하고, 실제 색상 단계 UI 연결은 계획대로 단계 8에서 수행하는 것이 적절하다.

## 6. board, switch, plate, mount, nickname, logo image 데이터 흐름

### 공통 빌드 정보

- 원본 모델은 사진별 `PhotoItem.buildInfo: KeyboardBuildInfo`다.
- 필드는 내부 호환성을 위해 현재도 `housing`, `switchName`, `plate`, `mount`, `keycap`, `nickname` 이름을 사용한다.
- 화면과 템플릿 표시 레이블은 `BuildInfoDisplay.kt`를 거쳐 `housing`을 `BOARD`로 표시한다.
- 계획 문서의 `board` 바인딩은 저장 모델에서 `housing` 필드에 대응시켜야 하며, 기존 영속 데이터 호환을 위해 필드명을 즉시 변경하면 안 된다.

### 로고

- `KeyboardBuildInfo`가 `logoId`, `customLogoUri`, `logoDisabled`를 가진다.
- `PresetRepository`가 하우징 벤더와 로고 ID를 연결하고, `LogoRenderResolver`가 배경 톤에 맞는 기본/흑/백 자산을 고른다.
- 사용자 로고는 URI에서 Bitmap/Image로 로드한다.
- 최종 렌더러에는 이미지 변형과 라벨을 포함한 `RenderAssets`가 전달된다.

### Web 차이

- 사용자 사진과 사용자 로고 Blob은 IndexedDB `sources`에 저장하고 상태에는 source ID를 URI처럼 보관한다.
- 기본 로고는 생성된 자산 목록과 `assets/` PNG를 사용한다.

## 7. 커스텀 템플릿 저장에 사용할 수 있는 기존 구조

### Android

- 소량 설정: Preferences DataStore
- 버전형 JSON 문서: `DraftSessionRepository`, `ExportWorkPayloadCodec` 패턴
- 간단한 사용자 목록: `BuildPresetRepository`의 SharedPreferences JSON
- 이미지/썸네일: 앱 내부 파일 + JSON에서 상대 파일명 참조 방식이 적합
- 새 수동 백업은 설정, 프리셋, 최근 내역, 완성 이미지를 ZIP으로 내보내므로 향후 커스텀 템플릿 JSON과 썸네일도 같은 백업 형식에 추가할 수 있다.

권장 저장소는 `CustomTemplateRepository` 하나로 분리하고, DataStore Preferences 안에 큰 목록을 계속 누적하기보다 앱 내부의 원자적 JSON 파일과 썸네일 디렉터리를 사용한다. 임시 파일에 쓴 뒤 rename하는 방식으로 손상 위험을 줄여야 한다.

### Web

- 설정, 최근 내역, 빌드 프리셋은 localStorage JSON이다.
- 사진 원본과 완성 이미지는 IndexedDB다.
- 커스텀 템플릿은 크기 증가와 썸네일 Blob을 고려해 IndexedDB 버전을 올리고 `customTemplates` object store를 추가하는 것이 적합하다. localStorage 목록에는 넣지 않는다.

## 8. 수정 예상 파일 목록

### 단계 1 필수

- `android/app/src/main/java/com/keyxif/app/domain/model/Models.kt` 또는 새 `domain/model/CustomTemplateModels.kt`
- `android/app/src/main/java/com/keyxif/app/ui/KeyxifViewModel.kt`
- `android/app/src/main/java/com/keyxif/app/ui/KeyxifApp.kt`
- `android/app/src/main/java/com/keyxif/app/ui/screens/TemplateSelectScreen.kt`
- 새 `android/app/src/main/java/com/keyxif/app/ui/screens/CustomTemplateEditorScreen.kt`
- 새 `android/app/src/main/java/com/keyxif/app/ui/components/CustomTemplateCanvas.kt`
- `web/store.js`
- `web/ui-main.js`
- 새 `web/ui-template-editor.js`
- `web/index.html` 및 `web/scripts/build-static.mjs`의 새 스크립트 포함 목록

### 후속 단계 예상

- 새 Android `data/repository/CustomTemplateRepository.kt`
- 새 Android `domain/renderer/CustomTemplateLayout.kt`, `CustomTemplateRenderer.kt`
- `KeyxifCanvasRenderer.kt`, `ExportWorkPayload.kt`, `DraftSessionRepository.kt`
- `PaletteScreen.kt`, `ExportScreen.kt`, 백업 manifest 처리
- Web `db.js`, `data/renderers.js`, `ui-settings.js`, 백업 처리
- Android 및 Web의 좌표 변환, 경계 검증, 직렬화 단위 테스트

기존 기본 템플릿 렌더러 파일은 공통 유틸리티 호출 외에는 변경을 최소화한다.

## 9. 권장 데이터 모델과 상태 관리 방식

### 영속 모델

- 계획의 `CustomTemplate`, `BackgroundFrame`, `CanvasElement`, `InternalCard`, `CardStyle` 개념은 유지한다.
- 시간은 플랫폼 공통 교환을 위해 epoch millisecond 또는 ISO-8601 중 하나로 통일한다. 현재 코드와 맞추려면 epoch millisecond가 단순하다.
- 좌표와 크기는 모두 컨테이너 기준 0~1 값으로 저장한다.
- `fitMode`는 초기 버전에서 반드시 `contain`만 허용한다.
- `templateVersion`과 별도로 저장 문서 전체의 `schemaVersion`을 두는 것이 좋다.
- `width/height/scale/aspectRatio`처럼 서로 계산 가능한 값을 동시에 수정 가능한 상태로 두지 않는다. 사진 배치는 정규화 rect와 읽기 전용 원본 종횡비를 기준으로 정하고 scale은 계산값으로 취급한다.
- `containerId`와 `coordinateSpace`는 저장 전 일치성을 검증한다.
- `board` 바인딩은 직렬화 키로 사용하되 현재 `KeyboardBuildInfo.housing`에서 값을 공급한다.

### 편집 상태

- 저장 모델과 UI 상태를 분리한다.
- `CustomTemplateEditorState`는 draft, 선택 요소 ID, 활성 탭, undo/redo 스택, dirty 여부와 검증 오류를 가진다.
- 포인터 이동 중에는 일시 transform만 갱신하고 pointer-up에서 한 번만 undo 명령을 기록한다.
- Android는 ViewModel의 별도 immutable editor state, Web은 store의 별도 editor branch를 사용한다.
- 화면 크기와 픽셀 좌표는 영속 모델에 저장하지 않는다.
- 단계 1에서는 편집 draft를 메모리에만 두고, 영속 저장은 단계 8 전까지 구현하지 않는다.

## 10. 기존 기능에 영향을 줄 수 있는 위험 요소

1. `CardTemplate` enum 변경은 설정, 드래프트, 내보내기 payload 디코딩 전체에 영향을 준다. 기본 enum은 유지한다.
2. 현재 일부 기본 템플릿은 `CenterCrop`이다. “원본 사진 크롭 금지”는 커스텀 렌더러에 강제하고 기본 템플릿 동작을 일괄 변경하지 않는다.
3. 템플릿 선택 카드와 실제 렌더 미리보기는 현재 서로 다른 코드다. 커스텀 썸네일을 수작업 Canvas로 복제하면 불일치가 생기므로 공통 렌더 결과로 만든다.
4. 미리보기와 저장은 해상도가 다르다. 텍스트 측정, 최소 크기, stroke, radius를 논리 단위에서 출력 배율로 변환해야 한다.
5. Android와 Web의 글꼴 측정 결과는 완전히 같지 않다. 줄바꿈 결과를 저장하지 말고 동일 규칙과 허용 오차를 테스트한다.
6. 사용자 로고 URI는 앱 삭제 후 무효가 될 수 있다. 커스텀 템플릿이 사용자 로고 파일을 소유해야 한다면 내부 복사와 백업 포함이 필요하다.
7. 큰 undo 스냅샷, 썸네일, 템플릿 JSON을 기본 앱 상태에 직접 넣으면 메모리와 자동 저장 비용이 커진다.
8. Web IndexedDB 스키마 버전 변경은 기존 `sources`와 `exported` store를 보존하는 upgrade 코드가 필요하다.
9. 프레임 요소의 사진 침범 여부는 사각형 네 영역으로 단순화할 수 없다. frame rect에서 photo safe rect와 교차하는지 기하 검증해야 한다.
10. 카드 삭제와 요소 삭제는 하나의 원자적 undo 명령이어야 한다.
11. 커스텀 템플릿을 export payload에 넣을 때 ID만 보내면 백그라운드 작업 중 수정/삭제될 수 있다. 최종 저장 작업에는 템플릿 스냅샷을 넣어야 한다.
12. 백업 형식은 향후 스키마 마이그레이션을 위해 버전을 유지해야 한다.

## 11. 단계 1부터 단계 10까지의 적합성 검토

- 단계 1: 적합. 단, 기본 `CardTemplate` 선택 타입을 바로 교체하지 말고 독립 editor state와 화면 뼈대만 추가한다.
- 단계 2: 적합. Android와 Web에서 같은 입력/출력 계약을 가진 순수 좌표·경계 함수를 먼저 만들고 플랫폼 포인터 UI가 이를 호출해야 한다.
- 단계 3: 적합. `contain` 전용 커스텀 사진 배치와 단일 프레임 구조가 현재 `RenderLayout`과 충돌하지 않도록 별도 계산기를 둔다.
- 단계 4: 적합. `board`는 현재 내부 `housing` 값에 매핑하고 로고 자산 해석은 기존 `PresetRepository`/웹 자산 해석을 재사용한다.
- 단계 5: 적합. 카드 clipping은 Canvas save/clip/restore 계층으로 구현한다.
- 단계 6: 적합. 스냅과 충돌 검증은 UI 밖 순수 함수여야 한다.
- 단계 7: 적합. Android Paint와 Web Canvas의 측정 차이를 허용하는 플랫폼별 측정 어댑터가 필요하다.
- 단계 8: 적합. 이 단계에서 처음 `CustomTemplateRepository`, IndexedDB store, 목록 통합과 색상 슬롯 연결을 완성한다.
- 단계 9: 적합. 내보내기 payload에는 커스텀 템플릿 전체 스냅샷을 포함하고 기본 렌더러 경로는 그대로 둔다.
- 단계 10: 적합. 성능 최적화 전에 단계 2~9의 회귀 테스트가 있어야 한다.

전체 순서는 현재 프로젝트에 적합하다. 단계 간 범위를 합치거나 앞당길 필요는 없다. 다만 단계 1에서 모델의 중복 필드를 정리하고 기본 템플릿과 커스텀 템플릿의 타입 경계를 확정해야 이후 마이그레이션 비용을 줄일 수 있다.

## 12. 단계 1 착수 시 확정된 통합 방침

- 기본 템플릿 enum과 렌더러는 변경하지 않는다.
- 커스텀 템플릿 모델과 편집 화면은 독립적으로 추가한다.
- 빈 템플릿은 논리 프레임, contain 사진 배치, 빈 요소/카드 목록을 가진다.
- 단계 1에서는 드래그, 요소 추가, 저장, 최종 출력 연결을 구현하지 않는다.
- Android와 Web에 동일한 필드와 탭 구조를 추가하되 플랫폼 UI 구현은 각각의 기존 패턴을 따른다.
- 단계 1은 사용자의 명시적인 다음 요청 전에는 시작하지 않는다.
