import assert from 'node:assert/strict';
import test from 'node:test';
import { withAndroidAiActDefaults } from './execution-policy.mjs';

test('reuses visual planning coordinates without disabling SDK locate fallback', () => {
  assert.deepEqual(withAndroidAiActDefaults(undefined), { effort: 'fast' });
  assert.deepEqual(withAndroidAiActDefaults({ cacheable: false }), {
    cacheable: false,
    effort: 'fast',
  });
});

test('keeps an explicit deep-locate override for advanced callers', () => {
  assert.equal(withAndroidAiActDefaults({ deepLocate: false }).deepLocate, false);
  assert.equal(withAndroidAiActDefaults({ deepLocate: true }).deepLocate, true);
  assert.equal(withAndroidAiActDefaults({ effort: 'deepThink' }).effort, 'deepThink');
});

test('deep planning also rechecks visual targets while preserving explicit locate choices', () => {
  assert.equal(withAndroidAiActDefaults({ effort: 'deepThink' }).deepLocate, true);
  assert.equal(withAndroidAiActDefaults({ effort: 'deepThink', deepLocate: false }).deepLocate, false);
  assert.equal(withAndroidAiActDefaults({ effort: 'fast' }).deepLocate, undefined);
  assert.equal(withAndroidAiActDefaults({ effort: 'balance' }).deepLocate, undefined);
});
