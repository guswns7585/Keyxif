/* =============================================================================
   Keyxif UI — 메인 4단계 화면 (PhotoPicker / BuildInfo / TemplateSelect / Export)
   screens-spec.md 1:1 구현. 의존: KeyxifStore, KeyxifSearch, KeyxifRenderer
   ============================================================================= */
(function () {
  'use strict';
  window.KeyxifUI = window.KeyxifUI || {};

  /* ---- Icons (Material style inline SVG) ---- */
  var ICONS = {
    add: '<svg viewBox="0 0 24 24"><path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6z"/></svg>',
    del: '<svg viewBox="0 0 24 24"><path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/></svg>',
    up: '<svg viewBox="0 0 24 24"><path d="M7.41 15.41 12 10.83l4.59 4.58L18 14l-6-6-6 6z"/></svg>',
    down: '<svg viewBox="0 0 24 24"><path d="M7.41 8.59 12 13.17l4.59-4.58L18 10l-6 6-6-6z"/></svg>',
    close: '<svg viewBox="0 0 24 24"><path d="M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/></svg>',
    check: '<svg viewBox="0 0 24 24"><path d="M9 16.17 4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"/></svg>',
    photo: '<svg viewBox="0 0 24 24"><path d="M18 20H4V6h9V4H4c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2v-9h-2v9zm-7.79-3.17-1.96-2.36L5.5 18h11l-3.54-4.71zM20 4V1h-2v3h-3c.01.01 0 2 0 2h3v2.99c.01.01 2 0 2 0V6h3V4h-3z"/></svg>',
    upload: '<svg viewBox="0 0 24 24"><path d="M9 16h6v-6h4l-7-7-7 7h4zm-4 2h14v2H5z"/></svg>',
    broken: '<svg viewBox="0 0 24 24"><path d="M21 5v6.59l-3-3.01-4 4.01-4-4-4 4-3-3.01V5c0-1.1.9-2 2-2h14c1.1 0 2 .9 2 2zm-3 6.42 3 3.01V19c0 1.1-.9 2-2 2H5c-1.1 0-2-.9-2-2v-6.58l3 2.99 4-4 4 4z"/></svg>',
    refresh: '<svg viewBox="0 0 24 24"><path d="M17.65 6.35A7.958 7.958 0 0 0 12 4c-4.42 0-7.99 3.58-8 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08a5.99 5.99 0 0 1-5.65 4c-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z"/></svg>',
    search: '<svg viewBox="0 0 24 24"><path d="M15.5 14h-.79l-.28-.27a6.5 6.5 0 1 0-.7.7l.27.28v.79l5 4.99L20.49 19zm-6 0A4.5 4.5 0 1 1 14 9.5 4.49 4.49 0 0 1 9.5 14z"/></svg>',
  };
  window.KeyxifUI.ICONS = ICONS;

  /* ---- 로컬 UI 상태 (재렌더에도 유지) ---- */
  var ui = {
    clearConfirmOpen: false,        // 전체 삭제 다이얼로그
    presetSheetOpen: false,         // 빌드 프리셋 피커 시트
    presetSheetQuery: '',
    presetDeleteTarget: null,       // {id, presetName}
    presetNameInput: '',
    presetNamePhotoId: null,        // 사진 전환 시 프리셋명 리셋
    searchSheet: null,              // { field: 'housing'|'switch'|'keycap', query, hiddenRecents: {} }
    fullscreen: null,               // { index, scale, offsetX, offsetY }
    renderCache: {},                // key -> { status, dataUrl, retry }
    tplSketchCache: {},             // templateId -> dataURL
  };
  var notify = function () {};
  window.KeyxifUI.setNotify = function (fn) { notify = fn; };
  window.KeyxifUI.localState = ui;

  function h(html) {
    var t = document.createElement('template');
    t.innerHTML = html.trim();
    return t.content.firstChild;
  }
  function frag(html) {
    var t = document.createElement('template');
    t.innerHTML = html;
    return t.content;
  }

  /* =====================================================================
     1. PhotoPickerScreen
     ===================================================================== */
  function renderPhotos(container, state, actions, helpers) {
    var esc = helpers.esc;
    var root = document.createElement('div');
    root.className = 'screen';

    // 상단 버튼 행
    var topRow = h('<div class="row gap8"></div>');
    var addBtn = h('<button class="btn btn-filled grow">' + ICONS.add + '<span>사진 추가</span></button>');
    addBtn.addEventListener('click', function () { pickPhotos(actions); });
    var clearBtn = h('<button class="btn btn-outlined"' + (state.photos.length ? '' : ' disabled') + '>' + ICONS.del + '<span>전체 삭제</span></button>');
    clearBtn.addEventListener('click', function () { ui.clearConfirmOpen = true; notify(); });
    topRow.appendChild(addBtn); topRow.appendChild(clearBtn);
    root.appendChild(topRow);

    // 공유 메시지 배너
    if (state.shareMessage) {
      var banner = h('<div class="banner"><span class="body-medium grow">' + esc(state.shareMessage) + '</span></div>');
      var ok = h('<button class="btn btn-text">확인</button>');
      ok.addEventListener('click', actions.clearShareMessage);
      banner.appendChild(ok);
      root.appendChild(banner);
    }

    if (state.photos.length === 0) {
      var empty = h(
        '<div class="empty-state">' +
        '<div class="empty-icon">' + ICONS.photo + '</div>' +
        '<div style="height:6px"></div>' +
        '<div class="title-large">키보드 사진으로 시작하세요</div>' +
        '<div class="body-medium muted">사진을 고르고 빌드 정보를 입력하면<br>스펙 카드가 함께 담긴 이미지로 저장됩니다.</div>' +
        '<div style="height:6px"></div>' +
        '</div>');
      var cta = h('<button class="btn btn-filled">' + ICONS.add + '<span>사진 선택하기</span></button>');
      cta.addEventListener('click', function () { pickPhotos(actions); });
      empty.appendChild(cta);
      root.appendChild(empty);
    } else {
      var list = h('<div class="col gap10"></div>');
      state.photos.forEach(function (p, index) {
        var housingText = helpers.meaningful(p.buildInfo.housing) || '빌드 정보 미입력';
        var url = helpers.getObjectURL(p.uri);
        var card = h(
          '<div class="card photo-card">' +
          '<div class="photo-thumb-wrap">' +
          (url ? '<img src="' + url + '" alt="">' : '<div style="width:82px;height:82px;border-radius:12px;background:var(--surface-container-high)"></div>') +
          '<div class="order-badge">' + (index + 1) + '</div>' +
          '</div>' +
          '<div class="col gap3 grow">' +
          '<div class="title-small ellipsis-1">' + esc(p.displayName) + '</div>' +
          '<div class="body-small muted ellipsis-1">' + esc(housingText) + '</div>' +
          '</div>' +
          '</div>');
        var upB = h('<button class="icon-btn" aria-label="위로 이동"' + (index === 0 ? ' disabled' : '') + '>' + ICONS.up + '</button>');
        upB.addEventListener('click', function () { actions.movePhoto(p.id, -1); });
        var downB = h('<button class="icon-btn" aria-label="아래로 이동"' + (index === state.photos.length - 1 ? ' disabled' : '') + '>' + ICONS.down + '</button>');
        downB.addEventListener('click', function () { actions.movePhoto(p.id, 1); });
        var rmB = h('<button class="icon-btn" aria-label="삭제">' + ICONS.close + '</button>');
        rmB.addEventListener('click', function () { actions.removePhoto(p.id); });
        card.appendChild(upB); card.appendChild(downB); card.appendChild(rmB);
        list.appendChild(card);
      });
      root.appendChild(list);
    }

    container.appendChild(root);

    // 전체 삭제 확인 다이얼로그
    if (ui.clearConfirmOpen) {
      container.appendChild(buildDialog({
        title: '사진을 모두 지울까요?',
        body: '업로드한 사진 ' + state.photos.length + '장을 Keyxif 작업 목록에서 모두 제거합니다.',
        confirmLabel: '전체 삭제', confirmClass: 'btn-error',
        onConfirm: function () { ui.clearConfirmOpen = false; actions.clearPhotos(); },
        onCancel: function () { ui.clearConfirmOpen = false; notify(); },
      }));
    }
  }

  function pickPhotos(actions) {
    var input = document.createElement('input');
    input.type = 'file'; input.accept = 'image/*'; input.multiple = true;
    input.addEventListener('change', function () {
      if (input.files && input.files.length) actions.addPhotos(input.files);
    });
    input.click();
  }

  /* =====================================================================
     공통: 다이얼로그 / 텍스트필드
     ===================================================================== */
  function buildDialog(opts) {
    var scrim = h('<div class="dialog-scrim"></div>');
    var dlg = h('<div class="dialog"></div>');
    if (opts.title) dlg.appendChild(h('<div class="title-large" style="font-size:20px">' + opts.title + '</div>'));
    if (opts.body) dlg.appendChild(h('<div class="body-medium" style="white-space:pre-line">' + opts.body + '</div>'));
    if (opts.content) dlg.appendChild(opts.content);
    var acts = h('<div class="dialog-actions"></div>');
    if (opts.onCancel) {
      var c = h('<button class="btn btn-text">' + (opts.cancelLabel || '취소') + '</button>');
      c.addEventListener('click', opts.onCancel);
      acts.appendChild(c);
    }
    if (opts.onConfirm) {
      var b = h('<button class="btn ' + (opts.confirmClass || 'btn-filled') + ' h40">' + (opts.confirmLabel || '확인') + '</button>');
      b.addEventListener('click', opts.onConfirm);
      acts.appendChild(b);
    }
    dlg.appendChild(acts);
    if (!opts.modal && opts.onCancel) {
      scrim.addEventListener('click', function (e) { if (e.target === scrim) opts.onCancel(); });
    }
    scrim.appendChild(dlg);
    return scrim;
  }

  // ClearableTextField
  function buildField(opts) {
    var wrap = h('<div class="field' + (opts.trailingBtn ? ' has-trailing' : ' has-clear-only') + '"></div>');
    var input = document.createElement('input');
    input.type = 'text';
    input.value = opts.value || '';
    input.placeholder = opts.placeholder || '';
    if (opts.dataKey) input.setAttribute('data-key', opts.dataKey);
    wrap.appendChild(input);
    if (opts.label) wrap.appendChild(h('<label>' + opts.label + '</label>'));
    var trailing = h('<div class="trailing"></div>');
    var clearBtn = h('<button class="icon-btn small' + (input.value ? '' : ' hidden') + '" aria-label="입력 내용 지우기">' + ICONS.close + '</button>');
    clearBtn.addEventListener('click', function () {
      input.value = '';
      clearBtn.classList.add('hidden');
      if (opts.onInput) opts.onInput('');
      input.focus();
    });
    trailing.appendChild(clearBtn);
    if (opts.trailingBtn) {
      var tb = h('<button class="btn btn-tonal h40 field-search-btn">' + ICONS.search + '<span>' + opts.trailingBtn + '</span></button>');
      tb.addEventListener('click', function () { if (opts.onTrailing) opts.onTrailing(input.value); });
      trailing.appendChild(tb);
    }
    wrap.appendChild(trailing);
    // imeSafe: 커밋이 전체 재렌더를 유발하는 필드는 한글 IME 조합 중 커밋을 보류.
    // (조합 중 재렌더 → input 교체 → 조합 세션 파괴 → 자모 분리 입력)
    var composing = false;
    input.addEventListener('compositionstart', function () { composing = true; });
    input.addEventListener('compositionend', function () {
      composing = false;
      if (opts.imeSafe && opts.onInput) {
        var v = input.value;
        // 같은 키 입력 태스크에서 다음 음절의 compositionstart가 이어질 수 있으므로
        // 미뤄서 커밋하고, 새 조합이 시작됐으면 건너뜀 (blur/change가 백스톱)
        setTimeout(function () { if (!composing) opts.onInput(v); }, 0);
      }
    });
    input.addEventListener('input', function (e) {
      clearBtn.classList.toggle('hidden', !input.value);
      if (opts.imeSafe && (composing || e.isComposing)) return;
      if (opts.onInput) opts.onInput(input.value);
    });
    input.addEventListener('change', function () {
      if (opts.imeSafe && opts.onInput) opts.onInput(input.value);
    });
    return { wrap: wrap, input: input };
  }

  /* =====================================================================
     2. BuildInfoScreen
     ===================================================================== */
  function renderBuildInfo(container, state, actions, helpers) {
    var esc = helpers.esc;
    var sel = window.KeyxifStore.selectedPhoto();
    var root = document.createElement('div');
    root.className = 'screen';

    if (ui.presetNamePhotoId !== (sel && sel.id)) {
      ui.presetNamePhotoId = sel && sel.id;
      ui.presetNameInput = '';
    }

    // ---- 섹션 1: 편집 중인 사진
    var s1 = h('<div class="card form-section"><div class="title-medium" style="font-weight:700">편집 중인 사진</div></div>');
    if (sel) {
      var selIdx = state.photos.findIndex(function (p) { return p.id === sel.id; });
      var url = helpers.getObjectURL(sel.uri);
      s1.appendChild(h(
        '<div class="row gap12">' +
        (url ? '<img src="' + url + '" style="width:72px;height:72px;object-fit:cover;border-radius:6px;flex:none">' : '') +
        '<div class="col gap2 grow">' +
        '<div class="title-medium ellipsis-2">' + esc(sel.displayName) + '</div>' +
        '<div class="body-small muted">' + (selIdx + 1) + ' / ' + state.photos.length + '</div>' +
        '</div></div>'));
    }
    var strip = h('<div class="thumb-strip"></div>');
    state.photos.forEach(function (p) {
      var u = helpers.getObjectURL(p.uri);
      var img = h('<img src="' + (u || '') + '" class="' + (sel && p.id === sel.id ? 'selected' : '') + '" alt="">');
      img.addEventListener('click', function () { actions.selectPhoto(p.id); });
      strip.appendChild(img);
    });
    s1.appendChild(strip);
    var applyAll = h('<button class="btn btn-outlined full"' + (sel && state.photos.length >= 2 ? '' : ' disabled') + '>이 빌드 정보를 모든 사진에 적용</button>');
    applyAll.addEventListener('click', actions.applyBuildInfoToAll);
    s1.appendChild(applyAll);
    var resetInfo = h('<button class="btn btn-outlined full"' + (sel ? '' : ' disabled') + '>빌드 정보 초기화</button>');
    resetInfo.addEventListener('click', actions.clearSelectedBuildInfo);
    s1.appendChild(resetInfo);
    root.appendChild(s1);

    // ---- 섹션 2: 빌드 프리셋
    var s2 = h('<div class="card form-section"><div class="title-medium" style="font-weight:700">빌드 프리셋</div></div>');
    var applied = null;
    if (sel) {
      var infoJson = JSON.stringify(sel.buildInfo);
      for (var i = 0; i < state.buildPresets.length; i++) {
        if (JSON.stringify(state.buildPresets[i].buildInfo) === infoJson) { applied = state.buildPresets[i]; break; }
      }
    }
    s2.appendChild(h('<div class="body-medium muted">' + (applied ? '현재 적용: ' + esc(applied.presetName) : '현재 적용된 프리셋 없음') + '</div>'));
    var openPicker = h('<button class="btn btn-filled full">빌드 프리셋 불러오기</button>');
    openPicker.addEventListener('click', function () { ui.presetSheetOpen = true; ui.presetSheetQuery = ''; notify(); });
    s2.appendChild(openPicker);
    if (state.buildPresets.length > 0) {
      s2.appendChild(h('<div class="label-medium muted">최근 프리셋</div>'));
      state.buildPresets.slice(0, 3).forEach(function (bp) {
        s2.appendChild(buildPresetRow(bp, actions, helpers));
      });
      var seeAll = h('<button class="btn btn-text" style="align-self:flex-start">전체 보기</button>');
      seeAll.addEventListener('click', function () { ui.presetSheetOpen = true; ui.presetSheetQuery = ''; notify(); });
      s2.appendChild(seeAll);
    } else {
      s2.appendChild(h('<div class="body-medium muted">저장된 프리셋이 없습니다.</div>'));
    }
    var saveRow = h('<div class="row gap8"></div>');
    var nameField = buildField({
      label: '새 프리셋 이름', placeholder: '비우면 자동 생성', value: ui.presetNameInput,
      dataKey: 'preset-name',
      onInput: function (v) { ui.presetNameInput = v; },
    });
    nameField.wrap.classList.add('grow');
    var saveBtn = h('<button class="btn btn-filled"' + (sel ? '' : ' disabled') + '>저장</button>');
    saveBtn.addEventListener('click', function () {
      actions.saveBuildPreset(ui.presetNameInput);
      ui.presetNameInput = '';
      notify();
    });
    saveRow.appendChild(nameField.wrap); saveRow.appendChild(saveBtn);
    s2.appendChild(saveRow);
    root.appendChild(s2);

    // ---- 섹션 3: 빌드 정보
    var info = sel ? sel.buildInfo : defaultInfo();
    var s3 = h('<div class="card form-section"><div class="title-medium" style="font-weight:700">빌드 정보</div></div>');
    appendRecentChips(s3, '최근 사용한 하우징', state.recentHousing, function (v) { setInfoField(actions, info, 'housing', v); });
    s3.appendChild(buildSearchField('housing', '하우징', '하우징 검색 또는 직접 입력', info.housing, actions, helpers));
    appendRecentChips(s3, '최근 사용한 스위치', state.recentSwitches, function (v) { setInfoField(actions, info, 'switchName', v); });
    s3.appendChild(buildSearchField('switch', '스위치', '스위치 검색 또는 직접 입력', info.switchName, actions, helpers));
    appendRecentChips(s3, '최근 사용한 키캡', state.recentKeycaps, function (v) { setInfoField(actions, info, 'keycap', v); });
    s3.appendChild(buildSearchField('keycap', '키캡', '키캡 검색 또는 직접 입력', info.keycap, actions, helpers));
    root.appendChild(s3);

    // ---- 섹션 4/5: 보강판 / 마운트
    root.appendChild(buildChipToggleSection('보강판', window.KeyxifSearch.plates, info.plate, function (v) {
      setInfoField(actions, info, 'plate', info.plate === v ? '' : v);
    }));
    root.appendChild(buildChipToggleSection('마운트', window.KeyxifSearch.mounts, info.mount, function (v) {
      setInfoField(actions, info, 'mount', info.mount === v ? '' : v);
    }));

    // ---- 섹션 6: 닉네임과 로고
    var s6 = h('<div class="card form-section"><div class="title-medium" style="font-weight:700">닉네임과 로고</div></div>');
    appendRecentChips(s6, '최근 사용한 닉네임', state.recentNicknames, function (v) { setInfoField(actions, info, 'nickname', v); });
    var nick = buildField({
      label: '닉네임', placeholder: '입력한 경우에만 표시합니다', value: info.nickname,
      dataKey: 'nickname', imeSafe: true,
      onInput: function (v) { setInfoField(actions, info, 'nickname', v); },
    });
    s6.appendChild(nick.wrap);

    var logoRow = h('<div class="chip-row"></div>');
    var autoSelected = !info.logoDisabled && info.logoId == null && !info.customLogoUri;
    // 로고 없음
    logoRow.appendChild(makeChip('로고 없음', info.logoDisabled, function () {
      if (info.logoDisabled) setInfo(actions, info, { logoDisabled: false });
      else setInfo(actions, info, { logoId: null, customLogoUri: null, logoDisabled: true });
    }));
    // 자동
    logoRow.appendChild(makeChip('자동', autoSelected, function () {
      if (autoSelected) setInfo(actions, info, { logoId: null, customLogoUri: null, logoDisabled: true });
      else setInfo(actions, info, { logoId: null, customLogoUri: null, logoDisabled: false });
    }));
    // 내장 로고들
    window.KeyxifSearch.logos.forEach(function (logo) {
      var on = !info.logoDisabled && info.logoId === logo.id;
      logoRow.appendChild(makeChip(esc(logo.name), on, function () {
        if (on) setInfo(actions, info, { logoId: null, logoDisabled: true });
        else setInfo(actions, info, { logoId: logo.id, customLogoUri: null, logoDisabled: false });
      }));
    });
    // 로고 업로드 / 사용자 로고
    var hasCustom = !!info.customLogoUri;
    var upChip = makeChip((hasCustom ? '사용자 로고' : '로고 업로드'), hasCustom && !info.logoDisabled, function () {
      if (hasCustom) setInfo(actions, info, { customLogoUri: null, logoDisabled: true });
      else {
        var inp = document.createElement('input');
        inp.type = 'file'; inp.accept = 'image/*';
        inp.addEventListener('change', function () {
          if (inp.files && inp.files[0]) actions.uploadCustomLogo(inp.files[0]);
        });
        inp.click();
      }
    }, ICONS.upload);
    logoRow.appendChild(upChip);
    s6.appendChild(logoRow);

    if (hasCustom && !info.logoDisabled) {
      var cUrl = helpers.getObjectURL(info.customLogoUri);
      if (cUrl) s6.appendChild(h('<img src="' + cUrl + '" alt="사용자 로고 미리보기" style="width:100%;height:72px;object-fit:contain">'));
    }
    root.appendChild(s6);
    container.appendChild(root);

    // ---- 프리셋 피커 바텀시트
    if (ui.presetSheetOpen) container.appendChild(buildPresetSheet(state, actions, helpers));
    // ---- 프리셋 삭제 확인
    if (ui.presetDeleteTarget) {
      var t = ui.presetDeleteTarget;
      container.appendChild(buildDialog({
        title: '프리셋을 삭제할까요?',
        body: '"' + esc(t.presetName) + '" 프리셋을 삭제합니다. 이 작업은 되돌릴 수 없습니다.',
        confirmLabel: '삭제', confirmClass: 'btn-error',
        onConfirm: function () { ui.presetDeleteTarget = null; actions.deleteBuildPreset(t.id); },
        onCancel: function () { ui.presetDeleteTarget = null; notify(); },
      }));
    }
    // ---- 검색 바텀시트
    if (ui.searchSheet) container.appendChild(buildSearchSheet(state, actions, helpers));
  }

  function defaultInfo() {
    return { housing: '', switchName: '', plate: '', mount: '', keycap: '', nickname: '', logoId: null, customLogoUri: null, logoDisabled: false };
  }
  function setInfo(actions, info, patch) {
    actions.updateBuildInfo(Object.assign({}, info, patch));
  }
  function setInfoField(actions, info, field, value) {
    var patch = {};
    patch[field] = value;
    setInfo(actions, info, patch);
  }
  function makeChip(label, selected, onClick, icon) {
    var c = h('<button class="chip' + (selected ? ' selected' : '') + '">' +
      (selected ? ICONS.check : (icon || '')) + '<span>' + label + '</span></button>');
    c.addEventListener('click', onClick);
    return c;
  }
  function appendRecentChips(parent, label, values, onPick) {
    if (!values || values.length === 0) return;
    var wrap = h('<div class="col gap6"><div class="label-medium muted">' + label + '</div></div>');
    var row = h('<div class="chip-row"></div>');
    values.slice(0, 3).forEach(function (v) {
      var esc = window.KeyxifStore.helpers.esc;
      var chip = h('<button class="chip"><span>' + esc(v) + '</span></button>');
      chip.addEventListener('click', function () { onPick(v); });
      row.appendChild(chip);
    });
    wrap.appendChild(row);
    parent.appendChild(wrap);
  }
  function buildChipToggleSection(title, values, current, onToggle) {
    var sec = h('<div class="card form-section"><div class="title-medium" style="font-weight:700">' + title + '</div></div>');
    var row = h('<div class="chip-row"></div>');
    values.forEach(function (v) {
      row.appendChild(makeChip(window.KeyxifStore.helpers.esc(v), current === v, function () { onToggle(v); }));
    });
    sec.appendChild(row);
    return sec;
  }

  function buildPresetRow(bp, actions, helpers) {
    var esc = helpers.esc;
    var m = helpers.meaningful;
    var descParts = [m(bp.buildInfo.housing), m(bp.buildInfo.switchName), m(bp.buildInfo.keycap)].filter(Boolean);
    var desc = descParts.length ? descParts.join(' · ') : '세부 정보 없음';
    var row = h(
      '<div class="row gap8">' +
      '<div class="col gap2 grow">' +
      '<div class="title-small ellipsis-1">' + esc(bp.presetName) + '</div>' +
      '<div class="body-small muted ellipsis-1">' + esc(desc) + '</div>' +
      '</div></div>');
    var apply = h('<button class="btn btn-outlined h40">적용</button>');
    apply.addEventListener('click', function () { actions.applyBuildPreset(bp); });
    var del = h('<button class="icon-btn error" aria-label="' + esc(bp.presetName) + ' 삭제">' + ICONS.del + '</button>');
    del.addEventListener('click', function () { ui.presetDeleteTarget = bp; notify(); });
    row.appendChild(apply); row.appendChild(del);
    return row;
  }

  function buildPresetSheet(state, actions, helpers) {
    var esc = helpers.esc;
    var scrim = h('<div class="sheet-scrim"></div>');
    var sheet = h('<div class="sheet"><div class="sheet-handle"></div><div class="title-large">빌드 프리셋 불러오기</div></div>');
    var search = buildField({
      label: '프리셋 검색', value: ui.presetSheetQuery, dataKey: 'preset-sheet-q',
      onInput: function (v) { ui.presetSheetQuery = v; refreshList(); },
    });
    sheet.appendChild(search.wrap);
    var listWrap = h('<div class="sheet-list"></div>');
    sheet.appendChild(listWrap);

    function refreshList() {
      listWrap.innerHTML = '';
      var q = ui.presetSheetQuery.trim().toLowerCase();
      var results = state.buildPresets.filter(function (bp) {
        if (!q) return true;
        return [bp.presetName, bp.buildInfo.housing, bp.buildInfo.switchName, bp.buildInfo.keycap]
          .some(function (f) { return String(f || '').toLowerCase().indexOf(q) >= 0; });
      });
      if (results.length === 0) {
        listWrap.appendChild(h('<div class="body-medium muted" style="padding:12px 4px">검색 결과가 없습니다.</div>'));
        return;
      }
      results.forEach(function (bp) {
        var m = helpers.meaningful;
        var descParts = [m(bp.buildInfo.housing), m(bp.buildInfo.switchName), m(bp.buildInfo.keycap)].filter(Boolean);
        var item = h(
          '<div class="list-item">' +
          '<div class="li-body">' +
          '<div class="title-small ellipsis-1">' + esc(bp.presetName) + '</div>' +
          '<div class="body-small muted ellipsis-1">' + esc(descParts.length ? descParts.join(' · ') : '세부 정보 없음') + '</div>' +
          '</div></div>');
        var apply = h('<button class="btn btn-text">적용</button>');
        apply.addEventListener('click', function () {
          ui.presetSheetOpen = false;
          actions.applyBuildPreset(bp);
        });
        var del = h('<button class="icon-btn error">' + ICONS.del + '</button>');
        del.addEventListener('click', function () { ui.presetDeleteTarget = bp; notify(); });
        item.appendChild(apply); item.appendChild(del);
        listWrap.appendChild(item);
      });
    }
    refreshList();
    scrim.addEventListener('click', function (e) { if (e.target === scrim) { ui.presetSheetOpen = false; notify(); } });
    scrim.appendChild(sheet);
    return scrim;
  }

  /* ---- PresetSearchField: 필드 + 검색 버튼 → 바텀시트 ---- */
  var FIELD_META = {
    housing: { label: '하우징', placeholder: '하우징 검색 또는 직접 입력', infoKey: 'housing' },
    switch: { label: '스위치', placeholder: '스위치 검색 또는 직접 입력', infoKey: 'switchName' },
    keycap: { label: '키캡', placeholder: '키캡 검색 또는 직접 입력', infoKey: 'keycap' },
  };

  function buildSearchField(fieldKey, label, placeholder, value, actions, helpers) {
    var field = buildField({
      label: label, placeholder: placeholder, value: value,
      dataKey: 'bi-' + fieldKey, trailingBtn: '검색', imeSafe: true,
      onInput: function (v) {
        var sel = window.KeyxifStore.selectedPhoto();
        if (sel) setInfoField(actions, sel.buildInfo, FIELD_META[fieldKey].infoKey, v);
      },
      onTrailing: function (v) {
        ui.searchSheet = { field: fieldKey, query: v, hiddenRecents: {} };
        notify();
      },
    });
    return field.wrap;
  }

  function buildSearchSheet(state, actions, helpers) {
    var esc = helpers.esc;
    var meta = FIELD_META[ui.searchSheet.field];
    var scrim = h('<div class="sheet-scrim"></div>');
    var sheet = h('<div class="sheet"><div class="sheet-handle"></div><div class="title-large">' + meta.label + ' 선택</div></div>');
    var search = buildField({
      label: meta.label + ' 검색', placeholder: meta.placeholder,
      value: ui.searchSheet.query, dataKey: 'search-sheet-q',
      onInput: function (v) { ui.searchSheet.query = v; refresh(); },
    });
    sheet.appendChild(search.wrap);
    var useDirect = h('<button class="btn btn-filled full"' + (ui.searchSheet.query.trim() ? '' : ' disabled') + '>직접 입력값 사용</button>');
    useDirect.addEventListener('click', function () { commitValue(ui.searchSheet.query); });
    sheet.appendChild(useDirect);
    var headerLbl = h('<div class="label-medium muted">최근 사용 / 내장 목록</div>');
    sheet.appendChild(headerLbl);
    var listWrap = h('<div class="sheet-list"></div>');
    sheet.appendChild(listWrap);

    function commitValue(v) {
      var sel = window.KeyxifStore.selectedPhoto();
      ui.searchSheet = null;
      if (sel) setInfoField(actions, sel.buildInfo, meta.infoKey, v);
      else notify();
    }
    function commitChoice(choice) {
      var sel = window.KeyxifStore.selectedPhoto();
      ui.searchSheet = null;
      if (!sel) { notify(); return; }
      if (choice.preset) {
        if (ui.lastSearchField === 'housing') actions.selectHousingPreset(choice.preset);
        else if (ui.lastSearchField === 'switch') actions.selectSwitchPreset(choice.preset);
        else actions.selectKeycapPreset(choice.preset);
      } else {
        setInfoField(actions, sel.buildInfo, meta.infoKey, choice.title);
      }
    }

    function refresh() {
      ui.lastSearchField = ui.searchSheet.field;
      var q = ui.searchSheet.query;
      var options;
      if (ui.searchSheet.field === 'housing') options = actions.housingOptions(q);
      else if (ui.searchSheet.field === 'switch') options = actions.switchOptions(q);
      else options = actions.keycapOptions(q);
      options = options.filter(function (o) { return !(o.isRecent && ui.searchSheet.hiddenRecents[o.title]); });
      useDirect.disabled = !q.trim();
      listWrap.innerHTML = '';
      if (options.length === 0) {
        headerLbl.textContent = '검색 결과가 없습니다.';
        return;
      }
      headerLbl.textContent = '최근 사용 / 내장 목록';
      options.forEach(function (o) {
        var item = h(
          '<div class="list-item">' +
          '<div class="li-body">' +
          '<div class="ellipsis-1" style="font-size:15px;font-weight:' + (o.isRecent ? '600' : '400') + '">' + esc(o.title) + '</div>' +
          (o.subtitle ? '<div class="body-small muted ellipsis-1">' + esc(o.subtitle) + '</div>' : '') +
          '</div></div>');
        if (o.isRecent) {
          var del = h('<button class="icon-btn small" aria-label="최근 항목 삭제">' + ICONS.del + '</button>');
          del.addEventListener('click', function () {
            ui.searchSheet.hiddenRecents[o.title] = true;
            if (ui.searchSheet.field === 'housing') actions.removeRecentHousing(o.title);
            else if (ui.searchSheet.field === 'switch') actions.removeRecentSwitch(o.title);
            else actions.removeRecentKeycap(o.title);
          });
          item.appendChild(del);
        }
        var pick = h('<button class="btn btn-text">' + (o.preset ? '선택' : '사용') + '</button>');
        pick.addEventListener('click', function () { commitChoice(o); });
        item.appendChild(pick);
        listWrap.appendChild(item);
      });
    }
    refresh();
    scrim.addEventListener('click', function (e) { if (e.target === scrim) { ui.searchSheet = null; notify(); } });
    scrim.appendChild(sheet);
    return scrim;
  }

  /* =====================================================================
     3. TemplateSelectScreen — 절차적 미니어처 스케치
     ===================================================================== */
  var SKETCH_PALETTE = ['#343A40', '#E8E2D4', '#B7C9BF', '#FF8E68'];
  var SKETCH_CHIP_Y = {
    ClassicFrame: 0.95, MinimalCaption: 0.96, BottomSpecBar: 0.91, PosterMargin: 0.94,
    DarkGlassStrip: 0.94, SideSpecRail: 0.22, TopNameplate: 0.10, MuseumMat: 0.93,
    CompactTicket: 0.93, CleanSignature: 0.96,
  };

  function rr(ctx, x, y, w, hh, r) {
    ctx.beginPath();
    if (ctx.roundRect) ctx.roundRect(x, y, w, hh, r);
    else ctx.rect(x, y, w, hh);
    ctx.fill();
  }

  function drawSketch(templateId) {
    if (ui.tplSketchCache[templateId]) return ui.tplSketchCache[templateId];
    var w = 312, hgt = 250; // 1.25 비율 ×2 해상도
    var c = document.createElement('canvas');
    c.width = w; c.height = hgt;
    var ctx = c.getContext('2d');

    // 공통 대각 그라디언트
    function gradient(x, y, gw, gh) {
      var g = ctx.createLinearGradient(x, y, x + gw, y + gh);
      g.addColorStop(0, '#BFC7C2'); g.addColorStop(0.5, '#6C7675'); g.addColorStop(1, '#292F31');
      return g;
    }
    ctx.fillStyle = gradient(0, 0, w, hgt);
    ctx.fillRect(0, 0, w, hgt);

    ctx.save();
    switch (templateId) {
      case 'ClassicFrame':
        ctx.fillStyle = '#F7F7F3'; ctx.fillRect(0, 0.84 * hgt, w, 0.16 * hgt);
        ctx.fillStyle = '#171717'; rr(ctx, 0.06 * w, 0.88 * hgt, 0.1 * w, 0.07 * hgt, 4);
        [0.22, 0.45, 0.68].forEach(function (x) {
          ctx.fillStyle = '#343434'; ctx.fillRect(x * w, 0.89 * hgt, 0.15 * w, 3);
          ctx.fillStyle = '#888888'; ctx.fillRect(x * w, 0.94 * hgt, 0.11 * w, 2);
        });
        break;
      case 'MinimalCaption':
        ctx.fillStyle = '#FCFCF9'; ctx.fillRect(0, 0.86 * hgt, w, 0.14 * hgt);
        ctx.fillStyle = '#282828'; ctx.fillRect(0.08 * w, 0.9 * hgt, 0.34 * w, 3);
        ctx.fillStyle = '#929292'; ctx.fillRect(0.08 * w, 0.95 * hgt, 0.24 * w, 2);
        break;
      case 'BottomSpecBar':
        ctx.fillStyle = '#232626'; ctx.fillRect(0, 0.89 * hgt, w, 0.11 * hgt);
        ctx.fillStyle = 'rgba(255,255,255,0.9)';
        [0.08, 0.39, 0.70].forEach(function (x) { ctx.fillRect(x * w, 0.94 * hgt, 0.18 * w, 3); });
        break;
      case 'CornerMark':
        ctx.fillStyle = 'rgba(0,0,0,0.58)'; rr(ctx, 0.64 * w, 0.06 * hgt, 0.3 * w, 0.09 * hgt, 5);
        ctx.fillStyle = '#fff';
        ctx.beginPath(); ctx.arc(0.7 * w, 0.105 * hgt, 0.025 * hgt, 0, 7); ctx.fill();
        ctx.fillRect(0.75 * w, 0.1 * hgt, 0.13 * w, 3);
        break;
      case 'PosterMargin':
        ctx.fillStyle = '#F8F8F5'; ctx.fillRect(0, 0, w, hgt);
        ctx.fillStyle = gradient(0.04 * w, 0.04 * hgt, 0.92 * w, 0.76 * hgt);
        ctx.fillRect(0.04 * w, 0.04 * hgt, 0.92 * w, 0.76 * hgt);
        ctx.fillStyle = '#171717'; ctx.fillRect(0.09 * w, 0.87 * hgt, 0.34 * w, 4);
        ctx.fillStyle = '#777777'; ctx.fillRect(0.09 * w, 0.93 * hgt, 0.45 * w, 3);
        break;
      case 'DarkGlassStrip':
        ctx.fillStyle = 'rgba(0,0,0,0.7)'; ctx.fillRect(0, 0.88 * hgt, w, 0.12 * hgt);
        ctx.fillStyle = '#ffffff'; rr(ctx, 0.05 * w, 0.91 * hgt, 0.11 * w, 0.06 * hgt, 4);
        [0.22, 0.46, 0.70].forEach(function (x) { ctx.fillRect(x * w, 0.94 * hgt, 0.14 * w, 3); });
        break;
      case 'SideSpecRail':
        ctx.fillStyle = '#F3F4F1'; ctx.fillRect(0.82 * w, 0, 0.18 * w, hgt);
        ctx.fillStyle = '#D9DAD6'; ctx.fillRect(0.82 * w, 0, 2, hgt);
        ctx.fillStyle = '#181818'; rr(ctx, 0.855 * w, 0.095 * hgt, 0.11 * w, 0.035 * hgt, 6);
        for (var si = 0; si < 4; si++) {
          var sy = (0.3 + 0.12 * si) * hgt;
          ctx.fillStyle = '#343434'; ctx.fillRect(0.85 * w, sy, 0.1 * w, 3);
          ctx.fillStyle = '#8B8B8B'; ctx.fillRect(0.85 * w, sy + 8, 0.08 * w, 2);
        }
        break;
      case 'TopNameplate':
        ctx.fillStyle = '#FAFAF7'; ctx.fillRect(0, 0, w, 0.13 * hgt);
        ctx.fillStyle = '#111111';
        ctx.beginPath(); ctx.arc(0.1 * w, 0.065 * hgt, 0.035 * hgt, 0, 7); ctx.fill();
        ctx.fillStyle = '#242424'; ctx.fillRect(0.18 * w, 0.045 * hgt, 0.32 * w, 3);
        ctx.fillStyle = '#8A8A8A'; ctx.fillRect(0.18 * w, 0.085 * hgt, 0.42 * w, 2);
        break;
      case 'MuseumMat':
        ctx.fillStyle = '#F6F5EF'; ctx.fillRect(0, 0, w, hgt);
        ctx.fillStyle = gradient(0.06 * w, 0.06 * hgt, 0.88 * w, 0.68 * hgt);
        ctx.fillRect(0.06 * w, 0.06 * hgt, 0.88 * w, 0.68 * hgt);
        ctx.fillStyle = '#232323'; ctx.fillRect(0.08 * w, 0.84 * hgt, 0.33 * w, 4);
        ctx.fillStyle = '#777777'; ctx.fillRect(0.08 * w, 0.9 * hgt, 0.54 * w, 3);
        ctx.fillStyle = '#202020';
        ctx.beginPath(); ctx.arc(0.86 * w, 0.86 * hgt, 0.035 * hgt, 0, 7); ctx.fill();
        break;
      case 'CompactTicket':
        ctx.fillStyle = '#EDEEEA'; ctx.fillRect(0, 0.86 * hgt, w, 0.14 * hgt);
        ctx.fillStyle = '#FBFAF6'; rr(ctx, 0.04 * w, 0.88 * hgt, 0.92 * w, 0.1 * hgt, 8);
        ctx.fillStyle = '#1D1D1D';
        ctx.beginPath(); ctx.arc(0.12 * w, 0.93 * hgt, 0.026 * hgt, 0, 7); ctx.fill();
        ctx.fillStyle = '#282828'; ctx.fillRect(0.18 * w, 0.91 * hgt, 0.32 * w, 3);
        ctx.fillStyle = '#8D8D8D'; ctx.fillRect(0.18 * w, 0.95 * hgt, 0.48 * w, 2);
        break;
      case 'CleanSignature':
        ctx.fillStyle = '#FAFAF7'; ctx.fillRect(0, 0.84 * hgt, w, 0.16 * hgt);
        ctx.fillStyle = '#151515'; ctx.fillRect(0.08 * w, 0.88 * hgt, 0.34 * w, 4);
        ctx.fillStyle = '#797C76'; ctx.fillRect(0.08 * w, 0.93 * hgt, 0.25 * w, 3);
        ctx.fillStyle = '#2E2F2C'; ctx.fillRect(0.08 * w, 0.97 * hgt, 0.42 * w, 3);
        ctx.fillStyle = '#222222';
        ctx.beginPath(); ctx.arc(0.87 * w, 0.91 * hgt, 0.03 * hgt, 0, 7); ctx.fill();
        break;
      case 'PlainExport':
        ctx.strokeStyle = 'rgba(255,255,255,0.2)'; ctx.lineWidth = 2;
        ctx.beginPath();
        if (ctx.roundRect) ctx.roundRect(0.06 * w, 0.06 * hgt, 0.88 * w, 0.88 * hgt, 4);
        else ctx.rect(0.06 * w, 0.06 * hgt, 0.88 * w, 0.88 * hgt);
        ctx.stroke();
        break;
    }
    ctx.restore();

    // 팔레트 칩 (PlainExport·CornerMark 제외)
    if (SKETCH_CHIP_Y[templateId] != null) {
      var chipD = hgt * 0.029 * 2, gap = hgt * 0.010 * 2;
      var rightX = (templateId === 'SideSpecRail' ? 0.965 : 0.94) * w;
      var cy = SKETCH_CHIP_Y[templateId] * hgt;
      var x = rightX;
      for (var ci = SKETCH_PALETTE.length - 1; ci >= 0; ci--) {
        x -= chipD;
        ctx.beginPath();
        ctx.arc(x + chipD / 2, cy, chipD / 2, 0, 7);
        ctx.fillStyle = SKETCH_PALETTE[ci];
        ctx.fill();
        ctx.lineWidth = 1.1 * 2;
        ctx.strokeStyle = 'rgba(0,0,0,0.22)';
        ctx.stroke();
        x -= gap;
      }
    }
    var url = c.toDataURL();
    ui.tplSketchCache[templateId] = url;
    return url;
  }

  function renderTemplate(container, state, actions, helpers) {
    var esc = helpers.esc;
    var consts = window.KeyxifStore.consts;
    var root = document.createElement('div');
    root.className = 'screen';

    var sel = window.KeyxifStore.selectedPhoto();
    var subParts = [];
    if (sel) {
      subParts.push('편집 중: ' + (sel.displayName || '사진'));
      var housing = helpers.meaningful(sel.buildInfo.housing);
      if (housing) subParts.push(housing);
    }
    var subtitle = subParts.length ? subParts.join(' · ') : '사진을 가리지 않는 카드 스타일을 선택하세요.';
    root.appendChild(h(
      '<div class="col gap4">' +
      '<div class="title-large">템플릿</div>' +
      '<div class="body-medium muted ellipsis-1">' + esc(subtitle) + '</div>' +
      '</div>'));

    var grid = h('<div class="grid-templates"></div>');
    // enum 순서: Models.kt 표기 순서 (ClassicFrame 먼저 보여주는 게 아니라 enum 순)
    consts.CARD_TEMPLATES.forEach(function (tid) {
      var selTpl = state.selectedTemplate === tid;
      var card = h(
        '<button class="tpl-card' + (selTpl ? ' selected' : '') + '">' +
        '<div class="tpl-preview">' +
        '<img src="' + drawSketch(tid) + '" style="width:100%;height:100%;object-fit:cover" alt="">' +
        (selTpl ? '<div class="tpl-check">' + ICONS.check + '</div>' : '') +
        '</div>' +
        '<div class="title-small ellipsis-1">' + esc(consts.TEMPLATE_NAME[tid]) + '</div>' +
        '<div class="body-small muted ellipsis-2">' + esc(consts.TEMPLATE_DESC[tid]) + '</div>' +
        '</button>');
      card.addEventListener('click', function () { actions.selectTemplate(tid); });
      grid.appendChild(card);
    });
    root.appendChild(grid);
    container.appendChild(root);
  }

  /* =====================================================================
     3.5 PaletteScreen
     ===================================================================== */
  function renderPalette(container, state, actions, helpers) {
    var root = document.createElement('div');
    root.className = 'screen';
    var selected = window.KeyxifStore.selectedPhoto();
    var analysis = selected ? selected.analysisResult : null;
    var renderStyle = (selected && selected.renderStyle) || {
      usePaletteColorForCardBackground: false,
      paletteBackgroundColorIndex: 0,
    };
    var colors = analysis ? analysis.paletteColors || [] : [];

    root.appendChild(h(
      '<div class="col gap4">' +
      '<div class="title-large">색상 분석</div>' +
      '<div class="body-medium muted">사진별 대표 색상을 확인하고 템플릿 카드 배경색을 따로 선택합니다.</div>' +
      '</div>'
    ));

    var strip = h('<div class="thumb-strip"></div>');
    state.photos.forEach(function (p) {
      var url = helpers.getObjectURL(p.uri) || '';
      var thumb = h('<button class="palette-thumb' + (p.id === (selected && selected.id) ? ' selected' : '') + '"><img src="' + url + '" alt=""></button>');
      thumb.addEventListener('click', function () { actions.selectPhoto(p.id); });
      strip.appendChild(thumb);
    });
    if (state.photos.length) root.appendChild(strip);

    var modeCard = h('<section class="card col gap12" style="padding:16px"></section>');
    modeCard.appendChild(h('<div class="title-medium">대표 색상 분석 방식</div>'));
    var modes = h('<div class="row gap8 wrap"></div>');
    [
      { label: '자동중앙', value: 'AutoCenter' },
      { label: '사각형으로 영역 지정', value: 'RectSelection' },
      { label: '직접 칠하기', value: 'PaintedMask' },
    ].forEach(function (item) {
      var btn = h('<button class="chip' + (analysis && analysis.analysisMode === item.value ? ' selected' : '') + '">' + item.label + '</button>');
      btn.addEventListener('click', function () { actions.updateSelectedPhotoAnalysisMode(item.value); });
      modes.appendChild(btn);
    });
    modeCard.appendChild(modes);
    if (analysis && analysis.isAnalyzing) modeCard.appendChild(h('<div class="palette-progress"><span></span></div><div class="body-small muted">대표 색상을 분석하고 있습니다.</div>'));
    if (analysis && analysis.errorMessage) modeCard.appendChild(h('<div class="body-small" style="color:var(--error)">' + helpers.esc(analysis.errorMessage) + '</div>'));

    if (selected && helpers.getObjectURL(selected.uri)) {
      var stage = h('<div class="palette-editor"><img class="palette-editor-source" src="' + helpers.getObjectURL(selected.uri) + '" alt=""><canvas></canvas></div>');
      var image = stage.querySelector('img');
      var canvas = stage.querySelector('canvas');
      function setupEditor() {
        if (!image.naturalWidth || !image.naturalHeight) return;
        var scale = Math.min(1, 1200 / Math.max(image.naturalWidth, image.naturalHeight));
        canvas.width = Math.max(1, Math.round(image.naturalWidth * scale));
        canvas.height = Math.max(1, Math.round(image.naturalHeight * scale));
        drawPaletteOverlay(canvas, analysis, image, null);
      }
      function redrawEditor(previewStroke) { drawPaletteOverlay(canvas, analysis, image, previewStroke || null); }
      image.addEventListener('load', setupEditor, { once: true });
      if (image.complete && image.naturalWidth > 0) setupEditor();
      else if (typeof image.decode === 'function') image.decode().then(setupEditor).catch(function () {});
      if (analysis.analysisMode === 'RectSelection') bindRectEditor(canvas, analysis, actions, redrawEditor);
      if (analysis.analysisMode === 'PaintedMask') bindMaskEditor(canvas, analysis, actions, redrawEditor);
      modeCard.appendChild(stage);

      var controls = h('<div class="row gap8 wrap"></div>');
      if (analysis.analysisMode === 'AutoCenter') {
        var ratioLabel = h('<div class="label-large" style="width:100%">중앙 영역 범위 ' + Math.round((analysis.analysisCenterCropRatio || .75) * 100) + '%</div>');
        var ratioInput = h('<input style="width:100%" type="range" min="0.35" max="1" step="0.01" value="' + (analysis.analysisCenterCropRatio || .75) + '">');
        ratioInput.addEventListener('input', function () { ratioLabel.textContent = '중앙 영역 범위 ' + Math.round(Number(ratioInput.value) * 100) + '%'; });
        ratioInput.addEventListener('change', function () { actions.updateSelectedPhotoCenterRatio(Number(ratioInput.value)); actions.reanalyzeSelectedPalette(); });
        controls.appendChild(ratioLabel); controls.appendChild(ratioInput);
        var again = h('<button class="btn btn-filled">중앙 기준으로 다시 분석</button>');
        again.addEventListener('click', actions.reanalyzeSelectedPalette); controls.appendChild(again);
      } else if (analysis.analysisMode === 'RectSelection') {
        var centerButton = h('<button class="btn btn-outlined">중앙으로</button>');
        centerButton.addEventListener('click', function () { actions.updateSelectedPhotoAnalysisRect(centerWebRect(analysis.analysisRectNormalized || defaultWebRect())); });
        controls.appendChild(centerButton);
        var applyRect = h('<button class="btn btn-filled">적용</button>');
        applyRect.addEventListener('click', actions.reanalyzeSelectedPalette); controls.appendChild(applyRect);
      } else if (analysis.analysisMode === 'PaintedMask') {
        var sizes = [['작게', 0.03], ['보통', 0.06], ['크게', 0.11]];
        ui.paletteBrushSize = ui.paletteBrushSize || 0.06;
        ui.paletteEraser = !!ui.paletteEraser;
        var brush = h('<button class="chip' + (!ui.paletteEraser ? ' selected' : '') + '">브러시</button>');
        brush.addEventListener('click', function () { ui.paletteEraser = false; notify(); }); controls.appendChild(brush);
        var eraser = h('<button class="chip' + (ui.paletteEraser ? ' selected' : '') + '">지우개</button>');
        eraser.addEventListener('click', function () { ui.paletteEraser = true; notify(); }); controls.appendChild(eraser);
        sizes.forEach(function (pair) {
          var b = h('<button class="chip' + (ui.paletteBrushSize === pair[1] ? ' selected' : '') + '">' + pair[0] + '</button>');
          b.addEventListener('click', function () { ui.paletteBrushSize = pair[1]; notify(); }); controls.appendChild(b);
        });
        var clear = h('<button class="btn btn-outlined">전체 지우기</button>');
        clear.addEventListener('click', function () { actions.updateSelectedPhotoMask([]); }); controls.appendChild(clear);
        var undo = h('<button class="btn btn-outlined"' + ((analysis.paintedMaskStrokes || []).length ? '' : ' disabled') + '>되돌리기</button>');
        undo.addEventListener('click', function () { actions.updateSelectedPhotoMask((analysis.paintedMaskStrokes || []).slice(0, -1)); }); controls.appendChild(undo);
        var applyMask = h('<button class="btn btn-filled">적용</button>');
        applyMask.addEventListener('click', actions.reanalyzeSelectedPalette); controls.appendChild(applyMask);
      }
      modeCard.appendChild(controls);
    }
    root.appendChild(modeCard);

    var colorCard = h('<section class="card col gap12" style="padding:16px"></section>');
    colorCard.appendChild(h('<div class="title-medium">사진별 카드 배경</div>'));
    if (!selected) {
      colorCard.appendChild(h('<div class="body-medium muted">먼저 사진을 추가해 주세요.</div>'));
    } else if (!colors.length) {
      colorCard.appendChild(h('<div class="body-medium muted">대표 색상을 분석 중이거나 추출된 색상이 없습니다.</div>'));
    } else {
      var row = h('<div class="palette-row wrap"></div>');
      colors.slice(0, state.settings.paletteColorCount).forEach(function (c, index) {
        var picked = renderStyle.paletteBackgroundColorIndex === index;
        var btn = h('<button class="chip' + (picked ? ' selected' : '') + '"><span class="color-dot" style="width:22px;height:22px;background:' + helpers.colorToCss(c) + '"></span>#' +
          ((c >>> 0) & 0xffffff).toString(16).toUpperCase().padStart(6, '0') + '</button>');
        btn.addEventListener('click', function () {
          actions.updateSelectedPhotoRenderStyle(function (rs) {
            rs.paletteBackgroundColorIndex = index;
            return rs;
          });
        });
        row.appendChild(btn);
      });
      colorCard.appendChild(row);
      var toggle = h('<label class="switch-row"><span><b>이 색상을 카드 배경으로 사용</b><br><span class="body-small muted">현재 선택한 사진에만 적용됩니다.</span></span><span class="switch"><input type="checkbox"' +
        (renderStyle.usePaletteColorForCardBackground ? ' checked' : '') + '><span class="track"></span><span class="thumb"></span></span></label>');
      toggle.querySelector('input').addEventListener('change', function (e) {
        actions.updateSelectedPhotoRenderStyle(function (rs) {
          rs.usePaletteColorForCardBackground = e.target.checked;
          return rs;
        });
      });
      colorCard.appendChild(toggle);
    }
    root.appendChild(colorCard);
    container.appendChild(root);
  }

  function defaultWebRect() { return { left: 0.15, top: 0.39, right: 0.85, bottom: 0.61 }; }
  function centerWebRect(r) {
    var w = r.right - r.left, h = r.bottom - r.top;
    return { left: 0.5 - w / 2, top: 0.5 - h / 2, right: 0.5 + w / 2, bottom: 0.5 + h / 2 };
  }
  function drawPaletteOverlay(canvas, analysis, image, previewStroke) {
    if (!canvas.width || !canvas.height || !image || !image.naturalWidth) return;
    var ctx = canvas.getContext('2d');
    ctx.globalAlpha = 1; ctx.globalCompositeOperation = 'source-over';
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.drawImage(image, 0, 0, canvas.width, canvas.height);
    if (!analysis) return;
    if (analysis.analysisMode === 'AutoCenter') {
      var ratio = Math.min(1, Math.max(.35, Number(analysis.analysisCenterCropRatio) || .75));
      var autoLeft = (1 - ratio) / 2, autoTop = (1 - ratio) / 2;
      ctx.strokeStyle = 'rgba(255,255,255,.92)'; ctx.lineWidth = Math.max(1.5, canvas.width / 480);
      ctx.strokeRect(autoLeft * canvas.width, autoTop * canvas.height, ratio * canvas.width, ratio * canvas.height);
    } else if (analysis.analysisMode === 'RectSelection') {
      var r = analysis.analysisRectNormalized || defaultWebRect();
      ctx.fillStyle = 'rgba(0,200,255,.18)'; ctx.strokeStyle = '#fff'; ctx.lineWidth = Math.max(2, canvas.width / 320);
      ctx.fillRect(r.left * canvas.width, r.top * canvas.height, (r.right - r.left) * canvas.width, (r.bottom - r.top) * canvas.height);
      ctx.strokeRect(r.left * canvas.width, r.top * canvas.height, (r.right - r.left) * canvas.width, (r.bottom - r.top) * canvas.height);
      [[r.left, r.top], [r.right, r.top], [r.left, r.bottom], [r.right, r.bottom]].forEach(function (p) {
        ctx.beginPath(); ctx.fillStyle = '#fff'; ctx.arc(p[0] * canvas.width, p[1] * canvas.height, Math.max(8, canvas.width / 90), 0, Math.PI * 2); ctx.fill();
        ctx.beginPath(); ctx.fillStyle = '#0089a8'; ctx.arc(p[0] * canvas.width, p[1] * canvas.height, Math.max(5, canvas.width / 140), 0, Math.PI * 2); ctx.fill();
      });
    } else if (analysis.analysisMode === 'PaintedMask') {
      var mask = document.createElement('canvas'); mask.width = canvas.width; mask.height = canvas.height;
      var maskCtx = mask.getContext('2d'); maskCtx.lineCap = 'round'; maskCtx.lineJoin = 'round';
      var strokes = (analysis.paintedMaskStrokes || []).slice(); if (previewStroke) strokes.push(previewStroke);
      strokes.forEach(function (s) {
        var pts = s.points || []; if (!pts.length) return;
        maskCtx.globalCompositeOperation = s.isEraser ? 'destination-out' : 'source-over';
        maskCtx.strokeStyle = '#fff'; maskCtx.fillStyle = '#fff';
        maskCtx.lineWidth = (s.brushSizeNormalized || .06) * Math.max(canvas.width, canvas.height);
        if (pts.length === 1) { maskCtx.beginPath(); maskCtx.arc(pts[0].x * canvas.width, pts[0].y * canvas.height, maskCtx.lineWidth / 2, 0, Math.PI * 2); maskCtx.fill(); }
        else { maskCtx.beginPath(); maskCtx.moveTo(pts[0].x * canvas.width, pts[0].y * canvas.height); for (var i = 1; i < pts.length; i++) maskCtx.lineTo(pts[i].x * canvas.width, pts[i].y * canvas.height); maskCtx.stroke(); }
      });
      maskCtx.globalCompositeOperation = 'source-in'; maskCtx.fillStyle = '#ff3158'; maskCtx.fillRect(0, 0, mask.width, mask.height);
      ctx.save(); ctx.globalAlpha = .30; ctx.drawImage(mask, 0, 0); ctx.restore();
    }
  }
  function pointerNormalized(canvas, e) {
    var b = canvas.getBoundingClientRect();
    return { x: Math.min(1, Math.max(0, (e.clientX - b.left) / b.width)), y: Math.min(1, Math.max(0, (e.clientY - b.top) / b.height)) };
  }
  function bindRectEditor(canvas, analysis, actions, redraw) {
    var start = null, original = null, handle = null;
    canvas.addEventListener('pointerdown', function (e) {
      start = pointerNormalized(canvas, e); original = Object.assign({}, analysis.analysisRectNormalized || defaultWebRect());
      var corners = { tl: [original.left, original.top], tr: [original.right, original.top], bl: [original.left, original.bottom], br: [original.right, original.bottom] };
      var closest = null, distance = Infinity;
      Object.keys(corners).forEach(function (key) { var p = corners[key]; var d = Math.hypot(start.x - p[0], start.y - p[1]); if (d < distance) { distance = d; closest = key; } });
      handle = distance <= .06 ? closest : (start.x >= original.left && start.x <= original.right && start.y >= original.top && start.y <= original.bottom ? 'move' : null);
      if (!handle) { start = null; return; }
      canvas.setPointerCapture(e.pointerId);
    });
    canvas.addEventListener('pointermove', function (e) {
      if (!start) return; var p = pointerNormalized(canvas, e), min = .08, next = Object.assign({}, original);
      if (handle === 'move') {
        var w = original.right - original.left, h = original.bottom - original.top;
        next.left = Math.min(1 - w, Math.max(0, original.left + p.x - start.x)); next.top = Math.min(1 - h, Math.max(0, original.top + p.y - start.y));
        next.right = next.left + w; next.bottom = next.top + h;
      } else {
        if (handle === 'tl' || handle === 'bl') next.left = Math.min(original.right - min, p.x);
        if (handle === 'tr' || handle === 'br') next.right = Math.max(original.left + min, p.x);
        if (handle === 'tl' || handle === 'tr') next.top = Math.min(original.bottom - min, p.y);
        if (handle === 'bl' || handle === 'br') next.bottom = Math.max(original.top + min, p.y);
      }
      analysis.analysisRectNormalized = next; redraw();
    });
    canvas.addEventListener('pointerup', function () { if (start) actions.updateSelectedPhotoAnalysisRect(analysis.analysisRectNormalized); start = null; handle = null; });
    canvas.addEventListener('pointercancel', function () { start = null; handle = null; });
  }
  function bindMaskEditor(canvas, analysis, actions, redraw) {
    var points = null;
    canvas.addEventListener('pointerdown', function (e) { points = [pointerNormalized(canvas, e)]; canvas.setPointerCapture(e.pointerId); redraw({ points: points, brushSizeNormalized: ui.paletteBrushSize || .06, isEraser: !!ui.paletteEraser }); });
    canvas.addEventListener('pointermove', function (e) { if (!points) return; points.push(pointerNormalized(canvas, e)); redraw({ points: points, brushSizeNormalized: ui.paletteBrushSize || .06, isEraser: !!ui.paletteEraser }); });
    canvas.addEventListener('pointerup', function () {
      if (!points || !points.length) return;
      var strokes = (analysis.paintedMaskStrokes || []).concat([{ points: points, brushSizeNormalized: ui.paletteBrushSize || .06, isEraser: !!ui.paletteEraser }]);
      analysis.paintedMaskStrokes = strokes; points = null; redraw(); actions.updateSelectedPhotoMask(strokes);
    });
    canvas.addEventListener('pointercancel', function () { points = null; redraw(); });
  }

  /* =====================================================================
     4. ExportScreen
     ===================================================================== */
  function renderKeyOf(photo, state) {
    var s = state.settings;
    return [state.selectedTemplate, JSON.stringify(photo.buildInfo),
      JSON.stringify(photo.renderStyle || {}),
      JSON.stringify(photo.analysisResult.paletteColors), s.textScale, s.nicknameStyle,
      s.nicknameEmphasis, s.showPaletteColors, s.paletteColorCount, s.paletteAnalysisMode,
      s.paletteCenterCropRatio, s.autoSelectLogoContrastVariant].join('|');
  }

  function ensurePreviewRender(photo, state, actions, maxSide) {
    var key = photo.id + '|' + maxSide + '|' + renderKeyOf(photo, state);
    var entry = ui.renderCache[key];
    if (entry) return { key: key, entry: entry };
    entry = { status: 'loading', dataUrl: null };
    ui.renderCache[key] = entry;
    // 캐시 정리: 현재 사진 세트의 살아있는 키(사진당 768/2048 두 개)는 절대 축출하지 않고
    // 오래된 renderKey 세대만 제거 — FIFO 축출 시 41장 이상에서 미스 캐스케이드 발생
    var keys = Object.keys(ui.renderCache);
    if (keys.length > Math.max(48, state.photos.length * 3 + 6)) {
      var valid = {};
      state.photos.forEach(function (p) {
        var rk = renderKeyOf(p, state);
        valid[p.id + '|768|' + rk] = true;   // Export 그리드
        valid[p.id + '|1024|' + rk] = true;  // 데스크톱 미리보기 패널
        valid[p.id + '|2048|' + rk] = true;  // 풀스크린
      });
      keys.forEach(function (k) { if (!valid[k]) delete ui.renderCache[k]; });
    }
    actions.renderPreviewBitmap(photo.id, maxSide).then(function (canvas) {
      if (!canvas) throw new Error('no canvas');
      entry.status = 'ok';
      entry.dataUrl = canvas.toDataURL('image/jpeg', 0.9);
      notify();
    }).catch(function () {
      // 템플릿 렌더 실패 → 원본 폴백
      actions.renderSourcePreviewBitmap(photo.id, maxSide).then(function (canvas) {
        if (!canvas) throw new Error('no source');
        entry.status = 'fallback';
        entry.dataUrl = canvas.toDataURL('image/jpeg', 0.9);
        notify();
      }).catch(function () {
        entry.status = 'error';
        notify();
      });
    });
    return { key: key, entry: entry };
  }

  var longPressTimer = null;

  function renderExport(container, state, actions, helpers) {
    // 재렌더 시 진행 중이던 롱프레스 타이머 정리 — 이전 노드의 clear 리스너가
    // DOM 교체로 사라져 유령 선택(500ms 후 멋대로 선택 모드 진입)이 생기는 것 방지
    if (longPressTimer) { clearTimeout(longPressTimer); longPressTimer = null; }
    var esc = helpers.esc;
    var root = document.createElement('div');
    root.className = 'screen';
    var s = state.settings;
    var prog = state.exportProgress;

    root.appendChild(h(
      '<div class="col gap4">' +
      '<div class="title-large">미리보기</div>' +
      '<div class="body-medium muted">WEBP 품질 ' + s.webpQuality + '% · Pictures/' + esc(s.saveDirectoryName) + '</div>' +
      '</div>'));

    if (prog.message) root.appendChild(h('<div class="body-medium muted">' + esc(prog.message) + '</div>'));
    if (prog.total > 0) {
      var pct = Math.min(100, Math.max(0, (prog.current / prog.total) * 100));
      root.appendChild(h('<div class="linear-progress"><div style="width:' + pct + '%"></div></div>'));
      root.appendChild(h('<div class="label-medium muted">' + prog.current + ' / ' + prog.total +
        ' · 성공 ' + prog.successCount + ' · 실패 ' + prog.failureCount + '</div>'));
    }

    var selectedIds = Object.keys(state.selectedExportPhotoIds);
    if (selectedIds.length > 0) {
      var banner = h('<div class="banner"><span class="label-large grow">' + selectedIds.length + '장 선택됨</span></div>');
      var clearSel = h('<button class="btn btn-text"' + (prog.isSaving ? ' disabled' : '') + '>선택 해제</button>');
      clearSel.addEventListener('click', actions.clearExportSelection);
      banner.appendChild(clearSel);
      root.appendChild(banner);
    }

    var grid = h('<div class="grid-export"></div>');
    state.photos.forEach(function (p) {
      grid.appendChild(buildExportCard(p, state, actions, helpers, selectedIds.length > 0));
    });
    root.appendChild(grid);
    container.appendChild(root);

    // 풀스크린 미리보기 — 블러 대상 밖(body)에 부착해야 오버레이가 흐려지지 않음
    if (state.expandedExportPhotoId && s.enableExportPreviewZoom) {
      document.body.appendChild(buildFullscreenPreview(state, actions, helpers));
    }
  }

  function statusText(p) {
    switch (p.renderStatus) {
      case 'Rendering': return '저장 중';
      case 'Saved': return '저장 완료';
      case 'Error': return p.errorMessage || '실패';
      default: return '저장 대기';
    }
  }

  function buildExportCard(p, state, actions, helpers, selectMode) {
    var esc = helpers.esc;
    var isSaving = state.exportProgress.isSaving;
    var selected = !!state.selectedExportPhotoIds[p.id];
    var r = ensurePreviewRender(p, state, actions, 768);

    var card = h('<div class="card export-card"></div>');
    var preview = h('<div class="render-preview"></div>');
    if (r.entry.status === 'loading') {
      preview.appendChild(h('<div class="spinner"></div>'));
    } else if (r.entry.status === 'ok' || r.entry.status === 'fallback') {
      preview.appendChild(h('<img src="' + r.entry.dataUrl + '" alt="">'));
      if (r.entry.status === 'fallback') preview.appendChild(h('<div class="fallback-badge">원본 표시 중</div>'));
    } else {
      var errBox = h('<div class="col gap6" style="align-items:center"><span style="width:28px;height:28px;color:var(--on-surface-variant)">' + ICONS.broken + '</span><span class="body-small muted">미리보기 생성 실패</span></div>');
      var retry = h('<button class="btn btn-text">' + ICONS.refresh + '<span> 다시 시도</span></button>');
      retry.addEventListener('click', function (e) {
        e.stopPropagation();
        delete ui.renderCache[r.key];
        notify();
      });
      errBox.appendChild(retry);
      preview.appendChild(errBox);
    }
    if (selectMode) {
      preview.appendChild(h(selected
        ? '<div class="select-badge on" aria-label="선택됨">' + ICONS.check + '</div>'
        : '<div class="select-badge off"></div>'));
    }
    // 클릭 / 롱프레스
    preview.addEventListener('click', function () {
      if (isSaving) return;
      if (selectMode) actions.setExportPhotoSelected(p.id, !selected);
      else if (state.settings.enableExportPreviewZoom) {
        ui.fullscreen = { scale: 1, offsetX: 0, offsetY: 0 };
        actions.setExpandedExportPhoto(p.id);
      }
    });
    preview.addEventListener('pointerdown', function () {
      if (isSaving) return;
      longPressTimer = setTimeout(function () {
        longPressTimer = null;
        actions.setExportPhotoSelected(p.id, true);
      }, 500);
    });
    ['pointerup', 'pointerleave', 'pointercancel'].forEach(function (ev) {
      preview.addEventListener(ev, function () {
        if (longPressTimer) { clearTimeout(longPressTimer); longPressTimer = null; }
      });
    });
    preview.addEventListener('contextmenu', function (e) { e.preventDefault(); });
    card.appendChild(preview);

    card.appendChild(h(
      '<div class="col gap2">' +
      '<div class="title-small ellipsis-1">' + esc(p.displayName) + '</div>' +
      '<div class="body-small ellipsis-2" style="color:' + (p.renderStatus === 'Error' ? 'var(--error)' : 'var(--on-surface-variant)') + '">' + esc(statusText(p)) + '</div>' +
      '</div>'));
    var saveBtn = h('<button class="btn btn-tonal full h40"' + (isSaving ? ' disabled' : '') + '>이 사진 저장</button>');
    saveBtn.addEventListener('click', function () { actions.savePhoto(p.id); });
    card.appendChild(saveBtn);
    return card;
  }

  /* ---- 풀스크린 확대 (줌/팬/페이저 — screens-spec §4 수학) ---- */
  function buildFullscreenPreview(state, actions, helpers) {
    var esc = helpers.esc;
    var photos = state.photos;
    var index = photos.findIndex(function (p) { return p.id === state.expandedExportPhotoId; });
    if (index < 0) index = 0;
    var photo = photos[index];
    if (!ui.fullscreen) ui.fullscreen = { scale: 1, offsetX: 0, offsetY: 0 };
    var z = ui.fullscreen;

    var overlay = h('<div class="fullscreen-preview"></div>');
    var top = h(
      '<div class="fp-topbar">' +
      '<div class="title-medium ellipsis-1" style="font-weight:600">' + (index + 1) + ' / ' + photos.length + ' · ' + esc(photo.displayName) + '</div>' +
      '</div>');
    var closeBtn = h('<button class="icon-btn" style="color:#fff" aria-label="닫기">' + ICONS.close + '</button>');
    closeBtn.addEventListener('click', function () {
      ui.fullscreen = null;
      actions.setExpandedExportPhoto(null);
    });
    top.appendChild(closeBtn);
    overlay.appendChild(top);

    var stage = h('<div class="fp-stage"></div>');
    var r = ensurePreviewRender(photo, state, actions, 2048);
    var content;
    if (r.entry.status === 'loading') {
      content = h('<div class="spinner white" style="position:absolute;inset:0;margin:auto"></div>');
    } else if (r.entry.dataUrl) {
      content = h('<img src="' + r.entry.dataUrl + '" alt="">');
      var visual = z.scale * 1.003;
      content.style.transform = 'translate(' + z.offsetX + 'px,' + z.offsetY + 'px) scale(' + visual + ')';
    } else {
      content = h('<div style="position:absolute;inset:0;display:flex;align-items:center;justify-content:center;color:#fff" class="body-medium">미리보기를 만들 수 없습니다.</div>');
    }
    stage.appendChild(content);
    overlay.appendChild(stage);

    // ---- 제스처
    var pointers = {};
    var lastDist = 0, lastMid = null, lastTap = 0;
    function clampOffsets() {
      var rect = stage.getBoundingClientRect();
      var maxX = rect.width * (z.scale - 1) / 2;
      var maxY = rect.height * (z.scale - 1) / 2;
      z.offsetX = Math.min(maxX, Math.max(-maxX, z.offsetX));
      z.offsetY = Math.min(maxY, Math.max(-maxY, z.offsetY));
    }
    function applyTransform() {
      if (z.scale <= 1.01) { z.scale = 1; z.offsetX = 0; z.offsetY = 0; }
      clampOffsets();
      if (content.tagName === 'IMG') {
        content.style.transform = 'translate(' + z.offsetX + 'px,' + z.offsetY + 'px) scale(' + (z.scale * 1.003) + ')';
      }
    }
    var swipeStartX = null;
    stage.addEventListener('pointerdown', function (e) {
      pointers[e.pointerId] = { x: e.clientX, y: e.clientY };
      stage.setPointerCapture(e.pointerId);
      var ids = Object.keys(pointers);
      if (ids.length === 1) {
        // 더블탭
        var now = Date.now();
        if (now - lastTap < 300) {
          if (z.scale > 1.01) { z.scale = 1; z.offsetX = 0; z.offsetY = 0; }
          else { z.scale = 2.4; z.offsetX = 0; z.offsetY = 0; }
          applyTransform();
          lastTap = 0;
        } else lastTap = now;
        if (z.scale <= 1.01) swipeStartX = e.clientX;
      } else if (ids.length === 2) {
        var a = pointers[ids[0]], b = pointers[ids[1]];
        lastDist = Math.hypot(a.x - b.x, a.y - b.y);
        lastMid = { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 };
        swipeStartX = null;
      }
    });
    stage.addEventListener('pointermove', function (e) {
      if (!pointers[e.pointerId]) return;
      var prev = pointers[e.pointerId];
      pointers[e.pointerId] = { x: e.clientX, y: e.clientY };
      var ids = Object.keys(pointers);
      if (ids.length === 2) {
        var a = pointers[ids[0]], b = pointers[ids[1]];
        var dist = Math.hypot(a.x - b.x, a.y - b.y);
        var mid = { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 };
        if (lastDist > 0) {
          z.scale = Math.min(5, Math.max(1, z.scale * (dist / lastDist)));
          z.offsetX += mid.x - lastMid.x;
          z.offsetY += mid.y - lastMid.y;
          applyTransform();
        }
        lastDist = dist; lastMid = mid;
      } else if (ids.length === 1 && z.scale > 1.01) {
        z.offsetX += e.clientX - prev.x;
        z.offsetY += e.clientY - prev.y;
        applyTransform();
      }
    });
    function endPointer(e) {
      delete pointers[e.pointerId];
      lastDist = 0;
      if (swipeStartX != null && z.scale <= 1.01) {
        var dx = e.clientX - swipeStartX;
        if (Math.abs(dx) > 60) {
          var next = index + (dx < 0 ? 1 : -1);
          if (next >= 0 && next < photos.length) {
            ui.fullscreen = { scale: 1, offsetX: 0, offsetY: 0 };
            actions.setExpandedExportPhoto(photos[next].id);
          }
        }
        swipeStartX = null;
      }
    }
    stage.addEventListener('pointerup', endPointer);
    stage.addEventListener('pointercancel', endPointer);
    // 데스크톱 휠 줌
    stage.addEventListener('wheel', function (e) {
      e.preventDefault();
      z.scale = Math.min(5, Math.max(1, z.scale * (e.deltaY < 0 ? 1.12 : 0.89)));
      applyTransform();
    }, { passive: false });

    return overlay;
  }

  /* =====================================================================
     데스크톱 실시간 미리보기 패널 (BuildInfo / Template 단계)
     ===================================================================== */
  function buildPreviewPane(state, actions, helpers) {
    var esc = helpers.esc;
    var sel = window.KeyxifStore.selectedPhoto();
    if (!sel) return null;
    var consts = window.KeyxifStore.consts;

    var pane = h('<aside class="preview-pane"></aside>');
    pane.appendChild(h(
      '<div class="preview-pane-head">' +
      '<span class="title-small">미리보기</span>' +
      '<span class="body-small muted ellipsis-1">' + esc(consts.TEMPLATE_NAME[state.selectedTemplate]) + '</span>' +
      '</div>'));

    var stage = h('<div class="preview-pane-stage"></div>');
    var r = ensurePreviewRender(sel, state, actions, 1024);
    if (r.entry.status === 'loading') {
      stage.appendChild(h('<div class="spinner"></div>'));
    } else if (r.entry.dataUrl) {
      stage.appendChild(h('<img src="' + r.entry.dataUrl + '" alt="미리보기">'));
      if (r.entry.status === 'fallback') stage.appendChild(h('<div class="fallback-badge">원본 표시 중</div>'));
    } else {
      stage.appendChild(h('<div class="col gap6" style="align-items:center;color:var(--on-surface-variant)">' +
        '<span style="width:28px;height:28px;display:inline-block">' + ICONS.broken + '</span>' +
        '<span class="body-small">미리보기 생성 실패</span></div>'));
    }
    pane.appendChild(stage);

    var foot = h('<div class="col gap6"></div>');
    foot.appendChild(h('<div class="body-small muted ellipsis-1">' + esc(sel.displayName) + '</div>'));
    var pal = sel.analysisResult.paletteColors;
    if (state.settings.showPaletteColors && pal && pal.length) {
      var palRow = h('<div class="palette-row"></div>');
      pal.slice(0, state.settings.paletteColorCount).forEach(function (c) {
        palRow.appendChild(h('<span class="color-dot" style="width:20px;height:20px;background:' + helpers.colorToCss(c) + '"></span>'));
      });
      foot.appendChild(palRow);
    }
    pane.appendChild(foot);
    return pane;
  }

  /* ---- Export ---- */
  window.KeyxifUI.renderPhotos = renderPhotos;
  window.KeyxifUI.renderBuildInfo = renderBuildInfo;
  window.KeyxifUI.renderPalette = renderPalette;
  window.KeyxifUI.renderTemplate = renderTemplate;
  window.KeyxifUI.renderExport = renderExport;
  window.KeyxifUI.buildPreviewPane = buildPreviewPane;
  window.KeyxifUI.buildDialog = buildDialog;
})();
