import { RuntimeFailure, failureEvent } from './runtime-errors.mjs';
import { createInterface } from 'node:readline';
import { createConversation } from './pi-conversation.mjs';
const emit = value => process.stdout.write(`TAPILOT_AGENT ${JSON.stringify(value)}\n`);
let conversation, started = false, sequence = 0, language = 'en';
const pending = new Map();
function rpc(method, params, signal) {
  return new Promise((resolve, reject) => {
    const requestId = String(++sequence);
    const abort = () => { pending.delete(requestId); reject(new RuntimeFailure('task_stopped', language)); };
    if (signal?.aborted) return abort();
    signal?.addEventListener('abort', abort, { once: true });
    pending.set(requestId, { resolve: value => { signal?.removeEventListener('abort', abort); resolve(value); },
      reject: error => { signal?.removeEventListener('abort', abort); reject(error); } });
    emit({ type: 'rpc', requestId, method, params });
  });
}
const input = createInterface({ input: process.stdin });
input.on('close', () => { conversation?.abort(); });
input.on('line', line => {
  try {
    const command = JSON.parse(line);
    if (command.type === 'init') {
      if (started) throw new Error('Agent already started');
      started = true;
      language = command.language === 'zh' ? 'zh' : 'en';
      conversation = createConversation({ ...command, rpc, emit });
      void conversation.run().then(result => emit({ type: 'result', ...result }), error => emit(failureEvent(error)))
        .finally(() => { input.close(); process.stdin.destroy(); });
    } else if (command.type === 'steer' || command.type === 'followUp') {
      if (!conversation) throw new Error('Agent unavailable');
      conversation.steer(command, command.type === 'followUp');
      emit({ type: 'accepted', id: command.id });
    } else if (command.type === 'abort') conversation?.abort();
    else if (command.requestId) {
      const request = pending.get(command.requestId);
      pending.delete(command.requestId);
      if (command.ok) request?.resolve(command.result);
      else request?.reject(command.errorCode ? new RuntimeFailure(command.errorCode, language) : new Error(command.error || 'Request failed'));
    }
  } catch (error) { emit(failureEvent(error)); conversation?.abort(); }
});
