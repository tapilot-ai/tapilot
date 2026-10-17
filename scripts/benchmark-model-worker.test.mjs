import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawn } from 'node:child_process';

test('worker timestamps streamed events across byte boundaries and still requires completion', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'tapilot-model-stream-'));
  try {
    for (const includeCompletion of [true, false]) {
      const events = [
        { type: 'response.created' },
        { type: 'response.in_progress' },
        { type: 'response.output_text.delta', delta: '好' },
        { type: 'response.output_item.done', output_index: 0, item: { content: [{ type: 'output_text', text: '好' }] } },
        ...(includeCompletion ? [{ type: 'response.completed', response: { model: 'gpt-6-luna', usage: { input_tokens: 1, output_tokens: 1 }, output: [] } }] : []),
      ];
      const stream = events.map(e => `data: ${JSON.stringify(e)}\r\n\r\n`).join('') + 'data: [DONE]';
      const shim = join(dir, 'shim.mjs');
      await writeFile(shim, `export const observeModelNetwork = () => () => {};
export const siwcFetch = () => async () => new Response(new ReadableStream({
  async start(controller) {
    const bytes = new TextEncoder().encode(${JSON.stringify(stream)});
    // Individual bytes split UTF-8, field names, JSON and CRLF boundaries.
    for (const byte of bytes) controller.enqueue(Uint8Array.of(byte));
    controller.close();
  }
}), {status: 200});\n`);
      const child = spawn(process.execPath, ['scripts/benchmark-model-worker.mjs', shim], { stdio: ['pipe', 'pipe', 'pipe'] });
      let stdout = '', stderr = '';
      child.stdout.on('data', b => { stdout += b; });
      child.stderr.on('data', b => { stderr += b; });
      const done = new Promise((resolve, reject) => { child.on('error', reject); child.on('exit', resolve); });
      child.stdin.end(JSON.stringify({id: 'fixture', body: '{}', transport: 'siwc-request'}) + '\n');
      assert.equal(await done, 0, stderr);
      const result = JSON.parse(stdout.trim().split('\n').at(-1));
      assert.equal(result.outcome, includeCompletion ? 'complete' : 'failed');
      assert.equal(result.outputDeltaEvents, 1);
      assert.ok(result.createdMs <= result.inProgressMs);
      assert.ok(result.inProgressMs <= result.firstOutputMs);
      assert.ok(result.firstOutputMs <= result.lastOutputMs);
      if (includeCompletion) {
        assert.equal(result.answer, '好');
        assert.ok(result.lastOutputMs <= result.completedEventMs);
        assert.ok(result.completedEventMs <= result.totalMs);
      } else assert.equal(result.completedEventMs, undefined);
    }
  } finally { await rm(dir, { recursive: true, force: true }); }
});
