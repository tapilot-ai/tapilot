#!/usr/bin/env node
// No device connection or action capability. Screenshots and credentials stay local.
import { readFile, mkdir, writeFile, rename } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { createRequire } from 'node:module';
import { dirname, resolve, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { parseArgs } from 'node:util';
import { ChatGPTAuth } from '../plugins/android-use/server/midscene/chatgpt-auth.mjs';
import { measuredFetch, siwcFetch } from '../node-runtime/src/siwc-fetch.mjs';
import { verifyPhone } from '../node-runtime/src/phone-verification.mjs';
import { verifyResolved } from './lib/verification-prototype.mjs';
import { resolutionMatches, locatingMatches } from './lib/replay-scoring.mjs';

const root = fileURLToPath(new URL('../', import.meta.url));
const { values: args } = parseArgs({ options: {
  screenshots: { type: 'string' }, output: { type: 'string' },
  mode: { type: 'string', default: 'verification' }, cases: { type: 'string' },
  variants: { type: 'string' },
  repeats: { type: 'string', default: '3' }, only: { type: 'string' },
  'auth-dir': { type: 'string' }, 'account-fingerprint': { type: 'string' },
} });
if (!['verification', 'locating'].includes(args.mode)) throw Error('Mode must be verification or locating');
args.cases ??= `tests/verification/${args.mode === 'locating' ? 'locating-cases' : 'cases'}.json`;
const allowedVariants = args.mode === 'locating' ? ['ordinary', 'deep', 'smaller'] : ['baseline', 'resolved'];
const selectedVariants = args.variants?.split(',') ?? allowedVariants;
if (selectedVariants.some(v => !allowedVariants.includes(v)) || new Set(selectedVariants).size !== selectedVariants.length) throw Error('Unknown or duplicate variant');
const repeats = Number(args.repeats);
if (!args.screenshots || !args.output || !args['auth-dir'] || !/^[a-f0-9]{16}$/.test(args['account-fingerprint'] ?? '') ||
    !Number.isInteger(repeats) || repeats < 1 || repeats > 10) throw Error('Specify screenshots, output, auth-dir, verified account-fingerprint and repeats 1..10');
const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');
const manifestFile = resolve(args.screenshots);
const manifest = JSON.parse(await readFile(manifestFile, 'utf8'));
const allCases = JSON.parse(await readFile(resolve(args.cases), 'utf8'));
if (!Array.isArray(allCases) || new Set(allCases.map(c => c.id)).size !== allCases.length) throw Error('Invalid or duplicate cases');
const selectedIds = args.only?.split(',');
if (selectedIds?.some(id => !allCases.some(c => c.id === id))) throw Error('Unknown selected case');
const cases = allCases.filter(c => !args.only || args.only.split(',').includes(c.id));
if (!cases.length) throw Error('No selected cases');
const screens = new Map();
for (const c of cases) {
  if (!/^[a-z0-9-]+$/.test(c.id) || typeof c.expected !== 'boolean') throw Error('Invalid replay case');
  if (args.mode === 'locating' && (typeof c.prompt !== 'string' || !c.prompt.trim() || c.expected !== true ||
      !Array.isArray(c.center) || c.center.length !== 2 || !c.center.every(Number.isFinite) ||
      !Number.isFinite(c.radius) || c.radius <= 0)) throw Error('Invalid locating fixture');
  const source = manifest[c.screenshot];
  if (!source || !/^[a-f0-9]{64}$/.test(source.sha256) || !['image/png', 'image/jpeg'].includes(source.mimeType) ||
      !Number.isInteger(source.width) || source.width <= 0 || !Number.isInteger(source.height) || source.height <= 0) throw Error(`Invalid screenshot manifest: ${c.screenshot}`);
  const bytes = await readFile(resolve(dirname(manifestFile), source.path));
  if (sha256(bytes) !== source.sha256) throw Error(`Screenshot changed: ${c.screenshot}`);
  screens.set(c.screenshot, { ...source, data: `data:${source.mimeType};base64,${bytes.toString('base64')}` });
}
const auth = new ChatGPTAuth(resolve(args['auth-dir']));
const status = await auth.status();
if (!status.authorized || sha256(status.account?.trim().toLowerCase() ?? '').slice(0, 16) !== args['account-fingerprint']) throw Error('Authorization does not match the verified phone account');
const out = resolve(args.output);
await mkdir(out, { recursive: false, mode: 0o700 }); // Never overwrite an earlier run.
for (const key of Object.keys(process.env)) if (key.startsWith('MIDSCENE_')) delete process.env[key];
process.env.DEBUG = ''; // Do not let a caller's SDK debug profile print model bodies.
process.env.MIDSCENE_RUN_DIR = join(out, 'midscene');
// Streaming is a global SDK setting, not part of an Agent's isolated modelConfig.
process.env.MIDSCENE_MODEL_STREAM_MODE = 'stream';
// SDK error logging can contain raw response headers. Keep only bounded metadata.
const originalWarn = console.warn;
console.warn = (...args) => {
  const error = args.find(value => value instanceof Error);
  process.stderr.write(JSON.stringify({ sdkDiagnostic: true, ...(error ? { error: error.name, status: error.status } : {}) }) + '\n');
};
const modelConfig = {
  MIDSCENE_MODEL_NAME: 'gpt-6-luna', MIDSCENE_MODEL_FAMILY: 'gpt-6',
  MIDSCENE_MODEL_BASE_URL: 'https://api.openai.com/v1', MIDSCENE_MODEL_API_KEY: 'oauth-managed',
  MIDSCENE_MODEL_PROTOCOL: 'openai-responses', MIDSCENE_MODEL_STREAM_MODE: 'stream',
  MIDSCENE_MODEL_TIMEOUT: '45000', MIDSCENE_MODEL_RETRY_COUNT: '0',
};
const require = createRequire(join(root, 'build/node-runtime-bundle/package.json'));
const sdk = name => import(pathToFileURL(require.resolve(name)).href);
const { Agent } = await sdk('@midscene/core/agent');
const { callAIWithObjectResponse, getModelRuntime } = await sdk('@midscene/core/ai-model');
const { ModelConfigManager } = await sdk('@midscene/shared/env');
const { imageInfoOfBase64 } = await sdk('@midscene/shared/img');
for (const [name, screen] of screens) {
  const size = await imageInfoOfBase64(screen.data);
  if (size.width !== screen.width || size.height !== screen.height) throw Error(`Screenshot dimensions differ: ${name}`);
}
const runtime = getModelRuntime(new ModelConfigManager(modelConfig).getModelConfig('insight'));
const network = [], samples = [];
const originalFetch = globalThis.fetch;
globalThis.fetch = siwcFetch(measuredFetch(originalFetch, timing => network.push(timing)), force => auth.accessToken(force));
const controller = new AbortController();
const abort = () => controller.abort();
process.once('SIGINT', abort); process.once('SIGTERM', abort);
const metadata = { kind: 'frozen-screenshot-replay', startedAt: new Date().toISOString(), mode: args.mode, model: 'gpt-6-luna',
  midsceneVersion: JSON.parse(await readFile(join(root, 'build/node-runtime-bundle/node_modules/@midscene/core/package.json'), 'utf8')).version,
  nodeVersion: process.version, accountFingerprint: args['account-fingerprint'], repeats, variants: selectedVariants,
  sourceHashes: Object.fromEntries(await Promise.all(['node-runtime/src/phone-verification.mjs', 'scripts/lib/verification-prototype.mjs', 'scripts/lib/arithmetic-verification.mjs', 'scripts/lib/replay-scoring.mjs', 'scripts/replay-verification.mjs', args.cases].map(async p => [p, sha256(await readFile(resolve(root, p)))]))),
  screenshots: Object.fromEntries([...screens].map(([key, s]) => [key, { sha256: s.sha256, width: s.width, height: s.height }])),
};
// Preserve the exact experiment source, including rejected candidates.
for (const path of Object.keys(metadata.sourceHashes)) {
  const destination = join(out, 'sources', path === args.cases ? 'fixtures.json' : path);
  await mkdir(dirname(destination), { recursive: true });
  await writeFile(destination, await readFile(resolve(root, path)), { mode: 0o600 });
}
async function save() {
  await writeFile(join(out, 'results.pending.json'), JSON.stringify({ ...metadata, samples }, null, 2), { mode: 0o600 });
  await rename(join(out, 'results.pending.json'), join(out, 'results.json'));
}
try {
  await save();
  for (let repeat = 1; repeat <= repeats; repeat++) for (const c of cases) {
    const variants = [...selectedVariants];
    if (repeat % 2 === 0) variants.reverse();
    for (const variant of variants) {
      controller.signal.throwIfAborted();
      const screen = screens.get(c.screenshot), start = performance.now(), networkStart = network.length;
      const agent = new Agent({ interfaceType: 'android', actionSpace: () => [],
        screenshotBase64: async () => screen.data,
        size: async () => ({ width: screen.width, height: screen.height }),
        describe: () => 'Frozen Android screenshot; no device or action access',
      }, { modelConfig, screenshotShrinkFactor: variant === 'smaller' ? 1.5 : 1, generateReport: true, autoPrintReportMsg: false,
        reportFileName: `${c.id}-${repeat}-${variant}`, cache: false });
      const sample = { case: c.id, repeat, variant, expected: c.expected, screenshotSha256: screen.sha256 };
      try {
        const query = (prompt, signal) => agent.aiQuery(prompt, { abortSignal: signal });
        if (args.mode === 'locating') {
          sample.screenshotShrinkFactor = variant === 'smaller' ? 1.5 : 1;
          sample.coordinateSpace = 'prepared-screenshot';
          sample.result = await agent.aiLocate(c.prompt, { deepLocate: variant === 'deep', cacheable: false, abortSignal: controller.signal });
          sample.originalCenter = sample.result?.center?.map(value => value * sample.screenshotShrinkFactor);
          sample.correct = locatingMatches(sample.result, c, sample.screenshotShrinkFactor);
        } else {
          sample.result = variant === 'baseline'
          ? (await verifyPhone(async ctx => ({ data: await query(ctx.input.prompt, ctx.signal) }), { signal: controller.signal }, { condition: JSON.stringify(c.contract) })).data
          : await verifyResolved({ query, signal: controller.signal,
              normalize: async (messages, signal) => (await callAIWithObjectResponse(messages, runtime, { abortSignal: signal, retryTimes: 0 })).content }, c.contract);
          sample.correct = sample.result.success === c.expected;
          if (variant === 'resolved') sample.resolutionCorrect = resolutionMatches(sample.result.resolution, c.resolution);
        }
      } catch (error) {
        sample.correct = false; sample.error = error.name; // Never persist error bodies or auth data.
      } finally {
        try { await agent.destroy(); } catch (error) { sample.correct = false; sample.cleanupError = error.name; }
        sample.report = agent.reportFile;
        sample.elapsedMs = Math.round(performance.now() - start);
        sample.http = network.slice(networkStart);
        samples.push(sample); await save();
        console.log(JSON.stringify({ case: c.id, repeat, variant, correct: sample.correct, resolutionCorrect: sample.resolutionCorrect,
          elapsedMs: sample.elapsedMs, requests: sample.http.length, ...(sample.error ? { error: sample.error } : {}) }));
      }
    }
  }
} finally {
  globalThis.fetch = originalFetch;
  console.warn = originalWarn;
  process.removeListener('SIGINT', abort); process.removeListener('SIGTERM', abort);
  await save();
}
if (samples.some(s => !s.correct || s.resolutionCorrect === false)) process.exitCode = 1;
