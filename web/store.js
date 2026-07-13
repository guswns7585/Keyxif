/* =============================================================================
   KeyxifStore — KeyxifViewModel.kt 1:1 웹 포트
   state-flow-spec.md 기준. 의존: KeyxifDB, KeyxifSearch, KeyxifPalette,
   KeyxifRenderer, KEYXIF_ASSETS
   ============================================================================= */
(function () {
  'use strict';

  var U = function () { return window.KeyxifRenderer.utils; };

  /* ------------------------------------------------------------------ */
  /* Enums & display names (Models.kt)                                   */
  /* ------------------------------------------------------------------ */

  var CARD_TEMPLATES = [
    'PlainExport', 'ClassicFrame', 'MinimalCaption', 'BottomSpecBar', 'CornerMark',
    'PosterMargin', 'DarkGlassStrip', 'SideSpecRail', 'TopNameplate', 'MuseumMat',
    'CompactTicket', 'CleanSignature',
  ];
  var TEMPLATE_NAME = {
    ClassicFrame: '클래식 프레임', MinimalCaption: '미니멀 캡션', BottomSpecBar: '하단 스펙 바',
    CornerMark: '코너 마크', PosterMargin: '포스터 마진', DarkGlassStrip: '다크 글래스 스트립',
    SideSpecRail: '사이드 스펙 레일', TopNameplate: '상단 네임플레이트', MuseumMat: '뮤지엄 매트',
    CompactTicket: '컴팩트 티켓', CleanSignature: '클린 시그니처', PlainExport: 'Plain Export',
  };
  var TEMPLATE_DESC = {
    ClassicFrame: '사진 밖의 얇은 바에 전체 빌드 정보를 정돈합니다.',
    MinimalCaption: '밝은 하단 여백에 핵심 정보만 담습니다.',
    BottomSpecBar: '아주 얇은 하단 바에 주요 스펙을 배열합니다.',
    CornerMark: '사진 모서리에 로고와 하우징만 작게 표시합니다.',
    PosterMargin: '사진집 같은 프레임과 하단 여백을 만듭니다.',
    DarkGlassStrip: '하단 가장자리에 얇은 반투명 3열 정보를 표시합니다.',
    SideSpecRail: '오른쪽 외부 레일에 로고와 세부 스펙을 세로로 배치합니다.',
    TopNameplate: '사진 위쪽 여백에 큰 이름표와 로고를 올립니다.',
    MuseumMat: '넓은 매트 여백과 작품 라벨 같은 정보를 만듭니다.',
    CompactTicket: '하단 티켓형 라벨에 주요 정보를 작게 모읍니다.',
    CleanSignature: '하단 여백에 하우징, 키캡, 닉네임을 서명처럼 정리합니다.',
    PlainExport: '꾸밈 없이 원본 사진을 WEBP로 저장합니다.',
  };
  var STEPS = ['Photos', 'BuildInfo', 'Palette', 'Template', 'Export'];
  var QUALITY_PRESETS = {
    HighCompression: { webpQuality: 80, keepOriginalResolution: false, maxLongSidePx: 1920 },
    Recommended: { webpQuality: 88, keepOriginalResolution: false, maxLongSidePx: 2048 },
    Balanced: { webpQuality: 92, keepOriginalResolution: true, maxLongSidePx: null },
    HighQuality: { webpQuality: 96, keepOriginalResolution: true, maxLongSidePx: null },
    Maximum: { webpQuality: 100, keepOriginalResolution: true, maxLongSidePx: null },
  };
  var QUALITY_PRESET_NAME = {
    HighCompression: '고압축', Recommended: '권장', Balanced: '균형',
    HighQuality: '고화질', Maximum: '최고화질', Custom: '사용자 지정',
  };
  var FILE_NAME_RULE_NAME = {
    KEYXIF_INDEX: 'Keyxif_번호', HOUSING_INDEX: 'Housing_번호',
    NICKNAME_INDEX: 'Nickname_번호', HOUSING_KEYCAP_INDEX: 'Housing_Keycap_번호',
  };

  var SAVE_LONG_SIDE_LIMIT = 4096;
  var PREVIEW_LONG_SIDE_LIMIT = 720;
  var VERSION = '1.0.5-web';

  /* ------------------------------------------------------------------ */
  /* Defaults & normalization (AppSettings — Models.kt)                  */
  /* ------------------------------------------------------------------ */

  function defaultSettings() {
    return {
      webpQuality: 92, outputFormat: 'WEBP', keepOriginalResolution: true,
      maxLongSidePx: null, fileNameRule: 'KEYXIF_INDEX', saveDirectoryName: 'Keyxif',
      openGalleryAfterSave: false, showSaveToast: true, skipFailedOnBatchSave: true,
      rememberLastTemplate: true, rememberLastNickname: true, recentInputLimit: 20,
      copyPreviousBuildInfoOnAdd: false, defaultTemplate: 'ClassicFrame',
      useCurrentPhotoForTemplatePreview: true, protectCenterAreaForOverlay: true,
      textScale: 1.0, qualityPreset: 'Recommended',
      autoRestoreDraftSession: true, askBeforeRestoreDraft: true,
      nicknameStyle: 'Plain', nicknameEmphasis: 1.1,
      showSwitchPresets: true, enableExportPreviewZoom: true,
      showBuildInfoInPlainExport: false, updateJsonUrl: '',
      showPaletteColors: true, paletteColorCount: 4,
      paletteAnalysisMode: 'AutoCenter', paletteCenterCropRatio: 0.75,
      autoSelectLogoContrastVariant: true,
      languageMode: 'System', themeMode: 'System',
    };
  }

  function clamp(v, lo, hi) { return Math.min(Math.max(v, lo), hi); }
  function enumOr(value, allowed, dflt) { return allowed.indexOf(value) >= 0 ? value : dflt; }

  function normalizeSettings(raw) {
    var d = defaultSettings();
    var s = Object.assign(d, raw || {});
    s.webpQuality = clamp(Math.round(Number(s.webpQuality) || 92), 70, 100);
    s.outputFormat = enumOr(s.outputFormat, ['WEBP', 'PNG'], 'WEBP');
    s.keepOriginalResolution = !!s.keepOriginalResolution;
    var mls = Number(s.maxLongSidePx);
    s.maxLongSidePx = isFinite(mls) && mls > 0 ? Math.round(mls) : null;
    s.fileNameRule = enumOr(s.fileNameRule, Object.keys(FILE_NAME_RULE_NAME), 'KEYXIF_INDEX');
    s.saveDirectoryName = String(s.saveDirectoryName || '').trim() || 'Keyxif';
    var lim = Number(s.recentInputLimit) || 20;
    s.recentInputLimit = lim <= 10 ? 10 : (lim <= 20 ? 20 : 50);
    s.defaultTemplate = enumOr(s.defaultTemplate, CARD_TEMPLATES, 'ClassicFrame');
    s.textScale = clamp(Number(s.textScale) || 1.0, 0.85, 1.35);
    s.qualityPreset = enumOr(s.qualityPreset, Object.keys(QUALITY_PRESET_NAME), 'Recommended');
    s.nicknameStyle = enumOr(s.nicknameStyle, ['Plain', 'AtPrefix', 'Credit'], 'Plain');
    s.nicknameEmphasis = clamp(Number(s.nicknameEmphasis) || 1.1, 0.9, 1.35);
    s.updateJsonUrl = String(s.updateJsonUrl || '').trim();
    s.paletteColorCount = clamp(Math.round(Number(s.paletteColorCount) || 4), 3, 5);
    s.paletteAnalysisMode = 'AutoCenter';
    s.paletteCenterCropRatio = clamp(Number(s.paletteCenterCropRatio) || 0.75, 0.35, 1.0);
    s.languageMode = enumOr(s.languageMode, ['System', 'Korean', 'English'], 'System');
    s.themeMode = enumOr(s.themeMode, ['System', 'Light', 'Dark'], 'System');
    ['openGalleryAfterSave', 'showSaveToast', 'skipFailedOnBatchSave', 'rememberLastTemplate',
      'rememberLastNickname', 'copyPreviousBuildInfoOnAdd', 'useCurrentPhotoForTemplatePreview',
      'protectCenterAreaForOverlay', 'autoRestoreDraftSession', 'askBeforeRestoreDraft',
      'showSwitchPresets', 'enableExportPreviewZoom', 'showBuildInfoInPlainExport',
      'showPaletteColors', 'autoSelectLogoContrastVariant',
    ].forEach(function (k) { s[k] = !!s[k]; });
    return s;
  }

  function defaultBuildInfo() {
    return {
      housing: '', switchName: '', plate: '', mount: '', keycap: '', nickname: '',
      logoId: null, customLogoUri: null, logoDisabled: false,
    };
  }
  function defaultAnalysis() {
    return {
      paletteColors: [], analyzedAt: 0, isAnalyzing: false, errorMessage: null,
      analysisMode: 'AutoCenter', analysisCenterCropRatio: 0.75,
      analysisRectNormalized: null, paintedMaskStrokes: [],
    };
  }
  function defaultAnalysisRect() {
    return { left: 0.15, top: 0.39, right: 0.85, bottom: 0.61 };
  }
  function defaultRenderStyle() {
    return {
      usePaletteColorForCardBackground: false,
      paletteBackgroundColorIndex: 0,
    };
  }
  function defaultExportProgress() {
    return { isSaving: false, current: 0, total: 0, successCount: 0, failureCount: 0, message: null };
  }

  function uuid() {
    if (window.crypto && crypto.randomUUID) return crypto.randomUUID();
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
      var r = (Math.random() * 16) | 0;
      return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
    });
  }

  /* ------------------------------------------------------------------ */
  /* State                                                               */
  /* ------------------------------------------------------------------ */

  var state = {
    currentStep: 'Photos',
    photos: [],
    selectedPhotoId: null,
    selectedExportPhotoIds: {},        // Set 대용: { id: true }
    expandedExportPhotoId: null,
    selectedTemplate: 'ClassicFrame',
    settings: defaultSettings(),
    isSettingsOpen: false,
    settingsPageName: null,
    isGalleryOpen: false,
    exportedImages: [],
    buildPresets: [],
    presetQuery: '',
    recentHousing: [], recentSwitches: [], recentKeycaps: [], recentNicknames: [],
    shareMessage: null,
    uiMessage: null,
    exportProgress: defaultExportProgress(),
    showDraftRestorePrompt: false,
    draftLastUpdatedAt: null,
  };

  var listeners = [];
  var autoSaveReady = false;
  var pendingDraft = null;
  var appliedInitialSettings = false;
  var paletteJobRunning = false;
  var paletteJobGeneration = 0;
  var draftTimer = null;

  function emit() {
    for (var i = 0; i < listeners.length; i++) { try { listeners[i](state); } catch (e) { console.error(e); } }
    scheduleDraftSave();
  }
  function subscribe(fn) {
    listeners.push(fn);
    return function () { var i = listeners.indexOf(fn); if (i >= 0) listeners.splice(i, 1); };
  }
  function selectedPhoto() {
    if (state.selectedPhotoId) {
      for (var i = 0; i < state.photos.length; i++) {
        if (state.photos[i].id === state.selectedPhotoId) return state.photos[i];
      }
    }
    return state.photos[0] || null;
  }
  function findPhoto(id) {
    for (var i = 0; i < state.photos.length; i++) if (state.photos[i].id === id) return state.photos[i];
    return null;
  }
  function visibleSteps(settings) {
    return (settings && settings.showPaletteColors)
      ? STEPS.slice()
      : ['Photos', 'BuildInfo', 'Template', 'Export'];
  }
  function message(msg) { state.uiMessage = msg; emit(); }

  /* ------------------------------------------------------------------ */
  /* Object URL / image caches                                           */
  /* ------------------------------------------------------------------ */

  var objectUrls = {};        // sourceId -> object URL
  var logoImages = {};        // drawableName -> HTMLImageElement (loaded)
  var customLogoImages = {};  // sourceId -> HTMLImageElement

  function getObjectURL(sourceId) { return objectUrls[sourceId] || null; }

  function ensureObjectURL(sourceId) {
    if (objectUrls[sourceId]) return Promise.resolve(objectUrls[sourceId]);
    return window.KeyxifDB.getSource(sourceId).then(function (rec) {
      if (!rec || !rec.blob) return null;
      var url = URL.createObjectURL(rec.blob);
      objectUrls[sourceId] = url;
      return url;
    });
  }
  function revokeObjectURL(sourceId) {
    if (objectUrls[sourceId]) { URL.revokeObjectURL(objectUrls[sourceId]); delete objectUrls[sourceId]; }
  }

  function loadImageFromDataUri(dataUri) {
    return new Promise(function (resolve, reject) {
      var img = new Image();
      img.onload = function () { resolve(img); };
      img.onerror = reject;
      img.src = dataUri;
    });
  }
  function logoImage(drawableName) {
    if (!drawableName) return Promise.resolve(null);
    if (logoImages[drawableName]) return Promise.resolve(logoImages[drawableName]);
    var uri = window.KEYXIF_ASSETS && window.KEYXIF_ASSETS[drawableName];
    if (!uri) return Promise.resolve(null);
    return loadImageFromDataUri(uri).then(function (img) {
      logoImages[drawableName] = img;
      return img;
    }).catch(function () { return null; });
  }
  function customLogoImage(sourceId) {
    if (!sourceId) return Promise.resolve(null);
    if (customLogoImages[sourceId]) return Promise.resolve(customLogoImages[sourceId]);
    return ensureObjectURL(sourceId).then(function (url) {
      if (!url) return null;
      return new Promise(function (resolve) {
        var img = new Image();
        img.onload = function () { customLogoImages[sourceId] = img; resolve(img); };
        img.onerror = function () { resolve(null); };
        img.src = url;
      });
    });
  }

  // decodeOrientedBitmap 대응: EXIF 보정 + 긴 변 제한 디코드
  function decodeBlob(blob, maxLongSide) {
    return createImageBitmap(blob, { imageOrientation: 'from-image' }).then(function (bmp) {
      var longest = Math.max(bmp.width, bmp.height);
      if (!maxLongSide || longest <= maxLongSide) return bmp;
      var ratio = maxLongSide / longest;
      var w = Math.max(1, Math.round(bmp.width * ratio));
      var h = Math.max(1, Math.round(bmp.height * ratio));
      return createImageBitmap(bmp, { resizeWidth: w, resizeHeight: h, resizeQuality: 'high' })
        .then(function (scaled) { bmp.close(); return scaled; });
    });
  }
  function decodeSource(sourceId, maxLongSide) {
    return window.KeyxifDB.getSource(sourceId).then(function (rec) {
      if (!rec || !rec.blob) throw new Error('source missing: ' + sourceId);
      return decodeBlob(rec.blob, maxLongSide);
    });
  }

  /* ------------------------------------------------------------------ */
  /* Language / theme helpers                                            */
  /* ------------------------------------------------------------------ */

  function resolvedLanguage() {
    var mode = state.settings.languageMode;
    if (mode === 'Korean') return 'ko';
    if (mode === 'English') return 'en';
    return (navigator.language || 'en').toLowerCase().indexOf('ko') === 0 ? 'ko' : 'en';
  }
  function text(ko, en) { return resolvedLanguage() === 'ko' ? ko : en; }

  var systemDarkMedia = window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null;
  function applyTheme() {
    var mode = state.settings.themeMode;
    var dark = mode === 'Dark' || (mode === 'System' && systemDarkMedia && systemDarkMedia.matches);
    document.documentElement.setAttribute('data-theme', dark ? 'dark' : 'light');
    var meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.setAttribute('content', dark ? '#151411' : '#F7F5F0');
  }
  if (systemDarkMedia) {
    systemDarkMedia.addEventListener('change', function () {
      if (state.settings.themeMode === 'System') { applyTheme(); emit(); }
    });
  }

  function pad2(n) { return String(n).padStart(2, '0'); }
  function fmtDate(ms) {
    if (!ms || ms <= 0) return '저장 시각 알 수 없음';
    var d = new Date(ms);
    return d.getFullYear() + '.' + pad2(d.getMonth() + 1) + '.' + pad2(d.getDate()) +
      ' ' + pad2(d.getHours()) + ':' + pad2(d.getMinutes());
  }

  /* ------------------------------------------------------------------ */
  /* Recents (RecentStore.kt)                                            */
  /* ------------------------------------------------------------------ */

  function loadRecents() {
    var r = window.KeyxifDB.loadJSON('keyxif.recents') || {};
    state.recentHousing = Array.isArray(r.housing) ? r.housing : [];
    state.recentSwitches = Array.isArray(r.switch) ? r.switch : [];
    state.recentKeycaps = Array.isArray(r.keycap) ? r.keycap : [];
    state.recentNicknames = Array.isArray(r.nickname) ? r.nickname : [];
  }
  function saveRecents() {
    window.KeyxifDB.saveJSON('keyxif.recents', {
      housing: state.recentHousing, switch: state.recentSwitches,
      keycap: state.recentKeycaps, nickname: state.recentNicknames,
    });
  }
  function addRecent(list, value, limit) {
    var meaningful = U().meaningfulBuildTextOrNull(value);
    if (!meaningful) return list;
    var out = [meaningful];
    var lower = meaningful.toLowerCase();
    for (var i = 0; i < list.length; i++) {
      if (String(list[i]).toLowerCase() !== lower) out.push(list[i]);
    }
    return out.slice(0, clamp(limit, 10, 50));
  }
  function recordBuildInfoRecents(info) {
    var limit = state.settings.recentInputLimit;
    state.recentHousing = addRecent(state.recentHousing, info.housing, limit);
    state.recentSwitches = addRecent(state.recentSwitches, info.switchName, limit);
    state.recentKeycaps = addRecent(state.recentKeycaps, info.keycap, limit);
    state.recentNicknames = addRecent(state.recentNicknames, info.nickname, limit);
    saveRecents();
  }
  function removeRecentFrom(listKey, value) {
    var lower = String(value || '').trim().toLowerCase();
    state[listKey] = state[listKey].filter(function (v) { return String(v).toLowerCase() !== lower; });
    saveRecents();
    emit();
  }

  /* ------------------------------------------------------------------ */
  /* Build presets (BuildPresetRepository.kt)                            */
  /* ------------------------------------------------------------------ */

  function loadBuildPresets() {
    var arr = window.KeyxifDB.loadJSON('keyxif.buildPresets');
    state.buildPresets = Array.isArray(arr) ? arr.slice().sort(function (a, b) { return (b.updatedAt || 0) - (a.updatedAt || 0); }) : [];
  }
  function saveBuildPresets() { window.KeyxifDB.saveJSON('keyxif.buildPresets', state.buildPresets); }

  /* ------------------------------------------------------------------ */
  /* Draft session (DraftSessionRepository.kt)                           */
  /* ------------------------------------------------------------------ */

  function serializeDraft() {
    return {
      selectedTemplate: state.selectedTemplate,
      currentStep: state.currentStep,
      selectedPhotoId: state.selectedPhotoId,
      lastUpdatedAt: Date.now(),
      settings: state.settings,
      photos: state.photos.map(function (p) {
        return {
          id: p.id, uri: p.uri, displayName: p.displayName,
          cropState: p.cropState,
          buildInfo: p.buildInfo,
          analysisResult: {
            paletteColors: p.analysisResult.paletteColors,
            analyzedAt: p.analysisResult.analyzedAt,
            isAnalyzing: false,
            errorMessage: p.analysisResult.errorMessage,
            analysisMode: p.analysisResult.analysisMode,
            analysisCenterCropRatio: p.analysisResult.analysisCenterCropRatio,
            analysisRectNormalized: p.analysisResult.analysisRectNormalized,
            paintedMaskStrokes: p.analysisResult.paintedMaskStrokes || [],
          },
          renderStyle: p.renderStyle || defaultRenderStyle(),
        };
      }),
    };
  }

  function scheduleDraftSave() {
    if (!autoSaveReady || state.showDraftRestorePrompt || !state.settings.autoRestoreDraftSession) return;
    if (draftTimer) clearTimeout(draftTimer);
    draftTimer = setTimeout(function () {
      draftTimer = null;
      if (!autoSaveReady || state.showDraftRestorePrompt || !state.settings.autoRestoreDraftSession) return;
      if (state.photos.length === 0) window.KeyxifDB.removeKey('keyxif.draft');
      else window.KeyxifDB.saveJSON('keyxif.draft', serializeDraft());
    }, 700);
  }

  function decodeDraft(raw) {
    if (!raw || typeof raw !== 'object') return null;
    try {
      var photos = Array.isArray(raw.photos) ? raw.photos.map(function (p) {
        var ar = p.analysisResult || {};
        var rs = p.renderStyle || {};
        return {
          id: String(p.id || uuid()),
          uri: String(p.uri || ''),
          displayName: String(p.displayName || '사진'),
          cropState: {
            scale: Number((p.cropState || {}).scale) || 1,
            offsetX: Number((p.cropState || {}).offsetX) || 0,
            offsetY: Number((p.cropState || {}).offsetY) || 0,
          },
          buildInfo: Object.assign(defaultBuildInfo(), p.buildInfo || {}),
          analysisResult: {
            paletteColors: (Array.isArray(ar.paletteColors) ? ar.paletteColors : []).filter(function (c) { return c !== 0; }),
            analyzedAt: Number(ar.analyzedAt) || 0,
            isAnalyzing: false,
            errorMessage: ar.errorMessage || null,
            analysisMode: ['RectSelection', 'TapAutoBox', 'ManualBox'].indexOf(ar.analysisMode) >= 0
              ? 'RectSelection' : (ar.analysisMode === 'PaintedMask' ? 'PaintedMask' : 'AutoCenter'),
            analysisCenterCropRatio: Number(ar.analysisCenterCropRatio) || 0.75,
            analysisRectNormalized: ar.analysisRectNormalized || null,
            paintedMaskStrokes: Array.isArray(ar.paintedMaskStrokes) ? ar.paintedMaskStrokes : [],
          },
          renderStyle: {
            usePaletteColorForCardBackground: !!rs.usePaletteColorForCardBackground,
            paletteBackgroundColorIndex: clamp(Math.round(Number(rs.paletteBackgroundColorIndex) || 0), 0, 4),
          },
          renderStatus: 'Idle',
          errorMessage: null,
        };
      }) : [];
      return {
        photoItems: photos,
        selectedTemplate: enumOr(raw.selectedTemplate, CARD_TEMPLATES, 'ClassicFrame'),
        currentStep: raw.currentStep === 'Adjust' ? 'Template' : enumOr(raw.currentStep, STEPS, 'Photos'),
        selectedPhotoId: raw.selectedPhotoId || null,
        settings: normalizeSettings(raw.settings),
        lastUpdatedAt: Number(raw.lastUpdatedAt) || 0,
      };
    } catch (e) { return null; }
  }

  /* ------------------------------------------------------------------ */
  /* Palette analysis scheduling (§6)                                     */
  /* ------------------------------------------------------------------ */

  function needsPaletteAnalysis(p) {
    var ar = p.analysisResult;
    if (ar.isAnalyzing) return false;
    return ar.analyzedAt <= 0 && (ar.analysisMode !== 'PaintedMask' || (ar.paintedMaskStrokes || []).length > 0);
  }

  function schedulePaletteAnalysis() {
    if (!state.settings.showPaletteColors) return;
    var generation = ++paletteJobGeneration;
    paletteJobRunning = true;
    state.photos.forEach(function (p) { p.analysisResult.isAnalyzing = false; });
    (function loop() {
      if (generation !== paletteJobGeneration) return;
      var s = state.settings;
      if (!s.showPaletteColors) { paletteJobRunning = false; return; }
      var target = null;
      for (var i = 0; i < state.photos.length; i++) {
        if (needsPaletteAnalysis(state.photos[i])) { target = state.photos[i]; break; }
      }
      if (!target) { paletteJobRunning = false; return; }
      target.analysisResult.isAnalyzing = true;
      target.analysisResult.errorMessage = null;
      emit();
      var request = JSON.parse(JSON.stringify(target.analysisResult));
      var mode = request.analysisMode;
      var ratio = request.analysisCenterCropRatio;
      decodeSource(target.uri, 640).then(function (bmp) {
        var colors = window.KeyxifPalette.analyze(bmp, mode, 5, ratio, request.analysisRectNormalized, request.paintedMaskStrokes);
        if (bmp.close) bmp.close();
        return colors || [];
      }).then(function (colors) {
        if (generation !== paletteJobGeneration) return;
        target.analysisResult = Object.assign({}, target.analysisResult, {
          paletteColors: colors, analyzedAt: Date.now(), isAnalyzing: false,
          errorMessage: colors.length === 0 ? '대표 색상을 찾지 못했습니다.' : null,
        });
        emit();
        setTimeout(loop, 40);
      }).catch(function (err) {
        if (generation !== paletteJobGeneration) return;
        target.analysisResult = Object.assign({}, target.analysisResult, {
          paletteColors: [], analyzedAt: Date.now(), isAnalyzing: false,
          errorMessage: (err && err.message) || '대표 색상을 찾지 못했습니다.',
        });
        emit();
        setTimeout(loop, 40);
      });
    })();
  }

  /* ------------------------------------------------------------------ */
  /* Rendering (semaphore 2)                                              */
  /* ------------------------------------------------------------------ */

  var renderActive = 0;
  var renderQueue = [];
  function withRenderSlot(task) {
    return new Promise(function (resolve, reject) {
      renderQueue.push({ task: task, resolve: resolve, reject: reject });
      pumpRenderQueue();
    });
  }
  function pumpRenderQueue() {
    while (renderActive < 2 && renderQueue.length > 0) {
      var item = renderQueue.shift();
      renderActive++;
      (function (it) {
        Promise.resolve().then(it.task).then(function (r) {
          renderActive--; it.resolve(r); pumpRenderQueue();
        }, function (e) {
          renderActive--; it.reject(e); pumpRenderQueue();
        });
      })(item);
    }
  }

  // 로고 자산 해석: preset drawable 이름 → 이미지 (variants)
  function resolveRenderAssets(buildInfo, paletteColors) {
    var S = window.KeyxifSearch;
    if (buildInfo.logoDisabled) {
      return Promise.resolve({ logoImage: null, logoLabel: '', logoVariants: null, paletteColors: paletteColors });
    }
    var preset = S.logoForBuildInfo(buildInfo);
    var label = (preset && preset.name) || S.logoName(buildInfo.logoId) || '';
    var custom = buildInfo.customLogoUri ? customLogoImage(buildInfo.customLogoUri) : Promise.resolve(null);
    return custom.then(function (customImg) {
      if (customImg) {
        return { customLogoImage: customImg, logoLabel: label, logoVariants: null, paletteColors: paletteColors };
      }
      if (!preset) return { logoImage: null, logoLabel: label, logoVariants: null, paletteColors: paletteColors };
      return Promise.all([
        logoImage(preset.drawable), logoImage(preset.blackDrawable), logoImage(preset.whiteDrawable),
        logoImage(preset.photoOverlayDrawable),
      ]).then(function (imgs) {
        return {
          logoLabel: label,
          logoVariants: { default: imgs[0], black: imgs[1], white: imgs[2] },
          photoOverlayImage: imgs[3],
          paletteColors: paletteColors,
        };
      });
    });
  }

  function renderPhotoCanvas(photo, template, settings, maxLongSide, sourceBlob) {
    return withRenderSlot(function () {
      return Promise.all([
        sourceBlob ? decodeBlob(sourceBlob, maxLongSide) : decodeSource(photo.uri, maxLongSide),
        resolveRenderAssets(photo.buildInfo, photo.analysisResult.paletteColors),
      ]).then(function (parts) {
        var bmp = parts[0], assets = parts[1];
        var buildInfo = Object.assign({}, photo.buildInfo, { customLogoImage: assets.customLogoImage || null });
        var canvas = window.KeyxifRenderer.render({
          image: bmp, buildInfo: buildInfo, template: template, settings: settings,
          renderStyle: photo.renderStyle || defaultRenderStyle(),
          assets: {
            logoImage: assets.logoVariants ? null : (assets.logoImage || null),
            logoVariants: assets.logoVariants,
            photoOverlayImage: assets.photoOverlayImage || null,
            logoLabel: assets.logoLabel,
            paletteColors: assets.paletteColors,
          },
        });
        if (bmp.close) bmp.close();
        return canvas;
      });
    });
  }

  function renderPreviewBitmap(photoId, maxLongSide) {
    var photo = findPhoto(photoId);
    if (!photo) return Promise.resolve(null);
    return renderPhotoCanvas(photo, state.selectedTemplate, state.settings, maxLongSide || PREVIEW_LONG_SIDE_LIMIT);
  }
  function renderSourcePreviewBitmap(photoId, maxLongSide) {
    var photo = findPhoto(photoId);
    if (!photo) return Promise.resolve(null);
    return decodeSource(photo.uri, maxLongSide || PREVIEW_LONG_SIDE_LIMIT).then(function (bmp) {
      var canvas = document.createElement('canvas');
      canvas.width = bmp.width; canvas.height = bmp.height;
      canvas.getContext('2d').drawImage(bmp, 0, 0);
      if (bmp.close) bmp.close();
      return canvas;
    });
  }

  /* ------------------------------------------------------------------ */
  /* Export pipeline (ExportWorker.kt → 순차 async)                       */
  /* ------------------------------------------------------------------ */

  var webpSupported = null;
  function detectWebp() {
    if (webpSupported !== null) return Promise.resolve(webpSupported);
    return new Promise(function (resolve) {
      var c = document.createElement('canvas'); c.width = 2; c.height = 2;
      c.toBlob(function (blob) {
        webpSupported = !!(blob && blob.type === 'image/webp');
        resolve(webpSupported);
      }, 'image/webp', 0.8);
    });
  }

  function sanitizeName(raw) {
    var v = String(raw || '').trim().replace(/[\\/:*?"<>|]/g, '').replace(/\s+/g, '_');
    v = v.slice(0, 64);
    return v || '';
  }
  function outputFileName(buildInfo, index, settings, ext) {
    var m = U().meaningfulBuildTextOrNull;
    var prefix = '';
    switch (settings.fileNameRule) {
      case 'HOUSING_INDEX': prefix = m(buildInfo.housing) || ''; break;
      case 'NICKNAME_INDEX': prefix = m(buildInfo.nickname) || ''; break;
      case 'HOUSING_KEYCAP_INDEX': {
        var parts = [];
        var h = m(buildInfo.housing); if (h) parts.push(h);
        var k = m(buildInfo.keycap); if (k) parts.push(k);
        prefix = parts.join('_');
        break;
      }
      default: prefix = 'Keyxif';
    }
    prefix = sanitizeName(prefix) || 'Keyxif';
    return prefix + '_' + pad2(index) + '.' + ext;
  }

  function canvasToBlob(canvas, format, quality) {
    return new Promise(function (resolve, reject) {
      var type = format === 'PNG' ? 'image/png' : 'image/webp';
      var q = format === 'PNG' ? undefined : clamp(quality, 1, 100) / 100;
      canvas.toBlob(function (blob) {
        if (blob) resolve(blob); else reject(new Error('인코딩에 실패했습니다.'));
      }, type, q);
    });
  }

  function makeThumbnail(canvas) {
    var longest = Math.max(canvas.width, canvas.height);
    var scale = Math.min(1, 320 / longest);
    var t = document.createElement('canvas');
    t.width = Math.max(1, Math.round(canvas.width * scale));
    t.height = Math.max(1, Math.round(canvas.height * scale));
    var ctx = t.getContext('2d');
    ctx.imageSmoothingEnabled = true;
    ctx.imageSmoothingQuality = 'high';
    ctx.drawImage(canvas, 0, 0, t.width, t.height);
    try { return t.toDataURL('image/webp', 0.8); } catch (e) { return t.toDataURL('image/png'); }
  }

  function downloadBlob(blob, fileName) {
    var url = URL.createObjectURL(blob);
    var a = document.createElement('a');
    a.href = url; a.download = fileName;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    setTimeout(function () { URL.revokeObjectURL(url); }, 30000);
  }

  var exportJobToken = 0;

  function enqueueExport(ids) {
    if (!ids || ids.length === 0) { message('저장할 사진이 없습니다.'); return; }
    var targets = ids.map(findPhoto).filter(Boolean);
    if (targets.length === 0) { message('저장할 사진을 찾을 수 없습니다.'); return; }

    var token = ++exportJobToken; // REPLACE 정책: 새 작업이 이전 작업을 대체
    var s = state.settings;
    var template = state.selectedTemplate;
    var total = targets.length;

    state.exportProgress = {
      isSaving: true, current: 0, total: total, successCount: 0, failureCount: 0,
      message: '저장 작업을 준비하는 중입니다.',
    };
    targets.forEach(function (p) { p.renderStatus = 'Rendering'; p.errorMessage = null; });
    emit();

    var saveLongSide = s.keepOriginalResolution ? SAVE_LONG_SIDE_LIMIT * 4 : (s.maxLongSidePx || SAVE_LONG_SIDE_LIMIT);
    var success = 0, failure = 0, failedIds = [];
    var dirLabel = 'Pictures/' + (sanitizeName(s.saveDirectoryName) || 'Keyxif');

    // 스냅샷 단계 (spec §47: 원본 바이트를 내구화한 페이로드) — 이후 removePhoto/
    // clearPhotos로 원본이 지워져도 이번 배치는 스냅샷으로 끝까지 저장한다.
    Promise.all(targets.map(function (p) {
      return window.KeyxifDB.getSource(p.uri).then(function (rec) {
        if (!rec || !rec.blob) throw new Error('저장 작업을 준비할 수 없습니다.');
        return {
          photo: p,
          blob: rec.blob,
          snap: {
            uri: p.uri,
            buildInfo: Object.assign({}, p.buildInfo),
            analysisResult: { paletteColors: p.analysisResult.paletteColors.slice() },
            renderStyle: Object.assign(defaultRenderStyle(), p.renderStyle || {}),
          },
        };
      });
    })).then(function (payload) {
      return detectWebp().then(function (webpOK) {
        if (token !== exportJobToken) return;
        var format = s.outputFormat === 'PNG' || !webpOK ? 'PNG' : 'WEBP';
        var ext = format === 'PNG' ? 'png' : 'webp';
        state.exportProgress.message = dirLabel + '에 백그라운드 저장을 시작했습니다.';
        emit();

        var chain = Promise.resolve();
        payload.forEach(function (item, i) {
          chain = chain.then(function () {
            if (token !== exportJobToken) return; // REPLACE: 대체됨
            if (!s.skipFailedOnBatchSave && failure > 0) return; // 첫 실패에서 중단
            var photo = item.photo;
            var index = i + 1;
            state.exportProgress = {
              isSaving: true, current: index, total: total,
              successCount: success, failureCount: failure,
              message: index + ' / ' + total + ' 처리 중',
            };
            photo.renderStatus = 'Rendering';
            emit();
            return renderPhotoCanvas(item.snap, template, s, saveLongSide, item.blob).then(function (canvas) {
              return canvasToBlob(canvas, format, s.webpQuality).then(function (blob) {
                if (token !== exportJobToken) return; // 부수효과(다운로드/기록) 직전 재확인
                var fileName = outputFileName(item.snap.buildInfo, index, s, ext);
                downloadBlob(blob, fileName);
                var m = U().meaningfulBuildTextOrNull;
                var record = {
                  id: Date.now() + '-' + photo.id + '-' + index,
                  uri: '', fileName: fileName, createdAt: Date.now(),
                  width: canvas.width, height: canvas.height,
                  fileSizeBytes: blob.size, templateName: template,
                  housing: m(item.snap.buildInfo.housing), switchName: m(item.snap.buildInfo.switchName),
                  keycap: m(item.snap.buildInfo.keycap), nickname: m(item.snap.buildInfo.nickname),
                  paletteColors: item.snap.analysisResult.paletteColors.slice(),
                  thumbDataUrl: makeThumbnail(canvas), blob: blob,
                };
                return window.KeyxifDB.putExported(record).then(function () {
                  if (token !== exportJobToken) return;
                  success++;
                  photo.renderStatus = 'Saved'; photo.errorMessage = null;
                  return refreshExported();
                });
              });
            }).catch(function (err) {
              if (token !== exportJobToken) return;
              failure++;
              failedIds.push(photo.id);
              photo.renderStatus = 'Error';
              photo.errorMessage = '백그라운드 저장 실패';
              emit();
            });
          });
        });

        return chain.then(function () {
          if (token !== exportJobToken) return;
          var finalMsg = '저장 완료: 성공 ' + success + '장, 실패 ' + failure + '장';
          state.exportProgress = {
            isSaving: false, current: total, total: total,
            successCount: success, failureCount: failure,
            message: s.showSaveToast ? finalMsg : null,
          };
          // spec §47 완료 전이: failedIds → Error(+고정 메시지), 그 외 → Saved
          // (skipFailedOnBatchSave=false 조기 중단으로 건너뛴 사진도 Saved — 안드로이드 동작 그대로)
          targets.forEach(function (p) {
            if (failedIds.indexOf(p.id) >= 0) { p.renderStatus = 'Error'; p.errorMessage = '백그라운드 저장 실패'; }
            else { p.renderStatus = 'Saved'; p.errorMessage = null; }
          });
          if (s.openGalleryAfterSave && success > 0) {
            state.isGalleryOpen = true; state.isSettingsOpen = false; state.settingsPageName = null;
          }
          emit();
        });
      });
    }).catch(function (err) {
      if (token !== exportJobToken) return;
      targets.forEach(function (p) { p.renderStatus = 'Error'; p.errorMessage = '백그라운드 저장 실패'; });
      state.exportProgress = {
        isSaving: false, current: 0, total: total, successCount: 0, failureCount: total,
        message: (err && err.message) || '저장 작업을 준비할 수 없습니다.',
      };
      emit();
    });
  }

  /* ------------------------------------------------------------------ */
  /* Exported records                                                     */
  /* ------------------------------------------------------------------ */

  function refreshExported() {
    return window.KeyxifDB.listExported().then(function (records) {
      state.exportedImages = records;
      emit();
    });
  }

  /* ------------------------------------------------------------------ */
  /* Photo import                                                         */
  /* ------------------------------------------------------------------ */

  function importFiles(files) {
    var list = Array.prototype.slice.call(files || []).filter(function (f) {
      return f && f.type && f.type.indexOf('image/') === 0;
    }).slice(0, 60);
    if (list.length === 0) { message('가져올 수 있는 새 사진이 없습니다.'); return Promise.resolve(); }
    message('사진을 가져오는 중입니다.');

    var copiedInfo = null;
    if (state.settings.copyPreviousBuildInfoOnAdd) {
      var src = state.photos[state.photos.length - 1] || selectedPhoto();
      if (src) copiedInfo = Object.assign({}, src.buildInfo);
    } else if (state.settings.rememberLastNickname && state.recentNicknames[0]) {
      copiedInfo = Object.assign(defaultBuildInfo(), { nickname: state.recentNicknames[0] });
    }

    var imported = 0;
    var firstId = null;
    var chain = Promise.resolve();
    list.forEach(function (file) {
      chain = chain.then(function () {
        var id = uuid();
        var srcId = Date.now() + '_' + id + '_' + sanitizeName(file.name || 'img');
        return window.KeyxifDB.putSource({ id: srcId, name: file.name || '사진', type: file.type, blob: file })
          .then(function () {
            objectUrls[srcId] = URL.createObjectURL(file);
            state.photos.push({
              id: id, uri: srcId,
              displayName: file.name || '사진',
              cropState: { scale: 1, offsetX: 0, offsetY: 0 },
              buildInfo: copiedInfo ? Object.assign({}, copiedInfo) : defaultBuildInfo(),
              analysisResult: defaultAnalysis(),
              renderStyle: defaultRenderStyle(),
              renderStatus: 'Idle', errorMessage: null,
            });
            if (!firstId) firstId = id;
            imported++;
          }).catch(function () { /* 실패 항목 무시 */ });
      });
    });
    return chain.then(function () {
      if (!state.selectedPhotoId && firstId) state.selectedPhotoId = firstId;
      message(imported > 0 ? '사진 ' + imported + '장을 추가했습니다.' : '가져올 수 있는 새 사진이 없습니다.');
      schedulePaletteAnalysis();
    });
  }

  /* ------------------------------------------------------------------ */
  /* Actions                                                              */
  /* ------------------------------------------------------------------ */

  var actions = {
    // ---- Overlays
    openSettings: function () { state.isSettingsOpen = true; state.isGalleryOpen = false; emit(); },
    closeSettings: function () {
      if (state.settingsPageName != null) state.settingsPageName = null;
      else state.isSettingsOpen = false;
      emit();
    },
    selectSettingsPage: function (name) { if (state.isSettingsOpen) { state.settingsPageName = name; emit(); } },
    openGallery: function () { state.isGalleryOpen = true; state.isSettingsOpen = false; state.settingsPageName = null; emit(); },
    closeGallery: function () { state.isGalleryOpen = false; emit(); },

    // ---- Settings
    updateSettings: function (transform) {
      var next = normalizeSettings(transform(Object.assign({}, state.settings)));
      var prevDefault = state.settings.defaultTemplate;
      state.settings = next;
      window.KeyxifDB.saveJSON('keyxif.settings', next);
      applyTheme();
      if (!appliedInitialSettings || (state.isSettingsOpen && prevDefault !== next.defaultTemplate)) {
        appliedInitialSettings = true;
        state.selectedTemplate = next.defaultTemplate;
      }
      emit();
      schedulePaletteAnalysis();
    },

    // ---- Messages
    consumeMessage: function () { state.uiMessage = null; },
    clearShareMessage: function () { state.shareMessage = null; emit(); },
    showExportMessage: function (msg) { state.exportProgress.message = msg; emit(); },

    // ---- Navigation
    navigateToStep: function (step) {
      if (step !== 'Photos' && state.photos.length === 0) { message('먼저 사진을 추가해 주세요.'); return; }
      if (step === 'Palette' && !state.settings.showPaletteColors) step = 'Template';
      state.currentStep = step;
      state.isSettingsOpen = false; state.settingsPageName = null; state.isGalleryOpen = false;
      emit();
    },
    navigateToPreviousStep: function () {
      var steps = visibleSteps(state.settings);
      var idx = steps.indexOf(state.currentStep);
      if (idx > 0) actions.navigateToStep(steps[idx - 1]);
    },
    handleSystemBack: function () {
      if (state.isSettingsOpen) { actions.closeSettings(); return true; }
      if (state.isGalleryOpen) { actions.closeGallery(); return true; }
      if (state.currentStep !== 'Photos') { actions.navigateToPreviousStep(); return true; }
      return false;
    },

    // ---- Photos
    addPhotos: importFiles,
    removePhoto: function (id) {
      var photo = findPhoto(id);
      if (!photo) return;
      window.KeyxifDB.deleteSource(photo.uri).catch(function () {});
      revokeObjectURL(photo.uri);
      state.photos = state.photos.filter(function (p) { return p.id !== id; });
      delete state.selectedExportPhotoIds[id];
      if (state.expandedExportPhotoId === id) state.expandedExportPhotoId = null;
      if (state.selectedPhotoId === id) state.selectedPhotoId = state.photos[0] ? state.photos[0].id : null;
      emit();
    },
    clearPhotos: function () {
      state.photos.forEach(function (p) {
        window.KeyxifDB.deleteSource(p.uri).catch(function () {});
        revokeObjectURL(p.uri);
      });
      state.photos = [];
      state.selectedPhotoId = null;
      state.selectedExportPhotoIds = {};
      state.expandedExportPhotoId = null;
      state.currentStep = 'Photos';
      exportJobToken++; // 진행 중이던 내보내기 체인 취소 (progress 부활 방지)
      state.exportProgress = defaultExportProgress();
      message('사진 목록을 비웠습니다.');
    },
    movePhoto: function (id, direction) {
      var idx = state.photos.findIndex(function (p) { return p.id === id; });
      if (idx < 0) return;
      var next = clamp(idx + direction, 0, state.photos.length - 1);
      if (next === idx) return;
      var item = state.photos.splice(idx, 1)[0];
      state.photos.splice(next, 0, item);
      emit();
    },
    selectPhoto: function (id) { if (findPhoto(id)) { state.selectedPhotoId = id; emit(); } },
    startNewSession: function () {
      window.KeyxifDB.removeKey('keyxif.draft');
      state.currentStep = 'Photos'; state.photos = []; state.selectedPhotoId = null;
      exportJobToken++; // 진행 중이던 내보내기 체인 취소
      state.exportProgress = defaultExportProgress();
      state.showDraftRestorePrompt = false; state.draftLastUpdatedAt = null;
      emit();
    },

    // ---- Export selection
    setExportPhotoSelected: function (id, selected) {
      if (!findPhoto(id)) return;
      if (selected) state.selectedExportPhotoIds[id] = true;
      else delete state.selectedExportPhotoIds[id];
      emit();
    },
    clearExportSelection: function () { state.selectedExportPhotoIds = {}; emit(); },
    setExpandedExportPhoto: function (id) {
      if (id != null && !findPhoto(id)) return;
      state.expandedExportPhotoId = id;
      emit();
    },

    // ---- Build info
    updateBuildInfo: function (buildInfo) {
      var p = selectedPhoto();
      if (!p) return;
      p.buildInfo = Object.assign({}, buildInfo);
      emit();
    },
    updateSelectedPhotoRenderStyle: function (transform) {
      var p = selectedPhoto();
      if (!p) return;
      p.renderStyle = Object.assign(defaultRenderStyle(), transform(Object.assign(defaultRenderStyle(), p.renderStyle || {})));
      p.renderStyle.paletteBackgroundColorIndex = clamp(Math.round(Number(p.renderStyle.paletteBackgroundColorIndex) || 0), 0, 4);
      emit();
    },
    updateSelectedPhotoAnalysisMode: function (mode) {
      var p = selectedPhoto();
      if (!p || ['AutoCenter', 'RectSelection', 'PaintedMask'].indexOf(mode) < 0) return;
      p.analysisResult.analysisMode = mode;
      if (mode === 'RectSelection' && !p.analysisResult.analysisRectNormalized) p.analysisResult.analysisRectNormalized = defaultAnalysisRect();
      p.analysisResult.analyzedAt = 0; p.analysisResult.isAnalyzing = false; p.analysisResult.errorMessage = null;
      emit();
      if (mode !== 'PaintedMask' || (p.analysisResult.paintedMaskStrokes || []).length) schedulePaletteAnalysis();
    },
    updateSelectedPhotoAnalysisRect: function (rect) {
      var p = selectedPhoto(); if (!p) return;
      p.analysisResult.analysisRectNormalized = Object.assign(defaultAnalysisRect(), rect || {});
      emit();
    },
    updateSelectedPhotoCenterRatio: function (ratio) {
      var p = selectedPhoto(); if (!p) return;
      p.analysisResult.analysisCenterCropRatio = clamp(Number(ratio) || 0.75, 0.35, 1);
      emit();
    },
    updateSelectedPhotoMask: function (strokes) {
      var p = selectedPhoto(); if (!p) return;
      p.analysisResult.paintedMaskStrokes = Array.isArray(strokes) ? strokes : [];
      emit();
    },
    reanalyzeSelectedPalette: function () {
      var p = selectedPhoto(); if (!p) return;
      p.analysisResult.analyzedAt = 0; p.analysisResult.isAnalyzing = false; p.analysisResult.errorMessage = null;
      emit(); schedulePaletteAnalysis();
    },
    clearSelectedBuildInfo: function () {
      var p = selectedPhoto();
      if (!p) return;
      p.buildInfo = defaultBuildInfo();
      emit();
    },
    applyBuildInfoToAll: function () {
      var p = selectedPhoto();
      if (!p) return;
      state.photos.forEach(function (o) { o.buildInfo = Object.assign({}, p.buildInfo); });
      recordBuildInfoRecents(p.buildInfo);
      emit();
    },
    selectHousingPreset: function (preset) {
      var p = selectedPhoto();
      if (!p) return;
      var logoId = window.KeyxifSearch.logoIdForHousing(preset);
      p.buildInfo = Object.assign({}, p.buildInfo, {
        housing: preset.name,
        logoId: logoId != null ? logoId : p.buildInfo.logoId,
        customLogoUri: null, logoDisabled: false,
      });
      emit();
    },
    selectSwitchPreset: function (preset) {
      var p = selectedPhoto();
      if (!p) return;
      p.buildInfo = Object.assign({}, p.buildInfo, { switchName: preset.name });
      emit();
    },
    selectKeycapPreset: function (preset) {
      var p = selectedPhoto();
      if (!p) return;
      p.buildInfo = Object.assign({}, p.buildInfo, { keycap: preset.name });
      emit();
    },
    completeBuildInfo: function () {
      var seen = {};
      state.photos.forEach(function (p) {
        var key = JSON.stringify(p.buildInfo);
        if (!seen[key]) { seen[key] = true; recordBuildInfoRecents(p.buildInfo); }
      });
      actions.navigateToStep(state.settings.showPaletteColors ? 'Palette' : 'Template');
    },

    // ---- Template
    selectTemplate: function (template) {
      state.selectedTemplate = template;
      emit();
      if (state.settings.rememberLastTemplate) {
        var next = normalizeSettings(Object.assign({}, state.settings, { defaultTemplate: template }));
        state.settings = next;
        window.KeyxifDB.saveJSON('keyxif.settings', next);
      }
    },

    // ---- Build presets
    updatePresetQuery: function (q) { state.presetQuery = q; emit(); },
    saveBuildPreset: function (name) {
      var p = selectedPhoto();
      if (!p) return;
      var m = U().meaningfulBuildTextOrNull;
      var trimmed = String(name || '').trim();
      if (!trimmed) {
        var parts = [];
        var h = m(p.buildInfo.housing); if (h) parts.push(h);
        var kc = m(p.buildInfo.keycap) || m(p.buildInfo.switchName);
        if (kc) parts.push(kc);
        trimmed = parts.join(' + ') || '새 빌드';
      }
      var now = Date.now();
      state.buildPresets.unshift({
        id: uuid(), presetName: trimmed,
        buildInfo: Object.assign({}, p.buildInfo),
        createdAt: now, updatedAt: now,
      });
      saveBuildPresets();
      recordBuildInfoRecents(p.buildInfo);
      emit();
    },
    applyBuildPreset: function (preset) {
      actions.updateBuildInfo(preset.buildInfo);
      recordBuildInfoRecents(preset.buildInfo);
      emit();
    },
    deleteBuildPreset: function (id) {
      state.buildPresets = state.buildPresets.filter(function (bp) { return bp.id !== id; });
      saveBuildPresets();
      emit();
    },

    // ---- Recents
    removeRecentHousing: function (v) { removeRecentFrom('recentHousing', v); },
    removeRecentSwitch: function (v) { removeRecentFrom('recentSwitches', v); },
    removeRecentKeycap: function (v) { removeRecentFrom('recentKeycaps', v); },
    removeRecentNickname: function (v) { removeRecentFrom('recentNicknames', v); },

    // ---- Preset queries
    housingOptions: function (q) { return window.KeyxifSearch.searchHousings(q, state.recentHousing); },
    switchOptions: function (q) { return window.KeyxifSearch.searchSwitches(q, state.recentSwitches, state.settings.showSwitchPresets); },
    keycapOptions: function (q) { return window.KeyxifSearch.searchKeycaps(q, state.recentKeycaps); },

    // ---- Rendering
    renderPreviewBitmap: renderPreviewBitmap,
    renderSourcePreviewBitmap: renderSourcePreviewBitmap,

    // ---- Export
    savePhoto: function (id) { enqueueExport([id]); },
    savePhotos: function (ids) { enqueueExport(ids); },
    saveAll: function () { enqueueExport(state.photos.map(function (p) { return p.id; })); },

    // ---- Exported records
    shareExportedImage: function (image) {
      window.KeyxifDB.getExported(image.id).then(function (rec) {
        if (!rec || !rec.blob) { message('공유할 앱을 열 수 없습니다.'); return; }
        var file = new File([rec.blob], rec.fileName, { type: rec.blob.type || 'image/webp' });
        if (navigator.canShare && navigator.canShare({ files: [file] })) {
          navigator.share({ files: [file], title: 'Keyxif 이미지 공유' }).catch(function () {});
        } else {
          downloadBlob(rec.blob, rec.fileName);
        }
      });
    },
    openExportedImage: function (image) {
      window.KeyxifDB.getExported(image.id).then(function (rec) {
        if (!rec || !rec.blob) { message('이미지를 열 수 없습니다.'); return; }
        downloadBlob(rec.blob, rec.fileName);
      });
    },
    removeExportedImageRecord: function (id) {
      window.KeyxifDB.deleteExported(id).then(refreshExported);
    },
    deleteExportedImageFile: function (image) { actions.deleteExportedImageFiles([image]); },
    deleteExportedImageFiles: function (images) {
      var chain = Promise.resolve();
      images.forEach(function (img) {
        chain = chain.then(function () { return window.KeyxifDB.deleteExported(img.id); });
      });
      chain.then(refreshExported).then(function () {
        message('이미지 ' + images.length + '개를 삭제했습니다.');
      });
    },
    deleteAllExportedImages: function () {
      actions.deleteExportedImageFiles(state.exportedImages.slice());
    },
    pruneMissingExportedImages: function () {
      var removed = 0;
      var chain = Promise.resolve();
      state.exportedImages.forEach(function (img) {
        chain = chain.then(function () {
          return window.KeyxifDB.hasExportedBlob(img.id).then(function (ok) {
            if (!ok) { removed++; return window.KeyxifDB.deleteExported(img.id); }
          });
        });
      });
      chain.then(refreshExported).then(function () {
        message('접근 불가 항목 ' + removed + '개를 정리했습니다.');
      });
    },
    clearExportedImageRecords: function () {
      window.KeyxifDB.clearExported().then(refreshExported).then(function () {
        message('완성 이미지 목록 기록을 초기화했습니다.');
      });
    },

    // ---- Draft
    restoreDraftSession: function () {
      var draft = pendingDraft;
      if (!draft) { state.showDraftRestorePrompt = false; emit(); return; }
      autoSaveReady = false;
      state.settings = draft.settings;
      window.KeyxifDB.saveJSON('keyxif.settings', draft.settings);
      applyTheme();
      var validated = [];
      var chain = Promise.resolve();
      draft.photoItems.forEach(function (p) {
        chain = chain.then(function () {
          return window.KeyxifDB.hasSource(p.uri).then(function (ok) {
            if (ok) {
              return ensureObjectURL(p.uri).then(function () {
                p.renderStatus = 'Idle'; p.errorMessage = null;
                validated.push(p);
              });
            }
            p.renderStatus = 'Error';
            p.errorMessage = '사진 접근 권한이 만료되었습니다.';
            validated.push(p);
          });
        });
      });
      chain.then(function () {
        state.photos = validated;
        state.selectedTemplate = draft.selectedTemplate;
        state.currentStep = draft.currentStep;
        state.selectedPhotoId = draft.selectedPhotoId;
        state.isSettingsOpen = false;
        state.showDraftRestorePrompt = false;
        state.draftLastUpdatedAt = draft.lastUpdatedAt;
        pendingDraft = null;
        autoSaveReady = true;
        message('이전 작업을 복구했습니다.');
        schedulePaletteAnalysis();
      });
    },
    discardDraftSession: function () {
      window.KeyxifDB.removeKey('keyxif.draft');
      pendingDraft = null;
      state.photos = [];
      state.selectedPhotoId = null;
      state.currentStep = 'Photos';
      state.showDraftRestorePrompt = false;
      state.draftLastUpdatedAt = null;
      autoSaveReady = true;
      message('이전 작업을 폐기했습니다.');
    },

    // ---- Misc
    openSupportEmail: function () {
      var href = 'mailto:typenews902@gmail.com?subject=' + encodeURIComponent('Keyxif 문의');
      try { window.location.href = href; } catch (e) {
        if (navigator.clipboard) navigator.clipboard.writeText('typenews902@gmail.com');
        message('메일 앱을 열 수 없어 이메일 주소를 복사했습니다.');
      }
    },

    // ---- 커스텀 로고 업로드 (BuildInfoScreen)
    uploadCustomLogo: function (file) {
      var p = selectedPhoto();
      if (!p || !file || file.type.indexOf('image/') !== 0) return Promise.resolve();
      var srcId = 'logo_' + Date.now() + '_' + sanitizeName(file.name || 'logo');
      return window.KeyxifDB.putSource({ id: srcId, name: file.name || 'logo', type: file.type, blob: file })
        .then(function () {
          objectUrls[srcId] = URL.createObjectURL(file);
          p.buildInfo = Object.assign({}, p.buildInfo, {
            customLogoUri: srcId, logoId: null, logoDisabled: false,
          });
          emit();
        });
    },
  };

  /* ------------------------------------------------------------------ */
  /* Init                                                                 */
  /* ------------------------------------------------------------------ */

  function init() {
    return window.KeyxifDB.init().then(function () {
      var savedSettings = window.KeyxifDB.loadJSON('keyxif.settings');
      state.settings = normalizeSettings(savedSettings);
      state.selectedTemplate = state.settings.defaultTemplate;
      appliedInitialSettings = true;
      applyTheme();
      loadRecents();
      loadBuildPresets();
      return refreshExported();
    }).then(function () {
      var draft = decodeDraft(window.KeyxifDB.loadJSON('keyxif.draft'));
      if (!draft || draft.photoItems.length === 0 || !state.settings.autoRestoreDraftSession) {
        autoSaveReady = true;
        emit();
        return;
      }
      pendingDraft = draft;
      if (state.settings.askBeforeRestoreDraft) {
        state.showDraftRestorePrompt = true;
        state.draftLastUpdatedAt = draft.lastUpdatedAt;
        emit();
      } else {
        actions.restoreDraftSession();
      }
    });
  }

  /* ------------------------------------------------------------------ */
  /* Public API                                                           */
  /* ------------------------------------------------------------------ */

  window.KeyxifStore = {
    init: init,
    getState: function () { return state; },
    subscribe: subscribe,
    actions: actions,
    selectedPhoto: selectedPhoto,
    findPhoto: findPhoto,
    helpers: {
      text: text,
      resolvedLanguage: resolvedLanguage,
      fmtDate: fmtDate,
      getObjectURL: getObjectURL,
      ensureObjectURL: ensureObjectURL,
      colorToCss: function (c) { return window.KeyxifRenderer.utils.colorToCss(c); },
      esc: function (s) {
        return String(s == null ? '' : s)
          .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
          .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
      },
      meaningful: function (s) { return U().meaningfulBuildTextOrNull(s); },
      VERSION: VERSION,
    },
    consts: {
      CARD_TEMPLATES: CARD_TEMPLATES,
      TEMPLATE_NAME: TEMPLATE_NAME,
      TEMPLATE_DESC: TEMPLATE_DESC,
      STEPS: STEPS,
      QUALITY_PRESETS: QUALITY_PRESETS,
      QUALITY_PRESET_NAME: QUALITY_PRESET_NAME,
      FILE_NAME_RULE_NAME: FILE_NAME_RULE_NAME,
      SAVE_LONG_SIDE_LIMIT: SAVE_LONG_SIDE_LIMIT,
      PREVIEW_LONG_SIDE_LIMIT: PREVIEW_LONG_SIDE_LIMIT,
    },
  };
})();
