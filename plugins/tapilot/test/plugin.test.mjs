import { assertPrivatePath } from '../server/private-path.mjs';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
import assert from 'node:assert/strict';
import tls from 'node:tls';
import { createHash } from 'node:crypto';
import { mkdtemp, readFile, rm, stat, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { execFileSync } from 'node:child_process';
import { ElicitRequestSchema } from '@modelcontextprotocol/sdk/types.js';
import { Preview, unlockRequired, connectionState } from '../server/preview.mjs';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js';
import { PhoneConnection } from '../server/transport.mjs';
import { parsePairingLink, saveConfig, selectDevice } from '../server/config.mjs';
import { callTool } from '../server/tools.mjs';

import { runtimeRequest, runtimePaths } from '../server/runtime-client.mjs';

const token = 'a'.repeat(43);
async function fixture(t, handler, { automaticAuthentication = true } = {}) {
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
        if (request.method === 'authenticate' && automaticAuthentication) {
          if (request.token !== token) socket.write(JSON.stringify({ id: request.id, ok: false, error: 'Not paired' }) + '\n');
          else reply({ protocolVersion: 1 });
        } else handler(request, reply, socket);
      }
    });
  });
  server.on('tlsClientError', () => {});
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const previousState=process.env.TAPILOT_STATE_DIR, previousConfig=process.env.TAPILOT_CONFIG, previousInstallations=process.env.TAPILOT_INSTALLATIONS_DIR;
  process.env.TAPILOT_STATE_DIR=join(dir,'state'); process.env.TAPILOT_CONFIG=join(dir,'config.json');
  process.env.TAPILOT_INSTALLATIONS_DIR=join(dir,'isolated-installations');
  t.after(async () => { await runtimeRequest('/stop',{}, {start:false});
    if(previousState===undefined) delete process.env.TAPILOT_STATE_DIR; else process.env.TAPILOT_STATE_DIR=previousState;
    if(previousConfig===undefined) delete process.env.TAPILOT_CONFIG; else process.env.TAPILOT_CONFIG=previousConfig;
    if(previousInstallations===undefined) delete process.env.TAPILOT_INSTALLATIONS_DIR; else process.env.TAPILOT_INSTALLATIONS_DIR=previousInstallations;
    for (const socket of connections) socket.destroy(); await new Promise(resolve => server.close(resolve)); await rm(dir, { recursive: true, force: true }); });
  return { dir, requests, connections, config: { version: 1, host: '127.0.0.1', port: server.address().port, fingerprint: createHash('sha256').update(der).digest('hex'), token } };
}

test('overlapping connection attempts wait for authentication before sending phone requests', async t => {
  let authenticate;
  const receivedAuthentication = new Promise(resolve => { authenticate = resolve; });
  const {config, requests} = await fixture(t, (request, reply) => {
    if (request.method === 'authenticate') authenticate(reply);
    else reply({canCapture:true});
  }, {automaticAuthentication:false});
  const phone = new PhoneConnection(config); t.after(() => phone.close());
  const first = phone.open();
  const reply = await receivedAuthentication;
  let secondReady = false;
  const second = phone.open().then(() => { secondReady = true; });
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(secondReady, false, 'a socket is not usable until authentication succeeds');
  assert.equal(phone.connected, false);
  reply({features:{jpegPreview:true}});
  await Promise.all([first, second]);
  assert.equal(phone.connected, true);
  assert.equal(phone.features.jpegPreview, true);
  assert.deepEqual(requests.map(request => request.method), ['authenticate']);
});

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
  await assertPrivatePath(path);
  const transport = new StdioClientTransport({ command: process.execPath, args: [fileURLToPath(new URL('../server/index.mjs', import.meta.url))], env: { ...process.env, TAPILOT_CONFIG: path, TAPILOT_STATE_DIR: join(dir,'state') }, stderr: 'pipe' });
  const client = new Client({ name: 'acceptance', version: '1' });
  t.after(() => client.close());
  await client.connect(transport);
  const listed=(await client.listTools()).tools;
  assert.equal(listed.length, 8);
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
  assert.match(resource.contents[0].text, /Tapilot/);
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


test('preview automatically recovers after unlock but preserves explicit privacy pauses', async () => {
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
  locked = false; assert.equal((await preview.frame(phone)).structuredContent.state, 'live');
  assert.equal(preview.reason, null);
  assert.ok(methods.every(method => method === 'device.preview'));
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
  await client.connect(new StdioClientTransport({command:process.execPath,args:[fileURLToPath(new URL('../server/index.mjs',import.meta.url))],env:{...process.env,TAPILOT_CONFIG:path,TAPILOT_STATE_DIR:join(dir,'state')},stderr:'pipe'}));
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
  await client.connect(new StdioClientTransport({command:process.execPath,args:[fileURLToPath(new URL('../server/index.mjs',import.meta.url))],env:{...process.env,TAPILOT_CONFIG:path,TAPILOT_STATE_DIR:join(dir,'state')},stderr:'pipe'}));
  const call = name => client.callTool({name,arguments:{}});
  assert.equal((await call('tapilot_preview_open')).structuredContent.state,'connecting');
  assert.equal((await call('tapilot_preview_frame')).structuredContent.state,'setup_required');
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
  let frame=0, rejectNextAction=false;
  const {dir,config,requests}=await fixture(t,(request,reply,socket)=>{
    if(request.method==='device.capture') reply({dataUrl:`data:image/png;base64,${png}`,geometry:{frameId:`f${++frame}`,imageWidth:16,imageHeight:16,logicalWidth:16,logicalHeight:16}});
    else if(request.method==='device.preview') reply({dataUrl:`data:image/png;base64,${png}`,capturedAt:Date.now()});
    else if(request.method==='device.execute' && rejectNextAction) {
      rejectNextAction=false;
      socket.write(JSON.stringify({id:request.id,ok:false,error:'Stale frame. Capture a new screen.'})+'\n');
    }
    else reply({canCapture:true});
  });
  await saveConfig(config,join(dir,'config.json'));
  const cli=async (...args)=>{
    try { const {stdout}=await exec(process.execPath,[fileURLToPath(new URL('../server/cli.mjs',import.meta.url)),...args],{env:{...process.env}}); return JSON.parse(stdout); }
    catch(error) { if(error.stdout) return JSON.parse(error.stdout); throw error; }
  };
  await Promise.all([cli('status'),cli('status'),cli('status')]);
  const imageDirectory=join(dir,'workspace','screenshots');
  const image=await cli('screenshot','--image-dir',imageDirectory);
  assert.ok(!image.isError,JSON.stringify(image));
  assert.ok(image.images[0].path.startsWith(imageDirectory+(process.platform === 'win32' ? '\\' : '/')),'sandboxed clients can keep image files in their workspace');
  assert.ok(image.images[0].path); await assertPrivatePath(image.images[0].path);
  const {url}=await cli('preview'); const previewURL=new URL(url);
  const headers={Authorization:`Bearer ${previewURL.hash.slice(1)}`,'Content-Type':'application/json',Origin:previewURL.origin};
  const panel=async request=>(await fetch(`${previewURL.origin}/panel`,{method:'POST',headers,body:JSON.stringify(request)})).json();
  assert.equal((await panel({name:'tapilot_preview_frame',arguments:{}})).structuredContent.state,'live');
  await panel({name:'tapilot_preview',arguments:{action:'suspend'}});
  const args=['--x','0','--y','0','--frame-id',image.geometry.frameId,'--report-id',image.report_id];
  const tapped=await cli('tap',...args,'--image-dir',imageDirectory);
  assert.ok(!tapped.isError,JSON.stringify(tapped));
  assert.ok(tapped.images[0].path.startsWith(imageDirectory+(process.platform === 'win32' ? '\\' : '/')));
  assert.equal(requests.filter(r=>r.method==='authenticate').length,1,'one shared connection across CLI processes and preview');
  const stale=await cli('tap',...args); assert.equal(stale.isError,true);
  assert.equal(stale.code,'stale_frame',JSON.stringify(stale));
  assert.equal(stale.next_action,'inspect_returned_image');
  assert.ok(stale.images?.[0].path,'a stale action returns a fresh observation without replay');
  assert.notEqual(stale.geometry.frameId,image.geometry.frameId);
  assert.equal(stale.report_id,image.report_id,'recovery stays in the original task report');
  assert.equal(requests.filter(r=>r.method==='device.execute').length,1,'stale action is not sent');
  rejectNextAction=true;
  const expired=await cli('tap','--x','0','--y','0','--frame-id',stale.geometry.frameId,'--report-id',image.report_id);
  assert.equal(expired.code,'stale_frame',JSON.stringify(expired));
  assert.equal(expired.action_executed,false);
  assert.equal(expired.next_action,'inspect_returned_image');
  assert.ok(expired.images?.[0].path);
  assert.notEqual(expired.geometry.frameId,stale.geometry.frameId);
  assert.equal(requests.filter(r=>r.method==='device.execute').length,2,'phone rejection is observed, never replayed');
  await panel({name:'tapilot_preview',arguments:{action:'pause'}});
  assert.equal((await cli('home')).isError,true);
  const report=await cli('resume'); assert.ok(!report.isError);
  const verdict=await cli('record','--text','fixture verified','--passed','true','--report-id',image.report_id);
  assert.ok(verdict.report_link); assert.ok((await readFile(verdict.report,'utf8')).includes('fixture verified'));
  assert.equal((await fetch(`${previewURL.origin}/panel`,{method:'POST',body:'{}'})).status,401);
  assert.equal((await fetch(`${previewURL.origin}/call`,{method:'POST',headers,body:'{}'})).status,403);
  assert.equal((await fetch(`${previewURL.origin}/panel`,{method:'POST',headers:{...headers,Origin:'https://example.com'},body:'{}'})).status,403);
  assert.match((await panel({name:'tapilot_midscene',arguments:{action:'home'}})).error,/Unsupported/);
  const html=await (await fetch(previewURL.origin)).text(); assert.match(html,/__TAPILOT_BROWSER__=true/);
  assert.ok(!html.includes(previewURL.hash.slice(1)),'HTML does not embed the browser capability');
});

test('explicit disconnect persists across runtime restart and blocks every automatic entry point', async t => {
  const {config,requests} = await fixture(t, (_request,reply) => reply({canCapture:true}));
  await saveConfig(config);
  const call = (name,args={}) => runtimeRequest('/call',{name,arguments:args});
  const list = await call('tapilot_devices',{action:'list'});
  assert.equal(requests.length,0);
  assert.equal(list.structuredContent.devices[0].id,config.fingerprint);
  assert.ok(!JSON.stringify(list).includes(config.token));
  await call('tapilot_status');
  assert.equal(requests[0].method,'authenticate');
  const {promisify} = await import('node:util');
  const {execFile} = await import('node:child_process');
  const cli = async (...args) => JSON.parse((await promisify(execFile)(process.execPath,
    [fileURLToPath(new URL('../server/cli.mjs',import.meta.url)),...args],{env:process.env})).stdout);
  assert.equal((await cli('devices')).devices[0].id,config.fingerprint);
  await cli('disconnect');
  const count=requests.length;
  await runtimeRequest('/stop');
  await assert.rejects(stat(runtimePaths().metadata), { code: 'ENOENT' },
    'The stop acknowledgement must not expose a stale endpoint to the next request');
  for (const [name,args] of [
    ['tapilot_preview_open',{}],['tapilot_preview_frame',{}],['tapilot_status',{}],
    ['tapilot_midscene',{action:'screenshot'}],['tapilot_midscene',{action:'home'}],
    ['tapilot_preview',{action:'open'}],
  ]) assert.equal((await call(name,args)).structuredContent.state,'disconnected');
  assert.equal(requests.length,count,'No connection or action after restart until user connects');
  assert.equal((await cli('connect','--device-id',config.fingerprint)).isError,false);
  assert.equal(requests.length,count+1);
  assert.equal(requests.at(-1).method,'authenticate');
  await call('tapilot_devices',{action:'forget',device_id:config.fingerprint});
  assert.equal((await call('tapilot_devices',{action:'list'})).structuredContent.devices.length,0);
});

test('removing pairing outside the runtime releases the old phone on the next device-list request', async t => {
  const {config,connections} = await fixture(t, (_request, reply) => reply({canCapture:true}));
  await saveConfig(config);
  await runtimeRequest('/call', {name:'tapilot_status',arguments:{}});
  assert.equal(connections.size, 1);
  const closed = new Promise(resolve => [...connections][0].once('close', resolve));
  await selectDevice('forget', config.fingerprint);
  const result = await runtimeRequest('/call', {name:'tapilot_devices',arguments:{action:'list'}});
  assert.equal(result.structuredContent.state, 'disconnected');
  assert.deepEqual(result.structuredContent.devices, []);
  await closed;
});

test('invalid resume arguments cannot undo an explicit disconnect', async t => {
  const {config,requests} = await fixture(t, (_request,reply) => reply({canCapture:true}));
  await saveConfig(config);
  await selectDevice('disconnect', config.fingerprint);
  const invalid = await runtimeRequest('/call', {name:'tapilot_preview_resume',arguments:{unexpected:true}});
  assert.equal(invalid.isError, true);
  const listed = await runtimeRequest('/call', {name:'tapilot_devices',arguments:{action:'list'}});
  assert.equal(listed.structuredContent.devices[0].state, 'disconnected');
  const observed = await runtimeRequest('/call', {name:'tapilot_status',arguments:{}});
  assert.equal(observed.isError, true);
  assert.equal(requests.length, 0);
});


test('manual pause or disconnect during an unlock probe discards the late frame', async () => {
  for (const reason of ['manual', 'disconnected']) {
    const preview = new Preview(); preview.pause('device_locked');
    let release;
    const pending = new Promise(resolve => { release = resolve; });
    const phone = {open: async () => {}, request: () => pending};
    const probe = preview.frame(phone);
    await Promise.resolve();
    preview.pause(reason);
    release({dataUrl:'data:image/png;base64,invalid'});
    assert.equal((await probe).structuredContent.state, reason);
    assert.equal(preview.lastFrame, undefined);
    assert.equal(preview.reason, reason);
  }
});

test('panel opener returns the shell without initializing runtime or loading pairing', async t => {
  const dir = await mkdtemp(join(tmpdir(), 'tapilot-open-shell-'));
  t.after(() => rm(dir, {recursive:true,force:true}));
  const state = join(dir, 'state-file');
  const config = join(dir, 'broken-pairing.json');
  await writeFile(state, 'not a directory');
  await writeFile(config, 'invalid pairing');
  const client = new Client({name:'panel-shell',version:'1'});
  t.after(() => client.close());
  await client.connect(new StdioClientTransport({command:process.execPath,
    args:[fileURLToPath(new URL('../server/index.mjs',import.meta.url))],
    env:{...process.env,TAPILOT_STATE_DIR:state,TAPILOT_CONFIG:config},stderr:'pipe'}));
  const result = await client.callTool({name:'tapilot_preview_open',arguments:{}});
  assert.equal(result.isError,undefined);
  assert.equal(result.structuredContent.state,'connecting');
  assert.equal(result._meta['openai/widgetSessionId'],'tapilot-screen');
  const open = args => client.callTool({name:'tapilot_preview_open',arguments:args});
  const session = result => result._meta['openai/widgetSessionId'];
  const recovered = await open({recover:true});
  assert.equal(recovered.isError, undefined);
  assert.equal(recovered.structuredContent.state, 'connecting');
  assert.match(session(recovered), /^tapilot-screen-[0-9a-f-]{36}$/);
  assert.notEqual(session(recovered), session(result));
  assert.equal(session(await open({})), session(recovered), 'plain opens reuse the recovered panel');
  assert.equal(session(await open({recover:false})), session(recovered));
  assert.equal((await open({recover:'true'})).isError, true);
  assert.equal(session(await open({})), session(recovered), 'invalid recovery cannot rotate the session');
  const continued = await open({recover:true});
  assert.notEqual(session(continued), session(recovered), 'next user turn requests a fresh host view');
  assert.equal(await readFile(config,'utf8'),'invalid pairing', 'recovery must not read or change phone pairing');

  assert.equal(await readFile(state,'utf8'),'not a directory');
  assert.equal((await client.callTool({name:'tapilot_preview_open',arguments:{invalid:true}})).isError,true);
});

test('failed explicit connection recovers on preview after accessibility becomes ready', async t => {
  let ready = false;
  const png = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=';
  const {config,requests} = await fixture(t, (request,reply,socket) => {
    if (request.method === 'authenticate') {
      if (ready) reply({protocolVersion:1});
      else socket.end(JSON.stringify({id:request.id,ok:false,error:'Enable Tapilot device control in Accessibility settings'})+'\n');
    } else reply({dataUrl:png,capturedAt:Date.now()});
  }, {automaticAuthentication:false});
  await saveConfig(config);
  const call = (name,args={}) => runtimeRequest('/call',{name,arguments:args});
  const failed = await call('tapilot_devices',{action:'connect',device_id:config.fingerprint});
  assert.equal(failed.structuredContent.state,'accessibility_required');
  ready = true;
  assert.equal((await call('tapilot_preview_frame')).structuredContent.state,'live');
  assert.equal(requests.filter(r=>r.method==='authenticate').length,2);
  assert.equal(requests.some(r=>r.method==='device.execute'),false);
});

test('network changes invalidate stale connected state and reconnect without replaying actions', async t => {
  const {config,requests} = await fixture(t, (request,reply)=>reply({canCapture:true}));
  let network='home';
  const phone=new PhoneConnection(config,{networkState:()=>network});t.after(()=>phone.close());
  await phone.open();assert.equal(phone.connected,true);
  network='office';assert.equal(phone.connected,false);
  await assert.rejects(phone.request('device.execute',{action:'home'}),/disconnected/);
  await phone.open();assert.equal(phone.connected,true);
  assert.deepEqual(requests.map(r=>r.method),['authenticate','authenticate']);
});

test('rediscovery tries later addresses and saves only the verified new endpoint', async t => {
  const {config,requests}=await fixture(t, (request,reply)=>reply({}));
  // The old address now belongs to another TLS identity. No token is sent there.
  const other=await fixture(t,()=>{});
  const previous={...config,host:'0.0.0.0',port:other.config.port};
  await saveConfig(previous);
  const phone=new PhoneConnection(previous,{
    discover:async()=>[{host:'127.0.0.1',port:other.config.port},{host:config.host,port:config.port}],
    find:async()=>{throw Error('reachable mDNS endpoint must avoid a scan');},
  });t.after(()=>phone.close());
  await phone.open();
  assert.equal(phone.config.host,config.host);assert.equal(phone.config.port,config.port);
  assert.equal(other.requests.length,0);assert.deepEqual(requests.map(r=>r.method),['authenticate']);
  const {loadConfig}=await import('../server/config.mjs');
  const saved=await loadConfig();assert.equal(saved.host,config.host);assert.equal(saved.token,config.token);
});

test('pairing QR and cancel remain available while the old phone preview is stalled', async t => {
  let release, received;
  const started=new Promise(resolve=>{received=resolve;});
  const {config}=await fixture(t,(request,reply)=>{
    if(request.method==='device.preview') {release=reply;received();} else reply({});
  });
  await saveConfig(config);
  const preview=runtimeRequest('/panel',{name:'tapilot_preview_frame',arguments:{}});
  await started;
  let deadline;
  try {
    const paired=await Promise.race([
      runtimeRequest('/panel',{name:'tapilot_preview_pair',arguments:{action:'pair'}}),
      new Promise((_,reject)=>{deadline=setTimeout(()=>reject(Error('QR blocked behind old phone request')),8000);}),
    ]);
    assert.equal(paired.isError,undefined);
    assert.match(paired._meta.pairingQr,/^data:image\/png;base64,/);
    const cancelled=await runtimeRequest('/panel',{name:'tapilot_preview_pair',arguments:{action:'pair_cancel'}});
    assert.equal(cancelled.structuredContent.cancelled,true);
  } finally {
    clearTimeout(deadline);
    release({dataUrl:'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=',capturedAt:Date.now()});
    await preview;
  }
});

test('returning to a remembered address reconnects without discovery or pairing and rejects a reused IP', async t => {
  const {config,requests}=await fixture(t,(request,reply)=>reply({}));
  const other=await fixture(t,()=>{});
  const previous={...config,host:'0.0.0.0',port:other.config.port,
    endpoints:[{host:'127.0.0.1',port:other.config.port},{host:config.host,port:config.port}]};
  await saveConfig(config);
  await saveConfig({...config,host:previous.host,port:previous.port});
  const phone=new PhoneConnection(previous,{
    discover:async()=>{throw Error('remembered address must avoid discovery');},
    find:async()=>{throw Error('remembered address must avoid scanning');},
  });t.after(()=>phone.close());
  await phone.open();
  assert.equal(other.requests.length,0);
  assert.deepEqual(requests.map(r=>r.method),['authenticate']);
  const {loadConfig}=await import('../server/config.mjs');
  const saved=await loadConfig();
  assert.equal(saved.host,config.host);
  assert.ok(saved.endpoints.some(e=>e.host===previous.host && e.port===previous.port));
});

test('panel resume returns a viewer-scoped frame on its first response', async t => {
 const png='data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=';
 const {config,requests}=await fixture(t,(request,reply)=>reply({dataUrl:png,capturedAt:Date.now()}));
 await saveConfig(config);
 const result=await runtimeRequest('/panel',{name:'tapilot_preview_resume',arguments:{viewer_id:'resume-viewer'}});
 assert.equal(result.structuredContent.state,'live');
 assert.equal(result.structuredContent.dataUrl,png);
 assert.ok(result.structuredContent.frame_id);
 assert.equal(requests.filter(r=>r.method==='device.preview').length,1);
});
