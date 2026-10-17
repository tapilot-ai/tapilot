import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile, readdir, rm, stat } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { execFileSync } from 'node:child_process';

test('captures the real Request boundary without consuming it or saving authorization', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'tapilot-request-'));
  try {
    const body = JSON.stringify({ model: 'gpt-6-luna', store: false, stream: true, input: [] });
    execFileSync(process.execPath, ['--input-type=module', '-e', `
      import assert from 'node:assert/strict';
      const body = ${JSON.stringify(body)};
      let called = 0;
      globalThis.fetch = async request => { called++; assert.equal(await request.text(), body); return new Response('ok'); };
      await import(${JSON.stringify(new URL('./lib/capture-model-request.mjs', import.meta.url).href)});
      const request = new Request('https://api.openai.com/v1/responses', {method:'POST', headers:{Authorization:'Bearer test-secret'},body});
      assert.equal(await (await fetch(request)).text(), 'ok');
      assert.equal(called,1);
    `], { env: { ...process.env, TAPILOT_BENCH_MODEL_REQUEST_DIR: directory } });
    assert.deepEqual(await readdir(directory), ['request-1.json']);
    assert.equal(await readFile(join(directory, 'request-1.json'), 'utf8'), body);
    assert.equal((await stat(join(directory, 'request-1.json'))).mode & 0o777, 0o600);
  } finally { await rm(directory, { recursive: true, force: true }); }
});
