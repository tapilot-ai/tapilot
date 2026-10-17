// Read-only diagnostic worker. Credentials enter via stdin and are never persisted or emitted.
import { createInterface } from 'node:readline';
import { createHash } from 'node:crypto';
import { pathToFileURL } from 'node:url';
const { observeModelNetwork, siwcFetch } = await import(pathToFileURL(process.argv[2]));
let network = [];
const stop = observeModelNetwork(value => network.push(value));
console.log(JSON.stringify({ ready: true, node: process.version, environmentProxyEnabled: process.env.NODE_USE_ENV_PROXY === '1' }));
try {
  for await (const line of createInterface({ input: process.stdin })) {
    const { id, token, body, transport = 'direct' } = JSON.parse(line);
    console.log(JSON.stringify({ received: id }));
    network = [];
    const started = performance.now();
    const measurement = { id, requestSha256: createHash('sha256').update(body).digest('hex'), requestBytes: Buffer.byteLength(body) };
    try {
      const probe = transport === 'transport-probe';
      const request = transport === 'siwc-request' ? siwcFetch(fetch, async () => token) : fetch;
      measurement.transport = transport;
      const response = await request(probe ? 'https://api.openai.com/v1/models' : 'https://api.openai.com/v1/responses', probe ? {
        method: 'GET', signal: AbortSignal.timeout(15000),
      } : {
        method: 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', Accept: 'text/event-stream' },
        body, signal: AbortSignal.timeout(45000),
      });
      measurement.headersMs = performance.now() - started;
      measurement.status = response.status;
      measurement.server = Object.fromEntries(['openai-processing-ms', 'x-request-id', 'cf-ray'].flatMap(k => response.headers.has(k) ? [[k, response.headers.get(k)]] : []));
      let pending = '', bytes = 0, completed;
      const items = new Map();
      const consume = line => {
        if (probe || !line.startsWith('data: ') || line.trim() === 'data: [DONE]') return;
        const event = JSON.parse(line.slice(6));
        const elapsed = performance.now() - started;
        if (event.type === 'response.created') measurement.createdMs ??= elapsed;
        if (event.type === 'response.in_progress') measurement.inProgressMs ??= elapsed;
        if (event.type === 'response.output_text.delta' && event.delta) {
          measurement.firstOutputMs ??= elapsed;
          measurement.lastOutputMs = elapsed;
          measurement.outputDeltaEvents = (measurement.outputDeltaEvents ?? 0) + 1;
        }
        if (event.type === 'response.completed') {
          completed = event.response;
          measurement.completedEventMs = elapsed;
        }
        if (event.type === 'response.output_item.done') items.set(event.output_index, event.item);
      };
      const decoder = new TextDecoder();
      for await (const chunk of response.body) {
        measurement.firstBodyMs ??= performance.now() - started;
        bytes += chunk.length;
        pending += decoder.decode(chunk, { stream: true });
        let boundary;
        while ((boundary = pending.indexOf('\n')) >= 0) {
          consume(pending.slice(0, boundary));
          pending = pending.slice(boundary + 1);
        }
      }
      pending += decoder.decode();
      if (pending) consume(pending);
      measurement.totalMs = performance.now() - started;
      measurement.responseBytes = bytes;
      if (probe) {
        // Unauthenticated API rejection measures a path floor, not model inference.
        if (response.status !== 401) throw Error('Unexpected probe status');
        measurement.outcome = 'complete';
        measurement.network = network;
        console.log(JSON.stringify(measurement));
        continue;
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
