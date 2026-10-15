import test from 'node:test';
import assert from 'node:assert/strict';
import tls from 'node:tls';
import { createHash } from 'node:crypto';
import { mkdtemp, readFile, rm, stat } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { execFileSync } from 'node:child_process';
import { ElicitRequestSchema } from '@modelcontextprotocol/sdk/types.js';
import { Preview, unlockRequired } from '../server/preview.mjs';
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
    if (request.method === 'device.preview') reply({ dataUrl: `data:image/png;base64,${png}`, capturedAt: Date.now() });
    else if (request.method === 'device.capture') reply({ dataUrl: `data:image/png;base64,${png}`, geometry: { frameId: 'f1', imageWidth: 1, imageHeight: 1 } });
    else reply({ canCapture: true });
  });
  const path = join(dir, 'config.json'); await saveConfig(config, path);
  assert.equal((await stat(path)).mode & 0o777, 0o600);
  const transport = new StdioClientTransport({ command: process.execPath, args: [new URL('../server/index.mjs', import.meta.url).pathname], env: { ...process.env, TAPILOT_CONFIG: path, ANDROID_USE_STATE_DIR: join(dir,'state') }, stderr: 'pipe' });
  const client = new Client({ name: 'acceptance', version: '1' });
  t.after(() => client.close());
  await client.connect(transport);
  assert.equal((await client.listTools()).tools.length, 14);
  const settings = await client.callTool({name:'tapilot_midscene',arguments:{action:'settings'}});
  assert.equal(JSON.parse(settings.content[0].text).mode,'steps');
  assert.equal(requests.length,0, 'Settings must not connect to the phone');
  const openers = (await client.listTools()).tools.filter(t => t._meta?.ui?.resourceUri);
  assert.deepEqual(openers.map(t => t.name), ['tapilot_preview_open']);
  const resources = await client.listResources();
  const resource = await client.readResource({ uri: resources.resources[0].uri });
  assert.match(resource.contents[0].text, /Android Use/);
  assert.deepEqual(resource.contents[0]._meta.ui.csp.connectDomains, []);
  const result = await client.callTool({ name: 'tapilot_screen', arguments: {} });
  assert.equal(result.isError, undefined);
  assert.equal(result.content[1].type, 'image'); assert.equal(result.content[1].data, png);
  assert.equal(JSON.parse(result.content[0].text).frameId, 'f1');
  const invalid = await client.callTool({ name: 'tapilot_tap', arguments: {} });
  assert.equal(invalid.isError, true);
  const frame = await client.callTool({ name: 'tapilot_preview_frame', arguments: {} });
  const args = { frame_id: frame.structuredContent.frame_id, action: 'tap', x: .5, y: .5 };
  const action = await client.callTool({ name: 'tapilot_preview_control', arguments: args });
  assert.equal(action.isError, undefined);
  assert.equal(action.structuredContent.action_completed, true);
  const repeated = await client.callTool({ name: 'tapilot_preview_control', arguments: args });
  assert.equal(repeated.isError, true);
  await client.callTool({ name: 'tapilot_disconnect', arguments: {} });
  assert.deepEqual(requests.map(r => r.method), ['authenticate', 'device.capture', 'device.preview', 'device.capture', 'device.execute', 'device.preview', 'disconnect']);
});

test('pairing links validate endpoint and credentials without echoing secrets', () => {
  assert.throws(() => parsePairingLink('tapilot://pair/not-valid'), /Invalid/);
  assert.throws(() => parsePairingLink('https://example.com'), /Paste/);
});


test('preview uses independent capture, pauses on lock and requires explicit resume', async () => {
  const preview = new Preview(); let locked = false; const methods = [];
  const phone = { open: async () => {}, request: async method => {
    methods.push(method); if (locked) throw new Error('Phone is locked. Unlock it on the device.');
    return { dataUrl: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=', capturedAt: 1 };
  } };
  assert.equal((await preview.frame(phone)).structuredContent.state, 'disconnected');
  assert.equal(methods.length, 0);
  preview.observe(); assert.equal((await preview.frame(phone)).structuredContent.state, 'live');
  assert.deepEqual(methods, ['device.preview']);
  locked = true; assert.equal((await preview.frame(phone)).structuredContent.state, 'device_locked');
  locked = false; assert.equal((await preview.frame(phone)).structuredContent.state, 'device_locked');
  await preview.resume(phone); assert.equal(preview.reason, null);
  preview.pause(); preview.observe(); assert.equal(preview.reason, 'manual');
  assert.equal((await preview.frame(phone)).structuredContent.dataUrl, undefined);
});

test('unlock interaction waits for actual acceptance and never repeats mutations', async () => {
  let repeated = 0, prompts = 0, answer = { action: 'cancel' };
  const server = { getClientCapabilities: () => ({ elicitation: { form: {} } }), elicitInput: async () => { prompts++; return answer; } };
  const preview = new Preview(); const retry = async () => { repeated++; return { content: [] }; };
  const declined = await unlockRequired(server, preview, {}, 'tapilot_screen', retry);
  assert.equal(JSON.parse(declined.content[0].text).needs_user_input, true);
  assert.equal(repeated, 0);
  answer = { action: 'accept', content: { completion: '已完成继续' } };
  await unlockRequired(server, preview, {}, 'tapilot_tap', retry);
  assert.equal(repeated, 0);
  await unlockRequired(server, preview, {}, 'tapilot_screen', retry);
  assert.equal(repeated, 1); assert.equal(prompts, 3); assert.equal(preview.reason, null);
  const fallback = await unlockRequired({ getClientCapabilities: () => ({}) }, preview, {}, 'tapilot_screen', retry);
  assert.equal(JSON.parse(fallback.content[0].text).code, 'phone_locked'); assert.equal(repeated, 1);
});

test('real MCP lock elicits once then returns fresh screen after user completion', async t => {
  let locked = true, prompts = 0;
  const { dir, config, requests } = await fixture(t, (request, reply, socket) => {
    if (locked) socket.write(JSON.stringify({ id: request.id, ok: false, error: 'Phone is locked. Unlock it on the device.' }) + '\n');
    else reply({ dataUrl: 'data:image/png;base64,YQ==', geometry: { frameId: 'unlocked' } });
  });
  const path = join(dir, 'config.json'); await saveConfig(config, path);
  const client = new Client({ name: 'unlock-test', version: '1' }, { capabilities: { elicitation: { form: {} } } });
  client.setRequestHandler(ElicitRequestSchema, async request => {
    prompts++; assert.match(request.params.message, /手动解锁/);
    const paused = await client.callTool({ name: 'tapilot_preview_frame', arguments: {} });
    assert.equal(paused.structuredContent.state, 'device_locked');
    assert.equal(paused.structuredContent.dataUrl, undefined);
    locked = false;
    return { action: 'accept', content: { completion: '已完成继续' } };
  });
  t.after(() => client.close());
  await client.connect(new StdioClientTransport({ command: process.execPath, args: [new URL('../server/index.mjs', import.meta.url).pathname], env: { ...process.env, TAPILOT_CONFIG: path, ANDROID_USE_STATE_DIR: join(dir,'state') }, stderr: 'pipe' }));
  const result = await client.callTool({ name: 'tapilot_screen', arguments: {} });
  assert.equal(result.isError, undefined); assert.equal(prompts, 1);
  assert.equal(JSON.parse(result.content[0].text).frameId, 'unlocked');
  assert.equal(requests.filter(r => r.method === 'device.capture').length, 2);
});


test('a newer privacy pause or disconnect wins over an old unlock dialog', async () => {
  const preview = new Preview(); let retried = false;
  const server = { getClientCapabilities: () => ({ elicitation: { form: {} } }), elicitInput: async () => {
    preview.pause('manual'); return { action: 'accept', content: { completion: '已完成继续' } };
  } };
  await unlockRequired(server, preview, {}, 'tapilot_screen', async () => { retried = true; });
  assert.equal(retried, false); assert.equal(preview.reason, 'manual');
});

test('MCP pause interrupts Midscene while a phone action is pending; live frames remain available', async t => {
  const png = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLbtAAAAABJRU5ErkJggg==';
  let signalStarted, completeAction;
  const started = new Promise(resolve => { signalStarted = resolve; });
  const {dir,config,requests} = await fixture(t,(request,reply)=>{
    if(request.method === 'device.capture') reply({dataUrl:`data:image/png;base64,${png}`,geometry:{frameId:'frame',imageWidth:1,imageHeight:1}});
    else if(request.method === 'device.preview') reply({dataUrl:`data:image/png;base64,${png}`});
    else if(request.method === 'device.execute') { completeAction = reply; signalStarted(); }
    else reply({canCapture:true});
  });
  const path=join(dir,'config.json');await saveConfig(config,path);
  const client=new Client({name:'midscene-pause-test',version:'1'});
  t.after(()=>client.close());
  await client.connect(new StdioClientTransport({command:process.execPath,args:[new URL('../server/index.mjs',import.meta.url).pathname],env:{...process.env,TAPILOT_CONFIG:path,ANDROID_USE_STATE_DIR:join(dir,'state')},stderr:'pipe'}));
  await client.callTool({name:'tapilot_status',arguments:{}});
  const action=client.callTool({name:'tapilot_midscene',arguments:{action:'home'}});
  await started;
  const progress=await client.callTool({name:'tapilot_midscene',arguments:{action:'status'}}, undefined, {timeout:2000});
  assert.equal(progress.structuredContent.active,true);
  assert.equal(progress.structuredContent.next_action,'wait_or_pause');
  const live=await client.callTool({name:'tapilot_preview_frame',arguments:{}});
  assert.equal(live.structuredContent.state,'live');
  const pause=await client.callTool({name:'tapilot_preview',arguments:{action:'pause'}});
  assert.equal(pause.structuredContent.state,'manual');
  completeAction(null);
  assert.equal((await action).isError,true);
  const blocked=await client.callTool({name:'tapilot_midscene',arguments:{action:'home'}});
  assert.equal(blocked.isError,true);
  assert.equal(requests.filter(r=>r.method==='device.execute').length,1);
  assert.equal(requests.filter(r=>r.method==='disconnect').length,0);
});

test('LAN source selection excludes VPNs and refuses ambiguous interfaces', async () => {
  const {localAddressForPhone: choose} = await import('../server/transport.mjs');
  const entry = (address,netmask='255.255.255.0') => [{family:'IPv4',internal:false,address,netmask}];
  const physical = {en0:entry('100.82.238.75','255.255.240.0'),utun8:entry('100.82.240.1','255.255.240.0')};
  assert.equal(choose('100.82.246.47',physical),'100.82.238.75');
  assert.equal(choose('127.0.0.1',physical),undefined);
  assert.equal(choose('100.82.246.47',{utun8:physical.utun8}),undefined);
  assert.equal(choose('100.82.246.47',{...physical,en1:entry('192.168.1.2')}),undefined);
  assert.equal(choose('192.168.1.10',{...physical,en1:entry('192.168.1.2')}),'192.168.1.2');
});

test('pause during unlock observation discards its late screen', async () => {
  const preview = new Preview();
  const server = {getClientCapabilities:()=>({elicitation:{form:{}}}),elicitInput:async()=>({action:'accept',content:{completion:'已完成继续'}})};
  const response=await unlockRequired(server,preview,{},'tapilot_screen',async()=>{
    preview.pause('manual');return {content:[{type:'image',data:'private',mimeType:'image/png'}]};
  });
  assert.equal(preview.reason,'manual');
  assert.deepEqual(response.content,[]);
  assert.equal(response.structuredContent.state,'manual');
});
