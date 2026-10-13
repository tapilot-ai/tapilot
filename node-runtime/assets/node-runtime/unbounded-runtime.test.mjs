import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, writeFile, rm } from 'node:fs/promises';
import { join } from 'node:path';
import { runTestProject } from '@midscene/test/config';

test('zero disables step deadlines while explicit positive deadlines still fail slow nodes', async () => {
  const root = await mkdtemp(join(process.cwd(), 'timeout-contract-'));
  try {
    for (const timeout of [0, 10]) {
      const project = join(root, String(timeout)); await mkdir(project);
      await writeFile(join(project, 'midscene.config.mjs'), `
        import { defineNode, z } from '@midscene/test';
        export default {
          test: { testTimeout: ${timeout} },
          nodes: [defineNode({ name: 'slow', inputSchema: z.object({}),
            async execute() { await new Promise(resolve => setTimeout(resolve, 80)); return { done: true }; }
          })],
          projects: [{ name: 'contract', files: { include: ['task.yaml'] } }]
        };
      `);
      await writeFile(join(project, 'task.yaml'), JSON.stringify({ cases: [{ name: 'slow node', steps: [{ slow: {} }] }] }));
      const result = await runTestProject({ cwd: project, projectRoot: project, resultDir: join(project, 'results') });
      assert.equal(result.exitCode, timeout === 0 ? 0 : 1);
    }
  } finally { await rm(root, { recursive: true, force: true }); }
});
