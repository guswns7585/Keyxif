/* =============================================================================
   KeyxifWebp — WASM(libwebp) 기반 WebP 인코더
   iOS 사파리처럼 canvas.toBlob('image/webp')를 지원하지 않는 브라우저용 폴백.
   인코더(글루+wasm ~320KB)는 실제로 필요할 때(첫 WebP 저장 시) 지연 로딩한다.
   기반: Google libwebp / Squoosh (@jsquash/webp의 emscripten 빌드), Apache-2.0.
   ============================================================================= */
(function () {
  'use strict';

  // 이 스크립트 자신의 URL(호스팅 시) → data/webp/ 경로 기준. 인라인(단일파일)이면 location 기준.
  var SELF = (document.currentScript && document.currentScript.src) || '';

  // 전체 옵션 기본값 (meta.js defaultOptions). quality만 호출부에서 덮어씀.
  var DEFAULT_OPTIONS = {
    quality: 88, target_size: 0, target_PSNR: 0, method: 4, sns_strength: 50,
    filter_strength: 60, filter_sharpness: 0, filter_type: 1, partitions: 0,
    segments: 4, pass: 1, show_compressed: 0, preprocessing: 0, autofilter: 0,
    partition_limit: 0, alpha_compression: 1, alpha_filtering: 1, alpha_quality: 100,
    lossless: 0, exact: 0, image_hint: 0, emulate_jpeg_size: 0, thread_level: 0,
    low_memory: 0, near_lossless: 100, use_delta_palette: 0, use_sharp_yuv: 0,
  };

  var modulePromise = null;

  function resolveUrl(rel) {
    var base = SELF || window.location.href;
    return new URL(rel, base).href;
  }

  // 인코더 모듈 지연 로딩 (glue = ESM default factory, wasm = 바이너리 주입)
  function loadModule() {
    if (modulePromise) return modulePromise;
    modulePromise = (function () {
      var glueUrl = resolveUrl('data/webp/webp_enc.js');
      var wasmUrl = resolveUrl('data/webp/webp_enc.wasm');
      return Promise.all([
        import(/* @vite-ignore */ glueUrl),
        fetch(wasmUrl).then(function (r) {
          if (!r.ok) throw new Error('webp wasm fetch 실패: ' + r.status);
          return r.arrayBuffer();
        }),
      ]).then(function (parts) {
        var factory = parts[0] && parts[0].default;
        var wasmBinary = parts[1];
        if (typeof factory !== 'function') throw new Error('webp 인코더 모듈 형식 오류');
        return factory({ noInitialRun: true, wasmBinary: wasmBinary });
      });
    })().catch(function (e) {
      modulePromise = null; // 실패 시 다음에 재시도 가능
      throw e;
    });
    return modulePromise;
  }

  // canvas → WebP Blob
  function encode(canvas, quality) {
    return loadModule().then(function (mod) {
      var ctx = canvas.getContext('2d');
      var img = ctx.getImageData(0, 0, canvas.width, canvas.height);
      var opts = {};
      for (var k in DEFAULT_OPTIONS) opts[k] = DEFAULT_OPTIONS[k];
      opts.quality = Math.max(1, Math.min(100, quality || 88));
      var result = mod.encode(img.data, canvas.width, canvas.height, opts);
      if (!result || !result.length) throw new Error('WebP 인코딩 실패');
      // embind가 반환한 typed array를 복사해 Blob 생성 (뷰 무효화 방지)
      return new Blob([new Uint8Array(result)], { type: 'image/webp' });
    });
  }

  // 미리 로드해두기(선택) — 호출 안 해도 encode 시 자동 로드
  function preload() { return loadModule().then(function () { return true; }, function () { return false; }); }

  window.KeyxifWebp = { encode: encode, preload: preload };
})();
