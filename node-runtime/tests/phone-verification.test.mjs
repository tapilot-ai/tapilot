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
