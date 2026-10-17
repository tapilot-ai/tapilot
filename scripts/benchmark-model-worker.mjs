// Read-only diagnostic worker. Credentials enter via stdin and are never persisted or emitted.
import { createInterface } from 'node:readline';
import { createHash } from 'node:crypto';
import { pathToFileURL } from 'node:url';
const { observeModelNetwork } = await import(pathToFileURL(process.argv[2]));
let network = [];
const stop = observeModelNetwork(value => network.push(value));
console.log(JSON.stringify({ ready: true, node: process.version }));
try {
  for await (const line of createInterface({ input: process.stdin })) {
    const { id, token, body } = JSON.parse(line);
    console.log(JSON.stringify({ received: id }));
    network = [];
    const started = performance.now();
    const measurement = { id, requestSha256: createHash('sha256').update(body).digest('hex'), requestBytes: Buffer.byteLength(body) };
    try {
      const response = await fetch('https://api.openai.com/v1/responses', {
        method: 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', Accept: 'text/event-stream' },
        body, signal: AbortSignal.timeout(45000),
      });
      measurement.headersMs = performance.now() - started;
      measurement.status = response.status;
      measurement.server = Object.fromEntries(['openai-processing-ms', 'x-request-id', 'cf-ray'].flatMap(k => response.headers.has(k) ? [[k, response.headers.get(k)]] : []));
      let text = '', bytes = 0;
      const decoder = new TextDecoder();
      for await (const chunk of response.body) {
        measurement.firstBodyMs ??= performance.now() - started;
        bytes += chunk.length; text += decoder.decode(chunk, { stream: true });
      }
      text += decoder.decode();
      measurement.totalMs = performance.now() - started;
      measurement.responseBytes = bytes;
      let completed; const items = new Map();
      for (const line of text.split('\n')) {
        if (!line.startsWith('data: ') || line === 'data: [DONE]') continue;
        const event = JSON.parse(line.slice(6));
        if (event.type === 'response.completed') completed = event.response;
        if (event.type === 'response.output_item.done') items.set(event.output_index, event.item);
      }
      if (!response.ok || !completed || completed.model !== 'gpt-6-luna') throw Error('Unverified response');
      measurement.model = completed.model;
      measurement.usage = { inputTokens: completed.usage.input_tokens, outputTokens: completed.usage.output_tokens,
        cachedTokens: completed.usage.input_tokens_details?.cached_tokens, reasoningTokens: completed.usage.output_tokens_details?.reasoning_tokens };
      const output = completed.output?.length ? completed.output : [...items.entries()].sort(([a], [b]) => a - b).map(([, item]) => item);
      measurement.answer = output.flatMap(o => o.content ?? []).filter(c => c.type === 'output_text').map(c => c.text).join('');
      if (!measurement.answer.trim()) throw Error('Missing answer');
      measurement.outcome = 'complete';
    } catch (error) {
      measurement.outcome = 'failed'; measurement.error = error.name; measurement.totalMs = performance.now() - started;
    }
    measurement.network = network;
    console.log(JSON.stringify(measurement));
  }
} finally { stop(); }
