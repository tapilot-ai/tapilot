import test from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { dirname, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
const require = createRequire(import.meta.url);
const lib = dirname(require.resolve('@midscene/core'));

for (const flavor of ['lib', 'es']) {
  const load = async relative => flavor === 'lib'
    ? require(resolve(lib, `${relative}.js`))
    : import(pathToFileURL(resolve(lib, '../es', `${relative}.mjs`)).href);
  const { callAiAndParseWithRetry, withSemanticRetryFeedback } = await load('ai-model/service-caller/semantic-retry');
  const { waitForRetry } = await load('ai-model/service-caller/request-timeout');
  const { parseStandardPlanningResponse } = await load('ai-model/workflows/planning/standard-planning-parser');
  const { createDefaultMidscenePlanningProtocol } = await load('ai-model/model-adapter/default-planning-protocol');
  const { actionOutputProtocol } = createDefaultMidscenePlanningProtocol({ jsonParser: JSON.parse });
  const parseResponse = ({ content }) => {
    const parsed = parseStandardPlanningResponse(content, { includeThought: false, actionOutputProtocol, actionSpace: [] });
    if (!parsed.action && parsed.finalizeSuccess === undefined && !parsed.error) throw Error('Incomplete planning response');
    return parsed;
  };

  test(`${flavor}: transport backoff still schedules its configured delay`, async t => {
    const delays = [];
    t.mock.method(globalThis, 'setTimeout', (callback, ms) => {
      delays.push(ms); queueMicrotask(callback); return 0;
    });
    await waitForRetry(2000);
    assert.deepEqual(delays, [2000]);
  });

  test(`${flavor}: corrects the real plain-text planning response without a backoff timer`, async t => {
    t.mock.method(globalThis, 'setTimeout', () => { throw Error('Semantic correction must not schedule network backoff'); });
    const attempts = [], corrections = [];
    const result = await callAiAndParseWithRetry({
      callAi: async (attempt, previousError) => {
        attempts.push(attempt);
        if (attempt) assert.match(withSemanticRetryFeedback([], previousError)[0].content, /Incomplete planning response/);
        return { content: attempt ? '<complete success="true">自动调节已开启。</complete>' : '自动调节已开启。' };
      }, parseResponse, toParseError: error => error,
      parseRetryTimes: 2, parseRetryInterval: 2000,
      onParseRetry: error => corrections.push(error.message),
    });
    assert.deepEqual(attempts, [0, 1]);
    assert.deepEqual(corrections, ['Incomplete planning response']);
    assert.equal(result.finalizeSuccess, true);
    assert.equal(result.finalizeMessage, '自动调节已开启。');
  });

  test(`${flavor}: malformed responses remain bounded failures and cancellation prevents another call`, async t => {
    t.mock.method(globalThis, 'setTimeout', () => { throw Error('Unexpected delay'); });
    let calls = 0;
    const options = {
      callAi: async () => { calls++; return { content: '自动调节已开启。' }; },
      parseResponse, toParseError: error => error, parseRetryTimes: 2, parseRetryInterval: 2000,
    };
    await assert.rejects(callAiAndParseWithRetry(options), /Incomplete planning response/);
    assert.equal(calls, 3);
    const controller = new AbortController(); calls = 0;
    await assert.rejects(callAiAndParseWithRetry({ ...options, abortSignal: controller.signal,
      onParseRetry: () => controller.abort(),
    }), { name: 'AbortError' });
    assert.equal(calls, 1);
    calls = 0;
    await assert.rejects(callAiAndParseWithRetry({ ...options, abortSignal: controller.signal }), { name: 'AbortError' });
    assert.equal(calls, 0);
  });
}
