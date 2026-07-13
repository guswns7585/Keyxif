/* =============================================================================
   ui-gallery.js — "완성 이미지" (ExportedGalleryScreen.kt 1:1 웹 포트)
   gallery-settings-spec.md SECTION 1 구현.
   window.KeyxifUI.renderGallery(container, state, actions, helpers)
   ============================================================================= */
(function () {
  'use strict';

  window.KeyxifUI = window.KeyxifUI || {};

  /* ------------------------------------------------------------------ */
  /* 로컬 UI 상태 (재렌더에도 유지되는 클로저 변수)                        */
  /* ------------------------------------------------------------------ */

  var mode = 'all';              // 'all' | 'housing' | 'brand' | 'color'
  var currentGroupKey = null;    // 그룹 진입 상태
  var selectedIds = {};          // 다중선택 집합 { id: true }
  var detailId = null;           // 상세 다이얼로그 대상 레코드 id
  var deleteTargets = null;      // 삭제 확인 다이얼로그 대상 레코드 배열
  var longPressConsumed = false; // 롱프레스 직후 click 무시 플래그
  var last = null;               // 마지막 render 인자 (로컬 재렌더용)

  var MODE_LABEL = { all: '전체', housing: '하우징', brand: '브랜드', color: '색상' };

  function rerender() {
    if (last) window.KeyxifUI.renderGallery(last.container, last.state, last.actions, last.helpers);
  }

  /* ------------------------------------------------------------------ */
  /* 아이콘 (Material Symbols 스타일 inline SVG)                          */
  /* ------------------------------------------------------------------ */

  var ICONS = {
    share: 'M680-80q-50 0-85-35t-35-85q0-6 3-28L282-392q-16 15-37 23.5t-45 8.5q-50 0-85-35t-35-85q0-50 35-85t85-35q24 0 45 8.5t37 23.5l281-164q-2-7-2.5-13.5T560-760q0-50 35-85t85-35q50 0 85 35t35 85q0 50-35 85t-85 35q-24 0-45-8.5T598-672L317-508q2 7 2.5 13.5t.5 14.5q0 8-.5 14.5T317-452l281 164q16-15 37-23.5t45-8.5q50 0 85 35t35 85q0 50-35 85t-85 35Z',
    download: 'M480-320 280-520l56-58 104 104v-326h80v326l104-104 56 58-200 200ZM240-160q-33 0-56.5-23.5T160-240v-120h80v120h480v-120h80v120q0 33-23.5 56.5T720-160H240Z',
    del: 'M280-120q-33 0-56.5-23.5T200-200v-520h-40v-80h200v-40h240v40h200v80h-40v520q0 33-23.5 56.5T680-120H280Zm400-600H280v520h400v-520ZM360-280h80v-360h-80v360Zm160 0h80v-360h-80v360Z',
    check: 'M382-240 154-468l57-57 171 171 367-367 57 57-424 424Z',
    close: 'm256-200-56-56 224-224-224-224 56-56 224 224 224-224 56 56-224 224 224 224-56 56-224-224-224 224Z',
  };
  function icon(name) {
    return '<svg viewBox="0 -960 960 960" fill="currentColor" aria-hidden="true"><path d="' + ICONS[name] + '"/></svg>';
  }

  /* ------------------------------------------------------------------ */
  /* 텍스트 / 그룹핑 유틸 (spec 1.5)                                       */
  /* ------------------------------------------------------------------ */

  function normalize(s) {
    return String(s == null ? '' : s).trim().toLowerCase().replace(/[^a-z0-9가-힣]+/g, '');
  }
  var BLOCKED_NORM = { '': true, untitledkeyboard: true, unknown: true, none: true, 'null': true };
  function isBlockedNorm(n) { return !!BLOCKED_NORM[n]; }

  function displayTitle(rec, helpers) {
    return helpers.meaningful(rec.nickname) || helpers.meaningful(rec.housing) ||
      helpers.meaningful(rec.fileName) || helpers.fmtDate(rec.createdAt);
  }

  /* ---- 브랜드 매칭 ---- */

  var BRAND_DISPLAY = {
    qwertykeys: 'Qwertykeys',
    geonworks: 'Geonworks', geon: 'Geonworks',
    modedesigns: 'Mode', mode: 'Mode',
    owlab: 'Owlab',
    singakbd: 'SingaKBD', singa: 'SingaKBD',
    matrixlab: 'Matrix Lab', matrix: 'Matrix Lab',
    kbdfans: 'KBDfans',
    jjwconcepts: 'JJW', jjw: 'JJW',
  };

  function vendorNameById(vid) {
    if (vid == null || vid === '') return null;
    var P = window.KEYXIF_PRESETS || {};
    var vs = P.vendors;
    if (!vs) return null;
    if (Array.isArray(vs)) {
      for (var i = 0; i < vs.length; i++) {
        var v = vs[i];
        if (typeof v === 'string') { if (v === vid) return v; continue; }
        if (v && (v.id === vid || v.vendorId === vid)) return v.name || v.title || null;
      }
      return null;
    }
    if (typeof vs === 'object') {
      var e = vs[vid];
      if (typeof e === 'string') return e;
      if (e && e.name) return e.name;
    }
    return null;
  }
  function vendorNamesList() {
    var P = window.KEYXIF_PRESETS || {};
    var vs = P.vendors;
    var out = [];
    if (!vs) return out;
    if (Array.isArray(vs)) {
      for (var i = 0; i < vs.length; i++) {
        var v = vs[i];
        if (typeof v === 'string') out.push(v);
        else if (v && (v.name || v.title)) out.push(v.name || v.title);
      }
    } else if (typeof vs === 'object') {
      Object.keys(vs).forEach(function (k) {
        var e = vs[k];
        if (typeof e === 'string') out.push(e);
        else if (e && e.name) out.push(e.name);
      });
    }
    return out;
  }

  var UNKNOWN_GROUP = { key: 'unknown', title: '정보 없음', chip: null, unknown: true };

  function housingGroupOf(housing) {
    var raw = String(housing == null ? '' : housing).trim();
    var n = normalize(raw);
    if (isBlockedNorm(n)) return UNKNOWN_GROUP;
    return { key: 'housing-' + n, title: raw, chip: null, unknown: false };
  }

  function brandGroupOf(housing) {
    var raw = String(housing == null ? '' : housing).trim();
    var n = normalize(raw);
    if (isBlockedNorm(n)) return UNKNOWN_GROUP;
    var P = window.KEYXIF_PRESETS || {};
    var housings = Array.isArray(P.housings) ? P.housings : [];
    var vendorName = null;
    for (var i = 0; i < housings.length && !vendorName; i++) {
      var hp = housings[i];
      if (!hp) continue;
      var cands = [hp.name, hp.id].concat(Array.isArray(hp.aliases) ? hp.aliases : []);
      for (var j = 0; j < cands.length; j++) {
        if (cands[j] != null && normalize(cands[j]) === n) {
          vendorName = hp.vendor || vendorNameById(hp.vendorId);
          break;
        }
      }
    }
    if (!vendorName) {
      // 프리셋 매칭 실패 → 벤더명 자체가 하우징 텍스트 앞부분에 오는 경우 매칭
      var names = vendorNamesList();
      for (var k = 0; k < names.length; k++) {
        var nv = normalize(names[k]);
        if (nv && (n === nv || n.indexOf(nv) === 0)) { vendorName = names[k]; break; }
      }
    }
    if (!vendorName) return UNKNOWN_GROUP;
    var display = BRAND_DISPLAY[normalize(vendorName)] || vendorName;
    return { key: 'brand-' + normalize(display), title: display, chip: null, unknown: false };
  }

  /* ---- 색상 그룹 (spec 1.5, HSV) ---- */

  var HUE_GROUPS = {
    red: { key: 'color-red', title: 'Red / Pink', chip: '#E45B67' },
    orange: { key: 'color-orange', title: 'Orange', chip: '#E58A3A' },
    yellow: { key: 'color-yellow', title: 'Yellow / Gold', chip: '#E0B93A' },
    green: { key: 'color-green', title: 'Green', chip: '#4FA66A' },
    cyan: { key: 'color-cyan', title: 'Cyan', chip: '#4CB6B7' },
    blue: { key: 'color-blue', title: 'Blue', chip: '#537ECF' },
    purple: { key: 'color-purple', title: 'Purple', chip: '#8B69CC' },
    redPink: { key: 'color-red-pink', title: 'Red / Pink', chip: '#D9689D' },
  };
  function hueGroupOf(h) {
    if (h < 18 || h >= 342) return HUE_GROUPS.red;
    if (h < 45) return HUE_GROUPS.orange;
    if (h < 72) return HUE_GROUPS.yellow;
    if (h < 155) return HUE_GROUPS.green;
    if (h < 195) return HUE_GROUPS.cyan;
    if (h < 250) return HUE_GROUPS.blue;
    if (h < 292) return HUE_GROUPS.purple;
    return HUE_GROUPS.redPink;
  }

  function intToHsv(c) {
    var r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
    var rf = r / 255, gf = g / 255, bf = b / 255;
    var max = Math.max(rf, gf, bf), min = Math.min(rf, gf, bf), d = max - min;
    var h = 0;
    if (d > 0) {
      if (max === rf) h = 60 * (((gf - bf) / d) % 6);
      else if (max === gf) h = 60 * ((bf - rf) / d + 2);
      else h = 60 * ((rf - gf) / d + 4);
      if (h < 0) h += 360;
    }
    var s = max === 0 ? 0 : d / max;
    var lum = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255;
    return { h: h, s: s, v: max, lum: lum };
  }

  function colorGroupOf(paletteColors) {
    var colors = (Array.isArray(paletteColors) ? paletteColors : [])
      .filter(function (c) { return c !== 0; }).slice(0, 3);
    if (colors.length === 0) return UNKNOWN_GROUP;
    var hsvs = colors.map(intToHsv);
    var chromatic = hsvs.filter(function (x) { return x.s >= 0.18 && x.v >= 0.18; });
    if (chromatic.length === 0) {
      var avg = 0;
      for (var i = 0; i < hsvs.length; i++) avg += hsvs[i].lum;
      avg /= hsvs.length;
      if (avg <= 0.18) return { key: 'color-dark', title: 'Black / Dark', chip: '#1F1F1F', unknown: false };
      if (avg >= 0.78) return { key: 'color-white', title: 'White / Silver', chip: '#E8E4DA', unknown: false };
      return { key: 'color-gray', title: 'Gray', chip: '#8C8C86', unknown: false };
    }
    // hue 그룹 최빈값
    var counts = {};
    var groupByKey = {};
    var orderKeys = [];
    chromatic.forEach(function (x) {
      var g = hueGroupOf(x.h);
      if (!counts[g.key]) { counts[g.key] = 0; groupByKey[g.key] = g; orderKeys.push(g.key); }
      counts[g.key]++;
    });
    var domKey = orderKeys[0];
    for (var j = 1; j < orderKeys.length; j++) {
      if (counts[orderKeys[j]] > counts[domKey]) domKey = orderKeys[j];
    }
    var ratio = counts[domKey] / chromatic.length;
    // hue 스프레드 = 360 - (정렬된 hue 사이 최대 원형 간격)
    var spread = 0;
    if (chromatic.length >= 2) {
      var hues = chromatic.map(function (x) { return x.h; }).sort(function (a, b) { return a - b; });
      var maxGap = 360 - hues[hues.length - 1] + hues[0];
      for (var k = 1; k < hues.length; k++) maxGap = Math.max(maxGap, hues[k] - hues[k - 1]);
      spread = 360 - maxGap;
    }
    if (chromatic.length >= 3 && ratio < 0.67 && spread > 85) {
      return { key: 'color-mixed', title: 'Multi / Mixed', chip: '#9B8BD9', unknown: false };
    }
    var dg = groupByKey[domKey];
    return { key: dg.key, title: dg.title, chip: dg.chip, unknown: false };
  }

  /* ---- 그룹 조립 (spec 1.4/1.5) ---- */

  function classify(rec) {
    if (mode === 'housing') return housingGroupOf(rec.housing);
    if (mode === 'brand') return brandGroupOf(rec.housing);
    return colorGroupOf(rec.paletteColors);
  }

  function buildGroups(images) {
    var map = {};
    var order = [];
    images.forEach(function (rec) {
      var g = classify(rec);
      if (!map[g.key]) {
        map[g.key] = { key: g.key, title: g.title, chip: g.chip, unknown: !!g.unknown, items: [] };
        order.push(g.key);
      }
      map[g.key].items.push(rec);
    });
    var groups = order.map(function (k) { return map[k]; });
    groups.forEach(function (g) {
      g.items = g.items.slice().sort(function (a, b) { return (b.createdAt || 0) - (a.createdAt || 0); });
    });
    // ① 정보 없음 맨 뒤 → ② 개수 내림차순 → ③ 제목 오름차순
    groups.sort(function (a, b) {
      var u = (a.unknown ? 1 : 0) - (b.unknown ? 1 : 0);
      if (u !== 0) return u;
      if (b.items.length !== a.items.length) return b.items.length - a.items.length;
      return String(a.title).localeCompare(String(b.title));
    });
    return groups;
  }

  /* ------------------------------------------------------------------ */
  /* HTML 조각                                                            */
  /* ------------------------------------------------------------------ */

  function imageCardHtml(rec, selectMode, helpers) {
    var esc = helpers.esc;
    var badge = '';
    if (selectMode) {
      badge = selectedIds[rec.id]
        ? '<span class="g-select-badge on" role="img" aria-label="선택됨">' + icon('check') + '</span>'
        : '<span class="g-select-badge off"></span>';
    }
    return '' +
      '<button type="button" class="gallery-card card" data-img-id="' + esc(rec.id) + '">' +
        '<div class="g-thumb">' +
          '<img src="' + esc(rec.thumbDataUrl || '') + '" alt="' + esc(rec.fileName || '') + '" draggable="false">' +
          badge +
        '</div>' +
        '<div class="g-text">' +
          '<div class="label-large ellipsis-1">' + esc(displayTitle(rec, helpers)) + '</div>' +
          '<div class="body-small muted">' + esc(helpers.fmtDate(rec.createdAt)) + '</div>' +
        '</div>' +
      '</button>';
  }

  function groupCardHtml(g, helpers) {
    var esc = helpers.esc;
    var thumbs = g.items.slice(0, 3);
    var inner = '';
    if (thumbs.length === 1) {
      inner = '<img src="' + esc(thumbs[0].thumbDataUrl || '') + '" alt="" draggable="false" ' +
        'style="position:absolute;left:6px;top:6px;width:calc(100% - 12px);height:calc(100% - 12px);object-fit:cover;border-radius:8px;">';
    } else {
      var pos = [
        'left:6px;top:50%;transform:translateY(-50%);z-index:3;',
        thumbs.length === 3
          ? 'left:50%;top:50%;transform:translate(-50%,-50%);z-index:2;'
          : 'right:6px;top:50%;transform:translateY(-50%);z-index:2;',
        'right:6px;top:50%;transform:translateY(-50%);z-index:1;',
      ];
      thumbs.forEach(function (t, i) {
        inner += '<img src="' + esc(t.thumbDataUrl || '') + '" alt="" draggable="false" ' +
          'style="position:absolute;width:58%;height:auto;aspect-ratio:1/1;object-fit:cover;border-radius:8px;' + pos[i] + '">';
      });
    }
    if (mode === 'color' && g.chip) {
      inner += '<span class="color-dot" style="position:absolute;right:8px;bottom:8px;width:28px;height:28px;background:' + esc(g.chip) + ';z-index:4;"></span>';
    }
    return '' +
      '<button type="button" class="gallery-card card" data-group-key="' + esc(g.key) + '">' +
        '<div class="group-preview" style="display:block;">' + inner + '</div>' +
        '<div class="g-text" style="padding:10px 12px;">' +
          '<div class="title-small ellipsis-1">' + esc(g.title) + '</div>' +
          '<div class="body-small muted">' + g.items.length + '개</div>' +
        '</div>' +
      '</button>';
  }

  function infoLineHtml(label, value, esc) {
    return '<div class="info-row">' +
      '<span class="label-medium muted">' + esc(label) + '</span>' +
      '<span class="body-medium">' + esc(value) + '</span>' +
    '</div>';
  }

  function detailDialogHtml(rec, helpers) {
    var esc = helpers.esc;
    var TN = (window.KeyxifStore && window.KeyxifStore.consts && window.KeyxifStore.consts.TEMPLATE_NAME) || {};
    var lines = '';
    lines += infoLineHtml('파일', rec.fileName || '', esc);
    lines += infoLineHtml('저장', helpers.fmtDate(rec.createdAt), esc);
    lines += infoLineHtml('템플릿', TN[rec.templateName] || rec.templateName || '', esc);
    lines += infoLineHtml('크기', rec.width + ' x ' + rec.height, esc);
    // WEB-ADD: 팔레트 칩 행 (원본 앱 미표시, 웹 추가)
    var palette = (Array.isArray(rec.paletteColors) ? rec.paletteColors : []).filter(function (c) { return c !== 0; });
    if (palette.length > 0) {
      var dots = palette.map(function (c) {
        return '<span class="color-dot" style="width:22px;height:22px;background:' + esc(helpers.colorToCss(c)) + ';"></span>';
      }).join('');
      lines += '<div class="palette-row">' + dots + '</div>';
    }
    var housing = helpers.meaningful(rec.housing);
    var sw = helpers.meaningful(rec.switchName);
    var keycap = helpers.meaningful(rec.keycap);
    var nickname = helpers.meaningful(rec.nickname);
    if (housing) lines += infoLineHtml('Housing', housing, esc);
    if (sw) lines += infoLineHtml('Switch', sw, esc);
    if (keycap) lines += infoLineHtml('Keycap', keycap, esc);
    if (nickname) lines += infoLineHtml('Nickname', nickname, esc);

    return '' +
      '<div class="dialog-scrim" data-detail-scrim>' +
        '<div class="dialog" role="dialog" aria-modal="true">' +
          '<div class="title-medium ellipsis-1">' + esc(displayTitle(rec, helpers)) + '</div>' +
          '<div class="col gap10">' +
            '<img src="' + esc(rec.thumbDataUrl || '') + '" alt="' + esc(rec.fileName || '') + '" ' +
              'style="width:100%;max-height:360px;object-fit:contain;border-radius:8px;background:var(--surface-container);">' +
            lines +
            '<hr class="divider">' +
            '<div class="row gap8">' +
              '<button type="button" class="btn btn-text h40" data-share>' + icon('share') + '<span>공유</span></button>' +
              '<button type="button" class="btn btn-text h40" data-download>' + icon('download') + '<span>다시 받기</span></button>' +
              '<button type="button" class="btn btn-text h40" data-del-file style="color:var(--error);">' + icon('del') + '<span>파일 삭제</span></button>' +
            '</div>' +
          '</div>' +
          '<div class="dialog-actions">' +
            '<button type="button" class="btn btn-outlined h40" data-remove-record>목록에서만 제거</button>' +
            '<button type="button" class="btn btn-text" data-close-detail>닫기</button>' +
          '</div>' +
        '</div>' +
      '</div>';
  }

  function deleteDialogHtml(targets, helpers) {
    var esc = helpers.esc;
    var body = targets.length === 1
      ? '&quot;' + esc(targets[0].fileName || '') + '&quot; 파일을 기기에서 삭제하고 갤러리 목록에서도 제거합니다.'
      : '선택한 이미지 ' + targets.length + '개를 기기에서 삭제하고 갤러리 목록에서도 제거합니다.';
    return '' +
      '<div class="dialog-scrim" data-delete-scrim>' +
        '<div class="dialog" role="dialog" aria-modal="true">' +
          '<div class="title-medium">이미지를 삭제할까요?</div>' +
          '<div class="body-medium">' + body + '</div>' +
          '<div class="dialog-actions">' +
            '<button type="button" class="btn btn-text" data-cancel-del>취소</button>' +
            '<button type="button" class="btn btn-error h40" data-confirm-del>삭제</button>' +
          '</div>' +
        '</div>' +
      '</div>';
  }

  /* ------------------------------------------------------------------ */
  /* 메인 렌더                                                            */
  /* ------------------------------------------------------------------ */

  window.KeyxifUI.renderGallery = function (container, state, actions, helpers) {
    last = { container: container, state: state, actions: actions, helpers: helpers };
    var esc = helpers.esc;
    var images = state.exportedImages || [];

    // 사라진 id 선택 집합에서 자동 제거 (spec 1.3)
    var alive = {};
    images.forEach(function (r) { alive[r.id] = true; });
    Object.keys(selectedIds).forEach(function (id) { if (!alive[id]) delete selectedIds[id]; });
    if (detailId && !alive[detailId]) detailId = null;

    var groups = mode === 'all' ? [] : buildGroups(images);
    var currentGroup = null;
    if (currentGroupKey && mode !== 'all') {
      for (var gi = 0; gi < groups.length; gi++) {
        if (groups[gi].key === currentGroupKey) { currentGroup = groups[gi]; break; }
      }
      if (!currentGroup) currentGroupKey = null;
    } else {
      currentGroupKey = null;
    }

    var selCount = Object.keys(selectedIds).length;
    var selectMode = selCount > 0;
    var visibleImages = currentGroup ? currentGroup.items : images;

    /* ---- HTML ---- */
    var h = [];

    // 헤더 (타이틀 "완성 이미지"는 main.js topbar 담당 — 서브타이틀부터)
    h.push('<div class="col gap8">');
    var subtitle = currentGroup
      ? currentGroup.title + ' 그룹 이미지 ' + currentGroup.items.length + '개'
      : 'Keyxif로 저장한 결과 이미지 ' + images.length + '개';
    h.push('<div class="body-medium muted">' + esc(subtitle) + '</div>');

    h.push('<div class="chip-row">');
    [['all', '전체'], ['housing', '하우징'], ['brand', '브랜드'], ['color', '색상']].forEach(function (m) {
      h.push('<button type="button" class="chip' + (mode === m[0] ? ' selected' : '') + '" data-gmode="' + m[0] + '"><span>' + m[1] + '</span></button>');
    });
    h.push('</div>');

    if (currentGroup) {
      h.push('<button type="button" class="btn btn-outlined h40" data-back style="align-self:flex-start;">' +
        esc(MODE_LABEL[mode]) + ' 그룹으로 돌아가기</button>');
    }

    if (images.length > 0) {
      h.push('<div class="row gap8">');
      if (selectMode) {
        h.push('<button type="button" class="btn btn-filled h40" data-del-selected>선택 삭제 ' + selCount + '</button>');
        h.push('<button type="button" class="btn btn-text" data-clear-sel>선택 해제</button>');
      }
      h.push('<button type="button" class="btn btn-outlined h40' + (selectMode ? ' grow' : '') + '" data-del-all>전체 삭제</button>');
      h.push('</div>');
    }
    h.push('</div>');

    // 본문
    if (images.length === 0) {
      // 빈 상태 (spec 1.10)
      h.push('<div class="empty-state" style="gap:12px;">');
      h.push('<div class="title-medium">아직 완성된 이미지가 없습니다.</div>');
      h.push('<div class="body-medium muted">사진을 선택하고 Keyxif 카드로 저장해보세요.</div>');
      h.push('<button type="button" class="btn btn-filled" data-empty-cta>사진 선택하기</button>');
      h.push('</div>');
    } else if (mode === 'all' || currentGroup) {
      h.push('<div class="grid-gallery">');
      visibleImages.forEach(function (rec) { h.push(imageCardHtml(rec, selectMode, helpers)); });
      h.push('</div>');
    } else {
      // 그룹 카드 그리드 (spec 1.6)
      if (groups.length === 0) {
        h.push('<div class="body-medium muted center" style="padding:36px 0;">그룹으로 묶을 이미지가 없습니다.</div>');
      } else {
        h.push('<div class="grid-groups">');
        groups.forEach(function (g) { h.push(groupCardHtml(g, helpers)); });
        h.push('</div>');
      }
    }

    // 상세 다이얼로그 (spec 1.7)
    var detailRec = null;
    if (detailId) {
      for (var di = 0; di < images.length; di++) {
        if (images[di].id === detailId) { detailRec = images[di]; break; }
      }
      if (detailRec) h.push(detailDialogHtml(detailRec, helpers));
    }

    // 삭제 확인 다이얼로그 (spec 1.8)
    if (deleteTargets && deleteTargets.length > 0) {
      h.push(deleteDialogHtml(deleteTargets, helpers));
    }

    container.innerHTML = h.join('');

    /* ---- 이벤트 바인딩 ---- */

    function qa(sel) { return Array.prototype.slice.call(container.querySelectorAll(sel)); }
    function q(sel) { return container.querySelector(sel); }

    qa('[data-gmode]').forEach(function (chip) {
      chip.addEventListener('click', function () {
        var m = chip.getAttribute('data-gmode');
        if (mode === m) return;
        mode = m;
        currentGroupKey = null;   // 선택된 그룹 해제
        selectedIds = {};         // 다중선택 초기화
        rerender();
      });
    });

    var backBtn = q('[data-back]');
    if (backBtn) backBtn.addEventListener('click', function () { currentGroupKey = null; rerender(); });

    var delSelBtn = q('[data-del-selected]');
    if (delSelBtn) delSelBtn.addEventListener('click', function () {
      var targets = visibleImages.filter(function (r) { return selectedIds[r.id]; });
      if (targets.length === 0) return;
      deleteTargets = targets;
      rerender();
    });

    var clearSelBtn = q('[data-clear-sel]');
    if (clearSelBtn) clearSelBtn.addEventListener('click', function () { selectedIds = {}; rerender(); });

    var delAllBtn = q('[data-del-all]');
    if (delAllBtn) delAllBtn.addEventListener('click', function () {
      deleteTargets = images.slice(); // 현재 그룹이 아닌 전체 목록
      rerender();
    });

    qa('[data-group-key]').forEach(function (card) {
      card.addEventListener('click', function () {
        currentGroupKey = card.getAttribute('data-group-key');
        rerender();
      });
    });

    // 이미지 카드: 탭 / 롱프레스 (spec 1.3)
    qa('[data-img-id]').forEach(function (card) {
      var id = card.getAttribute('data-img-id');
      var lpTimer = null;
      var lpFiredHere = false;

      function clearLp() { if (lpTimer) { clearTimeout(lpTimer); lpTimer = null; } }

      card.addEventListener('pointerdown', function (e) {
        if (e.pointerType === 'mouse' && e.button !== 0) return;
        lpFiredHere = false;
        clearLp();
        lpTimer = setTimeout(function () {
          lpTimer = null;
          lpFiredHere = true;
          longPressConsumed = true;
          setTimeout(function () { longPressConsumed = false; }, 600);
          selectedIds[id] = true; // 선택 집합에 추가 → 선택 모드 진입
          rerender();
        }, 500);
      });
      ['pointerup', 'pointercancel', 'pointerleave'].forEach(function (ev) {
        card.addEventListener(ev, clearLp);
      });
      card.addEventListener('contextmenu', function (e) { e.preventDefault(); });

      card.addEventListener('click', function () {
        if (lpFiredHere || longPressConsumed) { lpFiredHere = false; longPressConsumed = false; return; }
        if (selectMode) {
          if (selectedIds[id]) delete selectedIds[id];
          else selectedIds[id] = true;
          rerender();
        } else {
          detailId = id;
          rerender();
        }
      });
    });

    var cta = q('[data-empty-cta]');
    if (cta) cta.addEventListener('click', function () {
      actions.closeGallery();
      actions.navigateToStep('Photos');
    });

    // 상세 다이얼로그
    if (detailRec) {
      var detailScrim = q('[data-detail-scrim]');
      detailScrim.addEventListener('click', function (e) {
        if (e.target === detailScrim) { detailId = null; rerender(); }
      });
      q('[data-share]').addEventListener('click', function () { actions.shareExportedImage(detailRec); });
      q('[data-download]').addEventListener('click', function () { actions.openExportedImage(detailRec); });
      q('[data-del-file]').addEventListener('click', function () {
        detailId = null;                 // 상세 닫고
        deleteTargets = [detailRec];     // 삭제 확인 열기
        rerender();
      });
      q('[data-close-detail]').addEventListener('click', function () { detailId = null; rerender(); });
      q('[data-remove-record]').addEventListener('click', function () {
        actions.removeExportedImageRecord(detailRec.id); // 확인 없이 즉시 레코드만 제거
        detailId = null;
        rerender();
      });
    }

    // 삭제 확인 다이얼로그
    if (deleteTargets && deleteTargets.length > 0) {
      var delScrim = q('[data-delete-scrim]');
      delScrim.addEventListener('click', function (e) {
        if (e.target === delScrim) { deleteTargets = null; rerender(); }
      });
      q('[data-cancel-del]').addEventListener('click', function () { deleteTargets = null; rerender(); });
      q('[data-confirm-del]').addEventListener('click', function () {
        var targets = deleteTargets.slice();
        deleteTargets = null;
        targets.forEach(function (t) { delete selectedIds[t.id]; }); // 삭제된 id 선택 집합에서 제거
        actions.deleteExportedImageFiles(targets);
        rerender();
      });
    }
  };
})();
