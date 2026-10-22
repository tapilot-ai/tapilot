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
const tasksPerSample = 4, operationsPerTask = 30;
for (let repeat = 0; repeat < 5; repeat++) {
  let checkpoints = 0, bytes = 0, totalOperations = 0, totalTurns = 0;
  const start = performance.now();
  // Separate completed tasks keep this CPU benchmark within the production context budget.
  // Do not raise that budget to make a synthetic 120-operation transcript pass.
  for (let task = 0; task < tasksPerSample; task++) {
    let operations = 0;
    const conversation = createConversation({
      messages: [{ role: 'system', content: 'benchmark' }, { role: 'user', content: 'benchmark' }],
      rpc: async method => {
        if (method === 'phone.execute') return { success: true, result: 'verified '.repeat(128) };
        const name = operations++ < operationsPerTask ? 'operate_phone' : 'finish_task';
        const args = name === 'operate_phone'
          ? { instruction: 'Inspect current screen', success_condition: 'Screen visible',
              mode: 'observe', app: '', finishWhenVerified: false }
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
    if (result.phase !== 'completed' || result.operations !== operationsPerTask || result.turns !== operationsPerTask + 1) {
      throw new Error('Conversation benchmark changed task execution; inspect the regression');
    }
    totalOperations += result.operations; totalTurns += result.turns;
  }
  if (!process.argv[2] && checkpoints !== tasksPerSample * (2 * operationsPerTask + 3)) {
    throw new Error('Conversation benchmark changed checkpoint count; inspect the regression');
  }
  samples.push({ elapsedMs: Math.round(performance.now() - start), tasks: tasksPerSample, checkpoints, bytes,
    operations: totalOperations, turns: totalTurns });
}
console.log(JSON.stringify(samples, null, 2));
