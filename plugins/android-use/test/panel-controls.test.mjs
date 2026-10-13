import test from 'node:test';
import assert from 'node:assert/strict';
import { Preview, connectionState } from '../server/preview.mjs';
import { control } from '../server/panel-controls.mjs';
import { imagePoint } from '../ui/geometry.mjs';
const png = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=';
function device() {
  const calls = [];
  return { calls, connected: true, open: async () => {}, request: async (method, params) => {
    calls.push({ method, params });
    if (method === 'device.preview') return { dataUrl: png, capturedAt: Date.now() };
    if (method === 'device.capture') return { geometry: { frameId: 'native-frame', imageWidth: 1, imageHeight: 1 } };
    return {};
  } };
}
async function ready(phone) { const preview = new Preview(); preview.observe(); const frame = (await preview.frame(phone)).structuredContent; return { preview, frame }; }
test('letterboxing and scaling map visible image pixels, excluding margins', () => {
  const rect = { left: 10, top: 20, width: 400, height: 400 };
  assert.deepEqual(imagePoint(rect, 100, 200, 210, 220), { x: 50, y: 100 });
  assert.equal(imagePoint(rect, 100, 200, 109, 220), null);
  assert.equal(imagePoint(rect, 100, 200, 310, 220), null);
  assert.deepEqual(imagePoint(rect, 200, 100, 210, 220), { x: 100, y: 50 });
  assert.equal(imagePoint(rect, 200, 100, 210, 119), null);
  assert.equal(imagePoint({ ...rect, width: 0 }, 200, 100, 210, 220), null);
});
test('manual tap captures once then executes once using the native frame, consuming the panel frame', async () => {
  const phone = device(), { preview, frame } = await ready(phone);
  await preview.control(phone, { frame_id: frame.frame_id, action: 'tap', x: .5, y: .5 });
  assert.deepEqual(phone.calls.map(c => c.method), ['device.preview', 'device.capture', 'device.execute']);
  assert.deepEqual(phone.calls[2].params, { frameId: 'native-frame', action: { type: 'tap', x: .5, y: .5 } });
  await assert.rejects(preview.control(phone, { frame_id: frame.frame_id, action: 'home' }), /expired/);
  assert.equal(phone.calls.length, 3);
});
test('old, invalidated, paused and expired panel frames cannot operate the phone', async () => {
  const phone = device(), { preview, frame } = await ready(phone);
  await preview.frame(phone);
  await assert.rejects(preview.control(phone, { frame_id: frame.frame_id, action: 'home' }), /expired/);
  await preview.frame(phone); preview.lastFrame.receivedAt -= 11000;
  await assert.rejects(preview.control(phone, { frame_id: preview.lastFrame.id, action: 'home' }), /expired/);
  await preview.frame(phone); preview.invalidate();
  await assert.rejects(preview.control(phone, { frame_id: frame.frame_id, action: 'home' }), /expired/);
  await preview.frame(phone); preview.pause();
  await assert.rejects(preview.control(phone, { frame_id: frame.frame_id, action: 'home' }), /paused/);
  assert.ok(phone.calls.every(c => c.method === 'device.preview'));
});
test('invalid coordinates and malformed actions fail before capture', async () => {
  const phone = device();
  const frame = { id: 'f', width: 100, height: 200, receivedAt: performance.now() };
  for (const args of [{ action: 'tap', x: -1, y: 0 }, { action: 'tap', x: 100, y: 0 }, { action: 'tap', x: 0, y: 200 }, { action: 'shell' }, { action: 'home', unexpected: true }]) {
    await assert.rejects(control(phone, frame, { frame_id: 'f', ...args }));
  }
  assert.equal(phone.calls.length, 0);
});
test('orientation changes stop input after capture; disconnected controls never reconnect', async () => {
  const phone = device();
  const frame = { id: 'f', width: 100, height: 200, receivedAt: performance.now() };
  await assert.rejects(control(phone, frame, { frame_id: 'f', action: 'home' }), /orientation/);
  assert.deepEqual(phone.calls.map(c => c.method), ['device.capture']);
  phone.connected = false;
  phone.open = () => { throw new Error('Must not reconnect'); };
  await assert.rejects(control(phone, frame, { frame_id: 'f', action: 'home' }), /disconnected/);
  assert.equal(phone.calls.length, 1);
});
test('uncertain execution consumes its frame and is never replayed', async () => {
  const phone = device(), original = phone.request;
  let attempts = 0;
  phone.request = async (method, params) => { if (method === 'device.execute') { attempts++; throw new Error('Phone timed out'); } return original(method, params); };
  const { preview, frame } = await ready(phone);
  const args = { frame_id: frame.frame_id, action: 'input', text: '无线输入测试', replace: true };
  await assert.rejects(preview.control(phone, args), /timed out/);
  await assert.rejects(preview.control(phone, args), /expired/);
  assert.equal(attempts, 1);
});
test('swipe and Chinese input preserve user intent; text input never submits', async () => {
  const phone = device(), { preview } = await ready(phone);
  await preview.control(phone, { frame_id: preview.lastFrame.id, action: 'swipe', start_x: .5, start_y: .8, end_x: .5, end_y: .2, duration_ms: 500 });
  await preview.frame(phone);
  await preview.control(phone, { frame_id: preview.lastFrame.id, action: 'input', text: '无线输入测试', replace: false });
  const actions = phone.calls.filter(c => c.method === 'device.execute').map(c => c.params.action);
  assert.deepEqual(actions, [{ type: 'swipe', startX: .5, startY: .8, endX: .5, endY: .2, duration: 500 }, { type: 'input', text: '无线输入测试', replace: false, autoDismissKeyboard: false }]);
});
test('occupied, unpaired and network failures remain distinguishable', async () => {
  for (const [message, state] of [['Another task is already registered', 'occupied'], ['Phone certificate changed', 'pairing_required'], ['Not paired', 'pairing_required'], ['Pair this computer first', 'pairing_required'], ['Phone timed out', 'unavailable']]) {
    assert.equal(connectionState(new Error(message)), state);
    const preview = new Preview(); preview.observe();
    const result = await preview.frame({ open: async () => { throw new Error(message); } });
    assert.equal(result.structuredContent.state, state);
    assert.equal(result.structuredContent.dataUrl, undefined);
  }
});
