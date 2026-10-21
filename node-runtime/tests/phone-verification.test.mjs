import assert from 'node:assert/strict';
import test from 'node:test';
import { mkdtemp, writeFile, rm } from 'node:fs/promises';
import { join } from 'node:path';
import { runTestProject } from '@midscene/test/config';
import { collectFinalAiResponse } from './run-result.mjs';

test('bundled query and runner preserve positive and negative structured verification', async () => {
  const root = await mkdtemp(join(process.cwd(), 'verification-contract-'));
  try {
    await writeFile(join(root, 'midscene.config.mjs'), `
      import { defineNode } from '@midscene/test';
      import { commonAgentTestRunnerNodeDefinitions } from '@midscene/core/agent';
      const query = commonAgentTestRunnerNodeDefinitions.find(n => n.name === 'aiQuery');
      export default {
        nodes: [defineNode({ name: 'aiQuery', inputSchema: query.inputSchema,
          execute(ctx) { return query.execute({ aiQuery: async (prompt, options) => {
            if (options.abortSignal !== ctx.signal) throw new Error('Missing cancellation');
            return { success: prompt.success === 'true', observation: prompt.observation };
          } }, ctx.input, { signal: ctx.signal }); }
        })],
        projects: [{ name: 'contract', files: { include: ['task.yaml'] } }]
      };
    `);
    for (const success of [true, false]) {
      const value = { success, observation: success ? 'Cloudy, 20–28°C' : 'No weather reply is visible' };
      await writeFile(join(root, 'task.yaml'), JSON.stringify({ cases: [{ name: 'verify', steps: [
        { aiQuery: { prompt: { success: String(success), observation: value.observation } } },
      ] }] }));
      const result = await runTestProject({ cwd: root, projectRoot: root, resultDir: join(root, String(success)) });
      assert.equal(result.exitCode, 0); // Extraction success does not imply verification success.
      assert.deepEqual(JSON.parse(collectFinalAiResponse(result)), value);
    }
  } finally { await rm(root, { recursive: true, force: true }); }
});

import { verifyPhone, verificationCriteria } from './phone-verification.mjs';

test('one screenshot judgment preserves both success and negative evidence without retry', async () => {
  for (const success of [true, false]) {
    let calls = 0;
    const signal = new AbortController().signal;
    const result = await verifyPhone(async ctx => {
      assert.equal(ctx.signal, signal);
      calls++;
      assert.equal(Object.hasOwn(ctx.input.prompt, 'success'), true);
      assert.equal(ctx.input.prompt.observation.includes('Prior transcription'), false);
      return { data: { success, observation: 'Selected A under visible column X' } };
    }, { signal }, { instruction: 'Read the selected item and its column', condition: 'Selected A and its column are visible' });
    assert.equal(calls, 1);
    assert.equal(result.data.success, success);
  }
});

test('cancellation discards even a successful screenshot judgment', async () => {
  const controller = new AbortController(); let calls = 0;
  await assert.rejects(verifyPhone(async () => {
    calls++; controller.abort(); return { data: { success: true, observation: 'Visible' } };
  }, { signal: controller.signal }, { instruction: 'Read', condition: 'Visible' }), { name: 'AbortError' });
  assert.equal(calls, 1);
});

test('verifyPhone runner preserves the single screenshot judgment', async () => {
  const root = await mkdtemp(join(process.cwd(), 'verification-stages-'));
  try {
    await writeFile(join(root, 'midscene.config.mjs'), `
      import { defineNode, z } from '@midscene/test';
      import { commonAgentTestRunnerNodeDefinitions } from '@midscene/core/agent';
      import { verifyPhone, verificationCriteria } from '../phone-verification.mjs';
      const query = commonAgentTestRunnerNodeDefinitions.find(n => n.name === 'aiQuery');
      export default {
        nodes: [defineNode({ name: 'verifyPhone', inputSchema: z.object({ instruction: z.string(), condition: z.string() }),
          execute(ctx) { return verifyPhone(stage => query.execute({ aiQuery: async (prompt, options) => {
            if (options.abortSignal !== ctx.signal) throw new Error('Missing cancellation');
            return Object.hasOwn(prompt, 'success') ? { success: false, observation: 'Wrong selected item' } : { observation: 'Visible item A' };
          } }, stage.input, { signal: stage.signal }), ctx, ctx.input); }
        })],
        projects: [{ name: 'contract', files: { include: ['task.yaml'] } }]
      };
    `);
    await writeFile(join(root, 'task.yaml'), JSON.stringify({ cases: [{ name: 'verify', steps: [
      { verifyPhone: { instruction: 'Select item B', condition: 'B selected' } },
    ] }] }));
    const result = await runTestProject({ cwd: root, projectRoot: root, resultDir: join(root, 'result') });
    assert.equal(result.exitCode, 0);
    assert.deepEqual(JSON.parse(collectFinalAiResponse(result)), { success: false, observation: 'Wrong selected item' });
  } finally { await rm(root, { recursive: true, force: true }); }
});

test('unstructured or empty output cannot establish success', async () => {
  for (const data of ['Selected A', { observation: ' ' }, { success: 'true', observation: 'A' }]) {
    let calls = 0;
    const result = await verifyPhone(async () => { calls++; return { data }; },
      { signal: new AbortController().signal }, { instruction: 'Read', condition: 'Visible' });
    assert.equal(calls, 2);
    assert.equal(result.data.success, false);
  }
});

test('prompt echo is repaired by one read-only check; repeated echo cannot become success', async () => {
  for (const repeats of [false, true]) {
    let calls = 0;
    const result = await verifyPhone(async ctx => {
      calls++;
      if (calls === 1 || repeats) return { data: { success: true, observation: ctx.input.prompt.observation } };
      assert.equal(ctx.input.prompt.observation.includes('Prior transcription'), false);
      return { data: { success: true, observation: 'A selected under column X' } };
    }, { signal: new AbortController().signal }, { instruction: 'Read selected item', condition: 'Item and column visible' });
    assert.equal(calls, 2);
    assert.equal(result.data.success, !repeats);
    assert.equal(result.data.observation.includes('Re-read'), false);
  }
});

test('cancellation after malformed judgment prevents the format-repair query', async () => {
  const controller = new AbortController(); let calls = 0;
  await assert.rejects(verifyPhone(async () => {
    calls++;
    controller.abort(); return { data: { success: 'true', observation: 'A' } };
  }, { signal: controller.signal }, { instruction: 'Read', condition: 'Visible' }), { name: 'AbortError' });
  assert.equal(calls, 1);
});


test('intermediate verification separates this page from the full multi-page goal', () => {
  const original = ['Read item A on page one, then item B on page two; do not modify either.'];
  const contract = { userRequirements: original, scope: 'intermediate_step', proposedStepCondition: 'A and its label visible' };
  const step = verificationCriteria(JSON.stringify(contract), 'Read A');
  assert.equal(step.requiredVisibleOutcome, 'A and its label visible');
  assert.deepEqual(step.userRequirementsContext, original);
  assert.equal(step.scope, 'intermediate_step');
  const whole = verificationCriteria(JSON.stringify({ ...contract, scope: 'entire_user_goal' }), 'Read both');
  assert.deepEqual(whole.requiredVisibleOutcome, original);
  assert.equal(verificationCriteria('Explicit label must equal ABC', 'Read'), 'Explicit label must equal ABC');
});

// A current screenshot must not be asked to prove a historical page transition.
test('page-reading criteria preserve visible targets while deferring navigation history', () => {
  const criteria = verificationCriteria(JSON.stringify({ scope: 'intermediate_step', userRequirements: ['Move icons across pages'], proposedStepCondition: 'Read File Manager on the next page' }), 'Swipe left then read this page');
  assert.equal(criteria.requiredVisibleOutcome, 'Read File Manager on the next page');
  assert.match(criteria.rule, /conversation can compare separately recorded pages/);
  assert.match(criteria.rule, /does NOT prove a requested cross-page move/);
});
