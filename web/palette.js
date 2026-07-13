(function () {
  'use strict';

  // 1:1 port of PhotoPaletteAnalyzer.kt (Keyxif 1.0.3).
  // Input image is assumed already EXIF-corrected; the canvas decode below
  // replaces BitmapUtils.decodeOrientedBitmap(context, uri, ANALYSIS_LONG_SIDE).

  var ANALYSIS_LONG_SIDE = 640;
  var DEFAULT_CENTER_CROP_RATIO = 0.75;
  var MAX_SAMPLES = 12000;
  var MAX_COLORS = 5;
  var MIN_ALPHA = 220;
  var MIN_BUCKET_COUNT = 2;
  var QUANTIZE_STEP = 16;
  var MIN_DISTANCE_SQUARED = 44 * 44;

  var fround = Math.fround;

  // Kotlin Float literals, pre-rounded to 32-bit so comparisons match exactly.
  var F_035 = fround(0.35);
  var F_0035 = fround(0.035);
  var F_008 = fround(0.08);
  var F_096 = fround(0.96);
  var F_094 = fround(0.94);
  var F_088 = fround(0.88);
  var F_014 = fround(0.14);
  var F_062 = fround(0.62);
  var F_058 = fround(0.58);
  var F_042 = fround(0.42);
  var F_072 = fround(0.72);

  function analyze(image, mode, maxColors, centerCropRatio, rectNormalized, maskStrokes) {
    var mc = (typeof maxColors === 'number' && isFinite(maxColors)) ? Math.floor(maxColors) : MAX_COLORS;
    mc = Math.min(MAX_COLORS, Math.max(3, mc)); // maxColors.coerceIn(3, MAX_COLORS)
    var ratio = (typeof centerCropRatio === 'number' && isFinite(centerCropRatio))
      ? centerCropRatio
      : DEFAULT_CENTER_CROP_RATIO;

    var decoded = decodeToCanvas(image);
    if (decoded === null) return [];
    var source = mode === 'RectSelection'
      ? rectCrop(decoded, rectNormalized)
      : (mode === 'AutoCenter' ? centerCrop(decoded, ratio) : decoded);
    var mask = mode === 'PaintedMask' ? createMask(decoded, maskStrokes || []) : null;
    return extractColors(source, mc, mask);
  }

  function createCanvas(width, height) {
    if (typeof document !== 'undefined') {
      var canvas = document.createElement('canvas');
      canvas.width = width;
      canvas.height = height;
      return canvas;
    }
    return new OffscreenCanvas(width, height);
  }

  function context2d(canvas) {
    return canvas.getContext('2d', { willReadFrequently: true });
  }

  // Replaces decodeOrientedBitmap: draw to a canvas capped at 256 on the long side.
  function decodeToCanvas(image) {
    if (!image) return null;
    var width = (typeof image.naturalWidth === 'number') ? image.naturalWidth : image.width;
    var height = (typeof image.naturalHeight === 'number') ? image.naturalHeight : image.height;
    if (!(width > 0) || !(height > 0)) return null;
    var targetWidth = width;
    var targetHeight = height;
    var longest = Math.max(width, height);
    if (longest > ANALYSIS_LONG_SIDE) {
      var scale = fround(ANALYSIS_LONG_SIDE / longest);
      targetWidth = Math.max(1, Math.round(fround(width * scale)));
      targetHeight = Math.max(1, Math.round(fround(height * scale)));
    }
    var canvas = createCanvas(targetWidth, targetHeight);
    var ctx = context2d(canvas);
    ctx.imageSmoothingEnabled = true;
    ctx.drawImage(image, 0, 0, targetWidth, targetHeight);
    return canvas;
  }

  function centerCrop(canvas, ratio) {
    var safeRatio = coerce(fround(ratio), F_035, 1); // ratio.coerceIn(0.35f, 1f)
    var cropWidth = coerceInt(Math.round(fround(canvas.width * safeRatio)), 1, canvas.width);
    var cropHeight = coerceInt(Math.round(fround(canvas.height * safeRatio)), 1, canvas.height);
    var left = Math.max(0, Math.floor((canvas.width - cropWidth) / 2));
    var top = Math.max(0, Math.floor((canvas.height - cropHeight) / 2));
    if (left === 0 && top === 0 && cropWidth === canvas.width && cropHeight === canvas.height) {
      return canvas;
    }
    var cropped = createCanvas(cropWidth, cropHeight);
    context2d(cropped).drawImage(canvas, left, top, cropWidth, cropHeight, 0, 0, cropWidth, cropHeight);
    return cropped;
  }

  function rectCrop(canvas, rect) {
    var r = rect || { left: 0.15, top: 0.39, right: 0.85, bottom: 0.61 };
    var left = coerce(Math.round(coerce(Number(r.left) || 0, 0, 0.95) * canvas.width), 0, canvas.width - 1);
    var top = coerce(Math.round(coerce(Number(r.top) || 0, 0, 0.95) * canvas.height), 0, canvas.height - 1);
    var right = coerce(Math.round(coerce(Number(r.right) || 1, 0.05, 1) * canvas.width), left + 1, canvas.width);
    var bottom = coerce(Math.round(coerce(Number(r.bottom) || 1, 0.05, 1) * canvas.height), top + 1, canvas.height);
    var cropped = createCanvas(right - left, bottom - top);
    context2d(cropped).drawImage(canvas, left, top, right - left, bottom - top, 0, 0, right - left, bottom - top);
    return cropped;
  }

  function createMask(canvas, strokes) {
    if (!strokes.length) throw new Error('선택된 영역이 너무 작습니다. 키보드 부분을 더 칠해 주세요.');
    var mask = createCanvas(canvas.width, canvas.height);
    var ctx = context2d(mask);
    ctx.lineCap = 'round'; ctx.lineJoin = 'round';
    strokes.forEach(function (stroke) {
      var points = stroke.points || [];
      if (!points.length) return;
      ctx.globalCompositeOperation = stroke.isEraser ? 'destination-out' : 'source-over';
      ctx.strokeStyle = '#fff'; ctx.fillStyle = '#fff';
      ctx.lineWidth = coerce(Number(stroke.brushSizeNormalized) || 0.06, 0.01, 0.25) * Math.max(canvas.width, canvas.height);
      if (points.length === 1) {
        ctx.beginPath(); ctx.arc(points[0].x * canvas.width, points[0].y * canvas.height, ctx.lineWidth / 2, 0, Math.PI * 2); ctx.fill();
      } else {
        ctx.beginPath(); ctx.moveTo(points[0].x * canvas.width, points[0].y * canvas.height);
        for (var i = 1; i < points.length; i++) ctx.lineTo(points[i].x * canvas.width, points[i].y * canvas.height);
        ctx.stroke();
      }
    });
    ctx.globalCompositeOperation = 'source-over';
    return mask;
  }

  function extractColors(canvas, maxColors, mask) {
    var width = canvas.width;
    var height = canvas.height;
    if (width <= 0 || height <= 0) return [];
    var data = context2d(canvas).getImageData(0, 0, width, height).data;
    var maskData = mask ? context2d(mask).getImageData(0, 0, mask.width, mask.height).data : null;
    if (maskData) {
      var selectedPixels = 0;
      for (var mi = 3; mi < maskData.length; mi += 4) if (maskData[mi] >= 64) selectedPixels++;
      if (selectedPixels < 300) throw new Error('선택된 영역이 너무 작습니다. 키보드 부분을 더 칠해 주세요.');
    }
    var pixelCount = width * height; // pixel count, not byte count
    var stride = Math.max(1, Math.floor(pixelCount / MAX_SAMPLES));
    var buckets = new Map(); // key -> bucket, first-seen (insertion) order
    var hsl = [0, 0, 0];

    for (var index = 0; index < pixelCount; index += stride) {
      var offset = index * 4;
      if (maskData && maskData[offset + 3] < 64) continue;
      if (data[offset + 3] < MIN_ALPHA) continue;
      var red = data[offset];
      var green = data[offset + 1];
      var blue = data[offset + 2];
      rgbToHsl(red, green, blue, hsl);
      if (isLowValueNoise(hsl)) continue;
      var key = quantizedKey(red, green, blue);
      var bucket = buckets.get(key);
      if (bucket === undefined) {
        bucket = { count: 0, redSum: 0, greenSum: 0, blueSum: 0 };
        buckets.set(key, bucket);
      }
      bucket.count += 1;
      bucket.redSum += red;
      bucket.greenSum += green;
      bucket.blueSum += blue;
    }

    // Kotlin iterates HashMap values before the stable sort; replicate
    // java.util.HashMap iteration order so score ties break identically.
    var orderedKeys = javaHashMapIterationOrder(Array.from(buckets.keys()));
    var candidates = [];
    for (var i = 0; i < orderedKeys.length; i++) {
      var b = buckets.get(orderedKeys[i]);
      if (b.count >= MIN_BUCKET_COUNT) candidates.push(toCandidate(b));
    }
    candidates.sort(function (x, y) { return y.score - x.score; }); // stable, descending

    // distinctByDistance + take(maxColors)
    var selected = [];
    for (var j = 0; j < candidates.length && selected.length < maxColors; j++) {
      var color = candidates[j].color;
      var distinct = true;
      for (var k = 0; k < selected.length; k++) {
        if (rgbDistanceSquared(selected[k], color) < MIN_DISTANCE_SQUARED) {
          distinct = false;
          break;
        }
      }
      if (distinct) selected.push(color);
    }
    return selected;
  }

  function isLowValueNoise(hsl) {
    var saturation = hsl[1];
    var lightness = hsl[2];
    return saturation < F_0035 && (lightness < F_008 || lightness > F_096);
  }

  function quantizedKey(red, green, blue) {
    return (((red / QUANTIZE_STEP) | 0) << 8) |
      (((green / QUANTIZE_STEP) | 0) << 4) |
      ((blue / QUANTIZE_STEP) | 0);
  }

  function toCandidate(bucket) {
    // Long division truncates; sums are non-negative so floor === trunc.
    var red = coerceInt(Math.floor(bucket.redSum / bucket.count), 0, 255);
    var green = coerceInt(Math.floor(bucket.greenSum / bucket.count), 0, 255);
    var blue = coerceInt(Math.floor(bucket.blueSum / bucket.count), 0, 255);
    var hsl = [0, 0, 0];
    rgbToHsl(red, green, blue, hsl);
    var saturation = hsl[1];
    var lightness = hsl[2];
    var neutralPenalty = (saturation < F_008) ? F_058 : 1;
    var edgeLightnessPenalty;
    if (lightness < F_008 || lightness > F_094) {
      edgeLightnessPenalty = F_042;
    } else if (lightness < F_014 || lightness > F_088) {
      edgeLightnessPenalty = F_072;
    } else {
      edgeLightnessPenalty = 1;
    }
    // count * (0.62f + saturation) * neutralPenalty * edgeLightnessPenalty (Float, left-assoc)
    var score = fround(fround(fround(bucket.count * fround(F_062 + saturation)) * neutralPenalty) * edgeLightnessPenalty);
    return { color: colorRgb(red, green, blue), score: score };
  }

  // androidx.core.graphics.ColorUtils.RGBToHSL, with every Float op
  // rounded through Math.fround to match 32-bit float arithmetic.
  function rgbToHsl(r, g, b, outHsl) {
    var rf = fround(r / 255);
    var gf = fround(g / 255);
    var bf = fround(b / 255);
    var max = Math.max(rf, Math.max(gf, bf));
    var min = Math.min(rf, Math.min(gf, bf));
    var deltaMaxMin = fround(max - min);
    var h;
    var s;
    var l = fround(fround(max + min) / 2);
    if (max === min) {
      h = 0;
      s = 0;
    } else {
      if (max === rf) {
        h = fround(fround(fround(gf - bf) / deltaMaxMin) % 6);
      } else if (max === gf) {
        h = fround(fround(fround(bf - rf) / deltaMaxMin) + 2);
      } else {
        h = fround(fround(fround(rf - gf) / deltaMaxMin) + 4);
      }
      s = fround(deltaMaxMin / fround(1 - Math.abs(fround(fround(2 * l) - 1))));
    }
    h = fround(fround(h * 60) % 360);
    if (h < 0) h = fround(h + 360);
    outHsl[0] = coerce(h, 0, 360);
    outHsl[1] = coerce(s, 0, 1);
    outHsl[2] = coerce(l, 0, 1);
  }

  function colorRgb(red, green, blue) {
    // Android Color.rgb(): signed 32-bit int 0xFFRRGGBB
    return (0xff000000 | (red << 16) | (green << 8) | blue) | 0;
  }

  function rgbDistanceSquared(first, second) {
    var red = ((first >> 16) & 0xff) - ((second >> 16) & 0xff);
    var green = ((first >> 8) & 0xff) - ((second >> 8) & 0xff);
    var blue = (first & 0xff) - (second & 0xff);
    return red * red + green * green + blue * blue;
  }

  // java.util.HashMap iteration order for Integer keys (< 2^16, so hash == key):
  // buckets ascending by (key & (capacity-1)), insertion order within a bucket
  // (Java 8 resize splitting preserves relative order). Capacity growth follows
  // put-by-put simulation: resize when size > threshold (load factor 0.75), and
  // treeifyBin -> resize when a bin reaches 9 nodes while capacity < 64.
  // (Bins that would actually treeify at capacity >= 64 keep insertion order
  // here; that only matters when two candidate scores tie exactly.)
  function javaHashMapIterationOrder(keysInInsertionOrder) {
    var capacity = 16;
    var threshold = 12;
    var size = 0;
    var binCounts = new Map();

    function resize() {
      capacity *= 2;
      threshold *= 2;
      binCounts = new Map();
      for (var j = 0; j < size; j++) {
        var idx = keysInInsertionOrder[j] & (capacity - 1);
        binCounts.set(idx, (binCounts.get(idx) || 0) + 1);
      }
    }

    for (var i = 0; i < keysInInsertionOrder.length; i++) {
      var index = keysInInsertionOrder[i] & (capacity - 1);
      var binCount = (binCounts.get(index) || 0) + 1;
      binCounts.set(index, binCount);
      size += 1;
      if (binCount >= 9 && capacity < 64) resize();
      if (size > threshold) resize();
    }

    var mask = capacity - 1;
    return keysInInsertionOrder.slice().sort(function (a, b) {
      return (a & mask) - (b & mask); // stable sort keeps insertion order within a bucket
    });
  }

  function coerce(value, low, high) {
    return value < low ? low : (value > high ? high : value);
  }

  function coerceInt(value, low, high) {
    return Math.min(high, Math.max(low, value));
  }

  var globalObject = (typeof window !== 'undefined') ? window : globalThis;
  globalObject.KeyxifPalette = { analyze: analyze };
})();
