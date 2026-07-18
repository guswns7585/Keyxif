/* =============================================================================
   CustomTemplateEditorScreen — 단계 3: 배경 프레임과 사진 배치
   ============================================================================= */
(function () {
  'use strict';
  var activePointerSession = null;
  var workspaceGestureActive = false;
  var previewViewport = { scale: 1, x: 0, y: 0 };

  function h(html) {
    var t = document.createElement('template');
    t.innerHTML = html.trim();
    return t.content.firstElementChild;
  }

  function esc(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }
  function clampValue(value, min, max) {
    var number = Number(value);
    if (!isFinite(number)) number = min;
    return Math.min(Math.max(number, min), max);
  }
  function marginsFromPhoto(photo) {
    var width = clampValue(photo && photo.width, 0.01, 1);
    var height = clampValue(photo && photo.height, 0.01, 1);
    var x = clampValue(photo && photo.x, 0, 1 - width);
    var y = clampValue(photo && photo.y, 0, 1 - height);
    return {
      left: clampValue(x / width, 0, 3),
      right: clampValue((1 - x - width) / width, 0, 3),
      top: clampValue(y / height, 0, 3),
      bottom: clampValue((1 - y - height) / height, 0, 3),
    };
  }

  function elementBounds(element) {
    return { x: element.x, y: element.y, width: element.width, height: element.height };
  }
  function photoBounds(photo) {
    return { x: photo.x, y: photo.y, width: photo.width, height: photo.height };
  }
  function cardBounds(card) {
    return { x: card.x, y: card.y, width: card.width, height: card.height };
  }

  function toScreen(bounds, canvas) {
    return {
      left: bounds.x * canvas.width,
      top: bounds.y * canvas.height,
      width: bounds.width * canvas.width,
      height: bounds.height * canvas.height,
      right: (bounds.x + bounds.width) * canvas.width,
      bottom: (bounds.y + bounds.height) * canvas.height,
    };
  }

  function toCardScreen(card, photo, canvas) {
    var p = toScreen(photoBounds(photo), canvas);
    return {
      left: p.left + card.x * p.width,
      top: p.top + card.y * p.height,
      width: card.width * p.width,
      height: card.height * p.height,
      right: p.left + (card.x + card.width) * p.width,
      bottom: p.top + (card.y + card.height) * p.height,
    };
  }

  function guideContainerScreen(guide, photo, cards, canvas) {
    if (!guide || guide.coordinateSpace === 'frame') {
      return toScreen({ x: 0, y: 0, width: 1, height: 1 }, canvas);
    }
    if (guide.coordinateSpace === 'photo') {
      return toScreen(photoBounds(photo), canvas);
    }
    var card = (cards || []).find(function (item) { return item.id === guide.containerId; });
    if (!card) return toScreen(photoBounds(photo), canvas);
    var c = toCardScreen(card, photo, canvas);
    var padding = Math.min(Math.max((card.style && card.style.padding) || 0, 0), 0.3);
    var px = c.width * padding;
    var py = c.height * padding;
    return {
      left: c.left + px,
      top: c.top + py,
      width: Math.max(1, c.width - px * 2),
      height: Math.max(1, c.height - py * 2),
      right: c.left + c.width - px,
      bottom: c.top + c.height - py,
    };
  }

  function toElementScreen(element, photo, canvas, cards) {
    var bounds = elementBounds(element);
    if (element.coordinateSpace === 'internalCard') {
      var card = (cards || []).find(function (item) { return item.id === element.containerId; });
      if (card) {
        var c = toCardScreen(card, photo, canvas);
        var padding = Math.min(Math.max((card.style && card.style.padding) || 0, 0), 0.3);
        var px = c.width * padding;
        var py = c.height * padding;
        var cw = Math.max(1, c.width - px * 2);
        var ch = Math.max(1, c.height - py * 2);
        return {
          left: c.left + px + bounds.x * cw,
          top: c.top + py + bounds.y * ch,
          width: bounds.width * cw,
          height: bounds.height * ch,
          right: c.left + px + (bounds.x + bounds.width) * cw,
          bottom: c.top + py + (bounds.y + bounds.height) * ch,
        };
      }
    }
    if (element.coordinateSpace === 'photo') {
      var p = toScreen(photoBounds(photo), canvas);
      return {
        left: p.left + bounds.x * p.width,
        top: p.top + bounds.y * p.height,
        width: bounds.width * p.width,
        height: bounds.height * p.height,
        right: p.left + (bounds.x + bounds.width) * p.width,
        bottom: p.top + (bounds.y + bounds.height) * p.height,
      };
    }
    return toScreen(bounds, canvas);
  }

  function elementContainerScreen(element, photo, canvas, cards) {
    if (element.coordinateSpace === 'internalCard') return guideContainerScreen({ coordinateSpace: 'internalCard', containerId: element.containerId }, photo, cards, canvas);
    if (element.coordinateSpace === 'photo') return toScreen(photoBounds(photo), canvas);
    return { left: 0, top: 0, width: canvas.width, height: canvas.height, right: canvas.width, bottom: canvas.height };
  }

  function squareColorChipBounds(bounds, container) {
    var side = Math.max(bounds.width * container.width, bounds.height * container.height);
    var w = Math.min(side / Math.max(1, container.width), 1);
    var h = Math.min(side / Math.max(1, container.height), 1);
    return {
      x: clampValue(bounds.x, 0, Math.max(0, 1 - w)),
      y: clampValue(bounds.y, 0, Math.max(0, 1 - h)),
      width: w,
      height: h,
    };
  }

  function elementDelta(dx, dy, element, photo, canvas, cards) {
    if (element && element.coordinateSpace === 'internalCard') {
      var card = (cards || []).find(function (item) { return item.id === element.containerId; });
      if (card) {
        var c = toCardScreen(card, photo, canvas);
        var padding = Math.min(Math.max((card.style && card.style.padding) || 0, 0), 0.3);
        return { dx: dx / Math.max(1, c.width - c.width * padding * 2), dy: dy / Math.max(1, c.height - c.height * padding * 2) };
      }
    }
    if (element && element.coordinateSpace === 'photo') {
      var p = toScreen(photoBounds(photo), canvas);
      return { dx: dx / p.width, dy: dy / p.height };
    }
    return toNormalizedDelta(dx, dy, canvas);
  }

  function clientToCanvas(event, canvas) {
    var rect = canvas.getBoundingClientRect();
    return {
      x: (event.clientX - rect.left) * (canvas.width / rect.width),
      y: (event.clientY - rect.top) * (canvas.height / rect.height),
    };
  }
  function clientToCanvasFromRect(event, rect, width, height) {
    return {
      x: (event.clientX - rect.left) * (width / Math.max(1, rect.width)),
      y: (event.clientY - rect.top) * (height / Math.max(1, rect.height)),
    };
  }

  function toNormalizedDelta(dx, dy, canvas) {
    return { dx: dx / canvas.width, dy: dy / canvas.height };
  }

  function boundsFromScreenRect(rect, container, safePadding) {
    var safe = Number(safePadding) || 0;
    var w = clamp(rect.width / Math.max(1, container.width), 0.01, Math.max(0.01, 1 - safe * 2));
    var hgt = clamp(rect.height / Math.max(1, container.height), 0.01, Math.max(0.01, 1 - safe * 2));
    return {
      x: clamp((rect.left - container.left) / Math.max(1, container.width), safe, Math.max(safe, 1 - safe - w)),
      y: clamp((rect.top - container.top) / Math.max(1, container.height), safe, Math.max(safe, 1 - safe - hgt)),
      width: w,
      height: hgt,
    };
  }

  function elementPlacementForScreenRect(rect, editorState, canvas) {
    var photo = editorState.draft.photoPlacement;
    var cards = editorState.draft.internalCards || [];
    var center = { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 };
    var cardTarget = cards
      .filter(function (card) { return !card.hidden; })
      .sort(function (a, b) { return (b.zIndex || 0) - (a.zIndex || 0); })
      .map(function (card) {
        var cardScreen = toCardScreen(card, photo, canvas);
        var padding = Math.min(Math.max((card.style && card.style.padding) || 0, 0), 0.3);
        var paddingX = cardScreen.width * padding;
        var paddingY = cardScreen.height * padding;
        return {
          card: card,
          container: {
            left: cardScreen.left + paddingX,
            top: cardScreen.top + paddingY,
            width: Math.max(1, cardScreen.width - paddingX * 2),
            height: Math.max(1, cardScreen.height - paddingY * 2),
          },
        };
      })
      .find(function (item) {
        return center.x >= item.container.left && center.x <= item.container.left + item.container.width &&
          center.y >= item.container.top && center.y <= item.container.top + item.container.height;
      });
    if (cardTarget) {
      return {
        coordinateSpace: 'internalCard',
        containerId: cardTarget.card.id,
        bounds: boundsFromScreenRect(rect, cardTarget.container, 0.018),
      };
    }
    var photoScreen = toScreen(photoBounds(photo), canvas);
    if (center.x >= photoScreen.left && center.x <= photoScreen.right && center.y >= photoScreen.top && center.y <= photoScreen.bottom) {
      return {
        coordinateSpace: 'photo',
        containerId: 'photo',
        bounds: boundsFromScreenRect(rect, photoScreen, 0.018),
      };
    }
    return {
      coordinateSpace: 'frame',
      containerId: 'frame',
      bounds: boundsFromScreenRect(rect, { left: 0, top: 0, width: canvas.width, height: canvas.height }, 0.018),
    };
  }

  function moveBounds(bounds, dx, dy) {
    return {
      x: Math.min(Math.max(bounds.x + dx, 0), 1 - bounds.width),
      y: Math.min(Math.max(bounds.y + dy, 0), 1 - bounds.height),
      width: bounds.width,
      height: bounds.height,
    };
  }

  function resizeBounds(bounds, handle, dx, dy) {
    var min = 0.04;
    var left = bounds.x;
    var top = bounds.y;
    var right = bounds.x + bounds.width;
    var bottom = bounds.y + bounds.height;
    if (handle === 'tl') {
      left = Math.min(Math.max(left + dx, 0), right - min);
      top = Math.min(Math.max(top + dy, 0), bottom - min);
    } else if (handle === 'tr') {
      right = Math.min(Math.max(right + dx, left + min), 1);
      top = Math.min(Math.max(top + dy, 0), bottom - min);
    } else if (handle === 'br') {
      right = Math.min(Math.max(right + dx, left + min), 1);
      bottom = Math.min(Math.max(bottom + dy, top + min), 1);
    } else if (handle === 'bl') {
      left = Math.min(Math.max(left + dx, 0), right - min);
      bottom = Math.min(Math.max(bottom + dy, top + min), 1);
    }
    return { x: left, y: top, width: right - left, height: bottom - top };
  }
  function containPhotoBounds(bounds, photoAspectRatio, frameAspectRatio) {
    var photoRatio = Number(photoAspectRatio) > 0 ? Number(photoAspectRatio) : 1;
    var frameRatio = Number(frameAspectRatio) > 0 ? Number(frameAspectRatio) : 1;
    var normalizedAspect = photoRatio / frameRatio;
    var next = Object.assign({}, bounds);
    if (next.width / next.height > normalizedAspect) {
      var width = next.height * normalizedAspect;
      next.x += (next.width - width) / 2;
      next.width = width;
    } else {
      var height = next.width / normalizedAspect;
      next.y += (next.height - height) / 2;
      next.height = height;
    }
    next.width = Math.min(Math.max(next.width, 0.01), 1);
    next.height = Math.min(Math.max(next.height, 0.01), 1);
    next.x = Math.min(Math.max(next.x, 0), 1 - next.width);
    next.y = Math.min(Math.max(next.y, 0), 1 - next.height);
    return next;
  }
  function resizePhotoBounds(bounds, handle, dx, dy, photoAspectRatio, frameAspectRatio) {
    return containPhotoBounds(resizeBounds(bounds, handle, dx, dy), photoAspectRatio, frameAspectRatio);
  }
  function resizeFrameAspectRatio(width, height, handle, dx, dy) {
    var dw = (handle === 'tl' || handle === 'bl') ? -dx : dx;
    var dh = (handle === 'tl' || handle === 'tr') ? -dy : dy;
    return { width: Math.max(width + dw, 0.65), height: Math.max(height + dh, 0.65) };
  }
  function outsideContentAreas(photo) {
    var safe = Math.min(Math.max(photo.safePadding || 0.035, 0), 0.2);
    var left = Math.max(photo.x - safe, 0);
    var top = Math.max(photo.y - safe, 0);
    var right = Math.min(photo.x + photo.width + safe, 1);
    var bottom = Math.min(photo.y + photo.height + safe, 1);
    return [
      { x: 0, y: 0, width: 1, height: top },
      { x: 0, y: bottom, width: 1, height: 1 - bottom },
      { x: 0, y: top, width: left, height: bottom - top },
      { x: right, y: top, width: 1 - right, height: bottom - top },
    ].filter(function (r) { return r.width > 0.001 && r.height > 0.001; });
  }

  function hitTestElement(elements, point, photo, canvas, cards) {
    return elements.slice().sort(function (a, b) { return (b.zIndex || 0) - (a.zIndex || 0); }).find(function (element) {
      if (element.hidden || element.locked) return false;
      var b = toElementScreen(element, photo, canvas, cards);
      return point.x >= b.left && point.x <= b.right && point.y >= b.top && point.y <= b.bottom;
    }) || null;
  }

  function hitTestHandle(element, point, photo, canvas, cards) {
    var b = toElementScreen(element, photo, canvas, cards);
    var r = 18;
    var handles = [
      ['tl', b.left, b.top], ['tr', b.right, b.top],
      ['br', b.right, b.bottom], ['bl', b.left, b.bottom],
    ];
    for (var i = 0; i < handles.length; i++) {
      if (Math.abs(point.x - handles[i][1]) <= r && Math.abs(point.y - handles[i][2]) <= r) {
        return handles[i][0];
      }
    }
    return null;
  }
  function hitTestBoundsHandle(bounds, point, canvas) {
    var b = toScreen(bounds, canvas);
    var r = 16;
    var handles = [
      ['tl', b.left, b.top], ['tr', b.right, b.top],
      ['br', b.right, b.bottom], ['bl', b.left, b.bottom],
    ];
    for (var i = 0; i < handles.length; i++) {
      if (Math.abs(point.x - handles[i][1]) <= r && Math.abs(point.y - handles[i][2]) <= r) return handles[i][0];
    }
    return null;
  }
  function hitTestFrameHandle(point, canvas) {
    var r = 26;
    var handles = [
      ['tl', 0, 0], ['tr', canvas.width, 0],
      ['br', canvas.width, canvas.height], ['bl', 0, canvas.height],
    ];
    for (var i = 0; i < handles.length; i++) {
      if (Math.abs(point.x - handles[i][1]) <= r && Math.abs(point.y - handles[i][2]) <= r) return handles[i][0];
    }
    return null;
  }
  function hitTestCard(cards, point, photo, canvas) {
    return (cards || []).slice().sort(function (a, b) { return (b.zIndex || 0) - (a.zIndex || 0); }).find(function (card) {
      if (card.hidden || card.locked) return false;
      var b = toCardScreen(card, photo, canvas);
      return point.x >= b.left && point.x <= b.right && point.y >= b.top && point.y <= b.bottom;
    }) || null;
  }
  function hitTestCardHandle(card, point, photo, canvas) {
    var b = toCardScreen(card, photo, canvas);
    return hitTestBoundsHandle({ x: b.left / canvas.width, y: b.top / canvas.height, width: b.width / canvas.width, height: b.height / canvas.height }, point, canvas);
  }
  function isNearFrameEdge(point, canvas) {
    var r = 18;
    return point.x <= r || point.y <= r || canvas.width - point.x <= r || canvas.height - point.y <= r;
  }

  function buildValue(info, field) {
    if (!info) return String(field || '').toUpperCase();
    if (field === 'Board') return info.housing || 'BOARD';
    if (field === 'Switch') return info.switchName || 'SWITCH';
    if (field === 'Plate') return info.plate || 'PLATE';
    if (field === 'Mount') return info.mount || 'MOUNT';
    if (field === 'Nickname') return info.nickname || 'NICKNAME';
    return '';
  }

  function elementText(element, buildInfo) {
    var content = element.content || {};
    var value = content.type === 'buildField'
      ? buildValue(buildInfo, content.field)
      : (content.text || '');
    return element.style && element.style.uppercase ? String(value).toUpperCase() : value;
  }

  function cssColor(value, fallback) {
    if (window.KeyxifStore && window.KeyxifStore.helpers && window.KeyxifStore.helpers.colorToCss) {
      return window.KeyxifStore.helpers.colorToCss(value || fallback);
    }
    return typeof value === 'string' ? value : fallback;
  }

  function logoUrl(buildInfo, helpers) {
    if (!buildInfo || buildInfo.logoDisabled) return null;
    if (buildInfo.customLogoUri && helpers && helpers.getObjectURL) {
      return helpers.getObjectURL(buildInfo.customLogoUri);
    }
    var preset = window.KeyxifSearch && window.KeyxifSearch.logoForBuildInfo
      ? window.KeyxifSearch.logoForBuildInfo(buildInfo)
      : null;
    var key = preset && (preset.blackDrawable || preset.drawable || preset.whiteDrawable);
    return key && window.KEYXIF_ASSETS ? window.KEYXIF_ASSETS[key] : null;
  }

  function renderElementNodeInto(parent, element, buildInfo, logoSrc, bounds, canvas, selected, paletteColors, settings) {
    var style = element.style || {};
    var node = h('<div class="custom-template-element-node' + (selected ? ' selected' : '') + '"></div>');
    node.style.left = bounds.left;
    node.style.top = bounds.top;
    node.style.width = bounds.width;
    node.style.height = bounds.height;
    node.style.opacity = style.opacity == null ? '1' : String(style.opacity);
    if (element.type === 'text') {
      node.className += ' text-element';
      node.style.color = style.textColor || '#111111';
      var previewTextScale = Math.min(Math.max((settings && settings.textScale) || 1, 0.9), 1.55);
      var boxMin = Math.min(Number(bounds.pixelWidth) || canvas.width * 0.1, Number(bounds.pixelHeight) || canvas.height * 0.1);
      node.style.fontSize = Math.max(8.5, boxMin * Math.min(Math.max(Number(style.fontSize) || 0.045, 0.01), 0.4) * 2.2 * previewTextScale) + 'px';
      node.style.fontWeight = style.fontWeight === 'bold' ? '800' : (style.fontWeight === 'medium' ? '650' : '500');
      node.style.textAlign = style.textAlign === 'center' ? 'center' : (style.textAlign === 'end' ? 'right' : 'left');
      node.style.lineHeight = String(style.lineHeight || 1.12);
      node.textContent = elementText(element, buildInfo);
    } else if (element.type === 'logo') {
      node.className += ' logo-element';
      if (logoSrc) {
        var img = document.createElement('img');
        img.src = logoSrc;
        img.alt = '';
        node.appendChild(img);
      } else {
        node.textContent = 'LOGO';
      }
    } else if (element.type === 'colorChip') {
      node.className += ' chip-element';
      var chip = h('<span></span>');
      chip.style.background = cssColor((paletteColors && paletteColors[0]) || (element.content && element.content.color), '#B7C9BF');
      chip.style.borderRadius = style.chipShape === 'circle' ? '999px' : (style.chipShape === 'square' ? '0' : '999px');
      node.appendChild(chip);
    }
    parent.appendChild(node);
  }

  function renderElementNodes(wrap, elements, editorState, buildInfo, logoSrc, canvas, paletteColors, settings) {
    var photo = editorState.draft.photoPlacement;
    elements.forEach(function (element) {
      if (element.hidden || element.coordinateSpace === 'internalCard') return;
      var b = toElementScreen(element, photo, canvas, editorState.draft.internalCards || []);
      var selected = editorState.selectedTarget === 'Element' && editorState.selectedElementId === element.id;
      renderElementNodeInto(wrap, element, buildInfo, logoSrc, {
        left: (b.left / canvas.width * 100) + '%',
        top: (b.top / canvas.height * 100) + '%',
        width: (b.width / canvas.width * 100) + '%',
        height: (b.height / canvas.height * 100) + '%',
        pixelWidth: b.width,
        pixelHeight: b.height,
      }, canvas, selected, paletteColors, settings);
    });
  }

  function renderCardNodes(wrap, editorState, buildInfo, logoSrc, canvas, paletteColors, settings) {
    var cards = (editorState.draft.internalCards || []).slice().sort(function (a, b) { return (a.zIndex || 0) - (b.zIndex || 0); });
    var elements = editorState.draft.elements || [];
    var photo = editorState.draft.photoPlacement;
    var cardWarnings = editorState.cardSpaceWarnings || [];
    cards.forEach(function (card) {
      if (card.hidden) return;
      var b = toCardScreen(card, photo, canvas);
      var style = card.style || {};
      var selected = editorState.selectedTarget === 'Card' && editorState.selectedCardId === card.id;
      var warning = cardWarnings.find(function (item) { return item.cardId === card.id; });
      var classes = 'custom-template-card-node' +
        (selected ? ' selected' : '') +
        (warning ? ' warning ' + (warning.severity === 'Blocking' ? 'blocking' : 'caution') : '');
      var node = h('<div class="' + classes + '"></div>');
      node.style.left = (b.left / canvas.width * 100) + '%';
      node.style.top = (b.top / canvas.height * 100) + '%';
      node.style.width = (b.width / canvas.width * 100) + '%';
      node.style.height = (b.height / canvas.height * 100) + '%';
      node.style.background = style.backgroundColor || '#FFFFFF';
      node.style.opacity = style.opacity == null ? '0.9' : String(style.opacity);
      node.style.borderRadius = ((style.radius || 0.04) * 100) + '%';
      if (style.borderEnabled) {
        node.style.border = Math.max(1, (style.borderWidth || 0.002) * canvas.width) + 'px solid ' + (style.borderColor || '#FFFFFF');
      }
      elements.filter(function (element) {
        return element.coordinateSpace === 'internalCard' && element.containerId === card.id && !element.hidden;
      }).sort(function (a, b2) { return (a.zIndex || 0) - (b2.zIndex || 0); }).forEach(function (element) {
        var padding = Math.min(Math.max((style.padding || 0), 0), 0.3);
        renderElementNodeInto(node, element, buildInfo, logoSrc, {
          left: (padding * 100 + element.x * (1 - padding * 2) * 100) + '%',
          top: (padding * 100 + element.y * (1 - padding * 2) * 100) + '%',
          width: (element.width * (1 - padding * 2) * 100) + '%',
          height: (element.height * (1 - padding * 2) * 100) + '%',
          pixelWidth: b.width * (1 - padding * 2) * element.width,
          pixelHeight: b.height * (1 - padding * 2) * element.height,
        }, canvas, editorState.selectedTarget === 'Element' && editorState.selectedElementId === element.id, paletteColors, settings);
      });
      if (warning) {
        node.appendChild(h(
          '<div class="custom-template-card-warning-badge" title="' +
          esc((warning.messages || []).join(' / ')) +
          '">공간 부족</div>'
        ));
      }
      wrap.appendChild(node);
    });
  }

  function renderPreview(editorState, helpers, actions, settings) {
    var draft = editorState.draft;
    var frame = draft.frame;
    var photo = draft.photoPlacement;
    var selectedPhoto = window.KeyxifStore.selectedPhoto();
    var photoUrl = selectedPhoto && helpers && helpers.getObjectURL ? helpers.getObjectURL(selectedPhoto.uri) : null;
    var elements = (draft.elements || []).slice().sort(function (a, b) { return (a.zIndex || 0) - (b.zIndex || 0); });
    var cards = (draft.internalCards || []).slice().sort(function (a, b) { return (a.zIndex || 0) - (b.zIndex || 0); });
    var ratio = frame.aspectRatio || 0.8;
    var bg = frame.fill && frame.fill.color ? frame.fill.color : '#F7F5F0';
    var wrap = h('<div class="custom-template-preview-wrap"></div>');
    var stage = h('<div class="custom-template-frame-stage"></div>');
    var canvas = document.createElement('canvas');
    var buildInfo = selectedPhoto && selectedPhoto.buildInfo ? selectedPhoto.buildInfo : {};
    var paletteColors = selectedPhoto && selectedPhoto.analysisResult && selectedPhoto.analysisResult.paletteColors
      ? selectedPhoto.analysisResult.paletteColors
      : [];
    var logoSrc = logoUrl(buildInfo, helpers);
    canvas.width = 720;
    canvas.height = Math.round(canvas.width / ratio);
    canvas.className = 'custom-template-preview-canvas';
    stage.style.aspectRatio = String(ratio);
    var photoWidthRatio = Math.max(Number(photo.width) || 1, 0.04);
    var photoAspect = Math.max(Number(photo.aspectRatio) || ratio || 1, 0.05);
    var visualPhotoWidth = photoAspect < 1 ? Math.max(0.5, 0.82 * photoAspect) : 0.82;
    var visualWidth = clampValue(visualPhotoWidth / photoWidthRatio, 0.42, 5);
    stage.style.width = (visualWidth * 100) + '%';
    stage.style.transformOrigin = 'center center';
    stage.style.transform = 'translate(' + previewViewport.x + 'px, ' + previewViewport.y + 'px) scale(' + previewViewport.scale + ')';
    var ctx = canvas.getContext('2d');
    var photoNode = null;
    ctx.fillStyle = bg;
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    outsideContentAreas(photo).forEach(function (area) {
      var a = toScreen(area, canvas);
      ctx.fillStyle = 'rgba(83,107,96,0.06)';
      if (ctx.roundRect) ctx.roundRect(a.left, a.top, a.width, a.height, 6);
      else ctx.rect(a.left, a.top, a.width, a.height);
      ctx.fill();
    });

    var x = photo.x * canvas.width;
    var y = photo.y * canvas.height;
    var w = photo.width * canvas.width;
    var hgt = photo.height * canvas.height;
    var r = 18;
    ctx.fillStyle = '#E2E5E3';
    ctx.beginPath();
    if (ctx.roundRect) ctx.roundRect(x, y, w, hgt, r);
    else ctx.rect(x, y, w, hgt);
    ctx.fill();
    if (photoUrl) {
      var img = new Image();
      img.onload = function () {
        actions.updateCustomTemplatePhotoAspectRatio(img.naturalWidth / img.naturalHeight);
      };
      img.src = photoUrl;
      photoNode = h('<img class="custom-template-photo" alt="">');
      photoNode.src = photoUrl;
      photoNode.style.left = (photo.x * 100) + '%';
      photoNode.style.top = (photo.y * 100) + '%';
      photoNode.style.width = (photo.width * 100) + '%';
      photoNode.style.height = (photo.height * 100) + '%';
      photoNode.style.borderColor = editorState.selectedTarget === 'Photo' ? '#536B60' : 'rgba(83,107,96,0.72)';
    }
    ctx.strokeStyle = 'rgba(83, 107, 96, 0.72)';
    ctx.lineWidth = 3;
    ctx.setLineDash([14, 10]);
    ctx.beginPath();
    if (ctx.roundRect) ctx.roundRect(x, y, w, hgt, r);
    else ctx.rect(x, y, w, hgt);
    ctx.stroke();
    ctx.setLineDash([]);
    ctx.strokeStyle = 'rgba(83, 107, 96, 0.38)';
    ctx.lineWidth = 4;
    ctx.lineCap = 'round';
    ctx.beginPath();
    ctx.moveTo(x + w * 0.14, y + hgt * 0.5);
    ctx.lineTo(x + w * 0.86, y + hgt * 0.5);
    ctx.stroke();
    cards.forEach(function (card) {
      if (card.hidden) return;
      var cb = toCardScreen(card, photo, canvas);
      var selectedCard = editorState.selectedTarget === 'Card' && editorState.selectedCardId === card.id;
      var warning = (editorState.cardSpaceWarnings || []).find(function (item) { return item.cardId === card.id; });
      ctx.lineWidth = selectedCard || warning ? 1.6 : 0.9;
      ctx.strokeStyle = warning
        ? (warning.severity === 'Blocking' ? '#E5484D' : '#F59E0B')
        : (selectedCard ? '#536B60' : 'rgba(23,31,29,0.55)');
      ctx.beginPath();
      if (ctx.roundRect) ctx.roundRect(cb.left, cb.top, cb.width, cb.height, 8);
      else ctx.rect(cb.left, cb.top, cb.width, cb.height);
      ctx.stroke();
      if (selectedCard) {
        [[cb.left, cb.top], [cb.right, cb.top], [cb.right, cb.bottom], [cb.left, cb.bottom]].forEach(function (p) {
          ctx.beginPath(); ctx.arc(p[0], p[1], 8, 0, Math.PI * 2); ctx.fillStyle = '#536B60'; ctx.fill();
          ctx.beginPath(); ctx.arc(p[0], p[1], 3.6, 0, Math.PI * 2); ctx.fillStyle = '#fff'; ctx.fill();
        });
      }
    });
    elements.forEach(function (element) {
      if (element.hidden) return;
      var b = toElementScreen(element, photo, canvas, cards);
      var selected = editorState.selectedTarget === 'Element' && editorState.selectedElementId === element.id;
      ctx.fillStyle = 'rgba(183, 201, 191, 0.95)';
      ctx.beginPath();
      if (ctx.roundRect) ctx.roundRect(b.left, b.top, b.width, b.height, 8);
      else ctx.rect(b.left, b.top, b.width, b.height);
      ctx.fill();
      ctx.lineWidth = selected ? 1.6 : 0.9;
      ctx.strokeStyle = selected ? '#536B60' : 'rgba(23, 31, 29, 0.55)';
      ctx.stroke();
      if (selected) {
        [[b.left, b.top], [b.right, b.top], [b.right, b.bottom], [b.left, b.bottom]].forEach(function (p) {
          ctx.beginPath(); ctx.arc(p[0], p[1], 8, 0, Math.PI * 2); ctx.fillStyle = '#536B60'; ctx.fill();
          ctx.beginPath(); ctx.arc(p[0], p[1], 3.6, 0, Math.PI * 2); ctx.fillStyle = '#fff'; ctx.fill();
        });
      }
    });
    (editorState.snapGuides || []).forEach(function (guide) {
      var gb = guideContainerScreen(guide, photo, cards, canvas);
      var pos = Math.min(Math.max(Number(guide.position) || 0, 0), 1);
      ctx.save();
      ctx.strokeStyle = 'rgba(83,107,96,0.76)';
      ctx.lineWidth = 1;
      ctx.setLineDash([6, 6]);
      ctx.beginPath();
      if (guide.orientation === 'Vertical') {
        var gx = gb.left + gb.width * pos;
        ctx.moveTo(gx, gb.top);
        ctx.lineTo(gx, gb.bottom);
      } else {
        var gy = gb.top + gb.height * pos;
        ctx.moveTo(gb.left, gy);
        ctx.lineTo(gb.right, gy);
      }
      ctx.stroke();
      ctx.restore();
    });
    var photoSelected = editorState.selectedTarget === 'Photo';
    var frameSelected = editorState.selectedTarget === 'Frame' || editorState.activeTab === 'Frame';
    var pb = toScreen(photoBounds(photo), canvas);
    ctx.lineWidth = photoSelected ? 1.6 : 1;
    ctx.strokeStyle = photoSelected ? '#536B60' : 'rgba(83,107,96,0.72)';
    ctx.setLineDash([9, 7]);
    ctx.beginPath();
    if (ctx.roundRect) ctx.roundRect(pb.left, pb.top, pb.width, pb.height, 18);
    else ctx.rect(pb.left, pb.top, pb.width, pb.height);
    ctx.stroke();
    ctx.setLineDash([]);
    if (photoSelected) drawHandles(ctx, pb);
    if (frameSelected) {
      var fb = toScreen({ x: 0, y: 0, width: 1, height: 1 }, canvas);
      ctx.lineWidth = 1.4;
      ctx.strokeStyle = '#536B60';
      ctx.strokeRect(fb.left, fb.top, fb.width, fb.height);
    }
    stage.appendChild(canvas);
    if (photoNode) stage.appendChild(photoNode);
    renderCardNodes(stage, editorState, buildInfo, logoSrc, canvas, paletteColors, settings);
    renderElementNodes(stage, elements, editorState, buildInfo, logoSrc, canvas, paletteColors, settings);
    if (frameSelected) renderFrameHandleNodes(stage);
    var hitLayer = h('<div class="custom-template-hit-layer"></div>');
    attachPointerHandlers(hitLayer, canvas, editorState, window.KeyxifStore.actions);
    stage.appendChild(hitLayer);
    wrap.appendChild(stage);
    attachWorkspaceViewportHandlers(wrap, stage);
    return wrap;
  }

  function applyPreviewViewport(stage) {
    previewViewport.scale = clampValue(previewViewport.scale, 0.7, 4);
    previewViewport.x = clampValue(previewViewport.x, -900, 900);
    previewViewport.y = clampValue(previewViewport.y, -900, 900);
    stage.style.transform = 'translate(' + previewViewport.x + 'px, ' + previewViewport.y + 'px) scale(' + previewViewport.scale + ')';
  }

  function attachWorkspaceViewportHandlers(wrap, stage) {
    wrap.addEventListener('wheel', function (event) {
      if (!event.ctrlKey && !event.metaKey) return;
      event.preventDefault();
      var factor = event.deltaY < 0 ? 1.08 : 0.92;
      previewViewport.scale *= factor;
      applyPreviewViewport(stage);
    }, { passive: false });
    var touches = new Map();
    var lastGesture = null;
    wrap.addEventListener('pointerdown', function (event) {
      touches.set(event.pointerId, { x: event.clientX, y: event.clientY });
      if (touches.size >= 2) {
        wrap.setPointerCapture(event.pointerId);
        workspaceGestureActive = true;
        if (activePointerSession && window.KeyxifStore && window.KeyxifStore.actions) {
          activePointerSession = null;
          window.KeyxifStore.actions.finishCustomTemplateInteraction();
        }
        lastGesture = currentGesture(touches);
      }
    });
    wrap.addEventListener('pointermove', function (event) {
      if (!touches.has(event.pointerId)) return;
      touches.set(event.pointerId, { x: event.clientX, y: event.clientY });
      if (touches.size < 2 || !lastGesture) return;
      event.preventDefault();
      var next = currentGesture(touches);
      previewViewport.x += next.cx - lastGesture.cx;
      previewViewport.y += next.cy - lastGesture.cy;
      previewViewport.scale *= next.distance / Math.max(1, lastGesture.distance);
      lastGesture = next;
      applyPreviewViewport(stage);
    }, { passive: false });
    function finish(event) {
      touches.delete(event.pointerId);
      lastGesture = touches.size >= 2 ? currentGesture(touches) : null;
      workspaceGestureActive = touches.size >= 2;
    }
    wrap.addEventListener('pointerup', finish);
    wrap.addEventListener('pointercancel', finish);
  }

  function currentGesture(points) {
    var values = Array.from(points.values()).slice(0, 2);
    var a = values[0];
    var b = values[1] || values[0];
    var dx = b.x - a.x;
    var dy = b.y - a.y;
    return {
      cx: (a.x + b.x) / 2,
      cy: (a.y + b.y) / 2,
      distance: Math.sqrt(dx * dx + dy * dy),
    };
  }

  function drawHandles(ctx, bounds) {
    var radius = 5;
    [[bounds.left, bounds.top], [bounds.right, bounds.top], [bounds.right, bounds.bottom], [bounds.left, bounds.bottom]].forEach(function (p) {
      ctx.beginPath(); ctx.arc(p[0], p[1], radius, 0, Math.PI * 2); ctx.fillStyle = '#536B60'; ctx.fill();
      ctx.beginPath(); ctx.arc(p[0], p[1], radius * 0.42, 0, Math.PI * 2); ctx.fillStyle = '#fff'; ctx.fill();
    });
  }

  function renderFrameHandleNodes(stage) {
    ['tl', 'tr', 'br', 'bl'].forEach(function (key) {
      stage.appendChild(h('<span class="custom-template-frame-handle ' + key + '"></span>'));
    });
  }

  function attachPointerHandlers(targetNode, canvas, editorState, actions) {
    var active = null;
    targetNode.addEventListener('pointerdown', function (event) {
      if (workspaceGestureActive) return;
      if (activePointerSession) return;
      var point = clientToCanvas(event, canvas);
      var photo = editorState.draft.photoPlacement;
      var cards = editorState.draft.internalCards || [];
      var frameHandle = editorState.activeTab === 'Frame' || editorState.selectedTarget === 'Frame'
        ? hitTestFrameHandle(point, canvas)
        : null;
      var photoHandle = editorState.selectedTarget === 'Photo' ? hitTestBoundsHandle(photoBounds(photo), point, canvas) : null;
      var selected = (editorState.draft.elements || []).find(function (element) {
        return element.id === editorState.selectedElementId;
      });
      var selectedCard = cards.find(function (card) {
        return card.id === editorState.selectedCardId;
      });
      var elementHandle = selected ? hitTestHandle(selected, point, photo, canvas, cards) : null;
      var cardHandle = editorState.selectedTarget === 'Card' && selectedCard
        ? hitTestCardHandle(selectedCard, point, photo, canvas)
        : null;
      var elementTarget = elementHandle ? selected : hitTestElement(editorState.draft.elements || [], point, photo, canvas, cards);
      var cardTarget = cardHandle ? selectedCard : hitTestCard(cards, point, photo, canvas);
      var p = toScreen(photoBounds(photo), canvas);
      var photoHit = point.x >= p.left && point.x <= p.right && point.y >= p.top && point.y <= p.bottom;
      var target = null;
      if (frameHandle) target = { kind: 'Frame', handle: frameHandle };
      else if (photoHandle) target = { kind: 'Photo', handle: photoHandle };
      else if (elementTarget) target = { kind: 'Element', id: elementTarget.id, handle: elementHandle };
      else if (cardTarget) target = { kind: 'Card', id: cardTarget.id, handle: cardHandle };
      else if (photoHit) target = { kind: 'Photo', handle: null };
      else if (isNearFrameEdge(point, canvas)) target = { kind: 'Frame', handle: null };
      if (!target) {
        actions.selectCustomTemplateElement(null);
        actions.selectCustomTemplateCard(null);
        actions.selectCustomTemplateTarget('Photo');
        return;
      }
      event.preventDefault();
      targetNode.setPointerCapture(event.pointerId);
      if (target.kind === 'Element') actions.selectCustomTemplateElement(target.id);
      else if (target.kind === 'Card') actions.selectCustomTemplateCard(target.id);
      else actions.selectCustomTemplateTarget(target.kind);
      actions.beginCustomTemplateInteraction();
      active = {
        pointerId: event.pointerId,
        rect: canvas.getBoundingClientRect(),
        canvasWidth: canvas.width,
        canvasHeight: canvas.height,
        kind: target.kind,
        id: target.id || null,
        handle: target.handle,
        bounds: target.kind === 'Element'
          ? elementBounds(elementTarget)
          : (target.kind === 'Card'
            ? cardBounds(cardTarget)
            : (target.kind === 'Photo' ? photoBounds(photo) : null)),
        element: target.kind === 'Element' ? elementTarget : null,
        logicalWidth: editorState.draft.frame.logicalWidth,
        logicalHeight: editorState.draft.frame.logicalHeight,
        margins: marginsFromPhoto(photo),
        last: point,
      };
      activePointerSession = active;
      window.addEventListener('pointermove', moveActive, { passive: false });
      window.addEventListener('pointerup', finish, { passive: false });
      window.addEventListener('pointercancel', finish, { passive: false });
    });
    function moveActive(event) {
      active = activePointerSession;
      if (workspaceGestureActive) return;
      if (!active || active.pointerId !== event.pointerId) return;
      event.preventDefault();
      var canvasBox = { width: active.canvasWidth, height: active.canvasHeight };
      var point = clientToCanvasFromRect(event, active.rect, active.canvasWidth, active.canvasHeight);
      var d = toNormalizedDelta(point.x - active.last.x, point.y - active.last.y, canvasBox);
      if (active.kind === 'Element') {
        d = elementDelta(point.x - active.last.x, point.y - active.last.y, active.element, editorState.draft.photoPlacement, canvasBox, editorState.draft.internalCards || []);
        active.bounds = active.handle
          ? resizeBounds(active.bounds, active.handle, d.dx, d.dy)
          : moveBounds(active.bounds, d.dx, d.dy);
        if (active.handle && active.element && active.element.type === 'colorChip') {
          active.bounds = squareColorChipBounds(
            active.bounds,
            elementContainerScreen(active.element, editorState.draft.photoPlacement, canvasBox, editorState.draft.internalCards || [])
          );
        }
        if (active.handle) {
          actions.updateCustomTemplateElementBounds(active.id, active.bounds);
        } else {
          var movedElement = Object.assign({}, active.element, active.bounds);
          actions.updateCustomTemplateElementPlacement(
            active.id,
            elementPlacementForScreenRect(toElementScreen(movedElement, editorState.draft.photoPlacement, canvasBox, editorState.draft.internalCards || []), editorState, canvasBox)
          );
        }
      } else if (active.kind === 'Card') {
        var photoScreen = toScreen(photoBounds(editorState.draft.photoPlacement), canvas);
        d = {
          dx: (point.x - active.last.x) / Math.max(1, photoScreen.width),
          dy: (point.y - active.last.y) / Math.max(1, photoScreen.height),
        };
        active.bounds = active.handle
          ? resizeBounds(active.bounds, active.handle, d.dx, d.dy)
          : moveBounds(active.bounds, d.dx, d.dy);
        actions.updateCustomTemplateCardBounds(active.id, active.bounds);
      } else if (active.kind === 'Photo') {
        active.bounds = active.handle
          ? resizePhotoBounds(
            active.bounds,
            active.handle,
            d.dx,
            d.dy,
            editorState.draft.photoPlacement.aspectRatio,
            editorState.draft.frame.aspectRatio
          )
          : moveBounds(active.bounds, d.dx, d.dy);
        actions.updateCustomTemplatePhotoBounds(active.bounds);
      } else if (active.kind === 'Frame' && active.handle) {
        var framePhoto = toScreen(photoBounds(editorState.draft.photoPlacement), canvas);
        var logicalDelta = {
          dx: (point.x - active.last.x) / Math.max(1, framePhoto.width),
          dy: (point.y - active.last.y) / Math.max(1, framePhoto.height),
        };
        if (active.handle === 'tl' || active.handle === 'bl') {
          active.margins.left = clampValue(active.margins.left - logicalDelta.dx, 0, 3);
        }
        if (active.handle === 'tr' || active.handle === 'br') {
          active.margins.right = clampValue(active.margins.right + logicalDelta.dx, 0, 3);
        }
        if (active.handle === 'tl' || active.handle === 'tr') {
          active.margins.top = clampValue(active.margins.top - logicalDelta.dy, 0, 3);
        }
        if (active.handle === 'bl' || active.handle === 'br') {
          active.margins.bottom = clampValue(active.margins.bottom + logicalDelta.dy, 0, 3);
        }
        actions.updateCustomTemplateOuterMargins(active.margins);
      }
      active.last = point;
    }
    function finish(event) {
      active = activePointerSession;
      if (!active || active.pointerId !== event.pointerId) return;
      event.preventDefault();
      active = null;
      activePointerSession = null;
      window.removeEventListener('pointermove', moveActive);
      window.removeEventListener('pointerup', finish);
      window.removeEventListener('pointercancel', finish);
      actions.finishCustomTemplateInteraction();
    }
  }

  function tabIcon(tab) {
    if (tab === 'Frame') return '<span class="editor-tab-icon frame"></span>';
    if (tab === 'Element') return '<span class="editor-tab-icon element"></span>';
    return '<span class="editor-tab-icon card"></span>';
  }

  function fieldName(field) {
    return { Board: '보드', Switch: '스위치', Plate: '보강', Mount: '마운트', Nickname: '닉네임' }[field] || field;
  }

  function renderElementTools(editorState, actions) {
    var root = h('<div class="element-tool-panel"></div>');
    var usedFields = {};
    (editorState.draft.elements || []).forEach(function (element) {
      if (!element.hidden && element.type === 'text' && element.content && element.content.type === 'buildField') {
        usedFields[element.content.field] = true;
      }
    });
    var targetLabel = editorState.selectedTarget === 'Card' && editorState.selectedCardId
      ? '선택한 카드 안에 추가'
      : (editorState.selectedTarget === 'Photo' ? '사진 안에 추가' : '프레임에 추가');
    root.appendChild(h('<div class="body-small muted">' + targetLabel + '</div>'));
    var row = h('<div class="row gap8 wrap"></div>');
    [
      ['로고', actions.addCustomTemplateLogoElement],
      ['색상칩', actions.addCustomTemplateColorChipElement],
    ].forEach(function (item) {
      var btn = h('<button class="btn btn-filled">' + esc(item[0]) + '</button>');
      btn.addEventListener('click', item[1]);
      row.appendChild(btn);
    });
    var duplicate = h('<button class="chip">복제</button>');
    duplicate.disabled = !editorState.selectedElementId;
    duplicate.addEventListener('click', actions.duplicateSelectedCustomTemplateElement);
    row.appendChild(duplicate);
    var deleteBtn = h('<button class="chip">선택 삭제</button>');
    deleteBtn.disabled = !editorState.selectedElementId;
    deleteBtn.addEventListener('click', actions.deleteSelectedCustomTemplateElement);
    row.appendChild(deleteBtn);
    root.appendChild(row);
    var fields = h('<div class="chip-row"></div>');
    ['Board', 'Switch', 'Plate', 'Mount', 'Nickname'].forEach(function (field) {
      var chip = h('<button class="chip">' + esc(fieldName(field)) + '</button>');
      chip.disabled = !!usedFields[field];
      chip.addEventListener('click', function () { actions.addCustomTemplateTextElement(field); });
      fields.appendChild(chip);
    });
    root.appendChild(fields);
    return root;
  }

  function selectedTargetInfo(editorState) {
    if (editorState.selectedTarget === 'Photo') {
      return { label: '사진 영역', bounds: photoBounds(editorState.draft.photoPlacement), canAlign: true };
    }
    if (editorState.selectedTarget === 'Card' && editorState.selectedCardId) {
      var card = (editorState.draft.internalCards || []).find(function (item) { return item.id === editorState.selectedCardId; });
      return card ? { label: '카드', bounds: cardBounds(card), canAlign: true } : null;
    }
    if (editorState.selectedTarget === 'Element' && editorState.selectedElementId) {
      var element = (editorState.draft.elements || []).find(function (item) { return item.id === editorState.selectedElementId; });
      return element ? { label: element.type || '요소', bounds: elementBounds(element), canAlign: true } : null;
    }
    if (editorState.selectedTarget === 'Frame') {
      return { label: '프레임', bounds: { x: 0, y: 0, width: 1, height: 1 }, canAlign: false };
    }
    return null;
  }

  function pct(value) {
    return Math.round(Number(value || 0) * 100) + '%';
  }

  function renderPropertyPanel(editorState, actions) {
    var info = selectedTargetInfo(editorState);
    var root = h('<div class="custom-template-property-panel"></div>');
    root.appendChild(h(
      '<div class="row between gap8">' +
      '<div class="label-large">' + esc(info ? info.label : '선택 없음') + '</div>' +
      '<div class="body-small muted">' + (info
        ? ('X ' + pct(info.bounds.x) + ' · Y ' + pct(info.bounds.y) + ' · W ' + pct(info.bounds.width) + ' · H ' + pct(info.bounds.height))
        : '캔버스에서 대상을 선택하세요') + '</div>' +
      '</div>'
    ));
    var actionRow = h('<div class="row gap8 wrap"></div>');
    var deleteBtn = h('<button class="btn btn-outlined h40">선택 삭제</button>');
    deleteBtn.disabled = !editorState.selectedElementId && !(editorState.selectedTarget === 'Card' && editorState.selectedCardId);
    deleteBtn.addEventListener('click', actions.deleteSelectedCustomTemplateElement);
    actionRow.appendChild(deleteBtn);
    root.appendChild(actionRow);
    root.appendChild(h('<div class="body-small muted keyboard-only-help">방향키로 0.6%, Shift+방향키로 2.5%씩 이동합니다.</div>'));
    return root;
  }

  function renderFrameTools(editorState, actions) {
    var root = h('<div class="frame-tool-panel"></div>');
    var margins = marginsFromPhoto(editorState.draft.photoPlacement || {});
    root.appendChild(h(
      '<div class="body-small muted">사진을 기준으로 바깥 카드 영역을 붙입니다. 프레임 모서리 핀을 드래그하거나 방향별 여백을 조절하세요.</div>'
    ));
    root.appendChild(h(
      '<div class="body-small muted">바깥 영역 L ' + Math.round(margins.left * 100) +
      ' · R ' + Math.round(margins.right * 100) +
      ' · T ' + Math.round(margins.top * 100) +
      ' · B ' + Math.round(margins.bottom * 100) + '</div>'
    ));
    var noOuter = h('<button class="btn btn-tonal">사진 외부 영역 없음</button>');
    noOuter.addEventListener('click', function () {
      actions.beginCustomTemplateInteraction();
      actions.updateCustomTemplateOuterMargins({ left: 0, right: 0, top: 0, bottom: 0 });
      actions.finishCustomTemplateInteraction();
    });
    root.appendChild(noOuter);
    return root;
  }

  function sliderControl(label, value, min, max, step, onInput) {
    var wrap = h(
      '<label class="custom-template-slider">' +
      '<span class="body-small muted">' + esc(label) + '</span>' +
      '<input type="range" min="' + min + '" max="' + max + '" step="' + step + '" value="' + value + '">' +
      '<span class="body-small custom-template-slider-value">' + Math.round(Number(value) * 100) + '</span>' +
      '</label>'
    );
    var input = wrap.querySelector('input');
    var out = wrap.querySelector('.custom-template-slider-value');
    input.addEventListener('input', function () {
      var next = Number(input.value);
      out.textContent = Math.round(next * 100);
      onInput(next);
    });
    return wrap;
  }

  function renderCardTools(editorState, actions, consts) {
    var root = h('<div class="card-tool-panel"></div>');
    root.appendChild(h('<div class="body-small muted">사진 안에 카드 추가</div>'));
    var addRow = h('<div class="row gap8 wrap"></div>');
    (consts.CUSTOM_TEMPLATE_CARD_STYLE_PRESETS || []).forEach(function (preset) {
      var label = (consts.CUSTOM_TEMPLATE_CARD_STYLE_NAME || {})[preset] || preset;
      var btn = h('<button class="btn btn-filled">' + esc(label) + ' 추가</button>');
      btn.addEventListener('click', function () { actions.addCustomTemplateInternalCard(preset); });
      addRow.appendChild(btn);
    });
    root.appendChild(addRow);

    var styleRow = h('<div class="chip-row"></div>');
    styleRow.appendChild(h('<span class="body-small muted">선택 카드 스타일</span>'));
    (consts.CUSTOM_TEMPLATE_CARD_STYLE_PRESETS || []).forEach(function (preset) {
      var label = (consts.CUSTOM_TEMPLATE_CARD_STYLE_NAME || {})[preset] || preset;
      var chip = h('<button class="chip">' + esc(label) + '</button>');
      chip.disabled = !editorState.selectedCardId;
      chip.addEventListener('click', function () { actions.applySelectedCustomTemplateCardStyle(preset); });
      styleRow.appendChild(chip);
    });
    root.appendChild(styleRow);
    var deleteCardBtn = h('<button class="btn btn-outlined h40">선택 카드 삭제</button>');
    deleteCardBtn.disabled = !(editorState.selectedTarget === 'Card' && editorState.selectedCardId);
    deleteCardBtn.addEventListener('click', actions.deleteSelectedCustomTemplateElement);
    root.appendChild(deleteCardBtn);
    var selectedCard = editorState.selectedCardId
      ? (editorState.draft.internalCards || []).find(function (card) { return card.id === editorState.selectedCardId; })
      : null;
    if (selectedCard) {
      var style = selectedCard.style || {};
      var custom = h('<div class="custom-card-style-panel"></div>');
      custom.appendChild(h('<div class="label-large">선택 카드 직접 조정</div>'));
      var colorRow = h('<div class="custom-card-color-row"></div>');
      var color = h('<input type="color" aria-label="카드 배경색" value="' + esc(style.backgroundColor || '#FFFFFF') + '">');
      var hex = h('<input class="text-input" aria-label="카드 배경 색상 코드" value="' + esc(style.backgroundColor || '#FFFFFF') + '" maxlength="7">');
      function applyColor(value) {
        actions.updateSelectedCustomTemplateCardStyle({ backgroundColor: value });
      }
      color.addEventListener('input', function () { hex.value = color.value.toUpperCase(); applyColor(color.value); });
      hex.addEventListener('change', function () { applyColor(hex.value); });
      colorRow.appendChild(color);
      colorRow.appendChild(hex);
      custom.appendChild(colorRow);
      custom.appendChild(sliderControl('투명도', style.opacity == null ? 0.9 : style.opacity, 0.05, 1, 0.01, function (value) {
        actions.updateSelectedCustomTemplateCardStyle({ opacity: value });
      }));
      custom.appendChild(sliderControl('모서리 radius', style.radius == null ? 0.04 : style.radius, 0, 0.18, 0.005, function (value) {
        actions.updateSelectedCustomTemplateCardStyle({ radius: value });
      }));
      custom.appendChild(sliderControl('내부 여백', style.padding == null ? 0.06 : style.padding, 0, 0.24, 0.005, function (value) {
        actions.updateSelectedCustomTemplateCardStyle({ padding: value });
      }));
      root.appendChild(custom);
    }
    return root;
  }

  function render(container, state, actions, helpers) {
    var editorState = state.customTemplateEditorState;
    if (!editorState) return false;
    var consts = window.KeyxifStore.consts;
    var root = h('<div class="screen custom-template-editor"></div>');
    root.appendChild(h(
      '<div class="editor-head">' +
      '<div class="col gap4">' +
      '<div class="title-large">커스텀 템플릿</div>' +
      '<div class="body-medium muted">프레임, 사진, 카드, 텍스트 요소를 선택해 직접 배치합니다.</div>' +
      (editorState.collisionWarning ? '<div class="body-small custom-template-warning">' + esc(editorState.collisionWarning) + '</div>' : '') +
      '</div>' +
      '<div class="row gap4">' +
      '<button class="icon-btn editor-undo" aria-label="되돌리기">↶</button>' +
      '<button class="icon-btn editor-redo" aria-label="다시 실행">↷</button>' +
      '<button class="btn btn-filled editor-close">목록</button>' +
      '</div>' +
      '</div>'
    ));
    root.querySelector('.editor-close').addEventListener('click', function () {
      if (!editorState.isDirty || window.confirm('저장하지 않은 변경사항이 있습니다. 목록으로 돌아갈까요?')) {
        actions.closeCustomTemplateEditor();
      }
    });
    var undo = root.querySelector('.editor-undo');
    var redo = root.querySelector('.editor-redo');
    undo.disabled = !editorState.undoStack.length;
    redo.disabled = !editorState.redoStack.length;
    undo.addEventListener('click', actions.undoCustomTemplateEdit);
    redo.addEventListener('click', actions.redoCustomTemplateEdit);
    root.addEventListener('keydown', function (event) {
      var target = event.target;
      if (target && ['INPUT', 'TEXTAREA', 'SELECT'].indexOf(target.tagName) >= 0) return;
      var step = event.shiftKey ? 0.025 : 0.006;
      if (event.key === 'Delete' || event.key === 'Backspace') {
        event.preventDefault();
        actions.deleteSelectedCustomTemplateElement();
      } else if (event.key === 'ArrowLeft') {
        event.preventDefault();
        actions.nudgeCustomTemplateSelection(-step, 0);
      } else if (event.key === 'ArrowRight') {
        event.preventDefault();
        actions.nudgeCustomTemplateSelection(step, 0);
      } else if (event.key === 'ArrowUp') {
        event.preventDefault();
        actions.nudgeCustomTemplateSelection(0, -step);
      } else if (event.key === 'ArrowDown') {
        event.preventDefault();
        actions.nudgeCustomTemplateSelection(0, step);
      }
    });
    root.tabIndex = 0;
    var saveRow = h(
      '<div class="custom-template-save-row">' +
      '<input class="text-input custom-template-name-input" type="text" aria-label="템플릿 이름" value="' + esc(editorState.draft.name || '새 커스텀 템플릿') + '">' +
      '<button class="btn btn-filled custom-template-save">저장</button>' +
      '</div>'
    );
    saveRow.querySelector('.custom-template-save').addEventListener('click', function () {
      actions.saveCustomTemplate(saveRow.querySelector('.custom-template-name-input').value);
    });
    root.appendChild(saveRow);
    var workspace = h('<div class="custom-template-workspace"></div>');
    workspace.appendChild(renderPreview(editorState, helpers, actions, state.settings || {}));
    var controls = h('<div class="custom-template-controls"></div>');
    controls.appendChild(renderPropertyPanel(editorState, actions));
    var tabs = h('<div class="custom-template-tabs"></div>');
    consts.CUSTOM_TEMPLATE_TABS.forEach(function (tab) {
      var selected = editorState.activeTab === tab;
      var btn = h(
        '<button class="editor-tab' + (selected ? ' selected' : '') + '">' +
        tabIcon(tab) +
        '<span>' + esc(consts.CUSTOM_TEMPLATE_TAB_NAME[tab] || tab) + '</span>' +
        '</button>'
      );
      btn.addEventListener('click', function () { actions.selectCustomTemplateEditorTab(tab); });
      tabs.appendChild(btn);
    });
    controls.appendChild(tabs);
    if (editorState.activeTab === 'Frame') {
      controls.appendChild(renderFrameTools(editorState, actions));
    }
    if (editorState.activeTab === 'Element') {
      controls.appendChild(renderElementTools(editorState, actions));
    }
    if (editorState.activeTab === 'Card') {
      controls.appendChild(renderCardTools(editorState, actions, consts));
    }
    var reset = h('<button class="btn btn-tonal custom-template-reset">템플릿 초기화</button>');
    reset.addEventListener('click', function () {
      if (window.confirm('현재 편집 중인 배치와 요소를 기본 상태로 초기화할까요?')) {
        actions.resetCustomTemplateDraft();
      }
    });
    controls.appendChild(reset);
    workspace.appendChild(controls);
    root.appendChild(workspace);
    container.appendChild(root);
    setTimeout(function () { root.focus(); }, 0);
    return true;
  }

  window.KeyxifCustomTemplateEditor = { render: render };
})();
