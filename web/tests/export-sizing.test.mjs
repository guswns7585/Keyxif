import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const context = {
  window: {},
  document: {},
  navigator: { language: 'ko' },
};
vm.runInNewContext(readFileSync(new URL('../store.js', import.meta.url), 'utf8'), context);
const target = context.window.KeyxifStore.helpers.exportTargetDimensions;

test('large exports stay within the pixel budget without cropping', () => {
  const size = target(8000, 6000, 16384, 12000000);
  assert.ok(size.width * size.height <= 12000000);
  assert.ok(Math.abs(size.width / size.height - 4 / 3) < 0.001);
  assert.equal(size.reducedForMemory, true);
});

test('ordinary photos retain their original dimensions', () => {
  const size = target(2400, 1600, 16384, 12000000);
  assert.equal(size.width, 2400);
  assert.equal(size.height, 1600);
  assert.equal(size.reducedForMemory, false);
});

test('requested long-side limit is respected without a memory warning', () => {
  const size = target(8000, 6000, 2048, 12000000);
  assert.equal(size.width, 2048);
  assert.equal(size.height, 1536);
  assert.equal(size.reducedForMemory, false);
});
