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
