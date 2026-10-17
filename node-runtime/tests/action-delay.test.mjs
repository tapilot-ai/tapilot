import test from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { dirname, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const require = createRequire(import.meta.url);
const core = dirname(require.resolve('@midscene/core'));
const { AndroidAccessibilityDevice } = await import('@tapilot/android-accessibility');

for (const format of ['es', 'lib']) test(`real ${format} SDK keeps post-action readiness without generic pre-action sleep`, async t => {
  const module = format === 'es'
    ? await import(pathToFileURL(resolve(core, '../es/agent/task-builder.mjs')).href)
    : require(resolve(core, 'agent/task-builder.js'));
  const calls = [], delays = [];
  const device = new AndroidAccessibilityDevice({
    execute: async (action, frameId) => calls.push({ action, frameId }),
    capture: async () => ({ dataUrl: 'data:image/png;base64,AAAA', geometry: { frameId: 'observed-frame' } }),
  });
  await device.screenshotBase64();
  const realTimeout = globalThis.setTimeout;
  t.mock.method(globalThis, 'setTimeout', (fn, ms, ...args) => {
    delays.push(ms);
    return realTimeout(fn, 0, ...args);
  });
  const controller = new AbortController();
  const builder = new module.TaskBuilder({ interfaceInstance: device, actionSpace: device.actionSpace() });
  const { tasks } = await builder.build([{ type: 'AndroidBackButton', param: {} }], undefined, undefined, { abortSignal: controller.signal });
  const task = tasks.at(-1);
  await task.executor({ task: { timing: {} }, uiContext: { shrunkShotToLogicalRatio: 1 } });
  assert.deepEqual(calls, [{ action: { type: 'back' }, frameId: 'observed-frame' }]);
  assert.ok(delays.includes(300), 'post-action wait must remain enabled');
  assert.ok(!delays.includes(200), 'generic pre-action delay must be absent');
  controller.abort();
  await assert.rejects(task.executor({ task: { timing: {} }, uiContext: { shrunkShotToLogicalRatio: 1 } }), { name: 'AbortError' });
  assert.equal(calls.length, 1, 'cancelled action must not run');
});
