import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const context = { window: {} };
for (const file of ['../data/assets.js', '../data/logo-assets.generated.js', '../data/presets.js', '../search.js']) {
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

test('new contrast logo sets are registered', () => {
  for (const id of ['alps', 'cherry', 'kmg', 'otd']) {
    const logo = context.window.KEYXIF_PRESETS.logos.find((item) => item.id === id);
    assert.ok(logo, `${id} logo is missing`);
    assert.equal(logo.whiteDrawable, `logo_${id}_w`);
    assert.equal(logo.blackDrawable, `logo_${id}_b`);
  }
});

test('typed housing text can resolve a new logo', () => {
  const logo = context.window.KeyxifSearch.logoForBuildInfo({ housing: 'OTD 356CL' });
  assert.equal(logo?.id, 'otd');
});
