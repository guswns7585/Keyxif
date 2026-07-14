/* =============================================================================
   KeyxifSearch — PresetRepository.kt 검색/랭킹/로고 해석 포트
   screens-spec.md "제안 필터링·랭킹" + state-flow-spec 기준.
   의존: window.KEYXIF_PRESETS (presets.js)
   ============================================================================= */
(function () {
  'use strict';

  function P() { return window.KEYXIF_PRESETS || { vendors: [], housings: [], switches: [], keycaps: [], logos: [] }; }

  // 정규화 키: trim → lowercase → [a-z0-9가-힣] 외 제거
  function normalize(s) {
    return String(s == null ? '' : s).trim().toLowerCase().replace(/[^a-z0-9가-힣]+/g, '');
  }

  /* ---- 랭크: 0 정확 → 1 접두 → 2 정규정확 → 3 정규접두 → 4 부분 → 5 정규부분, 미매칭 -1 ---- */
  function rankOne(candidate, query, normQuery) {
    if (!candidate) return -1;
    var c = String(candidate).toLowerCase();
    var q = query.toLowerCase();
    var nc = normalize(candidate);
    if (q) {
      if (c === q) return 0;
      if (c.indexOf(q) === 0) return 1;
    }
    if (normQuery) {
      if (nc === normQuery) return 2;
      if (nc.indexOf(normQuery) === 0) return 3;
    }
    if (q && c.indexOf(q) >= 0) return 4;
    if (normQuery && nc.indexOf(normQuery) >= 0) return 5;
    return -1;
  }
  function bestRank(fields, query, normQuery) {
    var best = -1;
    for (var i = 0; i < fields.length; i++) {
      var r = rankOne(fields[i], query, normQuery);
      if (r >= 0 && (best < 0 || r < best)) best = r;
    }
    return best;
  }

  function vendorById(id) {
    if (id == null) return null;
    var vs = P().vendors;
    for (var i = 0; i < vs.length; i++) if (vs[i].id === id) return vs[i];
    return null;
  }

  function housingSearchFields(hp) {
    var fields = [hp.name].concat(hp.aliases || []);
    if (hp.vendor) fields.push(hp.vendor);
    if (hp.designer) fields.push(hp.designer);
    var v = vendorById(hp.vendorId);
    if (v) {
      fields.push(v.name);
      fields = fields.concat(v.aliases || []);
    }
    return fields;
  }
  function housingSubtitle(hp) {
    var vendor = hp.vendor || (vendorById(hp.vendorId) || {}).name || null;
    var parts = [];
    if (vendor) parts.push(vendor);
    if (hp.designer) parts.push(hp.designer);
    return parts.join(' / ') || null;
  }

  /* ---- 공용 검색 합성: 최근(매칭) 먼저 → 프리셋(랭크순) → dedupe → 상한 ---- */
  function search(query, recents, presets, fieldsOf, subtitleOf) {
    var q = String(query || '').trim();
    var nq = normalize(q);
    var out = [];

    (recents || []).forEach(function (r) {
      if (!q || bestRank([r], q, nq) >= 0) {
        out.push({ title: r, subtitle: '최근 사용', isRecent: true, preset: null });
      }
    });

    var ranked = [];
    (presets || []).forEach(function (p) {
      var rank = q || nq ? bestRank(fieldsOf(p), q, nq) : 0;
      if (rank >= 0) ranked.push({ p: p, rank: rank });
    });
    ranked.sort(function (a, b) { return a.rank - b.rank; });
    ranked.forEach(function (e) {
      out.push({ title: e.p.name, subtitle: subtitleOf(e.p), isRecent: false, preset: e.p });
    });

    // dedupe: 정규(title)|정규(subtitle)
    var seen = {};
    var deduped = [];
    for (var i = 0; i < out.length; i++) {
      var key = normalize(out[i].title) + '|' + normalize(out[i].subtitle || '');
      if (seen[key]) continue;
      seen[key] = true;
      deduped.push(out[i]);
    }
    return deduped.slice(0, q ? 160 : 80);
  }

  function searchHousings(query, recents) {
    return search(query, recents, P().housings, housingSearchFields, housingSubtitle);
  }
  function searchSwitches(query, recents, includePresets) {
    var presets = includePresets === false ? [] : P().switches;
    return search(query, recents, presets,
      function (p) { return [p.name].concat(p.aliases || []).concat(p.manufacturer ? [p.manufacturer] : []); },
      function (p) { return p.manufacturer || '앱 지원'; });
  }
  function searchKeycaps(query, recents) {
    return search(query, recents, P().keycaps,
      function (p) { return [p.name].concat(p.aliases || []).concat(p.manufacturer ? [p.manufacturer] : []); },
      function (p) { return p.manufacturer || null; });
  }

  /* ---- 로고 해석 (PresetRepository.logoForBuildInfo 체인) ---- */
  function logoById(id) {
    if (id == null) return null;
    var ls = P().logos;
    for (var i = 0; i < ls.length; i++) if (ls[i].id === id) return ls[i];
    return null;
  }
  function logoName(logoId) {
    var l = logoById(logoId);
    return l ? l.name : null;
  }
  function logoIdForHousing(hp) {
    if (!hp) return null;
    if (hp.logoId) return hp.logoId;
    var v = vendorById(hp.vendorId);
    if (v && v.logoId) return v.logoId;
    return null;
  }
  function matchHousingByText(text) {
    var n = normalize(text);
    if (!n) return null;
    var hs = P().housings;
    // 1차: 정확 정규 일치 (이름/별칭/id)
    for (var i = 0; i < hs.length; i++) {
      var hp = hs[i];
      if (normalize(hp.name) === n || normalize(hp.id) === n) return hp;
      var al = hp.aliases || [];
      for (var j = 0; j < al.length; j++) if (normalize(al[j]) === n) return hp;
    }
    // 2차: 접두 일치
    for (i = 0; i < hs.length; i++) {
      if (normalize(hs[i].name).indexOf(n) === 0) return hs[i];
    }
    return null;
  }
  function logoForBuildInfo(buildInfo) {
    if (!buildInfo || buildInfo.logoDisabled) return null;
    // 1) 명시 logoId
    var direct = logoById(buildInfo.logoId);
    if (direct) return direct;
    // 2) 하우징 텍스트 → 하우징 프리셋 → 로고 체인
    var hp = matchHousingByText(buildInfo.housing);
    if (hp) return logoById(logoIdForHousing(hp));
    return null;
  }

  /* ---- 정적 목록 (PresetData.kt) ---- */
  var PLATES = ['Alu', 'PP', 'POM', 'PC', 'FR4', 'Brass', 'SUS', 'Copper', 'PEI', 'CF', 'Plateless'];
  var MOUNTS = ['Top-mount', 'Bottom-mount', 'Sandwich-mount', 'O-ring-mount', 'Gasket-mount', 'Leaf Spring-mount', 'Tadpole-mount'];

  window.KeyxifSearch = {
    normalize: normalize,
    searchHousings: searchHousings,
    searchSwitches: searchSwitches,
    searchKeycaps: searchKeycaps,
    logoForBuildInfo: logoForBuildInfo,
    logoName: logoName,
    logoById: logoById,
    logoIdForHousing: logoIdForHousing,
    matchHousingByText: matchHousingByText,
    get plates() { return (P().plates && P().plates.length ? P().plates : PLATES); },
    get mounts() { return (P().mounts && P().mounts.length ? P().mounts : MOUNTS); },
    get logos() {
      return P().logos.slice().sort(function (a, b) {
        return String(a.name).localeCompare(String(b.name), undefined, { sensitivity: 'base' });
      });
    },
  };
})();
