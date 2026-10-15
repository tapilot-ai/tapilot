import { createRequire } from 'node:module';
import { dirname, join, resolve, basename } from 'node:path';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { homedir } from 'node:os';

const backend = process.argv.includes('--wasm') ? 'wasm' : 'native';
const live = process.argv.includes('--model');
if (live && backend === 'wasm') throw new Error('Run network comparison separately from the simulated Android backend');
if (backend === 'wasm') Object.defineProperty(process, 'platform', { value: 'android' });
const require = createRequire(new URL('../build/node-runtime-bundle/package.json', import.meta.url));
const { prepareRawScreenshot } = require(join(dirname(require.resolve('@midscene/core')), 'agent/screenshot-preparation.js'));
const sharp = require('sharp');
const output = resolve(`build/image-compression/${backend}`);
await mkdir(output, { recursive: true });
const fixtures = [
  { file: 'build/performance-baseline/restore-service.png', expected: ['Tapilot设备控制', '快捷启用', '已关闭'] },
  { file: 'build/performance-baseline/oppo-menu.png', expected: ['新对话', 'Use the phone Calculator app'] },
];
const variants = [
  { name: 'png-original', factor: null },
  { name: 'jpeg90-full', factor: 1 },
  { name: 'jpeg90-shrink1.5', factor: 1.5 },
  { name: 'jpeg90-shrink2', factor: 2 },
];
const percentile = (values, p) => [...values].sort((a, b) => a - b)[Math.ceil(values.length * p) - 1];
const encoded = new Map();
const rows = [];
for (const fixture of fixtures) {
  const bytes = await readFile(fixture.file);
  const source = `data:image/png;base64,${bytes.toString('base64')}`;
  const sourceInfo = await sharp(bytes).metadata();
  for (const variant of variants) {
    const prepare = async () => variant.factor === null ? source
      : (await prepareRawScreenshot(source, { shrinkFactor: variant.factor })).base64;
    const firstStarted = performance.now();
    let prepared = await prepare();
    const firstMs = performance.now() - firstStarted;
    const durations = [];
    for (let i = 0; i < 7; i++) {
      const started = performance.now();
      prepared = await prepare();
      durations.push(performance.now() - started);
    }
    const image = Buffer.from(prepared.split(',')[1], 'base64');
    const info = await sharp(image).metadata();
    const id = `${basename(fixture.file, '.png')}-${variant.name}`;
    const file = join(output, `${id}.${info.format === 'jpeg' ? 'jpg' : 'png'}`);
    await writeFile(file, image);
    rows.push({ fixture: basename(fixture.file), variant: variant.name, backend,
      originalSize: [sourceInfo.width, sourceInfo.height], size: [info.width, info.height],
      bytes: image.length, dataUrlBytes: Buffer.byteLength(prepared), firstMs,
      encodeP50Ms: percentile(durations, 0.5), encodeP95Ms: percentile(durations, 0.95), file });
    encoded.set(id, prepared);
  }
}
await writeFile(join(output, 'encoding.json'), JSON.stringify(rows, null, 2));
console.log(JSON.stringify(rows.map(({ file, ...row }) => row), null, 2));

if (live) {
  const { ChatGPTAuth } = await import('../plugins/android-use/server/midscene/chatgpt-auth.mjs');
  const { createChatGPTClient } = await import('../plugins/android-use/server/midscene/chatgpt-client.mjs');
  const auth = new ChatGPTAuth(join(homedir(), '.local/share/android-use/chatgpt'));
  const model = process.env.TAPILOT_IMAGE_BENCHMARK_MODEL || 'gpt-6-luna';
  const models = await auth.models();
  if (!models.some(item => item.slug === model)) throw new Error('Selected benchmark model is unavailable to the authorized account');
  const requests = [];
  let current;
  const client = await createChatGPTClient(auth, { onMetrics: metrics => { current = metrics; } })();
  const normalize = text => text.replace(/\s/g, '').toLowerCase();
  // Serial, alternating format order. The same prompt/image content is reused
  // across three rounds; report cache counts rather than hiding cache effects.
  for (let round = 0; round < 3; round++) {
    const order = round % 2 ? [...variants].reverse() : variants;
    for (const fixture of fixtures) for (const variant of order) {
      const id = `${basename(fixture.file, '.png')}-${variant.name}`;
      current = undefined;
      try {
        const response = await client.chat.completions.create({ model, stream: false,
          messages: [{ role: 'user', content: [
            { type: 'text', text: 'Transcribe the visible screen title and up to eight visible interactive labels with their adjacent status text. Keep the original language. Include open-menu items. Return only JSON: {"title":"...","labels":["..."]}. Read only visible pixels; do not infer hidden content.' },
            { type: 'image_url', image_url: { url: encoded.get(id), detail: 'high' } },
          ] }],
        });
        const text = response.choices[0].message.content;
        const matches = fixture.expected.map(label => ({ label, found: normalize(text).includes(normalize(label)) }));
        requests.push({ fixture: basename(fixture.file), variant: variant.name, round, model,
          ...current, matches, allLabelsFound: matches.every(match => match.found) });
      } catch (error) {
        requests.push({ fixture: basename(fixture.file), variant: variant.name, round, model,
          ...current, error: error.code || 'model_request_failed' });
        await writeFile(join(output, 'model-requests.json'), JSON.stringify(requests, null, 2));
        throw new Error(`Model benchmark stopped (${error.code || 'model_request_failed'}); no automatic retry`);
      }
      await writeFile(join(output, 'model-requests.json'), JSON.stringify(requests, null, 2));
      console.log(JSON.stringify(requests.at(-1)));
    }
  }
  const summary = variants.map(variant => {
    const selected = requests.filter(request => request.variant === variant.name);
    return { variant: variant.name, count: selected.length,
      totalP50Ms: percentile(selected.map(request => request.total_ms), 0.5),
      totalP95Ms: percentile(selected.map(request => request.total_ms), 0.95),
      firstTextP50Ms: percentile(selected.map(request => request.first_text_ms), 0.5),
      meanInputTokens: selected.reduce((sum, request) => sum + request.usage.input_tokens, 0) / selected.length,
      labelChecksPassed: selected.filter(request => request.allLabelsFound).length };
  });
  await writeFile(join(output, 'model-summary.json'), JSON.stringify(summary, null, 2));
  console.log(JSON.stringify(summary, null, 2));
}
