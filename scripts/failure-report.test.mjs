import assert from 'node:assert/strict';
import { mkdtempSync, writeFileSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { spawnSync } from 'node:child_process';
import test from 'node:test';

test('a failed execution still returns an existing HTML report through the bundled runner', () => {
  const root = fileURLToPath(new URL('../build/node-runtime-bundle/', import.meta.url));
  const project = mkdtempSync(join(tmpdir(), 'tapilot-failed-report-'));
  const moduleUrl = pathToFileURL(join(root, 'node_modules/@midscene/test/dist/es/index.mjs')).href;
  const configUrl = pathToFileURL(join(root, 'node_modules/@midscene/test/dist/es/cli/index.mjs')).href;
  try {
    writeFileSync(join(project, 'midscene.config.mjs'), `
      import { defineNode, z } from ${JSON.stringify(moduleUrl)};
      import { defineTestProject } from ${JSON.stringify(configUrl)};
      const fail = defineNode({ name: 'smoke.fail', description: 'Intentional failure',
        inputSchema: z.object({}), execute() { throw new Error('intentional report regression failure'); } });
      export default defineTestProject({ nodes: [fail], projects: [
        { name: 'failure', files: { include: ['failure.yaml'] } }
      ] });
    `);
    writeFileSync(join(project, 'failure.yaml'), 'cases:\n  - name: Failing task\n    steps:\n      - smoke.fail: {}\n');
    const execution = spawnSync(process.execPath, [join(root, 'runner.mjs'), 'test',
      '--project-root', project, '--result-dir', join(project, 'results'),
      '--config', join(project, 'midscene.config.mjs'), '--project', 'failure'],
      { cwd: root, encoding: 'utf8', timeout: 30000 });
    assert.equal(execution.status, 1, execution.stderr || execution.error?.message);
    const events = execution.stdout.split('\n').filter(line => line.startsWith('MIDSCENE_EVENT '))
      .map(line => JSON.parse(line.slice('MIDSCENE_EVENT '.length)));
    const completed = events.find(event => event.type === 'test.completed');
    assert.ok(completed, execution.stdout + execution.stderr);
    assert.equal(completed.exitCode, 1);
    assert.match(completed.failure, /intentional report regression failure/);
    assert.ok(resolve(completed.reportPath).startsWith(project + '/'));
    assert.match(readFileSync(completed.reportPath, 'utf8'), /<html/i);
  } finally {
    rmSync(project, { recursive: true, force: true });
  }
});
