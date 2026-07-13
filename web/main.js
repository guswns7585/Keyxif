/* =============================================================================
   Keyxif Web — 앱 셸 (KeyxifApp.kt 포트)
   topbar / stepbar / screen 라우팅 / bottombar / snackbar / draft 다이얼로그 / back
   ============================================================================= */
(function () {
  'use strict';

  var SHELL_ICONS = {
    back: '<svg viewBox="0 0 24 24"><path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"/></svg>',
    gallery: '<svg viewBox="0 0 24 24"><path d="M22 16V4c0-1.1-.9-2-2-2H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2zm-11-4 2.03 2.71L16 11l4 5H8l3-4zM2 6v14c0 1.1.9 2 2 2h14v-2H4V6H2z"/></svg>',
    settings: '<svg viewBox="0 0 24 24"><path d="M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z"/></svg>',
    check: '<svg viewBox="0 0 24 24"><path d="M9 16.17 4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"/></svg>',
  };

  var store, actions, helpers, consts;
  var appEl, blurEl;
  var renderScheduled = false;

  /* ---- 데스크톱(PC) 감지 — 폭 900px 이상이면 2단 레이아웃 ---- */
  var desktopMedia = window.matchMedia ? window.matchMedia('(min-width: 900px)') : null;
  function isDesktop() { return desktopMedia ? desktopMedia.matches : window.innerWidth >= 900; }

  function h(html) {
    var t = document.createElement('template');
    t.innerHTML = html.trim();
    return t.content.firstChild;
  }

  /* ---- 포커스 보존 ---- */
  function captureFocus() {
    var el = document.activeElement;
    if (!el || !el.getAttribute) return null;
    var key = el.getAttribute('data-key');
    if (!key) return null;
    var start = null, end = null;
    try { start = el.selectionStart; end = el.selectionEnd; } catch (e) { /* range/checkbox 등 */ }
    return { key: key, start: start, end: end };
  }
  function restoreFocus(saved) {
    if (!saved) return;
    var el = appEl.querySelector('[data-key="' + saved.key + '"]');
    if (!el) return;
    el.focus();
    try {
      if (saved.start != null) el.setSelectionRange(saved.start, saved.end);
    } catch (e) {}
  }

  /* ---- 스크롤 위치 보존 (재렌더 시 맨 위로 튀지 않도록) ---- */
  function captureScroll() {
    var res = {};
    ['.workarea-scroll', '.screen', '.view-pad'].forEach(function (sel) {
      var el = blurEl.querySelector(sel);
      if (el && el.scrollTop > 0) res[sel] = el.scrollTop;
    });
    return res;
  }
  function restoreScroll(saved) {
    if (!saved) return;
    Object.keys(saved).forEach(function (sel) {
      var el = blurEl.querySelector(sel);
      if (el) el.scrollTop = saved[sel];
    });
  }

  /* ---- Snackbar (§4.4: consume-then-show) ---- */
  var snackHost;
  function showSnackbar(text) {
    if (!snackHost) return;
    var el = h('<div class="snackbar"></div>');
    el.textContent = text;
    snackHost.appendChild(el);
    setTimeout(function () {
      el.style.transition = 'opacity 200ms ease';
      el.style.opacity = '0';
      setTimeout(function () { el.remove(); }, 220);
    }, 3000);
  }

  /* ---- Topbar ---- */
  function buildTopbar(state) {
    var esc = helpers.esc;
    var bar = h('<div class="topbar"></div>');
    var row = h('<div class="topbar-row"></div>');

    if (state.isSettingsOpen || state.isGalleryOpen) {
      var back = h('<button class="icon-btn" aria-label="' + helpers.text('뒤로가기', 'Back') + '">' + SHELL_ICONS.back + '</button>');
      back.addEventListener('click', function () {
        if (state.isSettingsOpen) actions.closeSettings();
        else actions.closeGallery();
      });
      row.appendChild(back);
      var title = state.isSettingsOpen ? helpers.text('설정', 'Settings') : helpers.text('완성 이미지', 'Finished Images');
      row.appendChild(h('<div class="headline-small grow">' + esc(title) + '</div>'));
    } else {
      // 앱 이름 클릭 → 메인(사진) 화면으로 복귀 (제목 영역만 클릭 대상, 빈 공간은 스페이서)
      var t = h('<button class="topbar-title" style="flex:none;cursor:pointer" aria-label="메인 화면으로"><span class="headline-small">Keyxif</span><span class="topbar-dot"></span></button>');
      t.addEventListener('click', function () { actions.navigateToStep('Photos'); });
      row.appendChild(t);
      row.appendChild(h('<div class="grow"></div>'));
      if (state.photos.length > 0) {
        row.appendChild(h('<div class="photo-pill label-medium">' +
          helpers.text('사진 ' + state.photos.length + '장', state.photos.length + ' photos') + '</div>'));
      }
      var g = h('<button class="icon-btn" aria-label="' + helpers.text('완성 이미지', 'Finished Images') + '">' + SHELL_ICONS.gallery + '</button>');
      g.addEventListener('click', actions.openGallery);
      row.appendChild(g);
      var s = h('<button class="icon-btn" aria-label="' + helpers.text('설정', 'Settings') + '">' + SHELL_ICONS.settings + '</button>');
      s.addEventListener('click', actions.openSettings);
      row.appendChild(s);
    }
    bar.appendChild(row);

    // Step bar (메인 모드 한정)
    if (!state.isSettingsOpen && !state.isGalleryOpen) {
      var stepBar = h('<div class="stepbar"></div>');
      var stepLabels = {
        Photos: helpers.text('사진', 'Photos'), BuildInfo: helpers.text('정보', 'Info'),
        Template: helpers.text('템플릿', 'Template'), Export: helpers.text('저장', 'Export'),
      };
      var steps = state.settings.showPaletteColors ? consts.STEPS.slice() : ['Photos', 'BuildInfo', 'Template', 'Export'];
      var currentIdx = steps.indexOf(state.currentStep);
      steps.forEach(function (step, i) {
        var cls = 'step-chip' + (i === currentIdx ? ' selected' : (i < currentIdx ? ' done' : ''));
        var badge = i < currentIdx
          ? '<span class="step-badge">' + SHELL_ICONS.check + '</span>'
          : '<span class="step-badge">' + (i + 1) + '</span>';
        var chip = h('<button class="' + cls + ' label-medium">' + badge + '<span>' + (stepLabels[step] || helpers.text('색상', 'Color')) + '</span></button>');
        chip.addEventListener('click', function () { actions.navigateToStep(step); });
        stepBar.appendChild(chip);
      });
      bar.appendChild(stepBar);
    }
    return bar;
  }

  /* ---- Bottom bar (§4.2) ---- */
  function buildBottombar(state) {
    if (state.isSettingsOpen || state.isGalleryOpen) return null;
    var bar = h('<div class="bottombar"></div>');
    var isSaving = state.exportProgress.isSaving;
    var steps = state.settings.showPaletteColors ? consts.STEPS.slice() : ['Photos', 'BuildInfo', 'Template', 'Export'];
    var stepIdx = steps.indexOf(state.currentStep);
    var hasPrev = stepIdx > 0;

    if (hasPrev) {
      var prev = h('<button class="btn btn-outlined grow"' + (isSaving ? ' disabled' : '') + '>' + helpers.text('이전', 'Previous') + '</button>');
      prev.addEventListener('click', actions.navigateToPreviousStep);
      bar.appendChild(prev);
    }

    if (state.currentStep === 'Export') {
      var n = Object.keys(state.selectedExportPhotoIds).length;
      var selLabel = n > 0
        ? helpers.text('선택 저장 ' + n, 'Save selected ' + n)
        : helpers.text('선택 저장', 'Save selected');
      var selBtn = h('<button class="btn btn-tonal grow"' + (n > 0 && !isSaving ? '' : ' disabled') + '>' + selLabel + '</button>');
      selBtn.addEventListener('click', function () {
        actions.savePhotos(Object.keys(state.selectedExportPhotoIds));
      });
      bar.appendChild(selBtn);
      var allBtn = h('<button class="btn btn-filled grow"' + (state.photos.length > 0 && !isSaving ? '' : ' disabled') + '>' + helpers.text('전체 저장', 'Save all') + '</button>');
      allBtn.addEventListener('click', actions.saveAll);
      bar.appendChild(allBtn);
    } else {
      var labels = {
        Photos: helpers.text('빌드 정보 입력', 'Enter build info'),
        BuildInfo: state.settings.showPaletteColors
          ? helpers.text('색상 설정', 'Set colors')
          : helpers.text('템플릿 선택', 'Choose template'),
        Palette: helpers.text('템플릿 선택', 'Choose template'),
        Template: helpers.text('미리보기 · 저장', 'Preview & save'),
      };
      var next = h('<button class="btn btn-filled ' + (hasPrev ? 'grow2' : 'grow') + '"' + (isSaving ? ' disabled' : '') + '>' + (labels[state.currentStep] || helpers.text('템플릿 선택', 'Choose template')) + '</button>');
      next.addEventListener('click', function () {
        if (state.currentStep === 'Photos') actions.navigateToStep('BuildInfo');
        else if (state.currentStep === 'BuildInfo') actions.completeBuildInfo();
        else if (state.currentStep === 'Palette') actions.navigateToStep('Template');
        else actions.navigateToStep('Export');
      });
      bar.appendChild(next);
    }
    return bar;
  }

  /* ---- 메인 렌더 ---- */
  function render() {
    renderScheduled = false;
    var state = store.getState();

    // 소비형 스낵바
    if (state.uiMessage != null) {
      var msg = state.uiMessage;
      actions.consumeMessage();
      showSnackbar(msg);
    }

    var focus = captureFocus();
    var scroll = captureScroll();
    // 이전 렌더의 풀스크린 오버레이 제거 (body 직속)
    document.querySelectorAll('body > .fullscreen-preview').forEach(function (el) { el.remove(); });
    blurEl.innerHTML = '';

    blurEl.appendChild(buildTopbar(state));

    var UI = window.KeyxifUI;
    var mainMode = !state.isSettingsOpen && !state.isGalleryOpen;
    var viewName = state.isSettingsOpen ? 'settings'
      : state.isGalleryOpen ? 'gallery'
        : state.currentStep.toLowerCase();
    // 데스크톱 미리보기 패널: 정보/템플릿 단계 + 선택된 사진이 있을 때
    var showPane = isDesktop() && mainMode &&
      (state.currentStep === 'BuildInfo' || state.currentStep === 'Palette' || state.currentStep === 'Template') &&
      !!store.selectedPhoto();

    var workarea = document.createElement('div');
    workarea.className = 'workarea' + (showPane ? ' has-pane' : '');
    workarea.setAttribute('data-view', viewName);
    var inner = document.createElement('div');
    inner.className = 'workarea-scroll';
    workarea.appendChild(inner);
    blurEl.appendChild(workarea);

    if (state.isSettingsOpen || state.isGalleryOpen) {
      // 갤러리/설정은 자체 패딩 래퍼(.view-pad)에 렌더
      var viewPad = document.createElement('div');
      viewPad.className = 'view-pad';
      inner.appendChild(viewPad);
      if (state.isSettingsOpen) UI.renderSettings(viewPad, state, actions, helpers);
      else UI.renderGallery(viewPad, state, actions, helpers);
    } else if (state.currentStep === 'Photos') UI.renderPhotos(inner, state, actions, helpers);
    else if (state.currentStep === 'BuildInfo') UI.renderBuildInfo(inner, state, actions, helpers);
    else if (state.currentStep === 'Palette') UI.renderPalette(inner, state, actions, helpers);
    else if (state.currentStep === 'Template') UI.renderTemplate(inner, state, actions, helpers);
    else UI.renderExport(inner, state, actions, helpers);

    if (showPane) {
      var pane = UI.buildPreviewPane(state, actions, helpers);
      if (pane) workarea.appendChild(pane);
    }

    var bottom = buildBottombar(state);
    if (bottom) blurEl.appendChild(bottom);

    // Export 확대 시 배경 블러 (§3.22)
    var blurred = !state.isSettingsOpen && !state.isGalleryOpen &&
      state.currentStep === 'Export' && state.expandedExportPhotoId != null &&
      state.settings.enableExportPreviewZoom;
    blurEl.classList.toggle('blurred', blurred);

    // Draft 복구 다이얼로그 (모달, 바깥 클릭 무시)
    var existing = appEl.querySelector('.draft-dialog-host');
    if (existing) existing.remove();
    if (state.showDraftRestorePrompt) {
      var host = document.createElement('div');
      host.className = 'draft-dialog-host';
      host.appendChild(UI.buildDialog({
        title: '이전 작업을 복구할까요?',
        body: '임시 저장된 Keyxif 작업이 있습니다.\n마지막 저장: ' + helpers.fmtDate(state.draftLastUpdatedAt),
        confirmLabel: '복구',
        cancelLabel: '새로 시작',
        modal: true,
        onConfirm: actions.restoreDraftSession,
        onCancel: actions.discardDraftSession,
      }));
      appEl.appendChild(host);
    }

    restoreScroll(scroll);
    restoreFocus(focus);
  }

  function scheduleRender() {
    if (renderScheduled) return;
    renderScheduled = true;
    // rAF는 백그라운드 탭에서 발화하지 않으므로 setTimeout 사용
    setTimeout(render, 0);
  }

  /* ---- Back handling (§4.5) ---- */
  function setupBackHandling() {
    history.replaceState({ keyxif: true }, '');
    history.pushState({ keyxif: true }, '');
    window.addEventListener('popstate', function () {
      var handled = actions.handleSystemBack();
      if (handled) history.pushState({ keyxif: true }, '');
    });
  }

  /* ---- Boot ---- */
  function boot() {
    appEl = document.getElementById('app');
    blurEl = document.getElementById('app-blur-target');
    snackHost = document.getElementById('snackbar-host');

    store = window.KeyxifStore;
    actions = store.actions;
    helpers = store.helpers;
    consts = store.consts;

    // 파비콘
    if (window.KEYXIF_ASSETS && window.KEYXIF_ASSETS.ic_keyxif) {
      var link = document.createElement('link');
      link.rel = 'icon';
      link.href = window.KEYXIF_ASSETS.ic_keyxif;
      document.head.appendChild(link);
    }

    window.KeyxifUI.setNotify(scheduleRender);
    store.subscribe(scheduleRender);
    // 데스크톱↔모바일 전환 시 레이아웃 재구성
    if (desktopMedia && desktopMedia.addEventListener) desktopMedia.addEventListener('change', scheduleRender);
    setupBackHandling();
    store.init().then(scheduleRender);
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();
