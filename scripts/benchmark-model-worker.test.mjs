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

test('worker tracks socket reuse without retaining headers or addresses', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'tapilot-model-connection-'));
  try {
    const shim = join(dir, 'shim.mjs');
    await writeFile(shim, `import {channel} from 'node:diagnostics_channel';
export const observeModelNetwork = () => () => {};
const socket = {alpnProtocol: 'h2', isSessionReused: () => false, remoteAddress: 'PRIVATE_ADDRESS'};
export const siwcFetch = () => async () => {
 const request = {headers: 'PRIVATE_TOKEN'};
 channel('undici:request:create').publish({request});
 channel('undici:client:sendHeaders').publish({request, socket, headers: 'PRIVATE_TOKEN'});
 return new Response('data: '+JSON.stringify({type:'response.completed',response:{model:'gpt-6-luna',usage:{input_tokens:1,output_tokens:1},output:[{content:[{type:'output_text',text:'OK'}]}]}})+'\\n\\n');
};`);
    const child = spawn(process.execPath, ['scripts/benchmark-model-worker.mjs', shim], {stdio:['pipe','pipe','pipe']});
    let stdout = '', stderr = '';
    child.stdout.on('data', b => {stdout += b;}); child.stderr.on('data', b => {stderr += b;});
    const done = new Promise((resolve, reject) => {child.on('error', reject); child.on('exit', resolve);});
    child.stdin.end([1,2].map(id=>JSON.stringify({id:String(id),body:'{}',transport:'siwc-request'})).join('\n')+'\n');
    assert.equal(await done, 0, stderr);
    assert.doesNotMatch(stdout, /PRIVATE_TOKEN|PRIVATE_ADDRESS/);
    const results = stdout.trim().split('\n').map(line=>JSON.parse(line)).filter(x=>x.outcome);
    assert.equal(results.length, 2);
    assert.deepEqual(results.map(r=>r.connectionTrace[0].previousRequests), [0,1]);
    assert.deepEqual(results.map(r=>r.connectionTrace[0].socketId), [1,1]);
    assert.ok(results.every(r=>r.connectionTrace[0].negotiatedProtocol==='h2' && r.connectionTrace[0].sendHeadersMs>=0));
  } finally {await rm(dir,{recursive:true,force:true});}
});
