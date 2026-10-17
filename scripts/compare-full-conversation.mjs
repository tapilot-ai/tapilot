#!/usr/bin/env node
// Controlled desktop execution of the phone's complete agent, including its Kotlin policies.
// Build :app:prepareBenchmarkCodec first. Pair/auth/reset happen outside the task timer.
import { spawn } from 'node:child_process';
import { createInterface } from 'node:readline';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { ChatGPTAuth } from '../plugins/android-use/server/midscene/chatgpt-auth.mjs';
import { PhoneConnection } from '../plugins/android-use/server/transport.mjs';
import { loadConfig, validateConfig } from '../plugins/android-use/server/config.mjs';
import { verifyRuntime, sourceFingerprint } from './runtime-integrity.mjs';

const root = fileURLToPath(new URL('../', import.meta.url));
const fixtureName = process.env.TAPILOT_BENCH_FIXTURE ?? 'read-auto-brightness';
if (!['read-auto-brightness', 'read-android-version'].includes(fixtureName)) throw Error('Unsupported full-task fixture');
const source = JSON.parse(await readFile(join(root, 'app/src/debug/assets/performance/full-tasks.json'), 'utf8'))[fixtureName];
if (process.version !== 'v24.18.0') throw Error('Use Node 24.18.0 to match the phone');
if (!process.env.TAPILOT_BENCH_AUTH_DIR || !process.env.TAPILOT_BENCH_ACCOUNT) throw Error('Explicit matched benchmark auth required');
verifyRuntime(root);
const fingerprint = sourceFingerprint(root);
const bundle = join(root, 'build/node-runtime-bundle');
if (JSON.parse(await readFile(join(bundle, 'manifest.json'))).sourceFingerprint !== fingerprint) throw Error('Stale runtime bundle');
const auth = new ChatGPTAuth(process.env.TAPILOT_BENCH_AUTH_DIR);
await auth.accessToken();
const account = process.env.TAPILOT_BENCH_ACCOUNT;
if ((await auth.status()).account !== account) throw Error('Account mismatch');
const out = join(root, 'artifacts/controlled-execution', `full-desktop-${new Date().toISOString().replaceAll(':', '-')}`);
await mkdir(out, { recursive: true });
const config = await loadConfig();
const connection = new PhoneConnection(validateConfig({ ...config, ...(process.env.TAPILOT_BENCH_HOST ? { host: process.env.TAPILOT_BENCH_HOST } : {}) }));
let codec, agent, runner, watchdog;
const pending = new Map(); let sequence = 0;
const phases = [], modelMetrics = [], executions = [];
function call(method, params) {
  return new Promise((resolve, reject) => {
    const id = String(++sequence); pending.set(id, { resolve, reject });
    codec.stdin.write(JSON.stringify({ id, method, params }) + '\n');
  });
}
async function timed(phase, fn) {
  const t = performance.now();
  try { const result = await fn(); phases.push({ phase, durationMs: performance.now() - t, success: true }); return result; }
  catch (error) { phases.push({ phase, durationMs: performance.now() - t, success: false }); throw error; }
}
let started;
try {
  const classpath = (await readFile(join(root, 'app/build/benchmark-codec/classpath.txt'), 'utf8')).trim();
  codec = spawn(process.env.TAPILOT_BENCH_JAVA ?? '/opt/homebrew/opt/openjdk@17/bin/java', ['-cp', classpath, 'lifeos.md.tapilot.performance.BenchmarkCodec'], { stdio: ['pipe', 'pipe', 'ignore'] });
  await new Promise((resolve, reject) => {
    const startup = setTimeout(() => reject(Error('Codec startup timeout')), 15000);
    const fail = () => { clearTimeout(startup); reject(Error('Codec unavailable')); for (const p of pending.values()) p.reject(Error('Codec exited')); pending.clear(); };
    codec.once('error', fail); codec.once('exit', fail);
    createInterface({ input: codec.stdout }).on('line', line => {
      try {
        const value = JSON.parse(line);
        if (value.ready) { clearTimeout(startup); resolve(); return; }
        const p = pending.get(value.id); pending.delete(value.id);
        if (value.ok) p?.resolve(value.result); else p?.reject(Error(`Codec: ${value.error}`));
      } catch { fail(); }
    });
  });
  await connection.open();
  watchdog = setTimeout(() => { agent?.kill(); runner?.kill(); codec?.kill(); connection.close(); }, 240000);
  started = performance.now();
  const apps = await timed('phone.appCatalog', async () => (await connection.request('device.getCapabilities', {})).apps ?? []);
  const messages = await call('messages', { source });
  agent = spawn(process.execPath, [join(bundle, 'pi-runner.mjs')], { cwd: bundle, stdio: ['pipe', 'pipe', 'ignore'] });
  const agentExit = new Promise((resolve, reject) => { agent.once('exit', resolve); agent.once('error', reject); });
  agent.stdin.write(JSON.stringify({ type: 'init', messages, installedApps: apps, goal: source, resume: [], pendingInputs: [], limits: { phoneOperations: 100, modelTurns: 100 } }) + '\n');
  let result;
  for await (const line of createInterface({ input: agent.stdout })) {
    if (!line.startsWith('TAPILOT_AGENT ')) continue;
    const event = JSON.parse(line.slice('TAPILOT_AGENT '.length));
    if (event.type === 'failure') throw Error('Agent failed');
    if (event.type === 'result') { result = event; continue; }
    if (event.type !== 'rpc') continue;
    const { method, params, requestId } = event;
    // Any failed operation ends this benchmark. No mutation is automatically replayed.
    const value = await timed(method, async () => {
      if (method === 'model.complete') {
        const history = structuredClone(params.messages);
        for (const m of history) if (m.tapilotResponsesAccount !== account) delete m.tapilotResponsesOutput;
        const body = await call('modelBody', { model: 'gpt-6-luna', messages: history, tools: params.tools });
        const response = await fetch('https://api.openai.com/v1/responses', {
          method: 'POST', headers: { Authorization: `Bearer ${await auth.accessToken()}`, 'Content-Type': 'application/json', Accept: 'text/event-stream' },
          body: JSON.stringify(body), signal: AbortSignal.timeout(45000),
        });
        const decoded = await call('modelDecode', { status: response.status, bodyBase64: Buffer.from(await response.arrayBuffer()).toString('base64') });
        if (decoded.modelMetrics?.model !== 'gpt-6-luna') throw Error('Actual outer model mismatch');
        modelMetrics.push(decoded.modelMetrics);
        return { ...decoded, tapilotResponsesAccount: account };
      }
      if (method === 'phone.findApps') return { apps: await call('findApps', { apps, query: params.query }) };
      if (method !== 'phone.execute') throw Error('Unsupported benchmark RPC');
      const task = await call('verifiedTask', { apps, ...params, app: params.app ?? '', mode: params.mode ?? 'act' });
      const taskFile = join(out, `task-${executions.length}.json`);
      await writeFile(taskFile, task);
      // Release the catalog connection before the exact execution harness acquires it.
      connection.close();
      runner = spawn(process.execPath, [join(root, 'scripts/compare-native-desktop.mjs'), 'run'], {
        cwd: root, env: { ...process.env, TAPILOT_BENCH_TASK_FILE: taskFile, TAPILOT_BENCH_CAPTURE_FORMAT: 'jpeg' }, stdio: ['ignore', 'pipe', 'ignore'],
      });
      let stdout = ''; runner.stdout.on('data', b => { stdout += b; });
      const code = await new Promise((resolve, reject) => { runner.once('error', reject); runner.once('exit', resolve); });
      if (code !== 0) throw Error('Phone execution failed; inspect screen, no replay');
      const summary = JSON.parse(stdout.trim().split('\n').at(-1));
      const measurement = JSON.parse(await readFile(join(summary.out, 'measurement.json')));
      if (measurement.status !== 'verified') throw Error('Unverified operation');
      executions.push({ out: summary.out, measurement,
        finishWhenVerifiedRequested: typeof params.finishWhenVerified === 'boolean' ? params.finishWhenVerified : null });
      return { success: true, result: measurement.queries[0].observation };
    });
    agent.stdin.write(JSON.stringify({ requestId, ok: true, result: value }) + '\n');
  }
  if (await agentExit !== 0 || result?.phase !== 'completed' || result.lastPhoneResult?.success !== true || !executions.length) throw Error('Full task not verified');
  if (sourceFingerprint(root) !== fingerprint) throw Error('Sources changed during measurement');
  const wallMs = performance.now() - started;
  await writeFile(join(out, 'measurement.json'), JSON.stringify({ status: 'verified', fixtureName, source, sourceFingerprint: fingerprint, nodeVersion: process.version,
    wallMs, phases, modelMetrics, executions, result, environmentProxyEnabled: process.env.NODE_USE_ENV_PROXY === '1',
    boundary: 'Desktop same-agent full conversation; includes catalog, Kotlin IPC, Pi startup, inner harness auth/connection/startup; excludes preflight and JVM warmup. Not Codex UI timing.' }, null, 2));
  console.log(JSON.stringify({ out, status: 'verified', wallMs }));
} catch (error) {
  await writeFile(join(out, 'measurement.json'), JSON.stringify({ status: 'failed', fixtureName, source, error: error.message, phases, modelMetrics, executions }, null, 2));
  console.error(JSON.stringify({ out, status: 'failed', reason: error.message })); process.exitCode = 1;
} finally { clearTimeout(watchdog); agent?.kill(); runner?.kill(); codec?.kill(); connection.close(); }
