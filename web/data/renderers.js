/* ===========================================================================
 * KeyxifRenderer — 1:1 JavaScript Canvas2D port of the Keyxif Android
 * Canvas rendering engine (Keyxif 1.0.3).
 *
 * Kotlin source -> sections in this file:
 *   domain/model/BuildInfoDisplay.kt   -> "BuildInfoDisplay" section
 *                                         (meaningfulBuildTextOrNull, isMeaningfulBuildText,
 *                                          toDisplayRows, displayNicknameOrNull,
 *                                          displayTitleOrNull, blockedBuildTexts)
 *   domain/renderer/TextDrawUtils.kt   -> "TextDrawUtils" section (ellipsize)
 *   domain/renderer/CanvasRenderUtils.kt -> "CanvasRenderUtils" section
 *                                         (paint/medium/regular, drawTextBaseline,
 *                                          drawRoundRect, drawGradientScrim, scaled,
 *                                          nicknameText, drawLabelValue, buildParts,
 *                                          visiblePaletteColors, drawPaletteChips,
 *                                          drawPaletteChipsInRect, MIN_TEXT_SIZE_PX,
 *                                          PALETTE_CHIP_SCALE)
 *   domain/renderer/LogoDrawUtils.kt   -> "LogoDrawUtils" section
 *                                         (draw, fitCenter, fitInside, fitHeight,
 *                                          LogoAnchor, LogoFitMode)
 *   domain/renderer/TemplateRenderer.kt -> "TemplateRenderer defaults" section
 *                                         (default backgroundColor/photoBounds/
 *                                          photoPlacement/logoBackgroundTone,
 *                                          PhotoPlacement)
 *   domain/renderer/TemplateRenderers.kt -> "Template renderers" section
 *                                         (all 12 renderers + drawInfoRow,
 *                                          drawLogoIfPresent, detailText,
 *                                          detailTextExcluding, nicknameDetail,
 *                                          rowsExcludingTitle, normalizedDisplayKey)
 *   domain/renderer/KeyxifCanvasRenderer.kt -> "Orchestrator" section
 *                                         (render(), drawTemplatePhoto,
 *                                          resolveLogoImage tone-variant chains)
 *
 * Android -> Canvas2D mapping conventions used throughout:
 *   - Paint(textSize = N, typeface = sans-serif-medium) -> ctx.font = "500 Npx sans-serif"
 *     (sans-serif -> 400, bold -> 700). A Paint here is a plain object
 *     { color, size, weight, align } with measureText/ascent/descent methods that
 *     measure via a shared hidden canvas, so signatures match the Kotlin Paint API.
 *   - canvas.drawText(text, x, baseline) -> ctx.fillText(text, x, baseline) with
 *     ctx.textBaseline = 'alphabetic'.
 *   - Paint.ascent()/descent() -> -fontBoundingBoxAscent / fontBoundingBoxDescent
 *     (fallback -0.8*size / 0.2*size where the metrics API is unavailable).
 *   - drawRoundRect -> ctx.roundRect when present, else an arcTo path.
 *   - LinearGradient -> ctx.createLinearGradient.
 *   - COLOR CHOICE: all colors are CSS strings. Color.rgb(r,g,b) -> "rgb(r,g,b)",
 *     Color.argb(a,r,g,b) -> "rgba(r,g,b,a/255)". Color.TRANSPARENT -> null.
 *     assets.paletteColors may be given either as "#rrggbb" (or any CSS color
 *     string) or as Android ARGB/RGB ints; ints are converted by colorToCss().
 *   - clipRect / clipPath -> ctx.save() + ctx.clip() + ctx.restore().
 *   - Output bitmap ARGB_8888 (source size) -> <canvas> with width/height equal
 *     to the source image's natural pixel size.
 *
 * Public API (window.KeyxifRenderer):
 *   render({ image, buildInfo, template, settings, assets }) -> HTMLCanvasElement
 *     image    : ImageBitmap | HTMLImageElement (already orientation-corrected)
 *     buildInfo: { housing, switchName, plate, mount, keycap, nickname,
 *                  logoId, customLogoImage (Image|null), logoDisabled }
 *                (logoId is not resolved here — the host app resolves it to
 *                 assets.logoLabel / assets.logoImage / assets.logoVariants)
 *     template : template id string (see KeyxifRenderer.templates)
 *     settings : { textScale, nicknameStyle ('Plain'|'AtPrefix'|'Credit'),
 *                  nicknameEmphasis, showPaletteColors, paletteColorCount,
 *                  showBuildInfoInPlainExport, autoSelectLogoContrastVariant }
 *     assets   : { logoImage (Image|null), logoLabel (string),
 *                  paletteColors (array of '#rrggbb' css strings or ARGB ints),
 *                  hasLogo (informational; render() recomputes it exactly like
 *                  the Kotlin RenderAssets default: logo image present OR the
 *                  label is meaningful build text),
 *                  logoVariants (optional { default, black, white } Images used
 *                  for the tone-based contrast-variant selection; when absent,
 *                  logoImage is used as the sole/default variant) }
 *   templates : ordered [{ id, backgroundTone }] matching the CardTemplate enum order.
 *   utils     : ellipsize, paints, text/palette/logo draw helpers, RectF, etc.
 * =========================================================================== */
(function () {
  'use strict';

  /* ---------------------------------------------------------------------- */
  /* Shared primitives                                                       */
  /* ---------------------------------------------------------------------- */

  var MIN_TEXT_SIZE_PX = 8.5;
  var PALETTE_CHIP_SCALE = 1.15;

  var COLOR_WHITE = 'rgb(255,255,255)';
  var COLOR_BLACK = 'rgb(0,0,0)';
  var COLOR_TRANSPARENT = null;

  function rgb(r, g, b) {
    return 'rgb(' + r + ',' + g + ',' + b + ')';
  }

  function argb(a, r, g, b) {
    return 'rgba(' + r + ',' + g + ',' + b + ',' + (a / 255) + ')';
  }

  // Android ARGB int (possibly signed) or CSS string -> CSS string.
  // Ints with a zero alpha byte are treated as opaque 0xRRGGBB values.
  function colorToCss(color) {
    if (typeof color === 'string') return color;
    if (typeof color !== 'number' || !isFinite(color)) return COLOR_BLACK;
    var v = color >>> 0;
    var a = (v >>> 24) & 0xff;
    var r = (v >>> 16) & 0xff;
    var g = (v >>> 8) & 0xff;
    var b = v & 0xff;
    if (a === 0) return rgb(r, g, b);
    if (a === 255) return rgb(r, g, b);
    return argb(a, r, g, b);
  }

  function colorToRgb(color) {
    if (typeof color === 'number' && isFinite(color)) {
      var v = color >>> 0;
      return { r: (v >>> 16) & 0xff, g: (v >>> 8) & 0xff, b: v & 0xff };
    }
    if (typeof color === 'string') {
      var m = color.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/i);
      if (m) return { r: Number(m[1]), g: Number(m[2]), b: Number(m[3]) };
      var hex = color.trim().replace(/^#/, '');
      if (hex.length === 6) {
        return {
          r: parseInt(hex.slice(0, 2), 16),
          g: parseInt(hex.slice(2, 4), 16),
          b: parseInt(hex.slice(4, 6), 16),
        };
      }
    }
    return { r: 0, g: 0, b: 0 };
  }

  function relativeLuminance(color) {
    var c = colorToRgb(color);
    function linear(channel) {
      var value = channel / 255;
      return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
    return 0.2126 * linear(c.r) + 0.7152 * linear(c.g) + 0.0722 * linear(c.b);
  }

  function readableContentColor(backgroundColor) {
    return relativeLuminance(backgroundColor) >= 0.36 ? rgb(20, 21, 20) : COLOR_WHITE;
  }

  function isDarkColor(backgroundColor) {
    return readableContentColor(backgroundColor) === COLOR_WHITE;
  }

  function sampleRenderedColor(ctx, x, y, fallback) {
    try {
      var px = clamp(Math.round(x), 0, ctx.canvas.width - 1);
      var py = clamp(Math.round(y), 0, ctx.canvas.height - 1);
      var data = ctx.getImageData(px, py, 1, 1).data;
      return rgb(data[0], data[1], data[2]);
    } catch (e) {
      return fallback || COLOR_BLACK;
    }
  }

  function contrastColorAt(ctx, assets, x, y) {
    return isDarkColor(sampleRenderedColor(ctx, x, y, assets.cardBackgroundColor)) ? COLOR_WHITE : rgb(18, 19, 18);
  }

  function assetsWithLogoContrast(assets, color) {
    var contrastImage = color === COLOR_WHITE ? assets.whiteLogoImage : assets.blackLogoImage;
    var next = Object.assign({}, assets);
    if (contrastImage) {
      next.logoBitmap = contrastImage;
      next.logoTintColor = null;
    } else if (next.logoTintColor) {
      next.logoTintColor = color;
    }
    return next;
  }

  function withAlpha(color, alpha) {
    var c = colorToRgb(color);
    return 'rgba(' + c.r + ',' + c.g + ',' + c.b + ',' + clamp(alpha, 0, 255) / 255 + ')';
  }

  function clamp(value, lo, hi) {
    return Math.min(Math.max(value, lo), hi);
  }

  function isBlank(s) {
    return s == null || String(s).trim().length === 0;
  }

  function isNotBlank(s) {
    return !isBlank(s);
  }

  // android.graphics.RectF equivalent.
  function RectF(left, top, right, bottom) {
    return {
      left: left,
      top: top,
      right: right,
      bottom: bottom,
      width: function () { return this.right - this.left; },
      height: function () { return this.bottom - this.top; },
      centerX: function () { return (this.left + this.right) / 2; },
      centerY: function () { return (this.top + this.bottom) / 2; },
    };
  }

  function copyRect(r) {
    return RectF(r.left, r.top, r.right, r.bottom);
  }

  function imageWidth(image) {
    return (image && (image.naturalWidth || image.width)) || 0;
  }

  function imageHeight(image) {
    return (image && (image.naturalHeight || image.height)) || 0;
  }

  /* Hidden measuring context so Paint.measureText works without the render ctx
   * (mirrors android.graphics.Paint measuring independently of a Canvas). */
  var measureCtx = null;
  var activeTemplateFontFamily = 'sans-serif';
  function getMeasureCtx() {
    if (measureCtx === null) {
      var c = document.createElement('canvas');
      c.width = 1;
      c.height = 1;
      measureCtx = c.getContext('2d');
    }
    return measureCtx;
  }

  /* ---------------------------------------------------------------------- */
  /* Paint (CanvasRenderUtils.paint / medium / regular)                      */
  /* ---------------------------------------------------------------------- */

  function makePaint(color, size, weight) {
    return {
      color: color,
      size: Math.max(size, MIN_TEXT_SIZE_PX), // textSize.coerceAtLeast(MIN_TEXT_SIZE_PX)
      weight: weight,
      family: activeTemplateFontFamily,
      align: 'left', // Paint.Align.LEFT default; 'right' / 'center' mirror RIGHT / CENTER
      fontString: function () {
        return this.weight + ' ' + this.size + 'px "' + this.family + '", sans-serif';
      },
      measureText: function (text) {
        var ctx = getMeasureCtx();
        ctx.font = this.fontString();
        return ctx.measureText(text).width;
      },
      ascent: function () { // Android ascent is negative
        var ctx = getMeasureCtx();
        ctx.font = this.fontString();
        var m = ctx.measureText('Mg');
        return (m.fontBoundingBoxAscent !== undefined) ? -m.fontBoundingBoxAscent : -this.size * 0.8;
      },
      descent: function () { // Android descent is positive
        var ctx = getMeasureCtx();
        ctx.font = this.fontString();
        var m = ctx.measureText('Mg');
        return (m.fontBoundingBoxDescent !== undefined) ? m.fontBoundingBoxDescent : this.size * 0.2;
      },
    };
  }

  // CanvasRenderUtils.paint(color, size) — Typeface.SANS_SERIF NORMAL -> 400
  function paint(color, size) {
    return makePaint(color, size, 400);
  }

  // Typeface.create("sans-serif-medium") -> weight 500
  function medium(size, color) {
    return makePaint(color === undefined ? COLOR_BLACK : color, size, 500);
  }

  // Typeface.create("sans-serif") -> weight 400
  function regular(size, color) {
    return makePaint(color === undefined ? COLOR_BLACK : color, size, 400);
  }

  function templateFontFamily(settings) {
    switch ((settings && settings.templateFont) || 'System') {
      case 'IbmPlexSansKr': return 'Keyxif IBM Plex Sans KR';
      case 'NotoSansKr': return 'Keyxif Noto Sans KR';
      case 'NotoSerifKr': return 'Keyxif Noto Serif KR';
      case 'NanumGothic': return 'Keyxif Nanum Gothic';
      case 'GowunBatang': return 'Keyxif Gowun Batang';
      case 'BlackHanSans': return 'Keyxif Black Han Sans';
      case 'NanumPenScript': return 'Keyxif Nanum Pen Script';
      case 'Gugi': return 'Keyxif Gugi';
      default: return 'sans-serif';
    }
  }

  function applyTextPaint(ctx, p) {
    ctx.font = p.fontString();
    ctx.fillStyle = p.color;
    ctx.textAlign = p.align;
    ctx.textBaseline = 'alphabetic';
  }

  // canvas.drawText(text, x, baseline, paint)
  function drawText(ctx, text, x, baseline, p) {
    applyTextPaint(ctx, p);
    ctx.fillText(text, x, baseline);
  }

  /* ---------------------------------------------------------------------- */
  /* BuildInfoDisplay (domain/model/BuildInfoDisplay.kt)                     */
  /* ---------------------------------------------------------------------- */

  var blockedBuildTexts = {
    'untitled keyboard': true,
    'untitled': true,
    'keyboard build': true,
    'unknown': true,
    'null': true,
    'none': true,
    'n/a': true,
    'na': true,
    '미입력': true,
    '없음': true,
    '정보 없음': true,
    '빌드 정보 미입력': true,
  };

  function meaningfulBuildTextOrNull(text) {
    var value = text == null ? '' : String(text).trim();
    if (value.length === 0) return null;
    var normalized = value.toLowerCase().replace(/\s+/g, ' ');
    return blockedBuildTexts[normalized] === true ? null : value;
  }

  function isMeaningfulBuildText(text) {
    return meaningfulBuildTextOrNull(text) !== null;
  }

  function toDisplayRows(info, includeNickname) {
    if (includeNickname === undefined) includeNickname = false;
    var rows = [];
    var v;
    v = meaningfulBuildTextOrNull(info.housing);
    if (v !== null) rows.push({ label: 'BOARD', value: v });
    v = meaningfulBuildTextOrNull(info.switchName);
    if (v !== null) rows.push({ label: 'Switch', value: v });
    v = meaningfulBuildTextOrNull(info.plate);
    if (v !== null) rows.push({ label: 'Plate', value: v });
    v = meaningfulBuildTextOrNull(info.mount);
    if (v !== null) rows.push({ label: 'Mount', value: v });
    v = meaningfulBuildTextOrNull(info.keycap);
    if (v !== null) rows.push({ label: 'Keycap', value: v });
    if (includeNickname) {
      v = meaningfulBuildTextOrNull(info.nickname);
      if (v !== null) rows.push({ label: 'Nickname', value: v });
    }
    return rows;
  }

  function displayNicknameOrNull(info) {
    return meaningfulBuildTextOrNull(info.nickname);
  }

  function displayTitleOrNull(info) {
    var candidates = [info.nickname, info.housing];
    for (var i = 0; i < candidates.length; i++) {
      var v = meaningfulBuildTextOrNull(candidates[i]);
      if (v !== null) return v;
    }
    return null;
  }

  /* ---------------------------------------------------------------------- */
  /* TextDrawUtils (domain/renderer/TextDrawUtils.kt)                        */
  /* ---------------------------------------------------------------------- */

  function ellipsize(text, p, maxWidth) {
    var value = String(text == null ? '' : text).trim();
    if (value.length === 0 || maxWidth <= 0) return '';
    if (p.measureText(value) <= maxWidth) return value;
    var suffix = '...';
    if (p.measureText(suffix) > maxWidth) return '';
    var end = value.length;
    while (end > 1 && p.measureText(value.substring(0, end) + suffix) > maxWidth) {
      end--;
    }
    return value.substring(0, end).replace(/\s+$/, '') + suffix;
  }

  /* ---------------------------------------------------------------------- */
  /* CanvasRenderUtils (domain/renderer/CanvasRenderUtils.kt)                */
  /* ---------------------------------------------------------------------- */

  function drawTextBaseline(ctx, text, x, baseline, p, maxWidth) {
    var safeText = ellipsize(text, p, maxWidth);
    if (isNotBlank(safeText)) {
      drawText(ctx, safeText, x, baseline, p);
    }
  }

  function drawLiquidGlassText(ctx, text, x, baseline, p, maxWidth, contrastRgb, unit, isDetail) {
    var safeText = ellipsize(text, p, maxWidth);
    if (!isNotBlank(safeText)) return;
    var contrastPrefix = 'rgba(' + contrastRgb.r + ',' + contrastRgb.g + ',' + contrastRgb.b + ',';

    ctx.save();
    applyTextPaint(ctx, p);
    ctx.lineJoin = 'round';
    ctx.miterLimit = 2;
    ctx.lineWidth = Math.max(1, unit * (isDetail ? 0.0009 : 0.00075));
    ctx.strokeStyle = contrastPrefix + (isDetail ? '0.46)' : '0.38)');
    ctx.strokeText(safeText, x, baseline);

    ctx.shadowColor = contrastPrefix + (isDetail ? '0.62)' : '0.56)');
    ctx.shadowBlur = unit * (isDetail ? 0.0032 : 0.0042);
    ctx.shadowOffsetX = 0;
    ctx.shadowOffsetY = unit * (contrastRgb.r === 0 ? 0.0011 : -0.0007);
    ctx.fillText(safeText, x, baseline);
    ctx.restore();
  }

  function roundRectPath(ctx, left, top, right, bottom, radius) {
    var w = right - left;
    var h = bottom - top;
    var rad = Math.max(0, Math.min(radius, w / 2, h / 2));
    ctx.beginPath();
    if (typeof ctx.roundRect === 'function') {
      ctx.roundRect(left, top, w, h, rad);
      return;
    }
    ctx.moveTo(left + rad, top);
    ctx.arcTo(right, top, right, bottom, rad);
    ctx.arcTo(right, bottom, left, bottom, rad);
    ctx.arcTo(left, bottom, left, top, rad);
    ctx.arcTo(left, top, right, top, rad);
    ctx.closePath();
  }

  function drawRoundRect(ctx, rect, radius, color) {
    roundRectPath(ctx, rect.left, rect.top, rect.right, rect.bottom, radius);
    ctx.fillStyle = color;
    ctx.fill();
  }

  function fillRect(ctx, left, top, right, bottom, color) {
    ctx.fillStyle = color;
    ctx.fillRect(left, top, right - left, bottom - top);
  }

  function drawGradientScrim(ctx, rect, startColor, endColor, vertical) {
    if (vertical === undefined) vertical = true;
    var gradient = vertical
      ? ctx.createLinearGradient(0, rect.top, 0, rect.bottom)
      : ctx.createLinearGradient(rect.left, 0, rect.right, 0);
    gradient.addColorStop(0, startColor);
    gradient.addColorStop(1, endColor);
    ctx.fillStyle = gradient;
    ctx.fillRect(rect.left, rect.top, rect.width(), rect.height());
  }

  function buildParts(info, includeNickname) {
    if (includeNickname === undefined) includeNickname = true;
    return toDisplayRows(info, includeNickname);
  }

  function scaled(base, settings, extra) {
    if (extra === undefined) extra = 1;
    return Math.max(base * clamp(settings.textScale, 0.85, 1.35) * extra, MIN_TEXT_SIZE_PX);
  }

  function nicknameText(info, settings) {
    var nickname = displayNicknameOrNull(info);
    if (nickname === null) return '';
    switch (settings.nicknameStyle) {
      case 'AtPrefix':
        return nickname.indexOf('@') === 0 ? nickname : '@' + nickname;
      case 'Credit':
        return 'by ' + nickname;
      case 'Plain':
      default:
        return nickname;
    }
  }

  function drawLabelValue(ctx, label, value, x, baseline, maxWidth, labelPaint, valuePaint) {
    var displayValue = meaningfulBuildTextOrNull(value);
    if (displayValue === null) return;
    var labelText = String(label).toUpperCase();
    drawText(ctx, labelText, x, baseline, labelPaint);
    var valueX = x + Math.max(labelPaint.measureText(labelText) + 16, maxWidth * 0.24);
    drawTextBaseline(ctx, displayValue, valueX, baseline, valuePaint, maxWidth - (valueX - x));
  }

  function visiblePaletteColors(assets, settings) {
    if (settings.showPaletteColors) {
      return assets.paletteColors.slice(0, clamp(settings.paletteColorCount, 3, 5));
    }
    return [];
  }

  function cardBackgroundColor(fallback, paletteColors, settings, renderStyle) {
    var style = renderStyle || {};
    if (!settings.showPaletteColors || !style.usePaletteColorForCardBackground) return fallback;
    if (style.customCardBackgroundColor) return colorToCss(style.customCardBackgroundColor);
    var colors = (paletteColors || []).filter(function (c) { return c != null; });
    var index = clamp(Math.round(Number(style.paletteBackgroundColorIndex) || 0), 0, 4);
    return colors[index] || fallback;
  }

  // PaletteChipAlignment: 'Start' | 'Center' | 'End'
  function drawPaletteChips(ctx, colors, right, centerY, chipSize, gap, strokeColor) {
    var effectiveSize = chipSize * PALETTE_CHIP_SCALE;
    var effectiveGap = Math.max(gap, effectiveSize * 0.32);
    var area = RectF(0, centerY - effectiveSize / 2, right, centerY + effectiveSize / 2);
    return drawPaletteChipsInRect(ctx, colors, area, chipSize, effectiveGap, strokeColor, 'End', colors.length);
  }

  function drawPaletteChipsInRect(ctx, colors, area, chipSize, gap, strokeColor, alignment, maxChips) {
    if (alignment === undefined) alignment = 'End';
    if (maxChips === undefined) maxChips = colors.length;
    if (colors.length === 0 || chipSize <= 0 || area.width() <= 0 || area.height() <= 0) return null;
    var effectiveSize = chipSize * PALETTE_CHIP_SCALE;
    if (area.height() < effectiveSize) return null;

    var effectiveGap = Math.max(gap, effectiveSize * 0.32);
    var capacity = Math.min(
      Math.floor((area.width() + effectiveGap) / (effectiveSize + effectiveGap)),
      maxChips
    );
    if (capacity <= 0) return null;

    var displayColors = colors.slice(0, capacity);
    var totalWidth = effectiveSize * displayColors.length + effectiveGap * Math.max(displayColors.length - 1, 0);
    if (totalWidth > area.width()) return null;

    var left;
    switch (alignment) {
      case 'Start': left = area.left; break;
      case 'Center': left = area.left + (area.width() - totalWidth) / 2; break;
      case 'End':
      default: left = area.right - totalWidth; break;
    }
    var actualLeft = left;
    var top = area.centerY() - effectiveSize / 2;
    var radius = effectiveSize / 2;
    // 저해상도 미리보기에서는 칩이 아주 작아져 상한(11%)이 1px보다 작아질 수 있다.
    var maxStrokeWidth = Math.max(effectiveSize * 0.11, 1);
    var strokeWidth = clamp(effectiveSize * 0.055, 1, maxStrokeWidth);

    for (var i = 0; i < displayColors.length; i++) {
      var cx = left + radius;
      var cy = top + radius;
      ctx.beginPath();
      ctx.arc(cx, cy, radius, 0, Math.PI * 2);
      ctx.fillStyle = colorToCss(displayColors[i]);
      ctx.fill();
      ctx.beginPath();
      ctx.arc(cx, cy, radius, 0, Math.PI * 2);
      ctx.strokeStyle = strokeColor;
      ctx.lineWidth = strokeWidth;
      ctx.stroke();
      left += effectiveSize + effectiveGap;
    }
    return RectF(actualLeft, top, actualLeft + totalWidth, top + effectiveSize);
  }

  /* ---------------------------------------------------------------------- */
  /* LogoDrawUtils (domain/renderer/LogoDrawUtils.kt)                        */
  /* LogoAnchor: 'Start' | 'Center' | 'End'; LogoFitMode: 'Height'|'Inside'  */
  /* ---------------------------------------------------------------------- */

  function logoFitCenter(sourceWidth, sourceHeight, box) {
    if (sourceWidth <= 0 || sourceHeight <= 0) return copyRect(box);
    var scale = Math.min(box.width() / sourceWidth, box.height() / sourceHeight);
    var width = sourceWidth * scale;
    var height = sourceHeight * scale;
    return RectF(
      box.centerX() - width / 2,
      box.centerY() - height / 2,
      box.centerX() + width / 2,
      box.centerY() + height / 2
    );
  }

  function logoFitInside(sourceWidth, sourceHeight, box, anchor) {
    var target = logoFitCenter(sourceWidth, sourceHeight, box);
    var left;
    switch (anchor) {
      case 'Start': left = box.left; break;
      case 'End': left = box.right - target.width(); break;
      case 'Center':
      default: left = target.left; break;
    }
    return RectF(left, target.top, left + target.width(), target.bottom);
  }

  function logoFitHeight(sourceWidth, sourceHeight, box, anchor) {
    if (sourceWidth <= 0 || sourceHeight <= 0) return copyRect(box);
    var scale = box.height() / sourceHeight;
    var width = sourceWidth * scale;
    var height = sourceHeight * scale;
    var left;
    switch (anchor) {
      case 'Start': left = box.left; break;
      case 'Center': left = box.centerX() - width / 2; break;
      case 'End':
      default: left = box.right - width; break;
    }
    return RectF(
      left,
      box.centerY() - height / 2,
      left + width,
      box.centerY() + height / 2
    );
  }

  function logoDraw(ctx, box, assets, textColor, backgroundColor, anchor, fitMode) {
    if (anchor === undefined) anchor = 'Center';
    if (fitMode === undefined) fitMode = 'Height';
    if (box.width() <= 0 || box.height() <= 0) return copyRect(box);

    var bitmap = assets.logoBitmap;
    if (bitmap != null) {
      var inset = box.height() * 0.04;
      var contentBox = RectF(box.left + inset, box.top + inset, box.right - inset, box.bottom - inset);
      var target = (fitMode === 'Height')
        ? logoFitHeight(imageWidth(bitmap), imageHeight(bitmap), contentBox, anchor)
        : logoFitInside(imageWidth(bitmap), imageHeight(bitmap), contentBox, anchor);
      ctx.save();
      var radius = Math.min(target.width(), target.height()) * 0.12;
      roundRectPath(ctx, target.left, target.top, target.right, target.bottom, radius);
      ctx.clip();
      ctx.imageSmoothingEnabled = true;
      ctx.imageSmoothingQuality = 'high';
      if (assets.logoTintColor) {
        var tinted = document.createElement('canvas');
        tinted.width = imageWidth(bitmap);
        tinted.height = imageHeight(bitmap);
        var tintedContext = tinted.getContext('2d');
        tintedContext.drawImage(bitmap, 0, 0);
        tintedContext.globalCompositeOperation = 'source-in';
        tintedContext.fillStyle = assets.logoTintColor;
        tintedContext.fillRect(0, 0, tinted.width, tinted.height);
        ctx.drawImage(tinted, target.left, target.top, target.width(), target.height());
      } else {
        ctx.drawImage(bitmap, target.left, target.top, target.width(), target.height());
      }
      ctx.restore();
      return target;
    }

    if (backgroundColor !== COLOR_TRANSPARENT && backgroundColor != null) {
      drawRoundRect(ctx, box, Math.min(box.width(), box.height()) * 0.18, backgroundColor);
    }
    var label = String(assets.logoLabel || '').substring(0, 14);
    if (isBlank(label) || !assets.hasLogo) {
      return RectF(box.left, box.top, box.left, box.top);
    }
    var textPaint = medium(Math.min(box.height() * 0.28, box.width() * 0.16), textColor);
    textPaint.align = 'center';
    var baseline = box.centerY() - (textPaint.descent() + textPaint.ascent()) / 2;
    drawText(
      ctx,
      ellipsize(label.toUpperCase(), textPaint, box.width() * 0.78),
      box.centerX(),
      baseline,
      textPaint
    );
    return copyRect(box);
  }

  /* ---------------------------------------------------------------------- */
  /* TemplateRenderers.kt private helpers                                    */
  /* ---------------------------------------------------------------------- */

  function normalizedDisplayKey(text) {
    var v = meaningfulBuildTextOrNull(text);
    if (v === null) return '';
    return v.toLowerCase().replace(/\s+/g, ' ');
  }

  function detailText(values, separator) {
    if (separator === undefined) separator = '  /  ';
    var out = [];
    var seen = {};
    for (var i = 0; i < values.length; i++) {
      var m = meaningfulBuildTextOrNull(values[i]);
      if (m === null) continue;
      var key = normalizedDisplayKey(m);
      if (seen[key] === true) continue; // distinctBy(normalizedDisplayKey)
      seen[key] = true;
      out.push(m);
    }
    return out.join(separator);
  }

  function detailTextExcluding(title, values) {
    var titleKey = normalizedDisplayKey(title);
    var out = [];
    var seen = {};
    for (var i = 0; i < values.length; i++) {
      var m = meaningfulBuildTextOrNull(values[i]);
      if (m === null) continue;
      var key = normalizedDisplayKey(m);
      if (titleKey.length > 0 && key === titleKey) continue; // filterNot(title match)
      if (seen[key] === true) continue; // distinctBy(normalizedDisplayKey)
      seen[key] = true;
      out.push(m);
    }
    return out.join('  /  ');
  }

  function nicknameDetail(info, settings, title) {
    var rawNickname = displayNicknameOrNull(info);
    var rawKey = normalizedDisplayKey(rawNickname);
    if (rawKey.length > 0 && rawKey === normalizedDisplayKey(title)) {
      return null;
    }
    return meaningfulBuildTextOrNull(nicknameText(info, settings));
  }

  function rowsExcludingTitle(rows, title) {
    var titleKey = normalizedDisplayKey(title);
    if (titleKey.length === 0) return rows;
    var out = [];
    for (var i = 0; i < rows.length; i++) {
      if (normalizedDisplayKey(rows[i].value) !== titleKey) out.push(rows[i]);
    }
    return out;
  }

  function drawInfoRow(ctx, row, x, labelBaseline, maxWidth, labelPaint, valuePaint, valueOffset) {
    drawTextBaseline(ctx, row.label.toUpperCase(), x, labelBaseline, labelPaint, maxWidth);
    drawTextBaseline(ctx, row.value, x, labelBaseline + valueOffset, valuePaint, maxWidth);
  }

  function wrapTextAtWords(text, paint, maxWidth, maxLines) {
    var words = String(text || '').trim().split(/\s+/).filter(Boolean);
    if (!words.length) return [];
    var lines = [], current = '';
    words.forEach(function (word) {
      var candidate = current ? current + ' ' + word : word;
      if (!current || paint.measureText(candidate) <= maxWidth) current = candidate;
      else { lines.push(current); current = word; }
    });
    if (current) lines.push(current);
    if (lines.length <= maxLines) return lines.map(function (line) { return ellipsize(line, paint, maxWidth); });
    return lines.slice(0, maxLines).map(function (line, index) {
      return index === maxLines - 1
        ? ellipsize([line].concat(lines.slice(maxLines)).join(' '), paint, maxWidth)
        : line;
    });
  }

  function wrapTextAtSeparators(text, paint, maxWidth, maxLines) {
    var parts = String(text || '').split('/').map(function (part) { return part.trim(); }).filter(Boolean);
    if (parts.length <= 1) return wrapTextAtWords(text, paint, maxWidth, maxLines);
    var lines = [], current = '';
    parts.forEach(function (part) {
      var candidate = current ? current + '  /  ' + part : part;
      if (!current || paint.measureText(candidate) <= maxWidth) current = candidate;
      else { lines.push(current); current = part; }
    });
    if (current) lines.push(current);
    var expanded = [];
    lines.forEach(function (line) {
      if (paint.measureText(line) <= maxWidth) expanded.push(line);
      else expanded = expanded.concat(wrapTextAtWords(line, paint, maxWidth, maxLines));
    });
    if (expanded.length <= maxLines) return expanded;
    return expanded.slice(0, maxLines).map(function (line, index) {
      return index === maxLines - 1
        ? ellipsize([line].concat(expanded.slice(maxLines)).join(' / '), paint, maxWidth)
        : line;
    });
  }

  function drawAdaptiveTextByCharacter(ctx, text, x, baseline, paint, maxWidth, assets) {
    var safeText = ellipsize(text, paint, maxWidth);
    if (isBlank(safeText)) return;
    var originalAlign = paint.align;
    var width = paint.measureText(safeText);
    var startX = originalAlign === 'right' ? x - width : (originalAlign === 'center' ? x - width / 2 : x);
    paint.align = 'left';
    var cursor = startX;
    for (var i = 0; i < safeText.length; i++) {
      var ch = safeText.charAt(i);
      var advance = paint.measureText(ch);
      var sampleX = cursor + advance / 2;
      var sampleY = baseline - paint.size * 0.45;
      var color = contrastColorAt(ctx, assets, sampleX, sampleY);
      applyTextPaint(ctx, paint);
      ctx.fillStyle = color;
      ctx.shadowColor = color === COLOR_WHITE ? 'rgba(0,0,0,0.46)' : 'rgba(255,255,255,0.34)';
      ctx.shadowBlur = paint.size * 0.055;
      ctx.shadowOffsetY = paint.size * 0.025;
      ctx.fillText(ch, cursor, baseline);
      cursor += advance;
    }
    ctx.shadowColor = 'transparent';
    ctx.shadowBlur = 0;
    ctx.shadowOffsetY = 0;
    paint.align = originalAlign;
  }

  function drawLogoIfPresent(ctx, rect, assets, textColor, anchor, fitMode) {
    if (fitMode === undefined) fitMode = 'Height';
    if (!assets.hasLogo) return null;
    return logoDraw(ctx, rect, assets, textColor, COLOR_TRANSPARENT, anchor, fitMode);
  }

  /* ---------------------------------------------------------------------- */
  /* TemplateRenderer defaults (TemplateRenderer.kt)                         */
  /* ---------------------------------------------------------------------- */

  var DEFAULT_BACKGROUND = rgb(18, 18, 18);

  function makeRenderer(overrides) {
    var r = {
      backgroundColor: function () { return DEFAULT_BACKGROUND; },
      layoutSpec: function () {
        return {
          mode: 'OverlayOnPhoto',
          leftInsetFraction: 0,
          topInsetFraction: 0,
          rightInsetFraction: 0,
          bottomInsetFraction: 0,
        };
      },
      photoBounds: function (bounds) {
        var spec = this.layoutSpec();
        var left = Number(spec.leftInsetFraction) || 0;
        var top = Number(spec.topInsetFraction) || 0;
        var right = Number(spec.rightInsetFraction) || 0;
        var bottom = Number(spec.bottomInsetFraction) || 0;
        return RectF(
          bounds.left + bounds.width() * left,
          bounds.top + bounds.height() * top,
          bounds.right - bounds.width() * right,
          bounds.bottom - bounds.height() * bottom
        );
      },
      photoPlacement: function () { return 'FitCenter'; },
      logoBackgroundTone: function () { return 'Mixed'; },
      draw: function (ctx, bounds, info, assets, settings) {},
    };
    for (var k in overrides) {
      if (Object.prototype.hasOwnProperty.call(overrides, k)) r[k] = overrides[k];
    }
    return r;
  }

  /* ---------------------------------------------------------------------- */
  /* Template renderers (TemplateRenderers.kt)                               */
  /* ---------------------------------------------------------------------- */

  /* --- ClassicFrameRenderer --- */
  var CLASSIC_BAR_RATIO = 0.14;
  var ClassicFrameRenderer = makeRenderer({
    backgroundColor: function () { return rgb(247, 247, 243); },
    logoBackgroundTone: function () { return 'Light'; },
    layoutSpec: function () { return { mode: 'ExternalBottomCard', bottomInsetFraction: CLASSIC_BAR_RATIO }; },
    draw: function (ctx, bounds, info, assets, settings) {
      var w = bounds.width();
      var h = bounds.height();
      var bar = RectF(0, h * (1 - CLASSIC_BAR_RATIO), w, h);
      var pad = w * 0.028;
      var logoWidth = Math.min(w * 0.13, bar.height() * 1.12);
      var logoBox = RectF(pad, bar.top + bar.height() * 0.2, pad + logoWidth, bar.bottom - bar.height() * 0.2);
      var logoActual = drawLogoIfPresent(ctx, logoBox, assets, rgb(24, 25, 24), 'Start');

      var rows = toDisplayRows(info, true).slice(0, 6);
      var startX = (logoActual !== null ? logoActual.right : bar.left) + pad;
      var availableWidth = Math.max(bar.right - startX - pad, w * 0.35);
      var labelPaint = medium(scaled(h * 0.012, settings), assets.cardContentColor);
      var valuePaint = medium(scaled(h * 0.0165, settings), assets.cardContentColor);

      if (rows.length > 0) {
        var columns = Math.min(rows.length, 3);
        var rowCount = Math.max(Math.floor((rows.length + columns - 1) / columns), 1);
        var columnWidth = availableWidth / columns;
        var firstLabelY = bar.top + bar.height() * (rowCount === 1 ? 0.41 : 0.28);
        var rowStep = rowCount === 1 ? 0 : bar.height() * 0.39;
        for (var index = 0; index < rows.length; index++) {
          var column = index % columns;
          var rowIndex = Math.floor(index / columns);
          var x = startX + column * columnWidth;
          var labelY = firstLabelY + rowIndex * rowStep;
          drawInfoRow(ctx, rows[index], x, labelY, columnWidth * 0.88, labelPaint, valuePaint, bar.height() * 0.18);
        }
      }

      var paletteArea = RectF(
        startX,
        bar.bottom - bar.height() * 0.31,
        w - pad,
        bar.bottom - bar.height() * 0.08
      );
      drawPaletteChipsInRect(
        ctx,
        visiblePaletteColors(assets, settings),
        paletteArea,
        bar.height() * 0.095,
        bar.height() * 0.045,
        argb(72, 0, 0, 0),
        'End',
        rows.length > 3 ? 3 : settings.paletteColorCount
      );
    },
  });

  /* --- MinimalCaptionRenderer --- */
  var MINIMAL_CAPTION_RATIO = 0.12;
  var MinimalCaptionRenderer = makeRenderer({
    backgroundColor: function () { return rgb(252, 252, 249); },
    logoBackgroundTone: function () { return 'Light'; },
    layoutSpec: function () { return { mode: 'ExternalBottomCard', bottomInsetFraction: MINIMAL_CAPTION_RATIO }; },
    draw: function (ctx, bounds, info, assets, settings) {
      var w = bounds.width();
      var h = bounds.height();
      var captionTop = h * (1 - MINIMAL_CAPTION_RATIO);
      var captionHeight = h * MINIMAL_CAPTION_RATIO;
      var pad = w * 0.045;
      var titlePaint = medium(scaled(h * 0.024, settings), assets.cardContentColor);
      var bodyPaint = regular(scaled(h * 0.0155, settings), assets.cardContentColor);
      var logoWidth = Math.min(w * 0.12, captionHeight * 1.05);
      var logoBox = RectF(w - pad - logoWidth, captionTop + captionHeight * 0.24, w - pad, captionTop + captionHeight * 0.76);
      var logoActual = drawLogoIfPresent(ctx, logoBox, assets, rgb(28, 29, 28), 'End');
      var textRight = logoActual !== null ? logoActual.left - pad : (w - pad);
      var title = displayTitleOrNull(info);
      var detail = detailTextExcluding(title, [
        info.housing,
        info.switchName,
        info.keycap,
        nicknameDetail(info, settings, title),
      ]);

      if (title !== null) {
        drawTextBaseline(ctx, title, pad, captionTop + captionHeight * 0.43, titlePaint, textRight - pad);
      }
      if (isNotBlank(detail)) {
        var y = captionTop + captionHeight * (title === null ? 0.58 : 0.67);
        drawTextBaseline(ctx, detail, pad, y, bodyPaint, textRight - pad);
      }
      drawPaletteChipsInRect(
        ctx,
        visiblePaletteColors(assets, settings),
        RectF(pad, captionTop + captionHeight * 0.76, textRight, captionTop + captionHeight * 0.98),
        captionHeight * 0.105,
        captionHeight * 0.048,
        argb(72, 0, 0, 0),
        'Start'
      );
    },
  });

  /* --- BottomSpecBarRenderer --- */
  var BOTTOM_BAR_RATIO = 0.10;
  var BottomSpecBarRenderer = makeRenderer({
    backgroundColor: function () { return rgb(35, 38, 38); },
    logoBackgroundTone: function () { return 'Dark'; },
    layoutSpec: function () { return { mode: 'ExternalBottomCard', bottomInsetFraction: BOTTOM_BAR_RATIO }; },
    draw: function (ctx, bounds, info, assets, settings) {
      var w = bounds.width();
      var h = bounds.height();
      var top = h * (1 - BOTTOM_BAR_RATIO);
      fillRect(ctx, 0, top, w, h, assets.cardBackgroundColor);
      var pad = w * 0.035;
      var primaryRows = toDisplayRows(info, false).filter(function (row) {
        return row.label === 'BOARD' || row.label === 'Switch' || row.label === 'Keycap';
      }).slice(0, 3);
      var labelPaint = regular(scaled(h * 0.0105, settings), assets.cardContentColor);
      var valuePaint = medium(scaled(h * 0.0165, settings), assets.cardContentColor);
      var detailPaint = regular(scaled(h * 0.0138, settings, settings.nicknameEmphasis), assets.cardContentColor);
      detailPaint.align = 'right';

      if (primaryRows.length > 0) {
        var columnWidth = (w - pad * 2) / primaryRows.length;
        for (var index = 0; index < primaryRows.length; index++) {
          var x = pad + columnWidth * index;
          drawInfoRow(ctx, primaryRows[index], x, top + h * BOTTOM_BAR_RATIO * 0.36, columnWidth * 0.86, labelPaint, valuePaint, h * BOTTOM_BAR_RATIO * 0.29);
        }
      }
      var detail = detailText(
        [info.plate, info.mount, nicknameText(info, settings)],
        ' / '
      );
      if (isNotBlank(detail)) {
        drawText(ctx, ellipsize(detail, detailPaint, w * 0.56), w - pad, top + h * BOTTOM_BAR_RATIO * 0.9, detailPaint);
      }
      drawPaletteChipsInRect(
        ctx,
        visiblePaletteColors(assets, settings),
        RectF(w * 0.68, top + h * BOTTOM_BAR_RATIO * 0.08, w - pad, top + h * BOTTOM_BAR_RATIO * 0.32),
        h * BOTTOM_BAR_RATIO * 0.12,
        h * BOTTOM_BAR_RATIO * 0.055,
        argb(88, 255, 255, 255),
        'End',
        primaryRows.length >= 3 ? 3 : settings.paletteColorCount
      );
    },
  });

  /* --- CornerMarkRenderer --- */
  var CornerMarkRenderer = makeRenderer({
    logoBackgroundTone: function () { return 'Dark'; },
    draw: function (ctx, bounds, info, assets, settings) {
      var title = displayTitleOrNull(info);
      var subtitle = detailTextExcluding(title, [info.housing, info.switchName, info.keycap]);
      if (title === null && isBlank(subtitle) && !assets.hasLogo) return;

      var w = bounds.width();
      var h = bounds.height();
      var pad = Math.min(w, h) * 0.025;
      var titlePaint = medium(scaled(h * 0.017, settings), assets.cardContentColor);
      var subPaint = regular(scaled(h * 0.011, settings, settings.nicknameEmphasis), assets.cardContentColor);
      var cardScale = 1.15;
      var maxCardWidth = w * 0.48;
      var minCardWidth = (title === null && isBlank(subtitle)) ? w * 0.092 : w * 0.195;
      var minCardHeight = h * 0.060;
      var maxCardHeight = h * 0.132;
      var horizontalPadding = Math.min(w, h) * 0.014 * cardScale;
      var verticalPadding = Math.min(w, h) * 0.010 * cardScale;
      var gap = Math.min(w, h) * 0.012;
      var maxLogoWidth = maxCardWidth * 0.34;
      var maxLogoHeight = h * 0.046;
      var logoTarget = null;
      if (assets.logoBitmap != null && assets.hasLogo) {
        logoTarget = logoFitInside(
          imageWidth(assets.logoBitmap),
          imageHeight(assets.logoBitmap),
          RectF(0, 0, maxLogoWidth, maxLogoHeight),
          'Center'
        );
      }
      var logoWidth = logoTarget !== null ? logoTarget.width() : (assets.hasLogo ? maxLogoHeight : 0);
      var logoHeight = logoTarget !== null ? logoTarget.height() : (assets.hasLogo ? maxLogoHeight : 0);

      var titleWidth = title !== null ? titlePaint.measureText(title) : 0;
      var subtitleWidth = isBlank(subtitle) ? 0 : subPaint.measureText(subtitle);
      var naturalTextWidth = Math.max(titleWidth, subtitleWidth);
      var maxTextWidth = Math.max(
        !assets.hasLogo
          ? maxCardWidth - horizontalPadding * 2
          : maxCardWidth - horizontalPadding * 2 - logoWidth - gap,
        w * 0.08
      );
      var textWidth = Math.min(naturalTextWidth, maxTextWidth);
      var textBlockHeight;
      if (title === null && isBlank(subtitle)) {
        textBlockHeight = 0;
      } else {
        textBlockHeight = (title !== null ? titlePaint.size : 0) + (isNotBlank(subtitle) ? subPaint.size * 1.25 : 0);
      }
      var contentWidth = horizontalPadding * 2 + textWidth + (assets.hasLogo && textWidth > 0 ? logoWidth + gap : logoWidth);
      var contentHeight = verticalPadding * 2 + Math.max(logoHeight, textBlockHeight);
      var markWidth = clamp(contentWidth, minCardWidth, maxCardWidth);
      var markHeight = clamp(contentHeight, minCardHeight, maxCardHeight);
      var mark = RectF(w - pad - markWidth, pad, w - pad, pad + markHeight);

      drawRoundRect(ctx, mark, markHeight * 0.18, argb(150, 12, 13, 13));

      var textX = mark.left + horizontalPadding;
      if (assets.hasLogo) {
        var logoBox = RectF(
          mark.left + horizontalPadding,
          mark.centerY() - maxLogoHeight / 2,
          mark.left + horizontalPadding + maxLogoWidth,
          mark.centerY() + maxLogoHeight / 2
        );
        var logoActual = drawLogoIfPresent(ctx, logoBox, assets, COLOR_WHITE, 'Start', 'Inside');
        textX = (logoActual !== null ? logoActual.right : logoBox.left) + (textWidth > 0 ? gap : 0);
      }

      if (textWidth <= 0) return;
      var availableTextWidth = mark.right - textX - horizontalPadding;
      if (isBlank(subtitle)) {
        var baseline = mark.centerY() - (titlePaint.descent() + titlePaint.ascent()) / 2;
        if (title !== null) {
          drawTextBaseline(ctx, title, textX, baseline, titlePaint, availableTextWidth);
        }
      } else {
        var firstBaseline = mark.centerY() - (titlePaint.size + subPaint.size * 0.35) / 2 + titlePaint.size * 0.86;
        if (title !== null) {
          drawTextBaseline(ctx, title, textX, firstBaseline, titlePaint, availableTextWidth);
        }
        drawTextBaseline(ctx, subtitle, textX, firstBaseline + subPaint.size * 1.2, subPaint, availableTextWidth);
      }
    },
  });

  /* --- PosterMarginRenderer --- */
  var PosterMarginRenderer = makeRenderer({
    backgroundColor: function () { return rgb(248, 248, 245); },
    logoBackgroundTone: function () { return 'Light'; },
    layoutSpec: function () {
      return {
        mode: 'ExternalFrame',
        leftInsetFraction: 0.035,
        topInsetFraction: 0.035,
        rightInsetFraction: 0.035,
        bottomInsetFraction: 0.17,
      };
    },
    draw: function (ctx, bounds, info, assets, settings) {
      var w = bounds.width();
      var h = bounds.height();
      var footerTop = h * 0.845;
      var pad = w * 0.055;
      var logoWidth = Math.min(w * 0.14, h * 0.105);
      var logoBox = RectF(w - pad - logoWidth, footerTop + h * 0.025, w - pad, footerTop + h * 0.09);
      var titlePaint = medium(scaled(h * 0.028, settings), assets.cardContentColor);
      var specPaint = regular(scaled(h * 0.015, settings), assets.cardContentColor);
      var signaturePaint = medium(scaled(h * 0.014, settings, settings.nicknameEmphasis), assets.cardContentColor);
      signaturePaint.align = 'right';
      var logoActual = drawLogoIfPresent(ctx, logoBox, assets, rgb(28, 28, 27), 'End');
      var textRight = logoActual !== null ? logoActual.left - pad * 1.5 : (w - pad);
      var title = displayTitleOrNull(info);
      var details = detailTextExcluding(title, [info.housing, info.switchName, info.keycap]);
      var signature = nicknameDetail(info, settings, title);

      if (title !== null) {
        drawTextBaseline(ctx, title, pad, footerTop + h * 0.052, titlePaint, textRight - pad);
      }
      if (isNotBlank(details)) {
        var y = footerTop + h * (title === null ? 0.062 : 0.086);
        drawTextBaseline(ctx, details, pad, y, specPaint, textRight - pad);
      }
      if (signature !== null && isNotBlank(signature)) {
        drawText(ctx, ellipsize(signature, signaturePaint, w * 0.38), w - pad, h - h * 0.025, signaturePaint);
      }
      drawPaletteChipsInRect(
        ctx,
        visiblePaletteColors(assets, settings),
        RectF(pad, h - h * 0.062, textRight, h - h * 0.028),
        h * 0.014,
        h * 0.006,
        argb(70, 0, 0, 0),
        'Start'
      );
    },
  });

  function addRoundedRectPath(ctx, rect, radius) {
    var r = Math.min(radius, rect.width() / 2, rect.height() / 2);
    ctx.moveTo(rect.left + r, rect.top);
    ctx.lineTo(rect.right - r, rect.top);
    ctx.quadraticCurveTo(rect.right, rect.top, rect.right, rect.top + r);
    ctx.lineTo(rect.right, rect.bottom - r);
    ctx.quadraticCurveTo(rect.right, rect.bottom, rect.right - r, rect.bottom);
    ctx.lineTo(rect.left + r, rect.bottom);
    ctx.quadraticCurveTo(rect.left, rect.bottom, rect.left, rect.bottom - r);
    ctx.lineTo(rect.left, rect.top + r);
    ctx.quadraticCurveTo(rect.left, rect.top, rect.left + r, rect.top);
    ctx.closePath();
  }

  var liquidGlassBackdropCache = typeof WeakMap === 'function' ? new WeakMap() : null;
  var liquidGlassStableCache = typeof Map === 'function' ? new Map() : null;
  var liquidGlassCacheOrder = [];
  var liquidGlassCachePixels = 0;
  var MAX_LIQUID_GLASS_CACHE_PIXELS = 8000000;
  var MAX_LIQUID_GLASS_CACHE_ENTRIES = 8;

  function releaseLiquidGlassCanvas(canvas) {
    if (canvas && canvas.__keyxifWebGl) {
      var loseContext = canvas.__keyxifWebGl.getExtension('WEBGL_lose_context');
      if (loseContext) loseContext.loseContext();
      canvas.__keyxifWebGl = null;
    }
  }

  function liquidGlassStableKey(sourceCacheKey, cacheKey) {
    return sourceCacheKey ? sourceCacheKey + '|' + cacheKey : null;
  }

  function getCachedLiquidGlassCanvas(image, sourceCacheKey, cacheKey) {
    var stableKey = liquidGlassStableKey(sourceCacheKey, cacheKey);
    var canvas = stableKey && liquidGlassStableCache ? liquidGlassStableCache.get(stableKey) : null;
    if (!canvas && liquidGlassBackdropCache) {
      var imageCache = liquidGlassBackdropCache.get(image);
      canvas = imageCache ? imageCache[cacheKey] : null;
    }
    if (!canvas) return null;
    liquidGlassCacheOrder = liquidGlassCacheOrder.filter(function (entry) {
      return entry.canvas !== canvas;
    });
    liquidGlassCacheOrder.push({
      image: stableKey ? null : image,
      stableKey: stableKey,
      cacheKey: cacheKey,
      canvas: canvas,
      pixels: canvas.width * canvas.height,
    });
    return canvas;
  }

  function cacheLiquidGlassCanvas(image, sourceCacheKey, cacheKey, canvas) {
    if (!liquidGlassBackdropCache && !liquidGlassStableCache) return;
    var stableKey = liquidGlassStableKey(sourceCacheKey, cacheKey);
    var existing = stableKey && liquidGlassStableCache ? liquidGlassStableCache.get(stableKey) : null;
    if (!stableKey && liquidGlassBackdropCache) {
      var imageCache = liquidGlassBackdropCache.get(image);
      if (!imageCache) {
        imageCache = Object.create(null);
        liquidGlassBackdropCache.set(image, imageCache);
      }
      existing = imageCache[cacheKey] || null;
      imageCache[cacheKey] = canvas;
    } else if (stableKey && liquidGlassStableCache) {
      liquidGlassStableCache.set(stableKey, canvas);
    }
    liquidGlassCacheOrder = liquidGlassCacheOrder.filter(function (entry) {
      if (entry.canvas !== existing && entry.canvas !== canvas) return true;
      liquidGlassCachePixels -= entry.pixels;
      return false;
    });
    var pixels = canvas.width * canvas.height;
    liquidGlassCachePixels += pixels;
    liquidGlassCacheOrder.push({ image: stableKey ? null : image, stableKey: stableKey, cacheKey: cacheKey, canvas: canvas, pixels: pixels });
    while (
      liquidGlassCacheOrder.length > MAX_LIQUID_GLASS_CACHE_ENTRIES ||
      liquidGlassCachePixels > MAX_LIQUID_GLASS_CACHE_PIXELS
    ) {
      var expired = liquidGlassCacheOrder.shift();
      liquidGlassCachePixels -= expired.pixels;
      if (expired.stableKey && liquidGlassStableCache) {
        if (liquidGlassStableCache.get(expired.stableKey) === expired.canvas) {
          liquidGlassStableCache.delete(expired.stableKey);
        }
      } else if (liquidGlassBackdropCache && expired.image) {
        var expiredCache = liquidGlassBackdropCache.get(expired.image);
        if (expiredCache && expiredCache[expired.cacheKey] === expired.canvas) delete expiredCache[expired.cacheKey];
      }
      releaseLiquidGlassCanvas(expired.canvas);
    }
  }

  function createMultiPassBlurCanvas(image, width, height, unit) {
    var current = document.createElement('canvas');
    current.width = width;
    current.height = height;
    var currentCtx = current.getContext('2d');
    currentCtx.imageSmoothingEnabled = true;
    currentCtx.imageSmoothingQuality = 'high';
    currentCtx.drawImage(image, 0, 0, width, height);

    var radii = [unit * 0.0035, unit * 0.006, unit * 0.009];
    radii.forEach(function (radius) {
      var previous = current;
      var next = document.createElement('canvas');
      next.width = width;
      next.height = height;
      var nextCtx = next.getContext('2d');
      nextCtx.imageSmoothingEnabled = true;
      nextCtx.imageSmoothingQuality = 'high';
      radius = Math.max(2, radius);
      if (typeof nextCtx.filter === 'string') {
        var padding = Math.ceil(radius * 2.5);
        var padded = document.createElement('canvas');
        padded.width = width + padding * 2;
        padded.height = height + padding * 2;
        var paddedCtx = padded.getContext('2d');
        paddedCtx.drawImage(current, padding, padding);
        paddedCtx.drawImage(current, 0, 0, 1, height, 0, padding, padding, height);
        paddedCtx.drawImage(current, width - 1, 0, 1, height, padding + width, padding, padding, height);
        paddedCtx.drawImage(current, 0, 0, width, 1, padding, 0, width, padding);
        paddedCtx.drawImage(current, 0, height - 1, width, 1, padding, padding + height, width, padding);
        paddedCtx.drawImage(current, 0, 0, 1, 1, 0, 0, padding, padding);
        paddedCtx.drawImage(current, width - 1, 0, 1, 1, padding + width, 0, padding, padding);
        paddedCtx.drawImage(current, 0, height - 1, 1, 1, 0, padding + height, padding, padding);
        paddedCtx.drawImage(current, width - 1, height - 1, 1, 1, padding + width, padding + height, padding, padding);
        nextCtx.filter = 'blur(' + radius.toFixed(2) + 'px)';
        nextCtx.drawImage(padded, -padding, -padding);
        nextCtx.filter = 'none';
        padded.width = 0;
        padded.height = 0;
      } else {
        var taps = [
          [-1, -1, 0.075], [0, -1, 0.125], [1, -1, 0.075],
          [-1, 0, 0.125], [0, 0, 0.20], [1, 0, 0.125],
          [-1, 1, 0.075], [0, 1, 0.125], [1, 1, 0.075],
        ];
        taps.forEach(function (tap) {
          nextCtx.globalAlpha = tap[2];
          nextCtx.drawImage(current, tap[0] * radius, tap[1] * radius, width, height);
        });
        nextCtx.globalAlpha = 1;
      }
      current = next;
      currentCtx = nextCtx;
      previous.width = 0;
      previous.height = 0;
    });
    return current;
  }

  function renderLiquidGlassWebGl(image, width, height, inner, innerRadius) {
    var scale = Math.min(1, 1600 / Math.max(width, height));
    var outputWidth = Math.max(1, Math.round(width * scale));
    var outputHeight = Math.max(1, Math.round(height * scale));
    var canvas = document.createElement('canvas');
    canvas.width = outputWidth;
    canvas.height = outputHeight;
    var gl = canvas.getContext('webgl', {
      alpha: false,
      antialias: false,
      depth: false,
      stencil: false,
      premultipliedAlpha: true,
      preserveDrawingBuffer: true,
      powerPreference: 'high-performance',
    });
    if (!gl) return null;
    canvas.__keyxifWebGl = gl;

    var vertexSource = [
      'attribute vec2 position;',
      'attribute vec2 texCoord;',
      'varying vec2 uv;',
      'void main() {',
      '  uv = texCoord;',
      '  gl_Position = vec4(position, 0.0, 1.0);',
      '}',
    ].join('\n');
    var fragmentSource = [
      'precision highp float;',
      'varying vec2 uv;',
      'uniform sampler2D image;',
      'uniform sampler2D blurredImage;',
      'uniform vec2 resolution;',
      'uniform vec2 innerCenter;',
      'uniform vec2 innerHalfSize;',
      'uniform float innerRadius;',
      'uniform float bevelWidth;',
      'uniform float refractionHeight;',
      'uniform float dispersion;',
      'float roundedRectSdf(vec2 point, vec2 halfSize, float radius) {',
      '  vec2 q = abs(point) - halfSize + radius;',
      '  return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius;',
      '}',
      'vec2 safeUv(vec2 value) {',
      '  vec2 inset = 0.5 / resolution;',
      '  return clamp(value, inset, vec2(1.0) - inset);',
      '}',
      'vec3 sampleImage(vec2 value) { return texture2D(image, safeUv(value)).rgb; }',
      'void main() {',
      '  vec2 fragCoord = uv * resolution;',
      '  vec2 point = fragCoord - innerCenter;',
      '  float distance = roundedRectSdf(point, innerHalfSize, innerRadius);',
      '  float edge = 1.0 - smoothstep(0.0, bevelWidth, max(distance, 0.0));',
      '  float edgePosition = clamp(max(distance, 0.0) / max(bevelWidth, 0.0001), 0.0, 1.0);',
      '  float epsilon = 1.25;',
      '  vec2 gradient = vec2(',
      '    roundedRectSdf(point + vec2(epsilon, 0.0), innerHalfSize, innerRadius) - roundedRectSdf(point - vec2(epsilon, 0.0), innerHalfSize, innerRadius),',
      '    roundedRectSdf(point + vec2(0.0, epsilon), innerHalfSize, innerRadius) - roundedRectSdf(point - vec2(0.0, epsilon), innerHalfSize, innerRadius)',
      '  );',
      '  vec2 normal = gradient / max(length(gradient), 0.0001);',
      '  float curvedEdge = edge * edge * (3.0 - 2.0 * edge);',
      '  float lensProfile = pow(curvedEdge, 1.35);',
      '  float displacement = refractionHeight * lensProfile;',
      '  vec2 refractedUv = safeUv(uv - normal * displacement / resolution);',
      '  vec3 soft = texture2D(blurredImage, refractedUv).rgb;',
      '  vec3 sharp = sampleImage(refractedUv);',
      '  float localDetail = length(sharp - soft);',
      '  float flatBoost = 1.0 - smoothstep(0.025, 0.16, localDetail);',
      '  float blurAmount = 0.50 - edge * 0.08;',
      '  vec3 color = mix(sharp, soft, blurAmount);',
      '  vec2 spectralOffset = normal * dispersion * (0.35 + edge * 0.65) / resolution;',
      '  color.r = mix(color.r, sampleImage(refractedUv - spectralOffset).r, edge * 0.42);',
      '  color.b = mix(color.b, sampleImage(refractedUv + spectralOffset).b, edge * 0.42);',
      '  float luminance = dot(color, vec3(0.2126, 0.7152, 0.0722));',
      '  color += vec3((0.5 - luminance) * 0.055);',
      '  color = mix(vec3(luminance), color, 1.08);',
      '  vec2 lightDirection = normalize(vec2(-0.58, -0.82));',
      '  float facingLight = max(dot(-normal, lightDirection), 0.0);',
      '  float facingShadow = max(dot(normal, lightDirection), 0.0);',
      '  float shadowBand = 1.0 - smoothstep(0.22, 0.92, edgePosition);',
      '  float refractionCaustic = smoothstep(0.03, 0.20, edgePosition) * (1.0 - smoothstep(0.28, 0.64, edgePosition));',
      '  float highlightWidth = clamp(1.0 / max(bevelWidth, 1.0), 0.012, 0.10);',
      '  float highlightDistance = (edgePosition - 0.145) / highlightWidth;',
      '  float highlightCaustic = exp(-highlightDistance * highlightDistance);',
      '  float causticStrength = refractionCaustic * (0.010 + pow(facingLight, 1.75) * 0.070) * (0.72 + flatBoost * 0.28);',
      '  float specular = pow(facingLight, 1.45) * highlightCaustic * (0.152 + flatBoost * 0.138);',
      '  float contactShade = pow(facingShadow, 1.20) * shadowBand * (0.060 + flatBoost * 0.055);',
      '  vec3 causticTint = mix(vec3(1.0), clamp(soft * 1.22, 0.0, 1.0), 0.16);',
      '  color = mix(color, causticTint, causticStrength);',
      '  color += vec3(specular);',
      '  color *= 1.0 - contactShade;',
      '  gl_FragColor = vec4(clamp(color, 0.0, 1.0), 1.0);',
      '}',
    ].join('\n');

    function compile(type, source) {
      var shader = gl.createShader(type);
      gl.shaderSource(shader, source);
      gl.compileShader(shader);
      if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
        gl.deleteShader(shader);
        return null;
      }
      return shader;
    }

    var vertex = compile(gl.VERTEX_SHADER, vertexSource);
    var fragment = compile(gl.FRAGMENT_SHADER, fragmentSource);
    if (!vertex || !fragment) return null;
    var program = gl.createProgram();
    gl.attachShader(program, vertex);
    gl.attachShader(program, fragment);
    gl.linkProgram(program);
    gl.deleteShader(vertex);
    gl.deleteShader(fragment);
    if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
      gl.deleteProgram(program);
      return null;
    }

    var vertices = new Float32Array([
      -1, -1, 0, 1,
       1, -1, 1, 1,
      -1,  1, 0, 0,
      -1,  1, 0, 0,
       1, -1, 1, 1,
       1,  1, 1, 0,
    ]);
    var buffer = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
    gl.bufferData(gl.ARRAY_BUFFER, vertices, gl.STATIC_DRAW);
    var stride = 4 * Float32Array.BYTES_PER_ELEMENT;
    var position = gl.getAttribLocation(program, 'position');
    var texCoord = gl.getAttribLocation(program, 'texCoord');
    gl.enableVertexAttribArray(position);
    gl.vertexAttribPointer(position, 2, gl.FLOAT, false, stride, 0);
    gl.enableVertexAttribArray(texCoord);
    gl.vertexAttribPointer(texCoord, 2, gl.FLOAT, false, stride, 2 * Float32Array.BYTES_PER_ELEMENT);

    var texture = gl.createTexture();
    gl.activeTexture(gl.TEXTURE0);
    gl.bindTexture(gl.TEXTURE_2D, texture);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
    gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, false);
    try {
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, image);
    } catch (e) {
      gl.deleteTexture(texture);
      gl.deleteBuffer(buffer);
      gl.deleteProgram(program);
      return null;
    }

    var blurredCanvas = createMultiPassBlurCanvas(image, outputWidth, outputHeight, Math.min(outputWidth, outputHeight));
    var blurTexture = gl.createTexture();
    gl.activeTexture(gl.TEXTURE1);
    gl.bindTexture(gl.TEXTURE_2D, blurTexture);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
    try {
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, blurredCanvas);
    } catch (e) {
      gl.deleteTexture(blurTexture);
      gl.deleteTexture(texture);
      gl.deleteBuffer(buffer);
      gl.deleteProgram(program);
      return null;
    }

    gl.useProgram(program);
    gl.uniform1i(gl.getUniformLocation(program, 'image'), 0);
    gl.uniform1i(gl.getUniformLocation(program, 'blurredImage'), 1);
    gl.uniform2f(gl.getUniformLocation(program, 'resolution'), outputWidth, outputHeight);
    gl.uniform2f(gl.getUniformLocation(program, 'innerCenter'), inner.centerX() * scale, inner.centerY() * scale);
    gl.uniform2f(gl.getUniformLocation(program, 'innerHalfSize'), inner.width() * 0.5 * scale, inner.height() * 0.5 * scale);
    gl.uniform1f(gl.getUniformLocation(program, 'innerRadius'), innerRadius * scale);
    gl.uniform1f(gl.getUniformLocation(program, 'bevelWidth'), Math.min(outputWidth, outputHeight) * 0.026);
    gl.uniform1f(gl.getUniformLocation(program, 'refractionHeight'), Math.min(outputWidth, outputHeight) * 0.072);
    gl.uniform1f(gl.getUniformLocation(program, 'dispersion'), Math.max(1.2, Math.min(3.6, Math.min(outputWidth, outputHeight) * 0.0018)));
    gl.viewport(0, 0, outputWidth, outputHeight);
    gl.drawArrays(gl.TRIANGLES, 0, 6);
    gl.finish();
    gl.deleteTexture(blurTexture);
    gl.deleteTexture(texture);
    gl.deleteBuffer(buffer);
    gl.deleteProgram(program);
    return canvas;
  }

  function drawLiquidGlassBackdrop(ctx, image, sourceCacheKey, width, height, unit, inner, innerRadius) {
    if (!image) return;
    var cacheKey = [
      Math.round(width), Math.round(height),
      Math.round(inner.left), Math.round(inner.top), Math.round(inner.right), Math.round(inner.bottom),
      Math.round(innerRadius),
    ].join(':');
    var cachedCanvas = getCachedLiquidGlassCanvas(image, sourceCacheKey, cacheKey);
    if (cachedCanvas) {
      ctx.drawImage(cachedCanvas, 0, 0, width, height);
      return;
    }
    var gpuCanvas = renderLiquidGlassWebGl(image, width, height, inner, innerRadius);
    if (gpuCanvas) {
      cacheLiquidGlassCanvas(image, sourceCacheKey, cacheKey, gpuCanvas);
      ctx.save();
      ctx.globalAlpha = 0.91;
      ctx.imageSmoothingEnabled = true;
      ctx.imageSmoothingQuality = 'high';
      ctx.drawImage(gpuCanvas, 0, 0, width, height);
      ctx.restore();
      return;
    }
    var scale = Math.min(1, 1600 / Math.max(width, height));
    var sampleWidth = Math.max(1, Math.round(width * scale));
    var sampleHeight = Math.max(1, Math.round(height * scale));
    var sourceCanvas = document.createElement('canvas');
    sourceCanvas.width = sampleWidth;
    sourceCanvas.height = sampleHeight;
    var sourceCtx = sourceCanvas.getContext('2d');
    sourceCtx.imageSmoothingEnabled = true;
    sourceCtx.imageSmoothingQuality = 'high';
    sourceCtx.drawImage(image, 0, 0, sampleWidth, sampleHeight);

    var blurCanvas = createMultiPassBlurCanvas(sourceCanvas, sampleWidth, sampleHeight, unit * scale);

    var glassCanvas = document.createElement('canvas');
    glassCanvas.width = sampleWidth;
    glassCanvas.height = sampleHeight;
    var glassCtx = glassCanvas.getContext('2d');
    glassCtx.imageSmoothingEnabled = true;
    glassCtx.imageSmoothingQuality = 'high';
    glassCtx.globalAlpha = 0.50;
    glassCtx.drawImage(blurCanvas, 0, 0);
    glassCtx.globalAlpha = 0.50;
    glassCtx.drawImage(sourceCanvas, 0, 0);
    glassCtx.globalAlpha = 1;

    try {
      var sourceData = sourceCtx.getImageData(0, 0, sampleWidth, sampleHeight);
      var glassBaseData = glassCtx.getImageData(0, 0, sampleWidth, sampleHeight);
      var edgeData = glassCtx.createImageData(sampleWidth, sampleHeight);
      var sourcePixels = sourceData.data;
      var glassBasePixels = glassBaseData.data;
      var edgePixels = edgeData.data;
      var left = inner.left * scale;
      var top = inner.top * scale;
      var right = inner.right * scale;
      var bottom = inner.bottom * scale;
      var radius = innerRadius * scale;
      var centerX = (left + right) * 0.5;
      var centerY = (top + bottom) * 0.5;
      var halfX = Math.max(0, (right - left) * 0.5 - radius);
      var halfY = Math.max(0, (bottom - top) * 0.5 - radius);
      var edgeBand = Math.max(5, Math.min(sampleWidth, sampleHeight) * 0.026);
      var maxShift = Math.max(4, Math.min(sampleWidth, sampleHeight) * 0.024);
      var chromaShift = Math.max(0.45, Math.min(1.2, Math.min(sampleWidth, sampleHeight) * 0.0012));

      function sampleChannelBilinear(sampleX, sampleY, channel) {
        var safeX = Math.max(0, Math.min(sampleWidth - 1, sampleX));
        var safeY = Math.max(0, Math.min(sampleHeight - 1, sampleY));
        var x0 = Math.floor(safeX);
        var y0 = Math.floor(safeY);
        var x1 = Math.min(x0 + 1, sampleWidth - 1);
        var y1 = Math.min(y0 + 1, sampleHeight - 1);
        var tx = safeX - x0;
        var ty = safeY - y0;
        var topLeft = sourcePixels[(y0 * sampleWidth + x0) * 4 + channel];
        var topRight = sourcePixels[(y0 * sampleWidth + x1) * 4 + channel];
        var bottomLeft = sourcePixels[(y1 * sampleWidth + x0) * 4 + channel];
        var bottomRight = sourcePixels[(y1 * sampleWidth + x1) * 4 + channel];
        var top = topLeft + (topRight - topLeft) * tx;
        var bottom = bottomLeft + (bottomRight - bottomLeft) * tx;
        return top + (bottom - top) * ty;
      }

      function smoothStepValue(edge0, edge1, value) {
        var amount = Math.max(0, Math.min(1, (value - edge0) / (edge1 - edge0)));
        return amount * amount * (3 - 2 * amount);
      }

      for (var y = 0; y < sampleHeight; y++) {
        for (var x = 0; x < sampleWidth; x++) {
          var dx = x - centerX;
          var dy = y - centerY;
          var qx = Math.abs(dx) - halfX;
          var qy = Math.abs(dy) - halfY;
          var outsideX = Math.max(qx, 0);
          var outsideY = Math.max(qy, 0);
          var signedDistance = Math.sqrt(outsideX * outsideX + outsideY * outsideY) + Math.min(Math.max(qx, qy), 0) - radius;
          if (signedDistance < 0 || signedDistance > edgeBand) continue;

          var normalX;
          var normalY;
          if (outsideX > 0 && outsideY > 0) {
            var length = Math.max(0.0001, Math.sqrt(outsideX * outsideX + outsideY * outsideY));
            normalX = outsideX / length * (dx < 0 ? -1 : 1);
            normalY = outsideY / length * (dy < 0 ? -1 : 1);
          } else if (qx > qy) {
            normalX = dx < 0 ? -1 : 1;
            normalY = 0;
          } else {
            normalX = 0;
            normalY = dy < 0 ? -1 : 1;
          }

          var t = Math.max(0, Math.min(1, 1 - signedDistance / edgeBand));
          var edgeWeight = t * t * (3 - 2 * t);
          var shift = maxShift * edgeWeight;
          var sourceX = x - normalX * shift;
          var sourceY = y - normalY * shift;
          var targetIndex = (y * sampleWidth + x) * 4;
          var detailDifference = (
            Math.abs(sourcePixels[targetIndex] - glassBasePixels[targetIndex]) +
            Math.abs(sourcePixels[targetIndex + 1] - glassBasePixels[targetIndex + 1]) +
            Math.abs(sourcePixels[targetIndex + 2] - glassBasePixels[targetIndex + 2])
          ) / (255 * 3);
          var flatBoost = Math.max(0, Math.min(1, 1 - (detailDifference - 0.025) / 0.135));
          var facingLight = Math.max(normalX * 0.58 + normalY * 0.82, 0);
          var facingShadow = Math.max(normalX * -0.58 + normalY * -0.82, 0);
          var edgePosition = Math.max(0, Math.min(1, signedDistance / edgeBand));
          var refractionCaustic = smoothStepValue(0.03, 0.20, edgePosition) *
            (1 - smoothStepValue(0.28, 0.64, edgePosition));
          var highlightWidth = Math.max(0.012, Math.min(0.10, 1 / Math.max(edgeBand, 1)));
          var highlightDistance = (edgePosition - 0.145) / highlightWidth;
          var highlightCaustic = Math.exp(-highlightDistance * highlightDistance);
          var causticStrength = refractionCaustic *
            (0.010 + Math.pow(facingLight, 1.75) * 0.070) *
            (0.72 + flatBoost * 0.28);
          var lightOffset = (
            highlightCaustic * facingLight * (0.152 + flatBoost * 0.138) -
            edgeWeight * facingShadow * (0.060 + flatBoost * 0.055)
          ) * 255;
          var red = Math.max(0, Math.min(255, sampleChannelBilinear(sourceX - normalX * chromaShift, sourceY - normalY * chromaShift, 0) + lightOffset));
          var green = Math.max(0, Math.min(255, sampleChannelBilinear(sourceX, sourceY, 1) + lightOffset));
          var blue = Math.max(0, Math.min(255, sampleChannelBilinear(sourceX + normalX * chromaShift, sourceY + normalY * chromaShift, 2) + lightOffset));
          var tintRed = 255 * 0.84 + glassBasePixels[targetIndex] * 0.16;
          var tintGreen = 255 * 0.84 + glassBasePixels[targetIndex + 1] * 0.16;
          var tintBlue = 255 * 0.84 + glassBasePixels[targetIndex + 2] * 0.16;
          edgePixels[targetIndex] = Math.round(red + (tintRed - red) * causticStrength);
          edgePixels[targetIndex + 1] = Math.round(green + (tintGreen - green) * causticStrength);
          edgePixels[targetIndex + 2] = Math.round(blue + (tintBlue - blue) * causticStrength);
          edgePixels[targetIndex + 3] = Math.round(230 * edgeWeight);
        }
      }
      var edgeCanvas = document.createElement('canvas');
      edgeCanvas.width = sampleWidth;
      edgeCanvas.height = sampleHeight;
      edgeCanvas.getContext('2d').putImageData(edgeData, 0, 0);
      glassCtx.drawImage(edgeCanvas, 0, 0);
    } catch (e) {
      // Cross-origin images can block pixel reads; the layered blur remains a safe fallback.
    }

    cacheLiquidGlassCanvas(image, sourceCacheKey, cacheKey, glassCanvas);
    ctx.save();
    ctx.globalAlpha = 0.86;
    ctx.imageSmoothingEnabled = true;
    ctx.imageSmoothingQuality = 'high';
    ctx.drawImage(glassCanvas, 0, 0, width, height);
    ctx.restore();
  }

  /* --- LiquidGlassFrameRenderer --- */
  var LiquidGlassFrameRenderer = makeRenderer({
    backgroundColor: function () { return rgb(226, 229, 228); },
    logoBackgroundTone: function () { return 'Mixed'; },
    draw: function (ctx, bounds, info, assets, settings) {
      var w = bounds.width();
      var h = bounds.height();
      var unit = Math.min(w, h);
      var sideWidth = unit * 0.058;
      var topWidth = unit * 0.056;
      var bottomHeight = Math.max(h * 0.145, unit * 0.12);
      var outer = RectF(0, 0, w, h);
      var inner = RectF(
        outer.left + sideWidth,
        outer.top + topWidth,
        outer.right - sideWidth,
        outer.bottom - bottomHeight
      );
      var outerRadius = 0;
      var innerRadius = unit * 0.025;
      var contentColor = assets.hasExplicitTextColor ? assets.cardContentColor : COLOR_WHITE;
      var darkScrim = relativeLuminance(contentColor) >= 0.18;
      var scrimRgb = darkScrim ? { r: 0, g: 0, b: 0 } : { r: 255, g: 255, b: 255 };

      ctx.save();
      ctx.beginPath();
      addRoundedRectPath(ctx, outer, outerRadius);
      addRoundedRectPath(ctx, inner, innerRadius);
      try { ctx.clip('evenodd'); } catch (e) { ctx.clip(); }

      drawLiquidGlassBackdrop(ctx, assets.sourcePhotoImage, assets.sourceCacheKey, w, h, unit, inner, innerRadius);
      if (assets.hasExplicitCardBackgroundColor) {
        fillRect(ctx, 0, 0, w, h, withAlpha(assets.cardBackgroundColor, 30));
      }
      var sheen = ctx.createLinearGradient(outer.left, outer.top, outer.right, outer.bottom);
      sheen.addColorStop(0, 'rgba(255,255,255,0.078)');
      sheen.addColorStop(0.48, 'rgba(255,255,255,0)');
      sheen.addColorStop(1, 'rgba(0,0,0,0.047)');
      fillRect(ctx, 0, 0, w, h, sheen);
      ctx.restore();

      var contentLeft = inner.left + unit * 0.015;
      var contentTop = inner.bottom + bottomHeight * 0.20;
      var contentRight = inner.right - unit * 0.015;
      var logoWidth = Math.min(w * 0.16, bottomHeight * 1.12);
      var logoBox = RectF(contentRight - logoWidth, contentTop, contentRight, outer.bottom - bottomHeight * 0.18);
      var logoColor = darkScrim ? COLOR_WHITE : rgb(18, 19, 18);
      var logoActual = drawLogoIfPresent(ctx, logoBox, assetsWithLogoContrast(assets, logoColor), logoColor, 'End', 'Inside');
      var textRight = logoActual !== null ? logoActual.left - unit * 0.022 : contentRight;
      var title = displayTitleOrNull(info);
      var details = detailTextExcluding(title, [info.housing, info.switchName, info.plate, info.mount, info.keycap]);
      var titlePaint = medium(scaled(h * 0.025, settings), contentColor);
      var detailPaint = regular(scaled(h * 0.0135, settings), contentColor);
      if (title !== null) {
        drawLiquidGlassText(ctx, title, contentLeft, contentTop + titlePaint.size, titlePaint, textRight - contentLeft, scrimRgb, unit, false);
      }
      if (isNotBlank(details)) {
        var detailY = contentTop + (title === null ? detailPaint.size * 1.15 : titlePaint.size * 1.78);
        drawLiquidGlassText(ctx, details, contentLeft, detailY, detailPaint, textRight - contentLeft, scrimRgb, unit, true);
      }
      drawPaletteChipsInRect(
        ctx,
        visiblePaletteColors(assets, settings),
        RectF(contentLeft, outer.bottom - bottomHeight * 0.30, textRight, outer.bottom - bottomHeight * 0.10),
        bottomHeight * 0.095,
        bottomHeight * 0.048,
        withAlpha(contentColor, 84),
        'Start'
      );
    },
  });

  /* --- DarkGlassStripRenderer --- */
  var DarkGlassStripRenderer = makeRenderer({
    logoBackgroundTone: function () { return 'Dark'; },
    draw: function (ctx, bounds, info, assets, settings) {
      var rows = toDisplayRows(info, false).filter(function (row) {
        return row.label === 'BOARD' || row.label === 'Switch' || row.label === 'Keycap';
      }).slice(0, 3);
      var colors = visiblePaletteColors(assets, settings);
      if (rows.length === 0 && !assets.hasLogo && colors.length === 0) return;

      var w = bounds.width();
      var h = bounds.height();
      var stripHeight = h * 0.115;
      var strip = RectF(0, h - stripHeight, w, h);
      fillRect(ctx, strip.left, strip.top, strip.right, strip.bottom, argb(184, 8, 10, 11));
      var pad = w * 0.025;
      var logoWidth = Math.min(w * 0.105, stripHeight * 1.15);
      var logoBox = RectF(pad, strip.top + stripHeight * 0.22, pad + logoWidth, strip.bottom - stripHeight * 0.22);
      var logoActual = drawLogoIfPresent(ctx, logoBox, assets, COLOR_WHITE, 'Start');
      var startX = (logoActual !== null ? logoActual.right : strip.left) + pad;
      var rowRight = colors.length > 0 ? w * 0.68 : w - pad;
      var labelPaint = regular(scaled(h * 0.0105, settings), assets.cardContentColor);
      var valuePaint = medium(scaled(h * 0.0168, settings), assets.cardContentColor);
      if (rows.length > 0) {
        var columnWidth = Math.max(rowRight - startX, w * 0.25) / rows.length;
        for (var index = 0; index < rows.length; index++) {
          var x = startX + index * columnWidth;
          drawInfoRow(ctx, rows[index], x, strip.top + stripHeight * 0.43, columnWidth * 0.88, labelPaint, valuePaint, stripHeight * 0.27);
        }
      }
      drawPaletteChipsInRect(
        ctx,
        colors,
        RectF(w * 0.70, strip.top + stripHeight * 0.36, w - pad, strip.bottom - stripHeight * 0.25),
        stripHeight * 0.105,
        stripHeight * 0.05,
        argb(88, 255, 255, 255),
        'End',
        rows.length >= 3 ? 3 : settings.paletteColorCount
      );
    },
  });

  /* --- SideSpecRailRenderer --- */
  var SIDE_RAIL_RATIO = 0.18;
  var SideSpecRailRenderer = makeRenderer({
    backgroundColor: function () { return rgb(243, 244, 241); },
    logoBackgroundTone: function () { return 'Light'; },
    layoutSpec: function () { return { mode: 'ExternalSideCard', rightInsetFraction: SIDE_RAIL_RATIO }; },
    draw: function (ctx, bounds, info, assets, settings) {
      var w = bounds.width();
      var h = bounds.height();
      var railLeft = w * (1 - SIDE_RAIL_RATIO);
      var rail = RectF(railLeft, 0, w, h);
      fillRect(ctx, rail.left, rail.top, rail.right, rail.bottom, assets.cardBackgroundColor);
      fillRect(ctx, rail.left, 0, rail.left + 2, h, assets.cardContentColor);
      var pad = w * 0.028;
      var maxLogoWidth = rail.width() * 0.72;
      var maxLogoHeight = h * 0.105;
      var logoBox = RectF(
        rail.left + (rail.width() - maxLogoWidth) / 2,
        h * 0.055,
        rail.left + (rail.width() + maxLogoWidth) / 2,
        h * 0.055 + maxLogoHeight
      );
      var logoActual = drawLogoIfPresent(ctx, logoBox, assets, rgb(22, 23, 23), 'Center', 'Inside');
      var title = displayTitleOrNull(info);
      var rows = rowsExcludingTitle(toDisplayRows(info, true), title).slice(0, 5);
      var titlePaint = medium(scaled(h * 0.024, settings), assets.cardContentColor);
      var labelPaint = regular(scaled(h * 0.0115, settings), assets.cardContentColor);
      var valuePaint = medium(scaled(h * 0.0155, settings), assets.cardContentColor);
      var cursorY = (logoActual !== null ? logoActual.bottom : rail.top) + (assets.hasLogo ? h * 0.06 : h * 0.075);
      if (title !== null) {
        drawTextBaseline(ctx, title, rail.left + pad, cursorY, titlePaint, rail.width() - pad * 2);
        cursorY += h * 0.075;
      }
      for (var i = 0; i < rows.length; i++) {
        var valueLines = wrapTextAtWords(rows[i].value, valuePaint, rail.width() - pad * 2, 2);
        drawTextBaseline(ctx, rows[i].label.toUpperCase(), rail.left + pad, cursorY, labelPaint, rail.width() - pad * 2);
        for (var lineIndex = 0; lineIndex < valueLines.length; lineIndex++) {
          drawTextBaseline(ctx, valueLines[lineIndex], rail.left + pad, cursorY + h * (0.028 + lineIndex * 0.024), valuePaint, rail.width() - pad * 2);
        }
        cursorY += h * (valueLines.length > 1 ? 0.115 : 0.095);
      }
      drawPaletteChipsInRect(
        ctx,
        visiblePaletteColors(assets, settings),
        RectF(
          rail.left + pad,
          Math.max(cursorY, h * 0.18),
          rail.right - pad,
          Math.min(h * 0.93, Math.max(cursorY, h * 0.18) + h * 0.04)
        ),
        h * 0.014,
        h * 0.006,
        argb(70, 0, 0, 0),
        'Start',
        3
      );
    },
  });

  /* --- TopNameplateRenderer --- */
  var TOP_HEADER_RATIO = 0.12;
  var TopNameplateRenderer = makeRenderer({
    backgroundColor: function () { return rgb(247, 247, 243); },
    logoBackgroundTone: function () { return 'Light'; },
    layoutSpec: function () { return { mode: 'ExternalFrame', topInsetFraction: TOP_HEADER_RATIO }; },
    draw: function (ctx, bounds, info, assets, settings) {
      var w = bounds.width();
      var h = bounds.height();
      var header = RectF(0, 0, w, h * TOP_HEADER_RATIO);
      fillRect(ctx, header.left, header.top, header.right, header.bottom, assets.cardBackgroundColor);
      var pad = w * 0.035;
      var logoWidth = Math.min(w * 0.11, header.height() * 0.58);
      var logoBox = RectF(pad, header.centerY() - logoWidth / 2, pad + logoWidth, header.centerY() + logoWidth / 2);
      var logoActual = drawLogoIfPresent(ctx, logoBox, assets, rgb(24, 25, 24), 'Start');
      var titlePaint = medium(scaled(h * 0.025, settings), assets.cardContentColor);
      var bodyPaint = regular(scaled(h * 0.0145, settings), assets.cardContentColor);
      var textX = (logoActual !== null ? logoActual.right : header.left) + pad * (logoActual === null ? 1 : 0.78);
      var right = w - pad;
      var title = displayTitleOrNull(info);
      var detail = detailTextExcluding(title, [
        info.housing,
        info.switchName,
        info.keycap,
        nicknameDetail(info, settings, title),
      ]);
      if (title !== null) {
        drawTextBaseline(ctx, title, textX, header.top + header.height() * 0.42, titlePaint, right - textX);
      }
      if (isNotBlank(detail)) {
        drawTextBaseline(ctx, detail, textX, header.top + header.height() * (title === null ? 0.55 : 0.67), bodyPaint, right - textX);
      }
      drawPaletteChipsInRect(
        ctx,
        visiblePaletteColors(assets, settings),
        RectF(w * 0.68, header.top + header.height() * 0.68, right, header.bottom - 5),
        header.height() * 0.09,
        header.height() * 0.04,
        argb(70, 0, 0, 0),
        'End',
        isBlank(detail) ? settings.paletteColorCount : 3
      );
      fillRect(ctx, 0, header.bottom - 2, w, header.bottom, assets.cardContentColor);
    },
  });

  /* --- MuseumMatRenderer --- */
  var MuseumMatRenderer = makeRenderer({
    backgroundColor: function () { return rgb(246, 245, 239); },
    logoBackgroundTone: function () { return 'Light'; },
    layoutSpec: function () {
      return {
        mode: 'ExternalFrame',
        leftInsetFraction: 0.055,
        topInsetFraction: 0.055,
        rightInsetFraction: 0.055,
        bottomInsetFraction: 0.23,
      };
    },
    draw: function (ctx, bounds, info, assets, settings) {
      var w = bounds.width();
      var h = bounds.height();
      var photo = this.photoBounds(bounds);
      var labelTop = photo.bottom + h * 0.045;
      var pad = w * 0.06;
      var logoSize = Math.min(w * 0.12, h * 0.08);
      var logoBox = RectF(w - pad - logoSize, labelTop, w - pad, labelTop + logoSize);
      var logoActual = drawLogoIfPresent(ctx, logoBox, assets, rgb(24, 24, 23), 'End');
      var titlePaint = medium(scaled(h * 0.026, settings), assets.cardContentColor);
      var bodyPaint = regular(scaled(h * 0.015, settings), assets.cardContentColor);
      var labelPaint = regular(scaled(h * 0.012, settings), assets.cardContentColor);
      var textRight = logoActual !== null ? logoActual.left - pad * 1.6 : (w - pad);
      var title = displayTitleOrNull(info);
      var details = detailTextExcluding(title, [
        info.housing,
        info.switchName,
        info.plate,
        info.mount,
        info.keycap,
        nicknameDetail(info, settings, title),
      ]);
      if (title !== null || isNotBlank(details)) {
        drawTextBaseline(ctx, 'KEYXIF BUILD CARD', pad, labelTop + h * 0.006, labelPaint, w * 0.45);
      }
      if (title !== null) {
        drawTextBaseline(ctx, title, pad, labelTop + h * 0.05, titlePaint, textRight - pad);
      }
      if (isNotBlank(details)) {
        var y = labelTop + h * (title === null ? 0.06 : 0.09);
        drawTextBaseline(ctx, details, pad, y, bodyPaint, textRight - pad);
      }
      drawPaletteChipsInRect(
        ctx,
        visiblePaletteColors(assets, settings),
        RectF(pad, labelTop + h * 0.102, textRight, labelTop + h * 0.135),
        h * 0.014,
        h * 0.006,
        argb(70, 0, 0, 0),
        'Start'
      );
    },
  });

  /* --- CompactTicketRenderer --- */
  var TICKET_RATIO = 0.13;
  var CompactTicketRenderer = makeRenderer({
    backgroundColor: function () { return rgb(235, 236, 232); },
    logoBackgroundTone: function () { return 'Light'; },
    layoutSpec: function () { return { mode: 'ExternalBottomCard', bottomInsetFraction: TICKET_RATIO }; },
    draw: function (ctx, bounds, info, assets, settings) {
      var title = displayTitleOrNull(info);
      var detail = detailTextExcluding(title, [
        info.housing,
        info.switchName,
        info.keycap,
        nicknameDetail(info, settings, title),
      ]);
      var colors = visiblePaletteColors(assets, settings);
      if (title === null && isBlank(detail) && !assets.hasLogo && colors.length === 0) return;

      var w = bounds.width();
      var h = bounds.height();
      var ticketTop = h * (1 - TICKET_RATIO);
      var pad = w * 0.035;
      var ticket = RectF(pad, ticketTop + h * 0.018, w - pad, h - h * 0.018);
      drawRoundRect(ctx, ticket, ticket.height() * 0.18, assets.cardBackgroundColor);
      var logoSize = Math.min(ticket.height() * 0.58, w * 0.1);
      var logoBox = RectF(
        ticket.left + ticket.height() * 0.2,
        ticket.centerY() - logoSize / 2,
        ticket.left + ticket.height() * 0.2 + logoSize,
        ticket.centerY() + logoSize / 2
      );
      var logoActual = drawLogoIfPresent(ctx, logoBox, assets, rgb(27, 28, 27), 'Start');
      var titlePaint = medium(scaled(h * 0.0195, settings), assets.cardContentColor);
      var bodyPaint = regular(scaled(h * 0.013, settings), assets.cardContentColor);
      var textX = (logoActual !== null ? logoActual.right : ticket.left) + ticket.height() * 0.2;
      var paletteLeft = ticket.right - ticket.width() * 0.24;
      var right = colors.length === 0
        ? ticket.right - ticket.height() * 0.2
        : paletteLeft - pad * 0.5;
      if (title !== null) {
        drawTextBaseline(ctx, title, textX, ticket.top + ticket.height() * 0.42, titlePaint, right - textX);
      }
      if (isNotBlank(detail)) {
        drawTextBaseline(ctx, detail, textX, ticket.top + ticket.height() * (title === null ? 0.56 : 0.7), bodyPaint, right - textX);
      }
      drawPaletteChipsInRect(
        ctx,
        colors,
        RectF(paletteLeft, ticket.top + ticket.height() * 0.30, ticket.right - ticket.height() * 0.18, ticket.bottom - ticket.height() * 0.30),
        ticket.height() * 0.13,
        ticket.height() * 0.055,
        argb(68, 0, 0, 0),
        'End',
        3
      );
    },
  });

  /* --- CleanSignatureRenderer --- */
  var CleanSignatureRenderer = makeRenderer({
    backgroundColor: function () { return rgb(250, 250, 247); },
    logoBackgroundTone: function () { return 'Light'; },
    layoutSpec: function () { return { mode: 'ExternalBottomCard', bottomInsetFraction: 0.155 }; },
    draw: function (ctx, bounds, info, assets, settings) {
      var lines = [];
      var v;
      v = meaningfulBuildTextOrNull(info.housing);
      if (v !== null) lines.push(v);
      v = meaningfulBuildTextOrNull(info.keycap);
      if (v !== null) lines.push(v);
      v = meaningfulBuildTextOrNull(nicknameText(info, settings));
      if (v !== null) lines.push(v);
      var colors = visiblePaletteColors(assets, settings);
      if (lines.length === 0 && !assets.hasLogo && colors.length === 0) return;

      var w = bounds.width();
      var h = bounds.height();
      var footerTop = h * 0.845;
      var pad = w * 0.052;
      var logoSize = Math.min(w * 0.09, h * 0.07);
      var logoBox = RectF(w - pad - logoSize, footerTop + h * 0.035, w - pad, footerTop + h * 0.035 + logoSize);
      var logoActual = drawLogoIfPresent(ctx, logoBox, assets, rgb(20, 21, 20), 'End');
      var titlePaint = medium(scaled(h * 0.026, settings), assets.cardContentColor);
      var bodyPaint = regular(scaled(h * 0.0145, settings), assets.cardContentColor);
      var nickPaint = medium(scaled(h * 0.019, settings, settings.nicknameEmphasis), assets.cardContentColor);
      var textRight = logoActual !== null
        ? logoActual.left - pad
        : (colors.length > 0 ? logoBox.left - pad : w - pad);
      var baselines = [footerTop + h * 0.052, footerTop + h * 0.083, footerTop + h * 0.112];
      var shown = lines.slice(0, 3);
      for (var index = 0; index < shown.length; index++) {
        var p = index === 0 ? titlePaint : (index === 1 ? bodyPaint : nickPaint);
        drawTextBaseline(ctx, shown[index], pad, baselines[index], p, textRight - pad);
      }
      drawPaletteChipsInRect(
        ctx,
        colors,
        RectF(
          logoBox.left,
          (logoActual !== null ? logoActual.bottom : logoBox.bottom) + h * 0.010,
          logoBox.right,
          footerTop + h * 0.148
        ),
        h * 0.014,
        h * 0.006,
        argb(70, 0, 0, 0),
        'Center',
        lines.length >= 3 ? 3 : settings.paletteColorCount
      );
    },
  });

  /* --- EditorialCoverRenderer --- */
  var EditorialCoverRenderer = makeRenderer({
    logoBackgroundTone: function () { return 'Mixed'; },
    photoPlacement: function () { return 'CenterCrop'; },
    draw: function (ctx, bounds, info, assets, settings) {
      var w = bounds.width();
      var h = bounds.height();
      var pad = Math.min(w, h) * 0.038;
      var title = displayTitleOrNull(info);
      var mainLine = meaningfulBuildTextOrNull(info.housing) || title;
      var switchLine = meaningfulBuildTextOrNull(info.switchName);
      var keycapLine = meaningfulBuildTextOrNull(info.keycap);
      var specLine = detailText([info.plate, info.mount], '  /  ');
      var signature = nicknameDetail(info, settings, title);
      if (title === null && mainLine === null && switchLine === null && keycapLine === null && isBlank(signature) && !assets.hasLogo) return;
      var isLandscape = w > h * 1.12;

      drawGradientScrim(ctx, RectF(0, 0, w, h * (isLandscape ? 0.30 : 0.36)), argb(isLandscape ? 96 : 122, 0, 0, 0), 'rgba(0,0,0,0)', true);
      drawGradientScrim(ctx, RectF(0, h * (isLandscape ? 0.48 : 0.52), w, h), 'rgba(0,0,0,0)', argb(150, 0, 0, 0), true);

      var logoWidth = isLandscape ? w * 0.155 : w * 0.20;
      var logoTop = isLandscape ? pad : h * 0.065;
      var logoBox = RectF(w - pad - logoWidth, logoTop, w - pad, logoTop + h * (isLandscape ? 0.095 : 0.082));
      var logoTone = contrastColorAt(ctx, assets, logoBox.centerX(), logoBox.centerY());
      drawLogoIfPresent(ctx, logoBox, assetsWithLogoContrast(assets, logoTone), logoTone, 'End', 'Inside');

      var contentColor = assets.hasExplicitTextColor ? assets.cardContentColor : COLOR_WHITE;
      var mainPaint = medium(scaled(h * (isLandscape ? 0.072 : 0.070), settings), contentColor);
      if (mainLine !== null) {
        drawAdaptiveTextByCharacter(ctx, mainLine.toUpperCase(), pad, h * (isLandscape ? 0.18 : 0.15), mainPaint, w * (isLandscape ? 0.64 : 0.78), assets);
      }
      var coverLinePaint = regular(scaled(h * (isLandscape ? 0.022 : 0.018), settings), contentColor);
      var smallPaint = regular(scaled(h * 0.014, settings), contentColor);
      function drawLines(text, x, firstBaseline, paintRef, maxWidth, lineStep, maxLines) {
        var lines = wrapTextAtSeparators(text, paintRef, maxWidth, maxLines);
        for (var li = 0; li < lines.length; li++) {
          drawAdaptiveTextByCharacter(ctx, lines[li], x, firstBaseline + lineStep * li, paintRef, maxWidth, assets);
        }
      }
      if (isLandscape) {
        var detailWidth = w * 0.66;
        if (isNotBlank(switchLine)) drawLines('SWITCH  ' + switchLine, pad, h * 0.68, coverLinePaint, detailWidth, h * 0.046, 2);
        if (isNotBlank(keycapLine)) drawLines('KEYCAP  ' + keycapLine, pad, h * 0.76, coverLinePaint, detailWidth, h * 0.046, 2);
        if (isNotBlank(specLine)) drawLines(specLine.toUpperCase(), pad, h * 0.855, smallPaint, detailWidth, h * 0.032, 2);
      } else {
        if (isNotBlank(switchLine)) drawLines('SWITCH  ' + switchLine, pad, h * 0.735, coverLinePaint, w * 0.68, h * 0.032, 2);
        if (isNotBlank(keycapLine)) drawLines('KEYCAP  ' + keycapLine, pad, h * 0.790, coverLinePaint, w * 0.68, h * 0.032, 2);
        if (isNotBlank(specLine)) drawLines(specLine.toUpperCase(), pad, h * 0.850, smallPaint, w * 0.68, h * 0.026, 2);
      }
      if (isNotBlank(signature)) {
        var signaturePaint = medium(scaled(h * 0.018, settings, settings.nicknameEmphasis), contentColor);
        signaturePaint.align = 'right';
        drawAdaptiveTextByCharacter(ctx, signature, w - pad, h - pad * 0.88, signaturePaint, w * 0.42, assets);
      }
    },
  });

  /* --- SoftEditorialRenderer --- */
  var SoftEditorialRenderer = makeRenderer({
    backgroundColor: function () { return COLOR_BLACK; },
    logoBackgroundTone: function () { return 'Mixed'; },
    photoPlacement: function () { return 'CenterCrop'; },
    draw: function (ctx, bounds, info, assets, settings) {
      var w = bounds.width();
      var h = bounds.height();
      var isLandscape = w > h * 1.12;
      var pad = Math.min(w, h) * 0.042;
      var title = displayTitleOrNull(info);
      var rows = rowsExcludingTitle(toDisplayRows(info, true), title).slice(0, 4);
      if (title === null && rows.length === 0 && !assets.hasLogo) return;
      drawGradientScrim(ctx, RectF(0, h * (isLandscape ? 0.54 : 0.58), w, h), 'rgba(0,0,0,0)', argb(132, 0, 0, 0), true);
      drawGradientScrim(ctx, RectF(0, 0, w, h * 0.20), argb(64, 0, 0, 0), 'rgba(0,0,0,0)', true);
      var logoBox = isLandscape
        ? RectF(w - pad - w * 0.155, pad, w - pad, pad + h * 0.095)
        : RectF(w - pad - w * 0.24, pad, w - pad, pad + h * 0.085);
      var logoTone = contrastColorAt(ctx, assets, logoBox.centerX(), logoBox.centerY());
      drawLogoIfPresent(ctx, logoBox, assetsWithLogoContrast(assets, logoTone), logoTone, 'End', 'Inside');
      var contentColor = assets.hasExplicitTextColor ? assets.cardContentColor : COLOR_WHITE;
      var titlePaint = medium(scaled(h * (isLandscape ? 0.040 : 0.044), settings), contentColor);
      if (title !== null) {
        var titleMaxWidth = isLandscape ? w * 0.62 : w * 0.72;
        var titleLines = wrapTextAtWords(title.toUpperCase(), titlePaint, titleMaxWidth, 2);
        for (var tl = 0; tl < titleLines.length; tl++) {
          drawAdaptiveTextByCharacter(ctx, titleLines[tl], pad, h * (isLandscape ? 0.68 : 0.73) + h * (tl * 0.048), titlePaint, titleMaxWidth, assets);
        }
      }
      var rowText = rows.slice(0, isLandscape ? 3 : 4).map(function (row) {
        return row.label.toUpperCase() + ' ' + row.value;
      }).join('  /  ');
      if (isNotBlank(rowText)) {
        var linePaint = regular(scaled(h * (isLandscape ? 0.0155 : 0.0145), settings), contentColor);
        var rowLines = wrapTextAtSeparators(rowText, linePaint, w * 0.76, 3);
        for (var rl = 0; rl < rowLines.length; rl++) {
          drawAdaptiveTextByCharacter(ctx, rowLines[rl], pad, h * (isLandscape ? 0.80 : 0.845) + h * 0.026 * rl, linePaint, w * 0.76, assets);
        }
      }
      var c = colorToRgb(contentColor);
      drawPaletteChipsInRect(
        ctx,
        visiblePaletteColors(assets, settings),
        isLandscape ? RectF(pad, h * 0.905, w * 0.64, h * 0.95) : RectF(pad, h * 0.905, w * 0.64, h * 0.95),
        h * 0.014,
        h * 0.006,
        'rgba(' + c.r + ',' + c.g + ',' + c.b + ',0.32)',
        'Start'
      );
    },
  });

  /* --- PlainExportRenderer --- */
  var PlainExportRenderer = makeRenderer({
    backgroundColor: function () { return COLOR_BLACK; },
    logoBackgroundTone: function () { return 'Mixed'; },
    photoPlacement: function () { return 'FitCenter'; },
    draw: function (ctx, bounds, info, assets, settings) {
      // Kotlin PlainExportRenderer.draw is a no-op in 1.0.3
      // (settings.showBuildInfoInPlainExport has no effect here).
      return;
    },
  });

  /* ---------------------------------------------------------------------- */
  /* Orchestrator (KeyxifCanvasRenderer.kt)                                  */
  /* ---------------------------------------------------------------------- */

  // CardTemplate enum order
  var TEMPLATE_ORDER = [
    { id: 'PlainExport', renderer: PlainExportRenderer },
    { id: 'ClassicFrame', renderer: ClassicFrameRenderer },
    { id: 'MinimalCaption', renderer: MinimalCaptionRenderer },
    { id: 'BottomSpecBar', renderer: BottomSpecBarRenderer },
    { id: 'CornerMark', renderer: CornerMarkRenderer },
    { id: 'PosterMargin', renderer: PosterMarginRenderer },
    { id: 'LiquidGlassFrame', renderer: LiquidGlassFrameRenderer },
    { id: 'DarkGlassStrip', renderer: DarkGlassStripRenderer },
    { id: 'SideSpecRail', renderer: SideSpecRailRenderer },
    { id: 'TopNameplate', renderer: TopNameplateRenderer },
    { id: 'MuseumMat', renderer: MuseumMatRenderer },
    { id: 'CompactTicket', renderer: CompactTicketRenderer },
    { id: 'CleanSignature', renderer: CleanSignatureRenderer },
    { id: 'EditorialCover', renderer: EditorialCoverRenderer },
    { id: 'SoftEditorial', renderer: SoftEditorialRenderer },
  ];

  var renderersById = {};
  var templates = [];
  for (var ti = 0; ti < TEMPLATE_ORDER.length; ti++) {
    renderersById[TEMPLATE_ORDER[ti].id] = TEMPLATE_ORDER[ti].renderer;
    templates.push({
      id: TEMPLATE_ORDER[ti].id,
      backgroundTone: TEMPLATE_ORDER[ti].renderer.logoBackgroundTone(),
      layoutSpec: TEMPLATE_ORDER[ti].renderer.layoutSpec(),
    });
  }

  function normalizeSettings(s) {
    s = s || {};
    return {
      textScale: typeof s.textScale === 'number' ? s.textScale : 1.0,
      nicknameStyle: s.nicknameStyle || 'Plain',
      nicknameEmphasis: typeof s.nicknameEmphasis === 'number' ? s.nicknameEmphasis : 1.1,
      showPaletteColors: s.showPaletteColors !== undefined ? !!s.showPaletteColors : true,
      paletteColorCount: typeof s.paletteColorCount === 'number' ? s.paletteColorCount : 4,
      showBuildInfoInPlainExport: !!s.showBuildInfoInPlainExport,
      autoSelectLogoContrastVariant:
        s.autoSelectLogoContrastVariant !== undefined ? !!s.autoSelectLogoContrastVariant : true,
    };
  }

  // KeyxifCanvasRenderer.resolveLogoDrawable — tone-based contrast variant chains.
  // assets.logoVariants = { default, black, white }; assets.logoImage acts as the
  // "default" variant when logoVariants is absent (or when its default is missing).
  function resolveLogoForBackground(assets, backgroundColor) {
    var variants = assets.logoVariants || null;
    var def = (variants && variants.default) || assets.logoImage || null;
    var black = (variants && variants.black) || null;
    var white = (variants && variants.white) || null;
    if (assets.logoColorPolicy === 'AUTO_MONO_TINT') {
      return {
        image: def || black || white,
        tintColor: isDarkColor(backgroundColor) ? COLOR_WHITE : COLOR_BLACK,
      };
    }
    return {
      image: isDarkColor(backgroundColor) ? (white || def || black) : (black || def || white),
      tintColor: null,
    };
  }

  // KeyxifCanvasRenderer.drawTemplatePhoto — CenterCrop / FitCenter with clip.
  function drawTemplatePhoto(ctx, image, destination, placement) {
    var iw = imageWidth(image);
    var ih = imageHeight(image);
    if (iw <= 0 || ih <= 0) return;
    var widthScale = destination.width() / iw;
    var heightScale = destination.height() / ih;
    var scale = placement === 'CenterCrop'
      ? Math.max(widthScale, heightScale)
      : Math.min(widthScale, heightScale);
    var scaledWidth = iw * scale;
    var scaledHeight = ih * scale;
    var centerX = destination.centerX();
    var centerY = destination.centerY();
    var target = RectF(
      centerX - scaledWidth / 2,
      centerY - scaledHeight / 2,
      centerX + scaledWidth / 2,
      centerY + scaledHeight / 2
    );
    ctx.save();
    ctx.beginPath();
    ctx.rect(destination.left, destination.top, destination.width(), destination.height());
    ctx.clip();
    ctx.imageSmoothingEnabled = true;
    ctx.imageSmoothingQuality = 'high';
    ctx.drawImage(image, target.left, target.top, target.width(), target.height());
    ctx.restore();
  }

  function drawPhotoOverlayLogoIfNeeded(ctx, photoBounds, overlayImage) {
    if (!overlayImage || imageWidth(overlayImage) <= 0 || imageHeight(overlayImage) <= 0) return;
    var targetWidth = photoBounds.width() * 0.33;
    var targetHeight = photoBounds.height() * 0.33;
    var marginX = photoBounds.width() * 0.035;
    var marginY = photoBounds.height() * 0.035;
    var box = RectF(
      photoBounds.right - targetWidth - marginX,
      photoBounds.bottom - targetHeight - marginY,
      photoBounds.right - marginX,
      photoBounds.bottom - marginY
    );
    var target = logoFitInside(imageWidth(overlayImage), imageHeight(overlayImage), box, 'Center');
    ctx.save();
    ctx.beginPath();
    ctx.rect(photoBounds.left, photoBounds.top, photoBounds.width(), photoBounds.height());
    ctx.clip();
    ctx.globalAlpha = 230 / 255;
    ctx.imageSmoothingEnabled = true;
    ctx.imageSmoothingQuality = 'high';
    ctx.drawImage(overlayImage, target.left, target.top, target.width(), target.height());
    ctx.restore();
  }

  function calculateRenderLayout(spec, imageWidthPx, imageHeightPx, maxLongSide) {
    spec = spec || {};
    var left = Number(spec.leftInsetFraction) || 0;
    var top = Number(spec.topInsetFraction) || 0;
    var right = Number(spec.rightInsetFraction) || 0;
    var bottom = Number(spec.bottomInsetFraction) || 0;
    var photoWidthFraction = 1 - left - right;
    var photoHeightFraction = 1 - top - bottom;
    if (imageWidthPx <= 0 || imageHeightPx <= 0 || photoWidthFraction <= 0 || photoHeightFraction <= 0) {
      throw new Error('KeyxifRenderer: invalid render layout');
    }
    var naturalWidth = imageWidthPx / photoWidthFraction;
    var naturalHeight = imageHeightPx / photoHeightFraction;
    var limit = Number(maxLongSide);
    var outputScale = Number.isFinite(limit) && limit > 0
      ? Math.min(1, limit / Math.max(naturalWidth, naturalHeight))
      : 1;
    var finalWidth = Math.max(1, Math.round(naturalWidth * outputScale));
    var finalHeight = Math.max(1, Math.round(naturalHeight * outputScale));
    var photoRect = RectF(
      finalWidth * left,
      finalHeight * top,
      finalWidth * (1 - right),
      finalHeight * (1 - bottom)
    );
    var mode = spec.mode || 'OverlayOnPhoto';
    var cardRect = null;
    if (mode === 'ExternalBottomCard') {
      cardRect = RectF(0, photoRect.bottom, finalWidth, finalHeight);
    } else if (mode === 'ExternalSideCard') {
      cardRect = RectF(photoRect.right, 0, finalWidth, finalHeight);
    }
    return {
      finalWidth: finalWidth,
      finalHeight: finalHeight,
      photoRect: photoRect,
      cardRect: cardRect,
      mode: mode,
    };
  }

  function calculateCustomTemplateLayout(template, imageWidthPx, imageHeightPx, maxLongSide) {
    var photo = (template && template.photoPlacement) || {};
    var photoWidth = clamp(Number(photo.width) || 0.84, 0.01, 1);
    var photoHeight = clamp(Number(photo.height) || 0.68, 0.01, 1);
    var photoX = clamp(Number(photo.x) || 0, 0, 1 - photoWidth);
    var photoY = clamp(Number(photo.y) || 0, 0, 1 - photoHeight);
    var marginLeft = clamp(photoX / photoWidth, 0, 3);
    var marginRight = clamp((1 - photoX - photoWidth) / photoWidth, 0, 3);
    var marginTop = clamp(photoY / photoHeight, 0, 3);
    var marginBottom = clamp((1 - photoY - photoHeight) / photoHeight, 0, 3);
    var naturalWidth = imageWidthPx * (1 + marginLeft + marginRight);
    var naturalHeight = imageHeightPx * (1 + marginTop + marginBottom);
    var limit = Number(maxLongSide);
    var outputScale = Number.isFinite(limit) && limit > 0
      ? Math.min(1, limit / Math.max(naturalWidth, naturalHeight))
      : 1;
    var finalWidth = Math.max(1, Math.round(naturalWidth * outputScale));
    var finalHeight = Math.max(1, Math.round(naturalHeight * outputScale));
    var photoRect = RectF(
      imageWidthPx * marginLeft * outputScale,
      imageHeightPx * marginTop * outputScale,
      imageWidthPx * (marginLeft + 1) * outputScale,
      imageHeightPx * (marginTop + 1) * outputScale
    );
    return {
      finalWidth: finalWidth,
      finalHeight: finalHeight,
      photoRect: photoRect,
    };
  }

  function customRect(container, x, y, width, height) {
    var left = container.left + container.width() * x;
    var top = container.top + container.height() * y;
    return RectF(
      left,
      top,
      left + container.width() * Math.max(0.001, width),
      top + container.height() * Math.max(0.001, height)
    );
  }

  function customFontSize(rect, normalizedSize, settings) {
    return Math.max(
      8.5,
      Math.min(rect.width(), rect.height()) * clamp(Number(normalizedSize) || 0.045, 0.01, 0.4) * 2.2 *
        clamp(settings.textScale, 0.9, 1.55)
    );
  }

  function customFieldLabel(field) {
    switch (field) {
      case 'Board': return 'BOARD';
      case 'Switch': return 'SWITCH';
      case 'Plate': return 'PLATE';
      case 'Mount': return 'MOUNT';
      case 'Nickname': return 'NICKNAME';
      default: return String(field || '').toUpperCase();
    }
  }

  function resolveCustomElementText(content, buildInfo) {
    content = content || {};
    var contentType = String(content.type || '').toLowerCase();
    if (contentType === 'statictext') {
      return isBlank(content.text) ? null : String(content.text);
    }
    if (contentType !== 'buildfield') return null;
    var field = content.field || 'Board';
    var value = null;
    switch (field) {
      case 'Board': value = meaningfulBuildTextOrNull(buildInfo.housing); break;
      case 'Switch': value = meaningfulBuildTextOrNull(buildInfo.switchName); break;
      case 'Plate': value = meaningfulBuildTextOrNull(buildInfo.plate); break;
      case 'Mount': value = meaningfulBuildTextOrNull(buildInfo.mount); break;
      case 'Nickname': value = meaningfulBuildTextOrNull(buildInfo.nickname); break;
      default: value = null; break;
    }
    if (value === null) return null;
    var format = String(content.format || '').toLowerCase();
    if (format === 'labelandvalue') return customFieldLabel(field) + ' ' + value;
    if (format === 'colon') return customFieldLabel(field) + ': ' + value;
    return value;
  }

  function resolveCustomTemplateColor(fallback, slotId, paletteColors, settings, renderStyle) {
    var slot = String(slotId || '').toLowerCase();
    if (settings.showPaletteColors) {
      if (slot.indexOf('text') >= 0) {
        return resolveCustomTextColor(fallback, paletteColors, settings, renderStyle);
      }
      var match = slot.match(/(\d+)/);
      if (slot.indexOf('palette') >= 0 && match) {
        return paletteColors[clamp(Number(match[1]) || 0, 0, 4)] || fallback;
      }
      if (slot.indexOf('background') >= 0 || slot.indexOf('card') >= 0) {
        if (renderStyle.customCardBackgroundColor) return colorToCss(renderStyle.customCardBackgroundColor);
        return paletteColors[clamp(Math.round(Number(renderStyle.paletteBackgroundColorIndex) || 0), 0, 4)] || fallback;
      }
    }
    return fallback;
  }

  function resolveCustomTextColor(fallback, paletteColors, settings, renderStyle) {
    if (settings.showPaletteColors && renderStyle.usePaletteColorForText) {
      if (renderStyle.customTextColor) return colorToCss(renderStyle.customTextColor);
      return paletteColors[clamp(Math.round(Number(renderStyle.paletteTextColorIndex) || 0), 0, 4)] || fallback;
    }
    return fallback;
  }

  function drawCustomInternalCard(ctx, card, rect, paletteColors, settings, renderStyle) {
    var style = card.style || {};
    var background = resolveCustomTemplateColor(
      colorToCss(style.backgroundColor || '#ffffff'),
      style.colorSlotId,
      paletteColors,
      settings,
      renderStyle
    );
    var radius = Math.min(rect.width(), rect.height()) * clamp(Number(style.radius) || 0, 0, 0.5);
    ctx.save();
    if (style.shadowEnabled) {
      ctx.shadowColor = withAlpha(COLOR_BLACK, clamp(Number(style.shadowOpacity) || 0.2, 0, 1) * 255);
      ctx.shadowBlur = Math.max(rect.width(), rect.height()) * clamp(Number(style.shadowBlur) || 0.02, 0, 0.2);
      ctx.shadowOffsetY = rect.height() * 0.018;
    }
    drawRoundRect(ctx, rect, radius, withAlpha(background, clamp(Number(style.opacity) || 0.92, 0, 1) * 255));
    ctx.restore();
    if (style.borderEnabled) {
      roundRectPath(ctx, rect.left, rect.top, rect.right, rect.bottom, radius);
      ctx.strokeStyle = colorToCss(style.borderColor || '#000000');
      ctx.lineWidth = Math.max(1, Math.min(rect.width(), rect.height()) * Math.max(0, Number(style.borderWidth) || 0.002));
      ctx.stroke();
    }
  }

  function drawCustomElement(ctx, element, container, buildInfo, assets, settings, paletteColors, renderStyle, defaultTextColor) {
    if (!element || element.hidden || container.width() <= 0 || container.height() <= 0) return;
    var style = element.style || {};
    var rect = customRect(
      container,
      Number(element.x) || 0,
      Number(element.y) || 0,
      Number(element.width) || 0.1,
      Number(element.height) || 0.1
    );
    if (rect.width() <= 0 || rect.height() <= 0) return;
    var type = String(element.type || '').toLowerCase();
    if (type === 'text') {
      var raw = resolveCustomElementText(element.content, buildInfo);
      if (isBlank(raw)) return;
      var text = style.uppercase ? String(raw).toUpperCase() : String(raw);
      var textColor = colorToCss(style.textColor || defaultTextColor);
      var paintRef = (style.fontWeight === 'Bold' || style.fontWeight === 'Medium')
        ? medium(customFontSize(rect, style.fontSize, settings), withAlpha(textColor, clamp(Number(style.opacity) || 1, 0, 1) * 255))
        : regular(customFontSize(rect, style.fontSize, settings), withAlpha(textColor, clamp(Number(style.opacity) || 1, 0, 1) * 255));
      paintRef.align = style.textAlign === 'Center' ? 'center' : (style.textAlign === 'End' ? 'right' : 'left');
      var x = style.textAlign === 'Center' ? rect.centerX() : (style.textAlign === 'End' ? rect.right : rect.left);
      var lines = wrapTextAtSeparators(text, paintRef, rect.width(), Math.max(1, Math.round(Number(style.maxLines) || 2)));
      var lineHeight = paintRef.size * clamp(Number(style.lineHeight) || 1.12, 0.9, 2.4);
      var baseline = rect.top - paintRef.ascent();
      ctx.save();
      ctx.beginPath();
      ctx.rect(rect.left, rect.top, rect.width(), rect.height());
      ctx.clip();
      for (var i = 0; i < lines.length; i++) {
        if (baseline <= rect.bottom + paintRef.descent()) drawText(ctx, lines[i], x, baseline, paintRef);
        baseline += lineHeight;
      }
      ctx.restore();
    } else if (type === 'logo') {
      if (!assets.hasLogo) return;
      var logoColor = colorToCss(style.textColor || defaultTextColor);
      var nextAssets = Object.assign({}, assets);
      if (nextAssets.logoTintColor == null && style.textColor) nextAssets.logoTintColor = logoColor;
      logoDraw(
        ctx,
        rect,
        nextAssets,
        withAlpha(logoColor, clamp(Number(style.opacity) || 1, 0, 1) * 255),
        COLOR_TRANSPARENT,
        style.textAlign === 'Start' ? 'Start' : (style.textAlign === 'End' ? 'End' : 'Center'),
        'Inside'
      );
    } else if (type === 'colorchip') {
      var chip = element.content || {};
      var side = Math.min(rect.width(), rect.height());
      rect = new Rect(
        rect.centerX() - side / 2,
        rect.centerY() - side / 2,
        rect.centerX() + side / 2,
        rect.centerY() + side / 2
      );
      var chipColor = resolveCustomTemplateColor(
        colorToCss(chip.color || '#B7C9BF'),
        chip.colorSlotId,
        paletteColors,
        settings,
        renderStyle
      );
      var displayColor = withAlpha(chipColor, clamp(Number(style.opacity) || 1, 0, 1) * 255);
      var radius = Math.min(rect.width(), rect.height()) * clamp(Number(style.cornerRadius) || 0.12, 0.02, 0.5);
      if (style.chipShape === 'Circle') {
        ctx.beginPath();
        ctx.arc(rect.centerX(), rect.centerY(), Math.min(rect.width(), rect.height()) / 2, 0, Math.PI * 2);
        ctx.fillStyle = displayColor;
        ctx.fill();
      } else if (style.chipShape === 'Square') {
        fillRect(ctx, rect.left, rect.top, rect.right, rect.bottom, displayColor);
      } else {
        drawRoundRect(ctx, rect, radius, displayColor);
      }
      ctx.strokeStyle = withAlpha(readableContentColor(chipColor), 82);
      ctx.lineWidth = Math.max(1, Math.min(rect.width(), rect.height()) * 0.045);
      if (style.chipShape === 'Circle') {
        ctx.beginPath();
        ctx.arc(rect.centerX(), rect.centerY(), Math.min(rect.width(), rect.height()) / 2, 0, Math.PI * 2);
        ctx.stroke();
      } else {
        roundRectPath(ctx, rect.left, rect.top, rect.right, rect.bottom, style.chipShape === 'Square' ? 0 : radius);
        ctx.stroke();
      }
    }
  }

  function renderCustomTemplate(options, settings, inputAssets, renderStyle) {
    var image = options.image;
    var buildInfo = options.buildInfo || {};
    var template = options.customTemplate;
    var iw = imageWidth(image);
    var ih = imageHeight(image);
    if (iw <= 0 || ih <= 0) throw new Error('KeyxifRenderer: image has no size');

    var layout = calculateCustomTemplateLayout(template, iw, ih, options.maxLongSide);
    var canvas = document.createElement('canvas');
    canvas.width = layout.finalWidth;
    canvas.height = layout.finalHeight;
    var ctx = canvas.getContext('2d');
    ctx.textBaseline = 'alphabetic';
    var bounds = RectF(0, 0, canvas.width, canvas.height);
    var inputPalette = inputAssets.paletteColors || [];
    var paletteColors = inputPalette.map(function (color) { return colorToCss(color); });
    var frame = template.frame || {};
    var fill = frame.fill || {};
    var backgroundColor = resolveCustomTemplateColor(
      colorToCss(fill.color || '#F7F5F0'),
      fill.colorSlotId,
      paletteColors,
      settings,
      renderStyle
    );
    fillRect(ctx, 0, 0, canvas.width, canvas.height, backgroundColor);
    drawTemplatePhoto(ctx, image, layout.photoRect, 'FitCenter');

    var logoDisabled = !!buildInfo.logoDisabled;
    var resolvedLogo = resolveLogoForBackground(inputAssets, backgroundColor);
    var hasCustomLogo = !!buildInfo.customLogoImage;
    var logoVariants = inputAssets.logoVariants || {};
    var logoBitmap = logoDisabled ? null : (buildInfo.customLogoImage || resolvedLogo.image || null);
    var logoLabel = logoDisabled ? '' : String(inputAssets.logoLabel || '');
    var contentColor = resolveCustomTextColor(readableContentColor(backgroundColor), paletteColors, settings, renderStyle);
    var renderAssets = {
      logoBitmap: logoBitmap,
      sourcePhotoImage: image,
      sourceCacheKey: options.sourceCacheKey || null,
      whiteLogoImage: logoVariants.white || null,
      blackLogoImage: logoVariants.black || null,
      logoLabel: logoLabel,
      paletteColors: paletteColors,
      hasLogo: logoBitmap != null || isMeaningfulBuildText(logoLabel),
      cardBackgroundColor: backgroundColor,
      cardContentColor: contentColor,
      hasExplicitTextColor: !!(settings.showPaletteColors && renderStyle.usePaletteColorForText),
      logoTintColor: hasCustomLogo ? null : resolvedLogo.tintColor,
    };
    drawPhotoOverlayLogoIfNeeded(ctx, layout.photoRect, logoDisabled ? null : inputAssets.photoOverlayImage);

    var previousFontFamily = activeTemplateFontFamily;
    activeTemplateFontFamily = templateFontFamily(settings);
    try {
      (template.elements || [])
        .filter(function (element) { return !element.hidden && String(element.coordinateSpace || '').toLowerCase() !== 'internalcard'; })
        .sort(function (a, b) { return (a.zIndex || 0) - (b.zIndex || 0); })
        .forEach(function (element) {
          var space = String(element.coordinateSpace || '').toLowerCase();
          var container = space === 'photo' ? layout.photoRect : bounds;
          drawCustomElement(ctx, element, container, buildInfo, renderAssets, settings, paletteColors, renderStyle, contentColor);
        });

      (template.internalCards || [])
        .filter(function (card) { return !card.hidden; })
        .sort(function (a, b) { return (a.zIndex || 0) - (b.zIndex || 0); })
        .forEach(function (card) {
          var cardRect = customRect(layout.photoRect, Number(card.x) || 0, Number(card.y) || 0, Number(card.width) || 0.1, Number(card.height) || 0.1);
          drawCustomInternalCard(ctx, card, cardRect, paletteColors, settings, renderStyle);
          var style = card.style || {};
          var padding = clamp(Number(style.padding) || 0.035, 0, 0.45);
          var contentRect = RectF(
            cardRect.left + cardRect.width() * padding,
            cardRect.top + cardRect.height() * padding,
            cardRect.right - cardRect.width() * padding,
            cardRect.bottom - cardRect.height() * padding
          );
          ctx.save();
          ctx.beginPath();
          ctx.rect(cardRect.left, cardRect.top, cardRect.width(), cardRect.height());
          ctx.clip();
          var cardTextColor = readableContentColor(colorToCss(style.backgroundColor || '#ffffff'));
          (template.elements || [])
            .filter(function (element) {
              return !element.hidden && String(element.coordinateSpace || '').toLowerCase() === 'internalcard' && element.containerId === card.id;
            })
            .sort(function (a, b) { return (a.zIndex || 0) - (b.zIndex || 0); })
            .forEach(function (element) {
              drawCustomElement(ctx, element, contentRect, buildInfo, renderAssets, settings, paletteColors, renderStyle, cardTextColor);
            });
          ctx.restore();
        });
    } finally {
      activeTemplateFontFamily = previousFontFamily;
    }
    return canvas;
  }

  function render(options) {
    var image = options.image;
    var buildInfo = options.buildInfo || {};
    var templateId = options.template;
    var settings = normalizeSettings(options.settings);
    var inputAssets = options.assets || {};
    var renderStyle = options.renderStyle || {};

    if (options.customTemplate) {
      return renderCustomTemplate(options, settings, inputAssets, renderStyle);
    }

    var templateRenderer = renderersById[templateId];
    if (!templateRenderer) {
      throw new Error('KeyxifRenderer: unknown template "' + templateId + '"');
    }

    var iw = imageWidth(image);
    var ih = imageHeight(image);
    if (iw <= 0 || ih <= 0) {
      throw new Error('KeyxifRenderer: image has no size');
    }

    var layout = calculateRenderLayout(
      templateRenderer.layoutSpec(),
      iw,
      ih,
      options.maxLongSide
    );

    var canvas = document.createElement('canvas');
    canvas.width = layout.finalWidth;
    canvas.height = layout.finalHeight;
    var ctx = canvas.getContext('2d');
    ctx.textBaseline = 'alphabetic';
    var bounds = RectF(0, 0, canvas.width, canvas.height);

    var paletteColors = [];
    var inputPalette = inputAssets.paletteColors || [];
    for (var pi = 0; pi < inputPalette.length; pi++) {
      paletteColors.push(colorToCss(inputPalette[pi]));
    }
    var backgroundColor = cardBackgroundColor(
      templateRenderer.backgroundColor(),
      paletteColors,
      settings,
      renderStyle
    );
    var contentColor = readableContentColor(backgroundColor);
    if (settings.showPaletteColors && renderStyle.usePaletteColorForText) {
      var textColorIndex = clamp(Math.round(Number(renderStyle.paletteTextColorIndex) || 0), 0, 4);
      contentColor = renderStyle.customTextColor ? colorToCss(renderStyle.customTextColor) : (paletteColors[textColorIndex] || contentColor);
    }

    // canvas.drawColor(cardBackgroundColor)
    fillRect(ctx, 0, 0, canvas.width, canvas.height, backgroundColor);

    // Photo placement into photoBounds with clipping
    var photoBounds = layout.photoRect;
    drawTemplatePhoto(ctx, image, photoBounds, templateRenderer.photoPlacement());

    // Logo resolution (customLogoUri -> customLogoImage; preset -> tone variant)
    var logoDisabled = !!buildInfo.logoDisabled;
    var logoBitmap = null;
    var resolvedLogo = resolveLogoForBackground(inputAssets, backgroundColor);
    var hasCustomLogo = !!buildInfo.customLogoImage;
    var logoVariants = inputAssets.logoVariants || {};
    var white = logoVariants.white || null;
    var black = logoVariants.black || null;
    if (!logoDisabled) {
      logoBitmap = buildInfo.customLogoImage
        || resolvedLogo.image
        || null;
    }
    var logoLabel = logoDisabled ? '' : String(inputAssets.logoLabel || '');
    // RenderAssets.hasLogo default: logoBitmap != null || logoLabel.isMeaningfulBuildText()
    var hasLogo = logoBitmap != null || isMeaningfulBuildText(logoLabel);

    var renderAssets = {
      logoBitmap: logoBitmap,
      sourcePhotoImage: image,
      sourceCacheKey: options.sourceCacheKey || null,
      whiteLogoImage: white,
      blackLogoImage: black,
      logoLabel: logoLabel,
      paletteColors: paletteColors,
      hasLogo: hasLogo,
      cardBackgroundColor: backgroundColor,
      hasExplicitCardBackgroundColor: !!(settings.showPaletteColors && renderStyle.usePaletteColorForCardBackground),
      cardContentColor: contentColor,
      hasExplicitTextColor: !!(settings.showPaletteColors && renderStyle.usePaletteColorForText),
      logoTintColor: hasCustomLogo ? null : resolvedLogo.tintColor,
    };

    drawPhotoOverlayLogoIfNeeded(ctx, photoBounds, logoDisabled ? null : inputAssets.photoOverlayImage);
    var previousFontFamily = activeTemplateFontFamily;
    activeTemplateFontFamily = templateFontFamily(settings);
    try {
      templateRenderer.draw(ctx, bounds, buildInfo, renderAssets, settings);
    } finally {
      activeTemplateFontFamily = previousFontFamily;
    }
    return canvas;
  }

  /* ---------------------------------------------------------------------- */
  /* Public API                                                              */
  /* ---------------------------------------------------------------------- */

  window.KeyxifRenderer = {
    render: render,
    templates: templates,
    utils: {
      MIN_TEXT_SIZE_PX: MIN_TEXT_SIZE_PX,
      PALETTE_CHIP_SCALE: PALETTE_CHIP_SCALE,
      RectF: RectF,
      rgb: rgb,
      argb: argb,
      colorToCss: colorToCss,
      paint: paint,
      medium: medium,
      regular: regular,
      ellipsize: ellipsize,
      drawText: drawText,
      drawTextBaseline: drawTextBaseline,
      drawRoundRect: drawRoundRect,
      drawGradientScrim: drawGradientScrim,
      drawLabelValue: drawLabelValue,
      drawPaletteChips: drawPaletteChips,
      drawPaletteChipsInRect: drawPaletteChipsInRect,
      visiblePaletteColors: visiblePaletteColors,
      scaled: scaled,
      nicknameText: nicknameText,
      buildParts: buildParts,
      toDisplayRows: toDisplayRows,
      meaningfulBuildTextOrNull: meaningfulBuildTextOrNull,
      isMeaningfulBuildText: isMeaningfulBuildText,
      displayNicknameOrNull: displayNicknameOrNull,
      displayTitleOrNull: displayTitleOrNull,
      logoDraw: logoDraw,
      logoFitCenter: logoFitCenter,
      logoFitInside: logoFitInside,
      logoFitHeight: logoFitHeight,
      drawTemplatePhoto: drawTemplatePhoto,
      calculateRenderLayout: calculateRenderLayout,
      calculateCustomTemplateLayout: calculateCustomTemplateLayout,
    },
  };
})();
