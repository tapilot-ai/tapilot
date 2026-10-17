#!/usr/bin/env node
// Identical screenshot request on desktop Node and the app's Android Node/UID.
import { spawn, execFileSync } from 'node:child_process';
import { createInterface } from 'node:readline';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';
import { ChatGPTAuth } from '../plugins/android-use/server/midscene/chatgpt-auth.mjs';
const root = fileURLToPath(new URL('../', import.meta.url));
const [serial, report] = process.argv.slice(2);
// Explicit diagnostic only: compare Android VPN/TUN routing with its local HTTP proxy.
// Never accept a remote proxy or credentials in a command-line environment value.
const phoneProxy = process.env.TAPILOT_BENCH_PHONE_PROXY;
if (phoneProxy && !/^http:\/\/127\.0\.0\.1:([1-9]\d{0,4})$/.test(phoneProxy)) throw Error('Phone proxy must be localhost HTTP');
if (phoneProxy && Number(new URL(phoneProxy).port) > 65535) throw Error('Invalid phone proxy port');
if (!serial || !report || !process.env.TAPILOT_BENCH_AUTH_DIR || !process.env.TAPILOT_BENCH_ACCOUNT) throw Error('Specify serial, saved report, benchmark auth/account');
if (process.version !== 'v24.18.0') throw Error('Use Node 24.18.0');
const auth = new ChatGPTAuth(process.env.TAPILOT_BENCH_AUTH_DIR);
if ((await auth.status()).account !== process.env.TAPILOT_BENCH_ACCOUNT) throw Error('Account mismatch');
const source = await readFile(report, 'utf8');
let image, body;
const savedRequest = report.endsWith('.json');
if (savedRequest) {
  const parsed = JSON.parse(source);
  if (parsed.model !== 'gpt-6-luna' || parsed.store !== false || parsed.stream !== true) throw Error('Unsupported saved request');
  image = source.match(/data:image\/jpeg;base64,[A-Za-z0-9+/=]+/)?.[0];
  body = source;
} else {
  image = [...source.matchAll(/<script[^>]*type="midscene-image"[^>]*>([\s\S]*?)<\/script>/g)].at(-1)?.[1].trim();
  body = JSON.stringify({ model: 'gpt-6-luna', store: false, stream: true, include: ['reasoning.encrypted_content'],
    input: [{ role: 'user', content: [{ type: 'input_text', text: 'Read the auto-brightness switch from this screenshot. Reply with its visible label and on/off state. If absent, say unknown. Do not perform any action.' }, { type: 'input_image', image_url: image }] }] });
}
if (!image?.startsWith('data:image/jpeg;base64,')) throw Error('JPEG screenshot missing');
const out = join(root, 'artifacts/controlled-execution', `model-path-${new Date().toISOString().replaceAll(':', '-')}`);
await mkdir(out, { recursive: true });
const adb = (...args) => execFileSync('adb', ['-s', serial, ...args], { encoding: 'utf8', timeout: 15000 });
const apk = adb('shell', 'pm', 'path', 'lifeos.md.tapilot').trim().replace(/^package:/, '');
if (!apk.startsWith('/data/app/') || !apk.endsWith('/base.apk') || /\s/.test(apk)) throw Error('Unexpected APK path');
const library = join(dirname(apk), 'lib/arm64');
const remoteScript = 'files/benchmark-model-worker.mjs';
const upload = '/data/local/tmp/tapilot-benchmark-model-worker.mjs';
adb('push', join(root, 'scripts/benchmark-model-worker.mjs'), upload);
try { adb('shell', 'run-as', 'lifeos.md.tapilot', 'cp', upload, remoteScript); }
finally { adb('shell', 'rm', '-f', upload); }
function worker(command, args) {
  const child = spawn(command, args, { stdio: ['pipe', 'pipe', 'ignore'] });
  let pending;
  function wait(id) {
    return new Promise((resolve, reject) => {
      const sent = performance.now(); let receiptMs;
      let timer = setTimeout(() => pending?.reject(Error(id ? 'Worker receipt timeout' : 'Worker startup timeout')), 10000);
      pending = {
        id,
        receive(value) {
          if (value !== id) { this.reject(Error('Worker receipt mismatch')); return; }
          receiptMs = performance.now() - sent;
          clearTimeout(timer);
          timer = setTimeout(() => this.reject(Error('Worker response timeout')), 60000);
        },
        resolve(value) { clearTimeout(timer); pending = undefined; resolve(id ? { ...value, receiptMs } : value); },
        reject(error) { clearTimeout(timer); pending = undefined; reject(error); },
      };
    });
  }
  const ready = wait();
  child.on('error', () => pending?.reject(Error('Worker error')));
  child.on('exit', () => pending?.reject(Error('Worker exited')));
  child.stdin.on('error', () => pending?.reject(Error('Worker input closed')));
  createInterface({ input: child.stdout }).on('line', line => {
    try { const value = JSON.parse(line); if (value.received) pending?.receive(value.received); else pending?.resolve(value); }
    catch { pending?.reject(Error('Invalid worker output')); }
  });
  return { child, ready, request(value) { const result = wait(value.id); child.stdin.write(JSON.stringify(value) + '\n'); return result; } };
}
let desktop, phone, watchdog;
const measurements = [];
try {
  desktop = worker(process.execPath, [join(root, 'scripts/benchmark-model-worker.mjs'), join(root, 'node-runtime/src/siwc-fetch.mjs')]);
  const quote = value => "'" + value.replaceAll("'", "'\\''") + "'";
  const proxyEnvironment = phoneProxy ? `NODE_USE_ENV_PROXY=1 HTTPS_PROXY=${quote(phoneProxy)} HTTP_PROXY=${quote(phoneProxy)} ` : '';
  const command = `${proxyEnvironment}LD_LIBRARY_PATH=${quote(library)} exec ${quote(join(library, 'libnodebin.so'))} ${quote(remoteScript)} ${quote('/data/user/0/lifeos.md.tapilot/files/node-runtime/siwc-fetch.mjs')}`;
  phone = worker('adb', ['-s', serial, 'shell', '-T', `run-as lifeos.md.tapilot sh -c ${quote(command)}`]);
  watchdog = setTimeout(() => { desktop.child.kill(); phone.child.kill(); }, 240000);
  const readiness = await Promise.all([desktop.ready, phone.ready]);
  for (const ready of readiness) if (ready.node !== 'v24.18.0') throw Error('Worker runtime mismatch');
  if (readiness[0].environmentProxyEnabled !== (process.env.NODE_USE_ENV_PROXY === '1') ||
      readiness[1].environmentProxyEnabled !== Boolean(phoneProxy)) throw Error('Worker proxy configuration mismatch');
  for (const host of ['desktop', 'phone', 'phone', 'desktop', 'desktop', 'phone', 'phone', 'desktop']) {
    const id = `${host}-${measurements.length + 1}`;
    const result = await ({ desktop, phone }[host]).request({ id, token: await auth.accessToken(), body, transport: savedRequest ? 'siwc-request' : 'direct' });
    if (result.id !== id || result.receiptMs === undefined) throw Error('Missing or mismatched worker receipt');
    if (result.requestSha256 !== createHash('sha256').update(body).digest('hex')) throw Error('Request byte mismatch');
    measurements.push({ host, ...result });
    await writeFile(join(out, 'measurement.json'), JSON.stringify({ requestKind: savedRequest ? 'saved-model-request' : 'short-visual-query', screenshotSha256: createHash('sha256').update(image).digest('hex'), requestSha256: result.requestSha256,
      nodeVersion: process.version, desktopEnvironmentProxyEnabled: process.env.NODE_USE_ENV_PROXY === '1', phoneLocalProxyEnabled: Boolean(phoneProxy), measurements }, null, 2));
    console.log(JSON.stringify({ host, outcome: result.outcome, headersMs: result.headersMs, totalMs: result.totalMs }));
    if (result.outcome !== 'complete') throw Error('Request failed; sample retained, no automatic retry');
  }
  console.log(JSON.stringify({ out, status: 'verified', samples: measurements.length }));
} finally {
  clearTimeout(watchdog); desktop?.child.kill(); phone?.child.kill();
  adb('shell', 'run-as', 'lifeos.md.tapilot', 'rm', '-f', remoteScript);
}
