import { execFileSync } from 'node:child_process';
import { createHash, randomUUID } from 'node:crypto';
import { mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { hasExpectedScreenshotScale } from './lib/controlled-evidence.mjs';

const serial = process.env.TAPILOT_E2E_DEVICE_ID;
if (!serial) throw new Error('Set TAPILOT_E2E_DEVICE_ID; never select the first device');
const samples = Number(process.env.TAPILOT_PERF_SAMPLES || 3);
const maxWallMs = Number(process.env.TAPILOT_PERF_MAX_WALL_MS || 0);
if (!Number.isInteger(samples) || samples < 1 || samples > 10 || !Number.isFinite(maxWallMs) || maxWallMs < 0)
  throw new Error('Invalid performance sample count or wall-time budget');
const directory = resolve(process.env.TAPILOT_PERF_OUTPUT || 'build/device-performance');
mkdirSync(directory, { recursive: true });
const adb = (args, options = {}) => execFileSync('adb', ['-s', serial, ...args], {
  encoding: 'utf8', timeout: 30_000, maxBuffer: 8 * 1024 * 1024, ...options,
});
if (adb(['get-state']).trim() !== 'device') throw new Error('Selected device is not ready');
if (/lifeos\.md\.tapilot\/\.TaskRuntimeService/.test(adb(['shell', 'dumpsys', 'activity', 'services', 'lifeos.md.tapilot'])))
  throw new Error('A native task service is active; do not restart its app for instrumentation');
const abi = adb(['shell', 'getprop', 'ro.product.cpu.abilist']).trim();
if (!abi.split(',').includes('arm64-v8a')) throw new Error('Embedded Node requires an arm64-v8a Android device');
const identity = {
  serial, abi, model: adb(['shell', 'getprop', 'ro.product.model']).trim(),
  sdk: adb(['shell', 'getprop', 'ro.build.version.sdk']).trim(),
  commit: execFileSync('git', ['rev-parse', 'HEAD'], { encoding: 'utf8' }).trim(),
  dirty: !!execFileSync('git', ['status', '--porcelain'], { encoding: 'utf8' }).trim(),
};
if (process.env.TAPILOT_PERF_APK) identity.apkSha256 = createHash('sha256')
  .update(readFileSync(process.env.TAPILOT_PERF_APK)).digest('hex');
const installedPath = adb(['shell', 'pm', 'path', 'lifeos.md.tapilot']).trim().split('\n')[0].replace(/^package:/, '');
if (!/^\/data\/app\/[A-Za-z0-9_~/.+=-]+\.apk$/.test(installedPath)) throw new Error('Unexpected installed APK path');
identity.installedApkSha256 = adb(['shell', 'sha256sum', installedPath]).split(/\s/)[0];
if (identity.apkSha256 && identity.apkSha256 !== identity.installedApkSha256) throw new Error('Installed APK differs from the benchmark build');
const fixture = 'cache/performance-model.json';
const collectOnly = process.argv[2] === '--collect-only';
const runId = collectOnly ? process.argv[3] : randomUUID();
if (!/^[a-f0-9-]{36}$/.test(runId || '')) throw new Error('Collect-only requires the exact native run UUID');
identity.runId = runId;
writeFileSync(`${directory}/identity.json`, JSON.stringify(identity, null, 2));
rmSync(`${directory}/result.json`, { force: true });
let provisioned = false;
let failure;
try {
  if (!collectOnly) {
    if (process.env.TAPILOT_PERF_PROVISION_MODEL === '1') {
      if (process.env.TAPILOT_PERF_DEDICATED_DEVICE !== '1') throw new Error('Provision models only on an explicitly dedicated CI device');
      const config = Object.fromEntries(Object.entries({ baseUrl: 'MIDSCENE_MODEL_BASE_URL', modelName: 'MIDSCENE_MODEL_NAME',
        modelFamily: 'MIDSCENE_MODEL_FAMILY', apiKey: 'MIDSCENE_MODEL_API_KEY' }).map(([field, env]) => {
        if (!process.env[env]) throw new Error(`Missing ${env}`);
        return [field, process.env[env]];
      }));
      // No secret in argv/stdout. run-as confines the fixture to the debug app's private cache.
      provisioned = true;
      adb(['shell', 'run-as', 'lifeos.md.tapilot', 'tee', fixture], { input: JSON.stringify(config), stdio: ['pipe', 'ignore', 'pipe'] });
      adb(['shell', 'run-as', 'lifeos.md.tapilot', 'chmod', '600', fixture]);
    }
    const output = adb(['shell', 'am', 'instrument', '-w', '-e', 'samples', String(samples), '-e', 'run_id', runId,
      '-e', 'manual_accessibility_rebind', process.env.TAPILOT_PERF_MANUAL_ACCESSIBILITY || '0',
      '-e', 'screenshot_ab', process.env.TAPILOT_PERF_SCREENSHOT_AB || '0',
      'lifeos.md.tapilot.test/lifeos.md.tapilot.performance.NativeTaskPerformanceHarness'], { timeout: samples * 11 * 60_000 });
    writeFileSync(`${directory}/instrumentation.txt`, output);
  }
} catch (error) {
  // Provider output/credentials must not be copied into CI diagnostics.
  failure = new Error(`Device performance execution failed (${error.code || error.name})`);
} finally {
  if (provisioned) {
    try { adb(['shell', 'run-as', 'lifeos.md.tapilot', 'rm', '-f', fixture]); } catch {}
  }
  try {
    const remote = JSON.parse(adb(['shell', 'cat', '/sdcard/Android/data/lifeos.md.tapilot/files/performance/summary.json']));
    if (remote.runId !== runId) throw new Error('Remote artifacts belong to a different run');
    // adb pull merges directories. Remove only this runner-owned artifact folder
    // so an older HTML assertion cannot satisfy the current sample's checks.
    rmSync(`${directory}/native`, { recursive: true, force: true });
    adb(['pull', '/sdcard/Android/data/lifeos.md.tapilot/files/performance/.', `${directory}/native`]);
  } catch { failure ??= new Error('Native performance artifacts are missing'); }
}
if (failure) throw failure;
const summary = JSON.parse(readFileSync(`${directory}/native/summary.json`, 'utf8'));
if (summary.runId !== runId || summary.status !== 'passed' || summary.samples.length !== samples)
  throw new Error('Native task benchmark failed or artifacts belong to an older run; inspect summary and HTML reports');
// Read the original native report dump; deduplicate tasks repeated in agent and
// test-runner HTML. Do not infer success from a completion badge or answer text.
const tasks = new Map();
for (const file of readdirSync(`${directory}/native`).filter(name => name.endsWith('.html'))) {
  const html = readFileSync(`${directory}/native/${file}`, 'utf8');
  const blocks = html.matchAll(/<script\b[^<>]*type=["']midscene_web_dump["'][^<>]*>([\s\S]*?)<\/script>/g);
  for (const [, block] of blocks) {
    if (!block.trimStart().startsWith('{')) continue;
    const visit = value => {
      if (!value || typeof value !== 'object') return;
      if (value.taskId && value.timing) tasks.set(value.taskId, { ...value, sample: Number(file.split('-')[0]) });
      for (const [key, child] of Object.entries(value)) {
        if (!['param', 'output', 'recorder', 'uiContext'].includes(key)) {
          if (Array.isArray(child)) child.forEach(visit); else visit(child);
        }
      }
    };
    visit(JSON.parse(block));
  }
}
for (let sample = 0; sample < samples; sample++) {
  if (![...tasks.values()].some(task => task.sample === sample && task.subType === 'Assert' && task.status === 'finished' && task.output === true))
    throw new Error(`Sample ${sample} has no successful native final-screen assertion`);
  if (summary.screenshotAb) {
    const sampleTasks = [...tasks.values()].filter(task => task.sample === sample);
    const factor = summary.samples[sample].screenshotShrinkFactor;
    if (!sampleTasks.some(task => task.subType === 'Tap' && task.status === 'finished'))
      throw new Error(`Screenshot A/B sample ${sample} has no successful coordinate tap`);
    if (!hasExpectedScreenshotScale(sampleTasks, factor))
      throw new Error(`Screenshot A/B sample ${sample} did not use the requested image scaling`);
  }
}
const wallTimes = summary.samples.map(sample => sample.wallMs).sort((a, b) => a - b);
const percentile = p => wallTimes[Math.ceil(p * wallTimes.length) - 1];
function aggregate(summaries) {
  const totals = {};
  for (const summary of summaries) for (const [phase, value] of Object.entries(summary || {})) {
    const total = totals[phase] ??= { count: 0, totalMs: 0, maxMs: 0, failures: 0 };
    total.count += value.count; total.totalMs += value.totalMs;
    total.maxMs = Math.max(total.maxMs, value.maxMs); total.failures += value.failures;
  }
  for (const value of Object.values(totals)) value.meanMs = value.totalMs / value.count;
  return totals;
}
const result = { ...identity, samples, wallMs: { p50: percentile(0.5), p95: percentile(0.95), max: wallTimes.at(-1) },
  screenshotAb: summary.screenshotAb,
  screenshotSamples: summary.samples.map(sample => ({ index: sample.index, factor: sample.screenshotShrinkFactor, wallMs: sample.wallMs })),
  screenshots: [...tasks.values()].filter(task => task.usage && task.uiContext).map(task => ({ sample: task.sample, phase: task.subType,
    shotSize: task.uiContext.shotSize, ratio: task.uiContext.shrunkShotToLogicalRatio })),
  phases: aggregate(summary.samples.map(sample => sample.performance)),
  nativeRpc: aggregate(summary.samples.flatMap(sample => sample.operations.map(operation => operation.nativeRpcTimings))),
  midsceneModels: [...tasks.values()].filter(task => task.usage).map(task => ({
    sample: task.sample, phase: task.subType, model: task.usage.model_name,
    promptTokens: task.usage.prompt_tokens, cachedInput: task.usage.cached_input,
    outputTokens: task.usage.completion_tokens, totalTimeMs: task.usage.total_time_cost,
  })),
  launcherCatalogue: summary.launcherCatalogue,
};
writeFileSync(`${directory}/result.json`, JSON.stringify(result, null, 2));
console.log(JSON.stringify(result, null, 2));
if (maxWallMs && result.wallMs.max > maxWallMs) throw new Error(`Native task exceeded ${maxWallMs} ms budget`);
