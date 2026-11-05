// A real MCP Apps SDK host in Chromium, backed by the production STDIO server
// and runtime with an isolated TLS phone fixture. This is not native-client or
// Android hardware acceptance. No model credentials or real phone are used.
import { createServer } from 'node:http';
import tls from 'node:tls';
import { createHash } from 'node:crypto';
import { mkdtemp, mkdir, readFile, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';
import { setTimeout as delay } from 'node:timers/promises';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { chromium } from 'playwright-core';
import sharp from 'sharp';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js';
import { saveConfig } from '../server/config.mjs';
import { runtimeRequest } from '../server/runtime-client.mjs';
import { SCREEN_URI } from '../server/preview.mjs';

const root = fileURLToPath(new URL('../', import.meta.url));
const output = process.env.TAPILOT_PANEL_EVIDENCE || join(root, '../../build/client-validation/panels');
await mkdir(output, { recursive: true });
const dir = await mkdtemp(join(tmpdir(), 'tapilot-panel-browser-'));
const env = { ...process.env, TAPILOT_STATE_DIR: join(dir, 'state'), TAPILOT_CONFIG: join(dir, 'wireless.json'), TAPILOT_INSTALLATIONS_DIR: join(dir, 'isolated-installations') };
const clients = new Map(), sockets = new Set(), results = [];
let browser, web, phone, origin, actions = 0, captures = 0, resourceReads = 0;
try {
  execFileSync('openssl', ['req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-keyout', join(dir, 'key.pem'), '-out', join(dir, 'cert.pem'), '-subj', '/CN=Tapilot browser fixture', '-days', '1'], { stdio: 'ignore' });
  const key = await readFile(join(dir, 'key.pem')), cert = await readFile(join(dir, 'cert.pem'));
  const der = Buffer.from(cert.toString().replace(/-----[^-]+-----/g, '').replace(/\s/g, ''), 'base64');
  const dataUrl = 'data:image/png;base64,' + (await sharp({ create: { width: 200, height: 400, channels: 3, background: '#a1b5c9' } }).png().toBuffer()).toString('base64');
  phone = tls.createServer({ key, cert, minVersion: 'TLSv1.2', maxVersion: 'TLSv1.2' }, socket => {
    sockets.add(socket); socket.on('close', () => sockets.delete(socket)); socket.on('error', () => {});
    let buffer = '';
    socket.on('data', bytes => {
      buffer += bytes;
      let end;
      while ((end = buffer.indexOf('\n')) >= 0) {
        const call = JSON.parse(buffer.slice(0, end)); buffer = buffer.slice(end + 1);
        let result = {};
        if (call.method === 'authenticate') result = { protocolVersion: 1 };
        else if (['device.preview', 'device.capture'].includes(call.method)) result = { dataUrl, capturedAt: Date.now(), geometry: { frameId: `fixture-${++captures}`, imageWidth: 200, imageHeight: 400 } };
        else if (call.method === 'device.execute') actions++;
        socket.write(JSON.stringify({ id: call.id, ok: true, result }) + '\n');
      }
    });
  });
  await new Promise(resolve => phone.listen(0, '127.0.0.1', resolve));
  await saveConfig({ version: 1, host: '127.0.0.1', port: phone.address().port, fingerprint: createHash('sha256').update(der).digest('hex'), token: 'a'.repeat(43) }, env.TAPILOT_CONFIG);
  const hostCode = await build({ stdin: { contents: `
    import {AppBridge,PostMessageTransport} from '@modelcontextprotocol/ext-apps/app-bridge';
    const host=new URL(location.href).searchParams.get('host');
    const frame=document.querySelector('iframe');
    const bridge=new AppBridge(null,{name:'Tapilot test host',version:'1'},{serverTools:{},serverResources:{},openLinks:{}},{hostContext:{theme:'light',locale:'en-US',displayMode:'inline',availableDisplayModes:['inline','fullscreen']}});
    bridge.onrequestdisplaymode=async({mode})=>({mode:bridge.getAppCapabilities()?.availableDisplayModes?.includes(mode)?mode:'inline'});
    const rpc=async(method,params)=>{const r=await fetch('/rpc?host='+host+'&method='+method,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(params)});if(!r.ok)throw Error(await r.text());return r.json();};
    bridge.oncalltool=params=>rpc('call',params);
    bridge.onreadresource=params=>rpc('read',params);
    bridge.onopenlink=async()=>({});
    bridge.onsizechange=()=>{};
    await bridge.connect(new PostMessageTransport(frame.contentWindow,frame.contentWindow));
    frame.src='/frame?host='+host;
  `, resolveDir: root }, bundle: true, format: 'esm', write: false });
  web = createServer(async (req, res) => {
    try {
      if (req.headers.host !== new URL(origin).host || (req.headers.origin && req.headers.origin !== origin)) { res.writeHead(403); return res.end(); }
      const url = new URL(req.url, origin), host = url.searchParams.get('host');
      res.setHeader('Cache-Control', 'no-store');
      if (url.pathname === '/') { res.setHeader('Content-Type', 'text/html'); return res.end('<!doctype html><title>Tapilot MCP Apps SDK host</title><iframe title="Tapilot" style="width:440px;height:850px;border:0"></iframe><script type="module" src="/host.js"></script>'); }
      if (url.pathname === '/host.js') { res.setHeader('Content-Type', 'text/javascript'); return res.end(hostCode.outputFiles[0].text); }
      const client = clients.get(host);
      if (!client) throw new Error('Unknown test host');
      if (url.pathname === '/frame') { const result = await client.readResource({ uri: SCREEN_URI }); res.setHeader('Content-Type', 'text/html'); return res.end(result.contents[0].text); }
      if (url.pathname !== '/rpc' || req.method !== 'POST') { res.writeHead(404); return res.end(); }
      let body = ''; for await (const chunk of req) { body += chunk; if (body.length > 65536) throw new Error('Request too large'); }
      const params = JSON.parse(body);
      const result = url.searchParams.get('method') === 'read' ? (resourceReads++, await client.readResource(params)) : await client.callTool(params);
      res.setHeader('Content-Type', 'application/json'); res.end(JSON.stringify(result));
    } catch (error) { res.writeHead(400); res.end(error.message); }
  });
  await new Promise(resolve => web.listen(0, '127.0.0.1', resolve));
  origin = `http://127.0.0.1:${web.address().port}`;
  const executablePath = process.env.TAPILOT_CHROME_PATH || (process.platform === 'darwin' ? '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' : undefined);
  browser = await chromium.launch({ executablePath, headless: true });
  for (const host of ['codex', 'doubao', 'workbuddy', 'cursor', 'vscode']) {
    const client = new Client({ name: 'tapilot-panel-browser-test', version: '1' });
    await client.connect(new StdioClientTransport({ command: process.execPath, args: [join(root, 'server/index.mjs')], env: { ...env, TAPILOT_HOST: host }, stderr: 'pipe' }));
    clients.set(host, client);
    const page = await browser.newPage({ viewport: { width: 500, height: 930 } });
    const errors = []; page.on('pageerror', error => errors.push(error.message));
    await page.goto(`${origin}/?host=${host}`);
    const frame = page.frameLocator('iframe');
    await frame.locator('main img').waitFor({ state: 'visible', timeout: 15000 });
    await frame.locator('body').evaluate(() => document.fonts.ready);
    const count = actions;
    if (host === 'workbuddy') {
      assert.equal(await frame.locator('.navigation').isVisible(), false);
      assert.equal(await frame.locator('#host-notice').isVisible(), true);
      assert.ok(resourceReads > 0);
      assert.equal(actions, count);
    } else {
      await frame.getByRole('button', { name: 'Home', exact: true }).click();
      const deadline = Date.now() + 5000;
      while (actions === count && Date.now() < deadline) await delay(20);
      assert.equal(actions, count + 1, await frame.locator('body').innerText());
      await frame.getByRole('button', { name: 'Pause screen', exact: true }).click();
      await frame.locator('main img').waitFor({ state: 'hidden' });
      await frame.locator('#resume').click();
      await frame.locator('main img').waitFor({ state: 'visible' });
    }
    assert.deepEqual(errors, []);
    await page.screenshot({ path: join(output, `${host}.png`), fullPage: true });
    results.push({ host, sdkIframe: 'passed', preview: 'passed', controls: host === 'workbuddy' ? 'read-only' : 'passed', nativeHost: 'not_tested', hardware: 'not_tested' });
    await page.close();
  }
  // Exercise the original browser adapter too, including its panel-only token.
  const preview = JSON.parse(execFileSync(process.execPath, [join(root, 'server/cli.mjs'), 'preview'], { env, encoding: 'utf8' }));
  const page = await browser.newPage({ viewport: { width: 500, height: 930 }, locale: 'en-US' });
  await page.goto(preview.url);
  await page.locator('main img').waitFor({ state: 'visible' });
  assert.equal(new URL(page.url()).hash, '', 'the browser removes its capability from the address bar');
  const before = actions;
  await page.getByRole('button', { name: 'Home', exact: true }).click();
  const deadline = Date.now() + 5000;
  while (actions === before && Date.now() < deadline) await delay(20);
  assert.equal(actions, before + 1);
  await page.getByRole('button', { name: 'Pause screen', exact: true }).click();
  await page.locator('main img').waitFor({ state: 'hidden' });
  await page.locator('#resume').click();
  await page.locator('main img').waitFor({ state: 'visible' });
  await page.screenshot({ path: join(output, 'browser.png'), fullPage: true });
  results.push({ host: 'browser', preview: 'passed', controls: 'passed', hardware: 'not_tested' });
  await page.close();
  await writeFile(join(output, 'results.json'), JSON.stringify({ results, captures, actions, resourceReads }, null, 2));
  console.log(JSON.stringify({ results, captures, actions, resourceReads, evidence: output }));
} finally {
  await browser?.close();
  for (const client of clients.values()) await client.close();
  const saved = { state: process.env.TAPILOT_STATE_DIR, config: process.env.TAPILOT_CONFIG };
  process.env.TAPILOT_STATE_DIR = env.TAPILOT_STATE_DIR; process.env.TAPILOT_CONFIG = env.TAPILOT_CONFIG;
  await runtimeRequest('/stop', {}, { start: false }).catch(() => {});
  if (saved.state === undefined) delete process.env.TAPILOT_STATE_DIR; else process.env.TAPILOT_STATE_DIR = saved.state;
  if (saved.config === undefined) delete process.env.TAPILOT_CONFIG; else process.env.TAPILOT_CONFIG = saved.config;
  web?.closeAllConnections(); if (web) await new Promise(resolve => web.close(resolve));
  for (const socket of sockets) socket.destroy(); if (phone) await new Promise(resolve => phone.close(resolve));
  await rm(dir, { recursive: true, force: true });
}
