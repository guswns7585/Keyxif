import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const context = { window: {} };
for (const file of ['../data/assets.js', '../data/logo-assets.generated.js', '../data/presets.js']) {
  vm.runInNewContext(readFileSync(new URL(file, import.meta.url), 'utf8'), context);
}

test('Grit logo is registered with both contrast variants', () => {
  const grit = context.window.KEYXIF_PRESETS.logos.find((logo) => logo.id === 'grit');
  assert.ok(grit);
  assert.equal(grit.name, 'Grit');
  assert.equal(grit.whiteDrawable, 'logo_grit_w');
  assert.equal(grit.blackDrawable, 'logo_grit_b');
});

test('every registered logo drawable resolves to a web asset', () => {
  const assets = context.window.KEYXIF_ASSETS;
  for (const logo of context.window.KEYXIF_PRESETS.logos) {
    for (const key of [logo.drawable, logo.whiteDrawable, logo.blackDrawable, logo.photoOverlayDrawable]) {
      if (key) assert.ok(assets[key], `${logo.id} references missing asset ${key}`);
    }
  }
});
