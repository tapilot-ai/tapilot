import test from 'node:test';
import assert from 'node:assert/strict';
import { panelRequest } from '../ui/panel-request.mjs';

test('a stalled panel mutation times out once and does not replay or consume a late result', async () => {
  let expire, calls = 0, finish, cleared = false;
  const pending = new Promise(resolve => { finish = resolve; });
  const result = panelRequest(() => { calls++; return pending; }, { name: 'tapilot_preview_control' }, {
    setTimer(fn, ms) { assert.equal(ms, 45000); expire = fn; return 1; },
    clearTimer(id) { assert.equal(id, 1); cleared = true; },
  });
  await Promise.resolve();
  expire();
  await assert.rejects(result, { code: 'panel_timeout' });
  finish({ structuredContent: { state: 'live' } });
  await Promise.resolve();
  assert.equal(calls, 1);
  assert.equal(cleared, true);
});

test('successful requests and failures clear their deadline', async () => {
  for (const fail of [false, true]) {
    let cleared = false;
    const result = panelRequest(() => { if (fail) throw new Error('offline'); return 42; }, {}, {
      setTimer() { return 9; }, clearTimer(id) { assert.equal(id, 9); cleared = true; },
    });
    if (fail) await assert.rejects(result, /offline/);
    else assert.equal(await result, 42);
    assert.equal(cleared, true);
  }
});
