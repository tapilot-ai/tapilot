import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { WirelessDevice } from '../plugins/android-use/server/midscene/device.mjs';

const fixture = JSON.parse(await readFile(new URL('../docs/protocols/fixtures/device.json', import.meta.url), 'utf8'));
test('wireless adapter emits actions consumed by the Kotlin protocol parser', async () => {
  const sent = [];
  const device = new WirelessDevice(async (method, params) => {
    if (method === 'device.capture') return { dataUrl: 'data:image/png;base64,AAAA', geometry: {frameId: fixture.frameId, imageWidth: 400, imageHeight: 800} };
    sent.push({method, params});
  });
  const [tap, input, swipe, home, launch] = fixture.actions;
  await device.capture(); await device.tap({x: tap.x, y: tap.y});
  await device.capture(); await device.input(input.text, {replace: input.replace});
  await device.capture(); await device.swipe({x: swipe.startX, y: swipe.startY}, {x: swipe.endX, y: swipe.endY}, {duration: swipe.duration});
  await device.capture(); await device.execute(home);
  await device.capture(); await device.launch(launch.uri);
  assert.deepEqual(sent, fixture.actions.map(action => ({method: 'device.execute', params: {action, frameId: fixture.frameId}})));
  await assert.rejects(device.execute(home), /Observe before acting/);
});
