import test from 'node:test';
import assert from 'node:assert/strict';
import { withSettledScreenshots, nativeFingerprint } from './settled-capture.mjs';

function fixture(frameAt, native = false) {
  let clock = 0, actions = 0, captures = 0, fail = false;
  const records = [], points = [];
  const bridge = { getCapabilities() {}, getSize() {}, cancelSession() {}, completeSession() {},
    async capture() {
      if (fail) throw new Error('capture cancelled');
      clock += 200; captures++;
      const value = frameAt(clock, actions);
      return { dataUrl: 'fixture', geometry: { frameId: String(captures) }, value,
        ...(native ? { fingerprint: { algorithm: 'rgb-luma-64x96-v1', frameId: String(captures), data: Buffer.alloc(6144, value).toString('base64') } } : {}) };
    },
    async execute(a, id) { actions++; points.push({ a, id }); },
  };
  const observer = withSettledScreenshots(bridge, { now: () => clock, sleep: async ms => { clock += ms; },
    fingerprint: native ? nativeFingerprint : async frame => Buffer.alloc(64 * 96, frame.value), record: x => records.push(x) });
  return { observer, records, points, fail: () => { fail = true; }, count: () => ({ actions, captures, clock }) };
}

test('unchanged old frames wait for delayed navigation and return the latest stable geometry', async () => {
  const f = fixture((time, actions) => actions && time >= 1100 ? 200 : 0);
  const before = await f.observer.capture();
  await f.observer.execute({ type: 'tap' }, before.geometry.frameId);
  const result = await f.observer.capture();
  assert.equal(result.value, 200); assert.equal(result.geometry.frameId, String(f.count().captures));
  assert.equal(f.count().actions, 1); assert.ok(f.records[0].durationMs >= 1100);
  assert.equal(f.records[0].state, 'stable');
  const n = f.count().captures; await f.observer.capture(); assert.equal(f.count().captures, n + 1);
});

test('no visible change waits to budget without claiming success or replaying the action', async () => {
  const f = fixture(() => 0); await f.observer.capture(); await f.observer.execute({ type: 'tap' });
  await f.observer.capture(); assert.equal(f.records[0].state, 'unchanged');
  assert.ok(f.records[0].durationMs >= 1500); assert.equal(f.count().actions, 1);
});

test('continuously moving frames have bounded observation and are returned as timeout', async () => {
  const f = fixture(time => time % 600 < 300 ? 0 : 255);
  await f.observer.capture(); await f.observer.execute({ type: 'scroll' }); await f.observer.capture();
  assert.equal(f.records[0].state, 'timeout'); assert.ok(f.records[0].durationMs <= 1800);
  assert.equal(f.count().actions, 1);
});

test('capture failure never returns old pixels or repeats a completed action', async () => {
  const f = fixture(() => 0); await f.observer.capture(); await f.observer.execute({ type: 'tap' }); f.fail();
  await assert.rejects(f.observer.capture(), /cancelled/); assert.equal(f.count().actions, 1);
});


test('temporary press feedback returning to the old page cannot settle before delayed navigation', async () => {
  const f = fixture((time, actions) => !actions ? 0 : time < 600 ? 100 : time < 1100 ? 0 : 200);
  await f.observer.capture(); await f.observer.execute({ type: 'tap' });
  const result = await f.observer.capture();
  assert.equal(result.value, 200); assert.ok(f.records[0].durationMs >= 1100);
  assert.equal(f.count().actions, 1);
});

for (const [name, frameAt, state, minimum] of [
  ['delayed navigation', (time, actions) => actions && time >= 1100 ? 200 : 0, 'stable', 1100],
  ['unchanged page', () => 0, 'unchanged', 1500],
  ['press feedback before delayed navigation', (time, actions) => !actions ? 0 : time < 600 ? 100 : time < 1100 ? 0 : 200, 'stable', 1100],
]) test(`native fingerprints preserve ${name} without decoding full images`, async () => {
  const f = fixture(frameAt, true);
  const before = await f.observer.capture();
  await f.observer.execute({ type: 'tap' }, before.geometry.frameId);
  const result = await f.observer.capture();
  assert.equal(f.records[0].state, state);
  assert.ok(f.records[0].durationMs >= minimum);
  assert.equal(result.geometry.frameId, String(f.count().captures));
  assert.equal(f.count().actions, 1);
});

test('native fingerprint cannot silently substitute stale or malformed evidence', () => {
  const frame = { geometry: { frameId: 'new' }, fingerprint: { algorithm: 'rgb-luma-64x96-v1', frameId: 'new', data: Buffer.alloc(6144, 42).toString('base64') } };
  assert.equal(nativeFingerprint(frame).length, 6144);
  for (const hint of [undefined, {...frame.fingerprint,frameId:'old'}, {...frame.fingerprint,algorithm:'unknown'}, {...frame.fingerprint,data:'!'.repeat(8192)}, {...frame.fingerprint,data:'AAAA'}]) {
    assert.throws(() => nativeFingerprint({...frame,fingerprint:hint}), /invalid native/);
  }
});
