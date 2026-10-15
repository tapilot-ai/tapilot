import assert from 'node:assert/strict';
import test from 'node:test';
import { withAndroidAiActDefaults } from './execution-policy.mjs';

test('uses a dedicated locate pass for Android actions by default', () => {
  assert.deepEqual(withAndroidAiActDefaults(undefined), { deepLocate: true });
  assert.deepEqual(withAndroidAiActDefaults({ cacheable: false }), {
    cacheable: false,
    deepLocate: true,
  });
});

test('keeps an explicit deep-locate override for advanced callers', () => {
  assert.equal(withAndroidAiActDefaults({ deepLocate: false }).deepLocate, false);
});
