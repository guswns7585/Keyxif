/* =============================================================================
   Keyxif Web — ui-settings.js
   SettingsScreen.kt 1:1 웹 포트 (gallery-settings-spec.md §2)
   window.KeyxifUI.renderSettings(container, state, actions, helpers)
   - 2단계 내비게이션: state.settingsPageName (null=루트)
   - Update 페이지는 WEB-OMIT (루트 메뉴 카드 자체 제거)
   ============================================================================= */
(function () {
  'use strict';

  window.KeyxifUI = window.KeyxifUI || {};

  /* ---- 모듈 로컬 UI 상태 (재렌더에도 유지) ---- */
  var confirmClearOpen = false; // WEB-ADD: 목록 기록 초기화 확인 다이얼로그
  var lastPageName = null;
  var uid = 0;

  /* ---- 루트 메뉴 (spec 2.2 순서 고정, Update WEB-OMIT) ---- */
  var PAGES = [
    { name: 'Output', ko: '출력 설정', en: 'Output', dko: '품질, 해상도, 파일명', den: 'Quality, resolution, file names' },
    { name: 'Display', ko: '표시 설정', en: 'Display', dko: '언어, 테마, 텍스트, 색상칩', den: 'Language, theme, text, palette' },
    { name: 'Save', ko: '저장 설정', en: 'Save', dko: '저장 후 동작과 미리보기', den: 'After-save behavior and preview' },
    { name: 'Edit', ko: '편집 설정', en: 'Editing', dko: '최근값, 기본 입력 동작', den: 'Recent values and defaults' },
    { name: 'Session', ko: '세션 설정', en: 'Session', dko: '작업 임시 저장과 복구', den: 'Draft save and restore' },
    { name: 'Template', ko: '템플릿 설정', en: 'Templates', dko: '기본 템플릿과 오버레이', den: 'Defaults and overlays' },
    { name: 'Gallery', ko: '완성 이미지 관리', en: 'Finished Images', dko: '저장 목록 정리', den: 'Saved image list' },
    { name: 'About', ko: '앱 정보', en: 'About', dko: '버전과 개인정보 처리', den: 'Version and privacy' },
    { name: 'Developer', ko: '개발 정보', en: 'Developer', dko: '문의와 기술 정보', den: 'Contact and technical info' },
  ];

  function pageTitle(name, text) {
    for (var i = 0; i < PAGES.length; i++) {
      if (PAGES[i].name === name) return text(PAGES[i].ko, PAGES[i].en);
    }
    return name;
  }

  /* ---- 현재 기기 UA 요약 (Developer §2.12) ---- */
  function uaSummary() {
    var ua = navigator.userAgent || '';
    var os = 'Unknown OS';
    if (/Windows NT/.test(ua)) os = 'Windows';
    else if (/Android/.test(ua)) os = 'Android';
    else if (/iPhone|iPad|iPod/.test(ua)) os = 'iOS';
    else if (/Mac OS X/.test(ua)) os = 'macOS';
    else if (/Linux/.test(ua)) os = 'Linux';
    var browser = 'Browser';
    var m;
    if ((m = ua.match(/Edg\/(\d+)/))) browser = 'Edge ' + m[1];
    else if ((m = ua.match(/OPR\/(\d+)/))) browser = 'Opera ' + m[1];
    else if ((m = ua.match(/Chrome\/(\d+)/))) browser = 'Chrome ' + m[1];
    else if ((m = ua.match(/Firefox\/(\d+)/))) browser = 'Firefox ' + m[1];
    else if ((m = ua.match(/Version\/(\d+)[^)]*Safari/))) browser = 'Safari ' + m[1];
    return os + ' · ' + browser;
  }

  window.KeyxifUI.renderSettings = function (container, state, actions, helpers) {
    var esc = helpers.esc;
    var text = helpers.text;
    var C = window.KeyxifStore.consts;
    var s = state.settings;
    var binds = [];

    /* 페이지 이동 시 로컬 다이얼로그 상태 초기화 */
    if (state.settingsPageName !== lastPageName) {
      lastPageName = state.settingsPageName;
      confirmClearOpen = false;
    }

    function rerender() {
      window.KeyxifUI.renderSettings(container, window.KeyxifStore.getState(), actions, helpers);
    }
    function up(mut) {
      actions.updateSettings(function (st) { mut(st); return st; });
    }

    /* ------------------------------------------------------------------ */
    /* 공용 빌더 (spec 2.1)                                                 */
    /* ------------------------------------------------------------------ */

    function chipsHtml(opts) { // { chips:[{label,value,selected,disabled}], disabled, onSelect }
      var id = 'cr-' + (uid++);
      var h = '<div class="chip-row" data-cr="' + id + '">';
      opts.chips.forEach(function (c, i) {
        h += '<button type="button" class="chip' + (c.selected ? ' selected' : '') + '"' +
          ((c.disabled || opts.disabled) ? ' disabled' : '') +
          ' data-i="' + i + '"><span>' + esc(c.label) + '</span></button>';
      });
      h += '</div>';
      binds.push(function (root) {
        var row = root.querySelector('[data-cr="' + id + '"]');
        if (!row) return;
        Array.prototype.forEach.call(row.querySelectorAll('.chip'), function (btn) {
          btn.addEventListener('click', function () {
            opts.onSelect(opts.chips[Number(btn.getAttribute('data-i'))].value);
          });
        });
      });
      return h;
    }

    function chipRow(opts) { // chipsHtml + 상단 라벨
      var h = '<div class="col gap8">';
      if (opts.label) h += '<div class="body-large">' + esc(opts.label) + '</div>';
      h += chipsHtml(opts) + '</div>';
      return h;
    }

    function toggleRow(opts) { // { title, desc, checked, onChange }
      var id = 'sw-' + (uid++);
      // 안정 키(제목 기반)로 커밋 재렌더 후에도 포커스 복원 — main.js restoreFocus가 [data-key]를 찾음
      var stableKey = 'set-sw-' + String(opts.title).replace(/[^a-z0-9가-힣]+/gi, '');
      binds.push(function (root) {
        var input = root.querySelector('[data-sw="' + id + '"] input');
        if (input) input.addEventListener('change', function () { opts.onChange(input.checked); });
      });
      return '<div class="toggle-row">' +
        '<div class="tr-body">' +
        '<div class="body-large">' + esc(opts.title) + '</div>' +
        (opts.desc ? '<div class="body-small muted">' + esc(opts.desc) + '</div>' : '') +
        '</div>' +
        '<label class="switch" data-sw="' + id + '">' +
        '<input type="checkbox" data-key="' + stableKey + '"' + (opts.checked ? ' checked' : '') + '>' +
        '<span class="track"></span><span class="thumb"></span>' +
        '</label></div>';
    }

    /* 슬라이더: input=라벨만 로컬 갱신, change=스토어 커밋 */
    function sliderControl(opts) { // { key,value,min,max,step,labelId,labelFor,onCommit,disabled }
      var id = 'sl-' + (uid++);
      binds.push(function (root) {
        var input = root.querySelector('[data-sl="' + id + '"]');
        if (!input) return;
        var label = opts.labelId ? root.querySelector('[data-sllabel="' + opts.labelId + '"]') : null;
        input.addEventListener('input', function () {
          if (label && opts.labelFor) label.textContent = opts.labelFor(Number(input.value));
        });
        input.addEventListener('change', function () { opts.onCommit(Number(input.value)); });
      });
      return '<input type="range" data-sl="' + id + '"' +
        (opts.key ? ' data-key="set-sl-' + opts.key + '"' : '') +
        ' min="' + opts.min + '" max="' + opts.max +
        '" step="' + opts.step + '" value="' + opts.value + '"' + (opts.disabled ? ' disabled' : '') + '>';
    }

    function sliderRow(opts) {
      var labelId = 'sll-' + (uid++);
      opts.labelId = labelId;
      return '<div class="col gap6">' +
        '<div class="body-large" data-sllabel="' + labelId + '">' + esc(opts.labelFor(opts.value)) + '</div>' +
        sliderControl(opts) + '</div>';
    }

    function infoRow(label, value) {
      return '<div class="info-row"><div class="label-medium muted">' + esc(label) + '</div>' +
        '<div class="body-medium">' + esc(value) + '</div></div>';
    }

    function actionButton(opts) { // { label, cls, style, tag, href, onClick }
      var id = 'act-' + (uid++);
      var tag = opts.tag || 'button';
      binds.push(function (root) {
        var el = root.querySelector('[data-act="' + id + '"]');
        if (el) el.addEventListener('click', function (ev) { ev.preventDefault(); opts.onClick(); });
      });
      return '<' + tag + (tag === 'a' ? ' href="' + esc(opts.href || '#') + '"' : ' type="button"') +
        ' class="' + opts.cls + '"' + (opts.style ? ' style="' + opts.style + '"' : '') +
        ' data-act="' + id + '">' + esc(opts.label) + '</' + tag + '>';
    }

    function section(title, rows) {
      return '<div class="card-outlined" style="padding:14px;display:flex;flex-direction:column;gap:12px">' +
        '<div class="title-medium" style="font-weight:700">' + esc(title) + '</div>' +
        rows.join('') + '</div>';
    }

    var DIVIDER = '<hr class="divider">';

    /* ------------------------------------------------------------------ */
    /* 출력 설정 (spec 2.3 — 6행)                                           */
    /* ------------------------------------------------------------------ */

    function pageOutput() {
      var rows = [];
      var presetKeys = ['HighCompression', 'Recommended', 'Balanced', 'HighQuality', 'Maximum'];
      // 1. 추천 품질 — Custom은 칩 미노출(어떤 칩도 선택 안 됨)
      rows.push(chipRow({
        label: '추천 품질',
        chips: presetKeys.map(function (k) {
          return { label: C.QUALITY_PRESET_NAME[k], value: k, selected: s.qualityPreset === k };
        }),
        onSelect: function (k) {
          up(function (st) {
            var p = C.QUALITY_PRESETS[k];
            st.webpQuality = p.webpQuality;
            st.keepOriginalResolution = p.keepOriginalResolution;
            st.maxLongSidePx = p.maxLongSidePx;
            st.qualityPreset = k;
          });
        },
      }));
      // 2. WEBP 품질 슬라이더 (70–100)
      rows.push(sliderRow({
        key: 'webpQuality',
        value: s.webpQuality, min: 70, max: 100, step: 1,
        labelFor: function (v) { return 'WEBP 품질 ' + Math.round(v) + '%'; },
        onCommit: function (v) {
          up(function (st) { st.webpQuality = Math.round(v); st.qualityPreset = 'Custom'; });
        },
      }));
      // 3. 출력 형식 — PNG 칩은 disabled
      rows.push(chipRow({
        label: '출력 형식',
        chips: [
          { label: 'WEBP', value: 'WEBP', selected: s.outputFormat === 'WEBP' },
          { label: 'PNG', value: 'PNG', selected: s.outputFormat === 'PNG', disabled: true },
        ],
        onSelect: function (v) { up(function (st) { st.outputFormat = v; }); },
      }));
      // 4. 원본 해상도 유지
      rows.push(toggleRow({
        title: '원본 해상도 유지',
        desc: '켜면 가능한 원본 크기로 저장합니다.',
        checked: s.keepOriginalResolution,
        onChange: function (on) {
          up(function (st) { st.keepOriginalResolution = on; st.qualityPreset = 'Custom'; });
        },
      }));
      // 5. 최대 긴 변 제한 — keepOriginalResolution이면 전체 disabled
      rows.push(chipRow({
        label: '최대 긴 변 제한',
        disabled: s.keepOriginalResolution,
        chips: [
          { label: '원본', value: null, selected: s.maxLongSidePx === null },
          { label: '2048px', value: 2048, selected: s.maxLongSidePx === 2048 },
          { label: '2400px', value: 2400, selected: s.maxLongSidePx === 2400 },
          { label: '3000px', value: 3000, selected: s.maxLongSidePx === 3000 },
          { label: '4000px', value: 4000, selected: s.maxLongSidePx === 4000 },
        ],
        onSelect: function (v) {
          up(function (st) { st.maxLongSidePx = v; st.qualityPreset = 'Custom'; });
        },
      }));
      // 6. 저장 파일명 규칙
      rows.push(chipRow({
        label: '저장 파일명 규칙',
        chips: Object.keys(C.FILE_NAME_RULE_NAME).map(function (k) {
          return { label: C.FILE_NAME_RULE_NAME[k], value: k, selected: s.fileNameRule === k };
        }),
        onSelect: function (v) { up(function (st) { st.fileNameRule = v; }); },
      }));
      return section(pageTitle('Output', text), rows);
    }

    /* ------------------------------------------------------------------ */
    /* 표시 설정 (spec 2.4 — 10행 + 구분선 2)                               */
    /* ------------------------------------------------------------------ */

    function pageDisplay() {
      var rows = [];
      // 1. 언어
      rows.push(chipRow({
        label: text('언어', 'Language'),
        chips: [
          { label: text('시스템', 'System'), value: 'System', selected: s.languageMode === 'System' },
          { label: '한국어', value: 'Korean', selected: s.languageMode === 'Korean' },
          { label: 'English', value: 'English', selected: s.languageMode === 'English' },
        ],
        onSelect: function (v) { up(function (st) { st.languageMode = v; }); },
      }));
      // 2. 화면 모드
      rows.push(chipRow({
        label: text('화면 모드', 'Appearance'),
        chips: [
          { label: text('시스템', 'System'), value: 'System', selected: s.themeMode === 'System' },
          { label: text('라이트', 'Light'), value: 'Light', selected: s.themeMode === 'Light' },
          { label: text('다크', 'Dark'), value: 'Dark', selected: s.themeMode === 'Dark' },
        ],
        onSelect: function (v) { up(function (st) { st.themeMode = v; }); },
      }));
      rows.push(DIVIDER);
      // 3. 텍스트 크기 — 프리셋 칩(정확 일치 시에만 선택) + 슬라이더, 라벨 공유
      var tsLabelId = 'sll-' + (uid++);
      rows.push(
        '<div class="col gap8">' +
        '<div class="body-large" data-sllabel="' + tsLabelId + '">' +
        esc('텍스트 크기 ' + s.textScale.toFixed(2) + 'x') + '</div>' +
        chipsHtml({
          chips: [
            { label: '작게', value: 0.9 },
            { label: '기본', value: 1.0 },
            { label: '크게', value: 1.15 },
            { label: '매우 크게', value: 1.3 },
          ].map(function (c) {
            c.selected = Math.abs(s.textScale - c.value) < 0.001;
            return c;
          }),
          onSelect: function (v) { up(function (st) { st.textScale = v; }); },
        }) +
        sliderControl({
          key: 'textScale',
          value: s.textScale, min: 0.85, max: 1.35, step: 0.01, labelId: tsLabelId,
          labelFor: function (v) { return '텍스트 크기 ' + v.toFixed(2) + 'x'; },
          onCommit: function (v) { up(function (st) { st.textScale = v; }); },
        }) +
        '</div>'
      );
      // 4. 닉네임 표시 스타일
      rows.push(chipRow({
        label: '닉네임 표시 스타일',
        chips: [
          { label: '그대로', value: 'Plain', selected: s.nicknameStyle === 'Plain' },
          { label: '@닉네임', value: 'AtPrefix', selected: s.nicknameStyle === 'AtPrefix' },
          { label: 'Credit', value: 'Credit', selected: s.nicknameStyle === 'Credit' },
        ],
        onSelect: function (v) { up(function (st) { st.nicknameStyle = v; }); },
      }));
      // 5. 닉네임 강조
      rows.push(sliderRow({
        key: 'nicknameEmphasis',
        value: s.nicknameEmphasis, min: 0.9, max: 1.35, step: 0.01,
        labelFor: function (v) { return '닉네임 강조 ' + v.toFixed(2) + 'x'; },
        onCommit: function (v) { up(function (st) { st.nicknameEmphasis = v; }); },
      }));
      rows.push(DIVIDER);
      // 6. 대표 색상 표시
      rows.push(toggleRow({
        title: '대표 색상 표시',
        desc: '사진에서 추출한 색상 팔레트를 템플릿에 표시합니다.',
        checked: s.showPaletteColors,
        onChange: function (on) { up(function (st) { st.showPaletteColors = on; }); },
      }));
      // 7. 대표 색상 개수
      rows.push(chipRow({
        label: '대표 색상 개수',
        chips: [3, 4, 5].map(function (n) {
          return { label: n + '개', value: n, selected: s.paletteColorCount === n };
        }),
        onSelect: function (v) { up(function (st) { st.paletteColorCount = v; }); },
      }));
      // 분석 방식과 영역은 편집 흐름의 색상 단계에서 사진별로 설정합니다.
      rows.push(toggleRow({
        title: '로고 자동 대비 선택',
        desc: '템플릿 배경에 따라 밝은/어두운 내장 로고를 자동으로 선택합니다.',
        checked: s.autoSelectLogoContrastVariant,
        onChange: function (on) { up(function (st) { st.autoSelectLogoContrastVariant = on; }); },
      }));
      return section(pageTitle('Display', text), rows);
    }

    /* ------------------------------------------------------------------ */
    /* 저장 설정 (spec 2.5 — 5행)                                           */
    /* ------------------------------------------------------------------ */

    function pageSave() {
      var rows = [];
      rows.push(infoRow('저장 위치', '다운로드 폴더 + 브라우저 저장소'));
      rows.push(toggleRow({
        title: '저장 완료 후 갤러리 열기',
        desc: '저장이 끝나면 완성 이미지 화면을 엽니다.',
        checked: s.openGalleryAfterSave,
        onChange: function (on) { up(function (st) { st.openGalleryAfterSave = on; }); },
      }));
      rows.push(toggleRow({
        title: '저장 완료 메시지 표시',
        desc: '저장 결과를 화면에 표시합니다.',
        checked: s.showSaveToast,
        onChange: function (on) { up(function (st) { st.showSaveToast = on; }); },
      }));
      rows.push(toggleRow({
        title: '전체 저장 중 실패 항목 건너뛰기',
        desc: '끄면 첫 실패에서 전체 저장을 멈춥니다.',
        checked: s.skipFailedOnBatchSave,
        onChange: function (on) { up(function (st) { st.skipFailedOnBatchSave = on; }); },
      }));
      rows.push(toggleRow({
        title: '저장 미리보기 확대 허용',
        desc: '저장 화면에서 렌더 미리보기를 크게 볼 수 있습니다.',
        checked: s.enableExportPreviewZoom,
        onChange: function (on) { up(function (st) { st.enableExportPreviewZoom = on; }); },
      }));
      return section(pageTitle('Save', text), rows);
    }

    /* ------------------------------------------------------------------ */
    /* 편집 설정 (spec 2.6 — 5행)                                           */
    /* ------------------------------------------------------------------ */

    function pageEdit() {
      var rows = [];
      rows.push(toggleRow({
        title: '마지막 사용 템플릿 기억',
        desc: '템플릿을 선택하면 기본 템플릿도 함께 갱신합니다.',
        checked: s.rememberLastTemplate,
        onChange: function (on) { up(function (st) { st.rememberLastTemplate = on; }); },
      }));
      rows.push(toggleRow({
        title: '마지막 닉네임 기억',
        desc: '새 사진 추가 때 최근 닉네임을 제안합니다.',
        checked: s.rememberLastNickname,
        onChange: function (on) { up(function (st) { st.rememberLastNickname = on; }); },
      }));
      rows.push(chipRow({
        label: '최근 사용 입력값 개수',
        chips: [10, 20, 50].map(function (n) {
          return { label: n + '개', value: n, selected: s.recentInputLimit === n };
        }),
        onSelect: function (v) { up(function (st) { st.recentInputLimit = v; }); },
      }));
      rows.push(toggleRow({
        title: '사진 추가 시 이전 빌드 정보 복사',
        desc: '새 사진에 직전 사진의 빌드 정보를 자동으로 넣습니다.',
        checked: s.copyPreviousBuildInfoOnAdd,
        onChange: function (on) { up(function (st) { st.copyPreviousBuildInfoOnAdd = on; }); },
      }));
      rows.push(toggleRow({
        title: '스위치 추천 목록 표시',
        desc: '스위치 입력창에서 내장 프리셋을 함께 보여줍니다.',
        checked: s.showSwitchPresets,
        onChange: function (on) { up(function (st) { st.showSwitchPresets = on; }); },
      }));
      return section(pageTitle('Edit', text), rows);
    }

    /* ------------------------------------------------------------------ */
    /* 세션 설정 (spec 2.7 — 2행)                                           */
    /* ------------------------------------------------------------------ */

    function pageSession() {
      var rows = [];
      rows.push(toggleRow({
        title: '자동 세션 임시 저장',
        desc: '사진 목록, 템플릿, 빌드 정보를 임시 저장합니다.',
        checked: s.autoRestoreDraftSession,
        onChange: function (on) { up(function (st) { st.autoRestoreDraftSession = on; }); },
      }));
      rows.push(toggleRow({
        title: '앱 시작 시 복구 여부 묻기',
        desc: '끄면 임시 저장된 작업을 자동 복구합니다.',
        checked: s.askBeforeRestoreDraft,
        onChange: function (on) { up(function (st) { st.askBeforeRestoreDraft = on; }); },
      }));
      return section(pageTitle('Session', text), rows);
    }

    /* ------------------------------------------------------------------ */
    /* 템플릿 설정 (spec 2.8 — 4행)                                         */
    /* ------------------------------------------------------------------ */

    function pageTemplate() {
      var rows = [];
      rows.push(chipRow({
        label: '기본 템플릿',
        chips: C.CARD_TEMPLATES.map(function (t) {
          return { label: C.TEMPLATE_NAME[t], value: t, selected: s.defaultTemplate === t };
        }),
        onSelect: function (v) { up(function (st) { st.defaultTemplate = v; }); },
      }));
      rows.push(toggleRow({
        title: '템플릿 미리보기에서 실제 사진 사용',
        desc: '끄면 화면 샘플 미리보기 중심으로 봅니다.',
        checked: s.useCurrentPhotoForTemplatePreview,
        onChange: function (on) { up(function (st) { st.useCurrentPhotoForTemplatePreview = on; }); },
      }));
      rows.push(toggleRow({
        title: '이미지 오버레이 중앙 영역 보호',
        desc: '로고와 정보가 사진 중앙을 덮지 않도록 제한합니다.',
        checked: s.protectCenterAreaForOverlay,
        onChange: function (on) { up(function (st) { st.protectCenterAreaForOverlay = on; }); },
      }));
      rows.push(toggleRow({
        title: 'Plain Export에 빌드 정보 표시',
        desc: '켜면 원본형 템플릿에도 입력한 빌드 정보를 함께 표시합니다.',
        checked: s.showBuildInfoInPlainExport,
        onChange: function (on) { up(function (st) { st.showBuildInfoInPlainExport = on; }); },
      }));
      return section(pageTitle('Template', text), rows);
    }

    /* ------------------------------------------------------------------ */
    /* 완성 이미지 관리 (spec 2.10 — 4행 + WEB-ADD 확인 다이얼로그)          */
    /* ------------------------------------------------------------------ */

    function pageGallery() {
      var n = state.exportedImages.length;
      var rows = [];
      rows.push(infoRow('저장된 항목', n + '개'));
      rows.push(actionButton({
        label: '파일 접근 불가 항목 정리',
        cls: 'btn btn-tonal full',
        onClick: function () { actions.pruneMissingExportedImages(); },
      }));
      rows.push('<div class="body-small muted">' +
        esc('목록 기록 초기화는 실제 이미지 파일은 삭제하지 않습니다.') + '</div>');
      rows.push(actionButton({
        label: '목록 기록 초기화',
        cls: 'btn btn-outlined full',
        onClick: function () { confirmClearOpen = true; rerender(); },
      }));
      return section(pageTitle('Gallery', text), rows);
    }

    /* ------------------------------------------------------------------ */
    /* 앱 정보 (spec 2.11 — 4행)                                            */
    /* ------------------------------------------------------------------ */

    function pageAbout() {
      var rows = [];
      rows.push('<div class="headline-small">Keyxif</div>');
      rows.push(infoRow('버전명', helpers.VERSION));
      rows.push(infoRow('오픈소스 라이선스', '준비 중'));
      rows.push(DIVIDER);
      rows.push('<div class="body-medium muted">' +
        esc('사진과 빌드 정보는 브라우저 안에서 처리됩니다. 서버 업로드나 외부 전송 없이, 사용자가 직접 저장한 결과물만 갤러리에 저장됩니다.') +
        '</div>');
      return section(pageTitle('About', text), rows);
    }

    /* ------------------------------------------------------------------ */
    /* 개발 정보 (spec 2.12 — 6행)                                          */
    /* ------------------------------------------------------------------ */

    function pageDeveloper() {
      var rows = [];
      rows.push(infoRow('개발자', 'KGJun'));
      // ActionInfoRow: 문의 — mailto 링크(텍스트 버튼 스타일) → openSupportEmail
      rows.push('<div class="info-row"><div class="label-medium muted">' + esc('문의') + '</div>' +
        actionButton({
          tag: 'a',
          href: 'mailto:typenews902@gmail.com',
          label: 'typenews902@gmail.com',
          cls: 'btn btn-text',
          style: 'align-self:flex-start;height:auto;padding:2px 0;text-decoration:none;',
          onClick: function () { actions.openSupportEmail(); },
        }) +
        '</div>');
      rows.push(infoRow('GitHub', 'guswns7585/keyxif'));
      rows.push(infoRow('기술 정보', 'JavaScript · Canvas · IndexedDB'));
      rows.push(infoRow('이미지 처리', 'In-browser rendering · WEBP Export · Local download'));
      rows.push(infoRow('현재 기기', uaSummary()));
      return section(pageTitle('Developer', text), rows);
    }

    /* ------------------------------------------------------------------ */
    /* 조립                                                                 */
    /* ------------------------------------------------------------------ */

    var html = '<div style="display:flex;flex-direction:column;gap:16px">';

    if (!state.settingsPageName) {
      /* ---- 루트: 서브타이틀 + 메뉴 카드 9개 ---- */
      html += '<div class="body-medium muted">' +
        esc(text('필요한 항목만 열어 조정하세요.', 'Open only the groups you want to adjust.')) + '</div>';
      PAGES.forEach(function (p) {
        html += '<button type="button" class="card-outlined settings-menu-card" data-menu="' + p.name + '">' +
          '<div class="title-medium">' + esc(text(p.ko, p.en)) + '</div>' +
          '<div class="body-small muted">' + esc(text(p.dko, p.den)) + '</div>' +
          '</button>';
      });
      binds.push(function (root) {
        Array.prototype.forEach.call(root.querySelectorAll('[data-menu]'), function (btn) {
          btn.addEventListener('click', function () {
            actions.selectSettingsPage(btn.getAttribute('data-menu'));
          });
        });
      });
    } else {
      /* ---- 페이지 상세: 뒤로 버튼 + 섹션 ---- */
      html += '<div>' + actionButton({
        label: text('< 설정', '< Settings'),
        cls: 'btn btn-text',
        style: 'margin-left:-12px;',
        onClick: function () { actions.selectSettingsPage(null); },
      }) + '</div>';
      switch (state.settingsPageName) {
        case 'Output': html += pageOutput(); break;
        case 'Display': html += pageDisplay(); break;
        case 'Save': html += pageSave(); break;
        case 'Edit': html += pageEdit(); break;
        case 'Session': html += pageSession(); break;
        case 'Template': html += pageTemplate(); break;
        case 'Gallery': html += pageGallery(); break;
        case 'About': html += pageAbout(); break;
        case 'Developer': html += pageDeveloper(); break;
      }
    }

    html += '</div>';

    /* ---- WEB-ADD: 목록 기록 초기화 확인 다이얼로그 ---- */
    if (confirmClearOpen) {
      var count = state.exportedImages.length;
      html += '<div class="dialog-scrim" data-dscrim><div class="dialog">' +
        '<div class="title-large">' + esc('목록 기록을 초기화할까요?') + '</div>' +
        '<div class="body-medium">' +
        esc('완성 이미지 기록 ' + count + '개를 목록에서 제거합니다. 실제 이미지 파일은 삭제하지 않습니다.') +
        '</div>' +
        '<div class="dialog-actions">' +
        '<button type="button" class="btn btn-text" data-clear-cancel>' + esc('취소') + '</button>' +
        '<button type="button" class="btn btn-error" data-clear-confirm>' + esc('초기화') + '</button>' +
        '</div></div></div>';
      binds.push(function (root) {
        var scrim = root.querySelector('[data-dscrim]');
        var cancel = root.querySelector('[data-clear-cancel]');
        var confirm = root.querySelector('[data-clear-confirm]');
        function close() { confirmClearOpen = false; rerender(); }
        if (scrim) scrim.addEventListener('click', function (ev) { if (ev.target === scrim) close(); });
        if (cancel) cancel.addEventListener('click', close);
        if (confirm) confirm.addEventListener('click', function () {
          confirmClearOpen = false;
          actions.clearExportedImageRecords();
          rerender();
        });
      });
    }

    container.innerHTML = html;
    binds.forEach(function (fn) { fn(container); });
  };
})();
