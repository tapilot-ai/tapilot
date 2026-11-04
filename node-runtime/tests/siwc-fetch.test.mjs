import { test } from 'node:test';
import assert from 'node:assert/strict';
import { siwcFetch } from './siwc-fetch.mjs';

test('every inference obtains current credentials; 401 refresh retries only that HTTP request', async () => {
  const tokens = [], requests = [];
  const fetch = siwcFetch(async request => {
    requests.push({ token: request.headers.get('Authorization'), body: await request.text() });
    return new Response('data: completed\n\n', { status: requests.length === 1 ? 401 : 200 });
  }, async force => { tokens.push(force); return force ? 'renewed' : 'current'; });
  const init = { method: 'POST', body: '{"input":"first"}' };
  assert.equal((await fetch('https://api.openai.com/v1/responses', init)).status, 200);
  await fetch('https://api.openai.com/v1/responses', { ...init, body: '{"input":"second"}' });
  assert.deepEqual(tokens, [false, true, false]);
  assert.deepEqual(requests.map(x => x.token), ['Bearer current', 'Bearer renewed', 'Bearer current']);
  assert.deepEqual(requests.map(x => x.body), ['{"input":"first"}', '{"input":"first"}', '{"input":"second"}']);
});

test('other hosts receive no ChatGPT credentials and cancellation stops before requesting a token', async () => {
  let tokenCalls = 0;
  const fetch = siwcFetch(async () => new Response('OK'), async () => { tokenCalls++; return 'private'; });
  await fetch('https://other.example/v1/responses');
  await fetch('https://api.openai.com/v1/models');
  const controller = new AbortController(); controller.abort();
  await assert.rejects(fetch('https://api.openai.com/v1/responses', { signal: controller.signal }));
  assert.equal(tokenCalls, 0);
});

test('transport measurements preserve streamed bytes and separate headers from body', async () => {
  const { measuredFetch } = await import('./siwc-fetch.mjs');
  let clock = 0, read = 0;
  const records = [];
  const fetch = measuredFetch(async () => {
    clock = 10;
    return new Response(new ReadableStream({ pull(c) {
      if (read++ === 0) { clock = 30; c.enqueue(new TextEncoder().encode('data: hello\n\n')); }
      else { clock = 50; c.close(); }
    } }, { highWaterMark: 0 }), { headers: { 'content-type': 'text/event-stream' } });
  }, value => records.push(value), () => clock);
  const response = await fetch('https://example.test', { method: 'POST', body: 'secret' });
  assert.equal(records.length, 0);
  assert.equal(await response.text(), 'data: hello\n\n');
  assert.equal(records.length, 1);
  assert.equal(records[0].headersMs, 10);
  assert.equal(records[0].firstBodyMs, 30);
  assert.equal(records[0].totalMs, 50);
  assert.equal(records[0].requestBytes, 6);
  assert.ok(!JSON.stringify(records).includes('secret'));
});

test('transport measurement forwards cancellation and telemetry failures are harmless', async () => {
  const { measuredFetch } = await import('./siwc-fetch.mjs');
  let cancelled = false;
  const response = await measuredFetch(async () => new Response(new ReadableStream({
    cancel() { cancelled = true; },
  })), () => { throw new Error('telemetry'); })('https://example.test');
  await response.body.cancel();
  assert.equal(cancelled, true);
});

test('transport diagnostics retain only validated server timing and edge code', async () => {
  const { measuredFetch } = await import('./siwc-fetch.mjs');
  const records = [];
  for (const headers of [
    { 'cf-ray': '123abc-SIN', 'openai-processing-ms': '123.5', 'x-request-id': 'private-id', 'set-cookie': 'private-cookie' },
    { 'cf-ray': 'private-value', 'openai-processing-ms': '-1' },
    { 'openai-processing-ms': '9'.repeat(400) },
  ]) {
    await (await measuredFetch(async () => new Response(null, { headers }), value => records.push(value))('https://example.test')).text();
  }
  assert.equal(records[0].edge, 'SIN');
  assert.equal(records[0].serverProcessingMs, 123.5);
  for (const record of records.slice(1)) {
    assert.equal(record.edge, undefined);
    assert.equal(record.serverProcessingMs, undefined);
  }
  assert.ok(!JSON.stringify(records).includes('private'));
});

test('real fetch diagnostics measure upload and response boundaries without retaining secrets', async () => {
  const { createServer } = await import('node:http');
  const { once } = await import('node:events');
  const { observeModelNetwork } = await import('./siwc-fetch.mjs');
  const records = [];
  const server = createServer((request, response) => {
    request.resume();
    request.on('end', () => { response.writeHead(200); response.end('fixture'); });
  });
  server.listen(0, '127.0.0.1'); await once(server, 'listening');
  const stop = observeModelNetwork(value => records.push(value));
  try {
    const response = await fetch(`http://127.0.0.1:${server.address().port}/secret-path`, {
      method: 'POST', body: 'secret-body', headers: { Authorization: 'secret-token' },
    });
    assert.equal(await response.text(), 'fixture');
    assert.equal(records.length, 1);
    assert.ok(records[0].bodySentMs >= 0);
    assert.ok(records[0].headersMs >= records[0].bodySentMs);
    assert.ok(records[0].totalMs >= records[0].headersMs);
    assert.equal(records[0].status, 200);
    assert.ok(!JSON.stringify(records).includes('secret'));
    stop();
    await (await fetch(`http://127.0.0.1:${server.address().port}`)).text();
    assert.equal(records.length, 1);
  } finally { stop(); server.close(); server.closeAllConnections(); }
});

test('SIWC credential refresh preserves the SDK proxy dispatcher for both attempts', async () => {
  const dispatcher = { dispatch() {} };
  const observed = [];
  const fetch = siwcFetch(async (request, init) => {
    observed.push(init?.dispatcher);
    return new Response(null, { status: observed.length === 1 ? 401 : 200 });
  }, async () => 'fixture-token');
  assert.equal((await fetch('https://api.openai.com/v1/responses', {
    method: 'POST', body: '{}', dispatcher,
  })).status, 200);
  assert.deepEqual(observed, [dispatcher, dispatcher]);
});
