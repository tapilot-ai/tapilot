import { performance } from 'node:perf_hooks';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

// Deterministic model/phone stubs measure loop + snapshot serialization only.
// This does not measure model latency, native fsync or physical gestures.
const moduleUrl = process.argv[2]
  ? pathToFileURL(resolve(process.argv[2]))
  : new URL('../build/node-runtime-bundle/pi-conversation.mjs', import.meta.url);
const { createConversation } = await import(moduleUrl);
const samples = [];
for (let repeat = 0; repeat < 5; repeat++) {
  let operations = 0, checkpoints = 0, bytes = 0;
  const start = performance.now();
  const conversation = createConversation({
    messages: [{ role: 'system', content: 'benchmark' }, { role: 'user', content: 'benchmark' }],
    rpc: async method => {
      if (method === 'phone.execute') return { success: true, result: 'verified '.repeat(128) };
      const name = operations++ < 120 ? 'operate_phone' : 'finish_task';
      const args = name === 'operate_phone'
        ? { instruction: 'Inspect current screen', success_condition: 'Screen visible' }
        : { outcome: 'completed', summary: 'Complete' };
      return { choices: [{ message: { tool_calls: [{
        id: `call-${operations}`, function: { name, arguments: JSON.stringify(args) },
      }] } }] };
    },
    emit: event => {
      if (event.type !== 'checkpoint') return;
      checkpoints++;
      bytes += Buffer.byteLength(JSON.stringify(event));
    },
  });
  const result = await conversation.run();
  if (!process.argv[2] && (checkpoints !== 243 || result.operations !== 120 || result.turns !== 121)) {
    throw new Error('Conversation benchmark changed checkpoint count or task execution; inspect the regression');
  }
  samples.push({ elapsedMs: Math.round(performance.now() - start), checkpoints, bytes,
    operations: result.operations, turns: result.turns });
}
console.log(JSON.stringify(samples, null, 2));
