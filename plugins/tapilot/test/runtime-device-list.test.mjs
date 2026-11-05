import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:net';
import { once } from 'node:events';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { createRuntime } from '../server/runtime.mjs';
import { saveConfig, selectDevice } from '../server/config.mjs';

test('saved device lists remain responsive during a stalled phone handshake and reconcile an external disconnect', async t => {
  const directory = await mkdtemp(join(tmpdir(), 'tapilot-device-list-'));
  const oldConfig = process.env.TAPILOT_CONFIG;
  const oldState = process.env.TAPILOT_STATE_DIR;
  process.env.TAPILOT_CONFIG = join(directory, 'wireless.json');
  process.env.TAPILOT_STATE_DIR = directory;
  const sockets = new Set();
  const server = createServer(socket => { sockets.add(socket); socket.on('data', () => {}); socket.on('error', () => {}); socket.on('close', () => sockets.delete(socket)); });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const config = { version: 1, host: '127.0.0.1', port: server.address().port, fingerprint: 'a'.repeat(64), token: 'x'.repeat(43) };
  await saveConfig(config);
  const runtime = createRuntime();
  let connecting;
  t.after(async () => {
    runtime.close();
    for (const socket of sockets) socket.destroy();
    await connecting;
    await new Promise(resolve => server.close(resolve));
    if (oldConfig === undefined) delete process.env.TAPILOT_CONFIG; else process.env.TAPILOT_CONFIG = oldConfig;
    if (oldState === undefined) delete process.env.TAPILOT_STATE_DIR; else process.env.TAPILOT_STATE_DIR = oldState;
    await rm(directory, { recursive: true, force: true });
  });
  const accepted = once(server, 'connection');
  connecting = runtime.call({ params: { name: 'tapilot_devices', arguments: { action: 'connect' } } });
  await accepted; // A real TCP peer deliberately never finishes the TLS handshake.
  const before = await readFile(process.env.TAPILOT_CONFIG, 'utf8');
  let timeout;
  const result = await Promise.race([
    runtime.call({ params: { name: 'tapilot_devices', arguments: { action: 'list' } } }),
    new Promise(resolve => { timeout = setTimeout(() => resolve(null), 500); }),
  ]);
  clearTimeout(timeout);
  assert.notEqual(result, null, 'a read-only device list must not wait behind phone connection attempts');
  assert.equal(result.structuredContent.devices[0].id, config.fingerprint);
  assert.equal(await readFile(process.env.TAPILOT_CONFIG, 'utf8'), before, 'listing never writes pairing or selection');
  const invalid = await runtime.call({ params: { name: 'tapilot_devices', arguments: { action: 'list', host: 'unexpected' } } });
  assert.equal(invalid.isError, true);
  await selectDevice('disconnect', config.fingerprint);
  const disconnected = await runtime.call({ params: { name: 'tapilot_devices', arguments: { action: 'list' } } });
  assert.equal(disconnected.structuredContent.devices[0].state, 'disconnected');
  assert.equal((await connecting).isError, true, 'external disconnect still closes the old in-flight transport');
});
