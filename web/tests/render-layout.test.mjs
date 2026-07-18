import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const context = { window: {} };
vm.runInNewContext(readFileSync(new URL('../data/renderers.js', import.meta.url), 'utf8'), context);

const renderer = context.window.KeyxifRenderer;
const layoutFor = (templateId, width, height, maxLongSide = 4096) => {
  const template = renderer.templates.find((item) => item.id === templateId);
  return renderer.utils.calculateRenderLayout(template.layoutSpec, width, height, maxLongSide);
};

function assertFullPhoto(layout, expectedAspectRatio) {
  const actual = layout.photoRect.width() / layout.photoRect.height();
  assert.ok(Math.abs(actual - expectedAspectRatio) < 0.002);
}

test('bottom card expands the final canvas below the full photo', () => {
  const layout = layoutFor('BottomSpecBar', 1600, 1000);
  assert.equal(layout.finalWidth, 1600);
  assert.ok(layout.finalHeight > 1000);
  assert.equal(layout.cardRect.top, layout.photoRect.bottom);
  assertFullPhoto(layout, 1.6);
});

test('poster margin and side rail expand outside the full photo', () => {
  const poster = layoutFor('PosterMargin', 1600, 1000);
  assert.ok(poster.finalWidth > 1600);
  assert.ok(poster.finalHeight > 1000);
  assert.ok(poster.photoRect.left > 0);
  assertFullPhoto(poster, 1.6);

  const side = layoutFor('SideSpecRail', 1600, 1000);
  assert.ok(side.finalWidth > 1600);
  assert.equal(side.finalHeight, 1000);
  assert.equal(side.cardRect.left, side.photoRect.right);
  assertFullPhoto(side, 1.6);
});

test('final long-side limit scales the composite and Plain Export stays unchanged', () => {
  const limited = layoutFor('MuseumMat', 2400, 1600, 1000);
  assert.equal(Math.max(limited.finalWidth, limited.finalHeight), 1000);
  assertFullPhoto(limited, 1.5);

  const plain = layoutFor('PlainExport', 1600, 1000);
  assert.equal(plain.finalWidth, 1600);
  assert.equal(plain.finalHeight, 1000);
  assert.equal(plain.photoRect.width(), 1600);
  assert.equal(plain.photoRect.height(), 1000);
});

test('external layouts keep portrait and square photos uncropped', () => {
  for (const template of ['BottomSpecBar', 'PosterMargin', 'SideSpecRail']) {
    assertFullPhoto(layoutFor(template, 1000, 1600), 1000 / 1600);
    assertFullPhoto(layoutFor(template, 1200, 1200), 1);
  }
});

test('custom template layout expands around a contained photo without cropping', () => {
  const customTemplate = {
    frame: { logicalWidth: 1, logicalHeight: 1.25, aspectRatio: 0.8, fill: { color: '#f7f5f0' } },
    photoPlacement: { x: 0.08, y: 0.08, width: 0.84, height: 0.68, fitMode: 'Contain' },
  };
  const layout = renderer.utils.calculateCustomTemplateLayout(customTemplate, 1600, 1000, 4096);
  assert.ok(layout.finalWidth > 1600);
  assert.ok(layout.finalHeight > 1000);
  assert.equal(Math.round(layout.photoRect.width()), 1600);
  assert.equal(Math.round(layout.photoRect.height()), 1000);

  const limited = renderer.utils.calculateCustomTemplateLayout(customTemplate, 2400, 1600, 1000);
  assert.equal(Math.max(limited.finalWidth, limited.finalHeight), 1000);
  assert.ok(Math.abs(limited.photoRect.width() / limited.photoRect.height() - 2400 / 1600) < 0.002);
});
