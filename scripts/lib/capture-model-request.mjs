// Opt-in benchmark preload. Store request bodies only, never request headers.
import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
const directory = process.env.TAPILOT_BENCH_MODEL_REQUEST_DIR;
if (!directory) throw Error('Benchmark request directory is required');
mkdirSync(directory, { recursive: true, mode: 0o700 });
const original = globalThis.fetch;
let sequence = 0;
globalThis.fetch = async function(input, init) {
  const url = typeof input === 'string' ? input : input?.url ?? String(input);
  const method = init?.method ?? input?.method;
  if (url === 'https://api.openai.com/v1/responses' && method === 'POST') {
    const body = typeof init?.body === 'string' ? init.body : input instanceof Request ? await new Request(input.clone(), init).text() : undefined;
    if (body === undefined) throw Error('Unsupported benchmark request body');
    const parsed = JSON.parse(body);
    if (parsed.model !== 'gpt-6-luna' || parsed.store !== false || parsed.stream !== true) throw Error('Unexpected benchmark request');
    writeFileSync(join(directory, `request-${++sequence}.json`), body, { mode: 0o600, flag: 'wx' });
  }
  return original.call(this, input, init);
};
