import test from 'node:test';
import assert from 'node:assert/strict';
import tls from 'node:tls';
import { createHash } from 'node:crypto';
import { mkdtemp, readFile, rm, stat } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { execFileSync } from 'node:child_process';
import { ElicitRequestSchema } from '@modelcontextprotocol/sdk/types.js';
import { Preview, unlockRequired, connectionState } from '../server/preview.mjs';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js';
import { PhoneConnection } from '../server/transport.mjs';
import { parsePairingLink, saveConfig } from '../server/config.mjs';
import { callTool } from '../server/tools.mjs';

import { runtimeRequest } from '../server/runtime-client.mjs';

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
  const previousState=process.env.ANDROID_USE_STATE_DIR, previousConfig=process.env.TAPILOT_CONFIG;
  process.env.ANDROID_USE_STATE_DIR=join(dir,'state'); process.env.TAPILOT_CONFIG=join(dir,'config.json');
  t.after(async () => { await runtimeRequest('/stop',{}, {start:false});
    if(previousState===undefined) delete process.env.ANDROID_USE_STATE_DIR; else process.env.ANDROID_USE_STATE_DIR=previousState;
    if(previousConfig===undefined) delete process.env.TAPILOT_CONFIG; else process.env.TAPILOT_CONFIG=previousConfig;
    for (const socket of connections) socket.destroy(); await new Promise(resolve => server.close(resolve)); await rm(dir, { recursive: true, force: true }); });
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
  const listed=(await client.listTools()).tools;
  assert.equal(listed.length, 7);
  assert.deepEqual(listed.filter(t=>!t._meta?.ui?.visibility).map(t=>t.name),['tapilot_preview_open']);
  assert.equal((await client.callTool({name:'tapilot_midscene',arguments:{action:'home'}})).isError,true);
  assert.equal((await client.callTool({name:'tapilot_preview_pair',arguments:{action:'home'}})).isError,true);
  withRuntime(client);
  const settings = await client.callTool({name:'tapilot_midscene',arguments:{action:'settings'}});
  assert.equal(JSON.parse(settings.content[0].text).mode,'steps');
  assert.equal(requests.length,0, 'Settings must not connect to the phone');
  const openers = (await client.listTools()).tools.filter(t => t._meta?.ui?.resourceUri);
  assert.deepEqual(openers.map(t => t.name), ['tapilot_preview_open']);
  const resources = await client.listResources();
  const resource = await client.readResource({ uri: resources.resources[0].uri });
  assert.match(resource.contents[0].text, /Android Use/);
  assert.deepEqual(resource.contents[0]._meta.ui.csp.connectDomains, []);
  const initial = await client.callTool({ name: 'tapilot_preview_frame', arguments: {} });
  assert.equal(initial.structuredContent.state, 'live', 'An existing panel reconnects after a host restart without an open/resume call');
  const suspended = await client.callTool({name:'tapilot_preview',arguments:{action:'suspend'}});
  assert.equal(suspended.structuredContent.suspended,true);
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
  const requestCount = requests.length;
  const released = await client.callTool({ name: 'tapilot_preview_frame', arguments: {} });
  assert.equal(released.structuredContent.state, 'disconnected');
  assert.equal(requests.length, requestCount, 'Polling must not undo explicit release');
  assert.deepEqual(requests.map(r => r.method), ['authenticate', 'device.preview', 'device.capture', 'device.preview', 'device.capture', 'device.execute', 'device.preview', 'disconnect']);
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
  preview.pause('disconnected');
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
  answer = { action: 'accept', content: { completion: 'Continue' } };
  await unlockRequired(server, preview, {}, 'tapilot_tap', retry);
  assert.equal(repeated, 0);
  await unlockRequired(server, preview, {}, 'tapilot_screen', retry);
  assert.equal(repeated, 1); assert.equal(prompts, 3); assert.equal(preview.reason, null);
  const fallback = await unlockRequired({ getClientCapabilities: () => ({}) }, preview, {}, 'tapilot_screen', retry);
  assert.equal(JSON.parse(fallback.content[0].text).code, 'phone_locked'); assert.equal(repeated, 1);
});

test('locked CLI screenshot waits for explicit resume, without replay', async t => {
  let locked=true;
  const png='iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=';
  const {dir,config,requests}=await fixture(t,(request,reply,socket)=>{
    if(locked) socket.write(JSON.stringify({id:request.id,ok:false,error:'Phone is locked. Unlock it on the device.'})+'\n');
    else reply({dataUrl:`data:image/png;base64,${png}`,geometry:{frameId:'unlocked'}});
  });
  await saveConfig(config,join(dir,'config.json'));
  const call=params=>runtimeRequest('/call',params);
  assert.equal((await call({name:'tapilot_screen',arguments:{}})).structuredContent.state,'device_locked');
  locked=false;
  await call({name:'tapilot_preview',arguments:{action:'resume'}});
  const result=await call({name:'tapilot_screen',arguments:{}});
  assert.equal(result.isError,undefined);
  assert.equal(JSON.parse(result.content[0].text).frameId,'unlocked');
  assert.equal(requests.filter(r=>r.method==='device.execute').length,0);
});


test('a newer privacy pause or disconnect wins over an old unlock dialog', async () => {
  const preview = new Preview(); let retried = false;
  const server = { getClientCapabilities: () => ({ elicitation: { form: {} } }), elicitInput: async () => {
    preview.pause('manual'); return { action: 'accept', content: { completion: 'Continue' } };
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
  withRuntime(client);
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
  const server = {getClientCapabilities:()=>({elicitation:{form:{}}}),elicitInput:async()=>({action:'accept',content:{completion:'Continue'}})};
  const response=await unlockRequired(server,preview,{},'tapilot_screen',async()=>{
    preview.pause('manual');return {content:[{type:'image',data:'private',mimeType:'image/png'}]};
  });
  assert.equal(preview.reason,'manual');
  assert.deepEqual(response.content,[]);
  assert.equal(response.structuredContent.state,'manual');
});

test('existing panel automatically recovers after first pairing and external credential replacement', async t => {
  const png = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=';
  const {dir,config,requests} = await fixture(t, (request,reply) => reply({dataUrl:`data:image/png;base64,${png}`,capturedAt:Date.now(),language:'zh'}));
  const path=join(dir,'config.json');
  const client=new Client({name:'pair-recovery',version:'1'});
  t.after(()=>client.close());
  await client.connect(new StdioClientTransport({command:process.execPath,args:[new URL('../server/index.mjs',import.meta.url).pathname],env:{...process.env,TAPILOT_CONFIG:path,ANDROID_USE_STATE_DIR:join(dir,'state')},stderr:'pipe'}));
  const call = name => client.callTool({name,arguments:{}});
  assert.equal((await call('tapilot_preview_open')).structuredContent.state,'setup_required');
  await saveConfig({...config,token:'b'.repeat(43)},path);
  assert.equal((await call('tapilot_preview_frame')).structuredContent.state,'pairing_required');
  await saveConfig({...config,language:'zh'},path);
  const frame=await call('tapilot_preview_frame');
  assert.equal(frame.structuredContent.state,'live');
  assert.equal(frame.structuredContent.language,'zh');
  assert.equal(requests.filter(r=>r.method==='device.execute').length,0);
  await client.callTool({name:'tapilot_preview',arguments:{action:'pause'}});
  const count=requests.length;
  await saveConfig({...config,token:'c'.repeat(43)},path);
  assert.equal((await call('tapilot_preview_frame')).structuredContent.state,'manual');
  assert.equal(requests.length,count,'credential replacement cannot bypass privacy pause');
});

test('pairing invalidates old frames without bypassing a locked phone', () => {
  const p=new Preview(); p.pause('device_locked'); p.lastFrame={id:'old'};
  p.reconnect(); assert.equal(p.reason,'device_locked'); assert.equal(p.lastFrame,undefined);
});


test('accessibility service failures have a distinct recovery state', () => {
  assert.equal(connectionState(new Error('Enable Tapilot device control in Accessibility settings')), 'accessibility_required');
});

function withRuntime(client) {
 const original=client.callTool.bind(client);
 client.callTool=(request,...args)=>['tapilot_midscene','tapilot_screen','tapilot_status','tapilot_tap'].includes(request.name)
   ? runtimeRequest('/call',request) : original(request,...args);
}

test('CLI processes share frames with the MCP panel, generate reports, and reject stale mutations', async t => {
  const {execFile}=await import('node:child_process');
  const {promisify}=await import('node:util');
  const exec=promisify(execFile);
  const sharp=(await import('sharp')).default;
  const png=(await sharp({create:{width:16,height:16,channels:3,background:'#ffaa00'}}).png().toBuffer()).toString('base64');
  let frame=0;
  const {dir,config,requests}=await fixture(t,(request,reply)=>{
    if(request.method==='device.capture') reply({dataUrl:`data:image/png;base64,${png}`,geometry:{frameId:`f${++frame}`,imageWidth:16,imageHeight:16,logicalWidth:16,logicalHeight:16}});
    else if(request.method==='device.preview') reply({dataUrl:`data:image/png;base64,${png}`,capturedAt:Date.now()});
    else reply({canCapture:true});
  });
  await saveConfig(config,join(dir,'config.json'));
  const cli=async (...args)=>{
    try { const {stdout}=await exec(process.execPath,[new URL('../server/cli.mjs',import.meta.url).pathname,...args],{env:{...process.env}}); return JSON.parse(stdout); }
    catch(error) { if(error.stdout) return JSON.parse(error.stdout); throw error; }
  };
  await Promise.all([cli('status'),cli('status'),cli('status')]);
  const image=await cli('screenshot');
  assert.ok(!image.isError,JSON.stringify(image));
  assert.ok(image.images[0].path); assert.equal((await stat(image.images[0].path)).mode&0o777,0o600);
  const {url}=await cli('preview'); const previewURL=new URL(url);
  const headers={Authorization:`Bearer ${previewURL.hash.slice(1)}`,'Content-Type':'application/json',Origin:previewURL.origin};
  const panel=async request=>(await fetch(`${previewURL.origin}/panel`,{method:'POST',headers,body:JSON.stringify(request)})).json();
  assert.equal((await panel({name:'tapilot_preview_frame',arguments:{}})).structuredContent.state,'live');
  await panel({name:'tapilot_preview',arguments:{action:'suspend'}});
  const args=['--x','0','--y','0','--frame-id',image.geometry.frameId,'--report-id',image.report_id];
  const tapped=await cli('tap',...args);
  assert.ok(!tapped.isError,JSON.stringify(tapped));
  assert.equal(requests.filter(r=>r.method==='authenticate').length,1,'one shared connection across CLI processes and preview');
  const stale=await cli('tap',...args); assert.equal(stale.isError,true);
  assert.equal(requests.filter(r=>r.method==='device.execute').length,1,'stale action is not sent');
  await panel({name:'tapilot_preview',arguments:{action:'pause'}});
  assert.equal((await cli('home')).isError,true);
  const report=await cli('resume'); assert.ok(!report.isError);
  const verdict=await cli('record','--text','fixture verified','--passed','true','--report-id',image.report_id);
  assert.ok(verdict.report_link); assert.ok((await readFile(verdict.report,'utf8')).includes('fixture verified'));
  assert.equal((await fetch(`${previewURL.origin}/panel`,{method:'POST',body:'{}'})).status,401);
  assert.equal((await fetch(`${previewURL.origin}/call`,{method:'POST',headers,body:'{}'})).status,403);
  assert.equal((await fetch(`${previewURL.origin}/panel`,{method:'POST',headers:{...headers,Origin:'https://example.com'},body:'{}'})).status,403);
  assert.match((await panel({name:'tapilot_midscene',arguments:{action:'home'}})).error,/Unsupported/);
  const html=await (await fetch(previewURL.origin)).text(); assert.match(html,/__ANDROID_USE_BROWSER__=true/);
  assert.ok(!html.includes(previewURL.hash.slice(1)),'HTML does not embed the browser capability');
});
