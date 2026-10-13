import test from 'node:test';
import assert from 'node:assert/strict';
import tls from 'node:tls';
import { createHash } from 'node:crypto';
import { mkdtemp, readFile, rm, stat } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { execFileSync } from 'node:child_process';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js';
import { PhoneConnection } from '../server/transport.mjs';
import { parsePairingLink, saveConfig } from '../server/config.mjs';
import { callTool } from '../server/tools.mjs';

const token = 'a'.repeat(43);
async function fixture(t, handler) {
  const dir = await mkdtemp(join(tmpdir(), 'tapilot-mcp-test-'));
  execFileSync('openssl', ['req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-keyout', join(dir, 'key.pem'), '-out', join(dir, 'cert.pem'), '-subj', '/CN=Tapilot test', '-days', '1'], { stdio: 'ignore' });
  const key = await readFile(join(dir, 'key.pem')), cert = await readFile(join(dir, 'cert.pem'));
  const der = Buffer.from(cert.toString().replace(/-----[^-]+-----/g, '').replace(/\s/g, ''), 'base64');
  const connections = new Set();
  const requests = [];
  const server = tls.createServer({ key, cert, minVersion: 'TLSv1.2', maxVersion: 'TLSv1.2' }, socket => {
    connections.add(socket); socket.on('close', () => connections.delete(socket)); socket.on('error', () => {});
    let buffer = '';
    socket.on('data', chunk => {
      buffer += chunk.toString(); let end;
      while ((end = buffer.indexOf('\n')) >= 0) {
        const request = JSON.parse(buffer.slice(0, end)); buffer = buffer.slice(end + 1); requests.push(request);
        const reply = value => socket.write(JSON.stringify({ id: request.id, ok: true, result: value }) + '\n');
        if (request.method === 'authenticate') {
          if (request.token !== token) socket.write(JSON.stringify({ id: request.id, ok: false, error: 'Not paired' }) + '\n');
          else reply({ protocolVersion: 1 });
        } else handler(request, reply, socket);
      }
    });
  });
  server.on('tlsClientError', () => {});
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(async () => { for (const socket of connections) socket.destroy(); await new Promise(resolve => server.close(resolve)); await rm(dir, { recursive: true, force: true }); });
  return { dir, requests, config: { version: 1, host: '127.0.0.1', port: server.address().port, fingerprint: createHash('sha256').update(der).digest('hex'), token } };
}

test('certificate mismatch sends no pairing credential', async t => {
  const { config, requests } = await fixture(t, () => {});
  const phone = new PhoneConnection({ ...config, fingerprint: '0'.repeat(64) });
  await assert.rejects(phone.open(), /certificate changed/);
  assert.equal(requests.length, 0);
});

test('authentication rejection closes connection', async t => {
  const { config } = await fixture(t, () => {});
  const phone = new PhoneConnection({ ...config, token: 'b'.repeat(43) });
  await assert.rejects(phone.open(), /Not paired/);
  assert.equal(phone.connected, false);
});

test('connection loss never retries a mutation', async t => {
  const { config, requests } = await fixture(t, (request, reply, socket) => socket.destroy());
  const phone = new PhoneConnection(config); t.after(() => phone.close());
  await phone.open();
  await assert.rejects(phone.request('device.execute', { action: { type: 'home' } }), /disconnected/);
  assert.equal(requests.filter(r => r.method === 'device.execute').length, 1);
  await assert.rejects(callTool(phone, 'tapilot_press', { button: 'home' }), /disconnected/);
  assert.equal(requests.length, 2);
});

test('tools reject invalid arguments before contacting phone', async () => {
  await assert.rejects(callTool({}, 'tapilot_tap', { x: -1, y: 2 }), /Invalid tool arguments/);
  await assert.rejects(callTool({}, 'tapilot_press', { button: 'shell' }), /Invalid tool arguments/);
  await assert.rejects(callTool({}, 'tapilot_launch_app', { package_name: 'https://example.com' }), /Invalid tool arguments/);
});

test('real MCP stdio handshake, image content, schema and disconnect', async t => {
  const png = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=';
  const { dir, config, requests } = await fixture(t, (request, reply) => {
    if (request.method === 'device.capture') reply({ dataUrl: `data:image/png;base64,${png}`, geometry: { frameId: 'f1', imageWidth: 1, imageHeight: 1 } });
    else reply({ canCapture: true });
  });
  const path = join(dir, 'config.json'); await saveConfig(config, path);
  assert.equal((await stat(path)).mode & 0o777, 0o600);
  const transport = new StdioClientTransport({ command: process.execPath, args: [new URL('../server/index.mjs', import.meta.url).pathname], env: { ...process.env, TAPILOT_CONFIG: path }, stderr: 'pipe' });
  const client = new Client({ name: 'acceptance', version: '1' });
  t.after(() => client.close());
  await client.connect(transport);
  assert.equal((await client.listTools()).tools.length, 8);
  const result = await client.callTool({ name: 'tapilot_screen', arguments: {} });
  assert.equal(result.isError, undefined);
  assert.equal(result.content[1].type, 'image'); assert.equal(result.content[1].data, png);
  assert.equal(JSON.parse(result.content[0].text).frameId, 'f1');
  const invalid = await client.callTool({ name: 'tapilot_tap', arguments: {} });
  assert.equal(invalid.isError, true);
  await client.callTool({ name: 'tapilot_disconnect', arguments: {} });
  assert.deepEqual(requests.map(r => r.method), ['authenticate', 'device.capture', 'disconnect']);
});

test('pairing links validate endpoint and credentials without echoing secrets', () => {
  assert.throws(() => parsePairingLink('tapilot://pair/not-valid'), /Invalid/);
  assert.throws(() => parsePairingLink('https://example.com'), /Paste/);
});
