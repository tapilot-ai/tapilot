import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, rmSync, readdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';
import { runTestProject } from '@midscene/test/config';
import { collectCases, createPlan, digest, prepareShard } from '../scripts/ci-shards.ts';

test('native merge retains all cases and failed verdicts and refuses mixed APK evidence', async () => {
  const root = mkdtempSync(join(tmpdir(), 'tapilot-report-merge-'));
  const sdk = import.meta.resolve('@midscene/test');
  const merger = fileURLToPath(new URL('../scripts/merge-ci-reports.mjs', import.meta.url));
  const ids = ['CORE-01', 'CORE-02', 'CHAT-01', 'DRAFT-01', 'DRAFT-02', 'DRAFT-03', 'UI-01', 'UI-02', 'UI-03', 'UI-04', 'UI-05'];
  const source = JSON.stringify({ cases: ids.map(name => ({ name, tags: ['smoke'], steps: [{ probe: { fail: ['CORE-02', 'DRAFT-01'].includes(name) } }] })) });
  try {
    mkdirSync(join(root, 'cases'));
    writeFileSync(join(root, 'cases/cases.yaml'), source);
    const apk = join(root, 'fixture.apk');
    writeFileSync(apk, 'fixture APK bytes');
    const identity = { sourceSha: 'a'.repeat(40), runId: '42', apkSha256: digest(readFileSync(apk)) };
    const plan = createPlan(collectCases(root), {}, identity);
    for (const lane of plan.lanes) {
      const directory = join(root, `shard-results/midscene-apk-shard-${lane.id}-1`);
      mkdirSync(join(directory, 'cases'), { recursive: true });
      writeFileSync(join(directory, 'cases/cases.yaml'), source);
      prepareShard(plan, lane.id, { apk, root: directory, sourceSha: identity.sourceSha, runId: identity.runId,
        evidenceDirectory: join(directory, 'midscene_run/ci') });
      writeFileSync(join(directory, 'config.mjs'), `import {defineNode,z} from ${JSON.stringify(sdk)};
export default {output:{reportFormat:'html-and-external-assets'},projects:[{name:'android-${lane.id}',files:{include:['.ci-cases/lane-${lane.id}/*.yaml']},retry:0}],nodes:[defineNode({name:'probe',inputSchema:z.strictObject({fail:z.boolean()}),execute({input}){if(input.fail)throw new Error('deliberate fixture failure');return {summary:'fixture evidence'};}})]};`);
      await runTestProject({ cwd: directory, projectRoot: directory, configPath: join(directory, 'config.mjs') });
    }
    const runMerge = () => spawnSync(process.execPath, [merger], { cwd: root, encoding: 'utf8', env: {
      ...process.env, GITHUB_SHA: identity.sourceSha, GITHUB_RUN_ID: identity.runId, TAPILOT_CI_PLAN: JSON.stringify(plan),
    } });
    const result = runMerge();
    assert.equal(result.status, 1, result.stderr);
    assert.match(result.stdout, /9\/11 passed/);
    const html = readFileSync(join(root, 'midscene_run/report/midscene-e2e-complete/index.html'), 'utf8');
    const match = html.match(/<script[^>]*type="midscene_test_run_dump"[^>]*>([\s\S]*?)<\/script>/);
    assert.ok(match, 'Must use the native Midscene test report');
    const dump = JSON.parse(match[1]);
    assert.equal(dump.summary.total, 11);
    assert.equal(dump.summary.failed, 2);
    assert.match(readFileSync(join(root, 'midscene_run/ci-badge.md'), 'utf8'), /pass=9&fail=2/);
    assert.equal(dump.projects.length, 4);
    for (const project of dump.projects) {
      for (const plan of project.plans ?? []) {
        const document = project.documents.find((item: { documentId: string }) => item.documentId === plan.documentId);
        assert.ok(document, 'Every planned document must match its real execution instead of creating an unrun duplicate');
        if (document.plan) assert.deepEqual(document.plan, plan, 'Embedded document plan must use the same remapped IDs');
        for (const plannedCase of plan.cases) {
          const executed = document.cases.find((item: { caseId: string }) => item.caseId === plannedCase.caseId);
          assert.ok(executed, 'Every selected case plan must match its executed case');
          assert.equal(executed.name, plannedCase.name);
          if (executed.plan) assert.deepEqual(executed.plan, plannedCase, 'Embedded case plan must use the same remapped IDs');
          const plannedSteps = new Set([...plannedCase.beforeEach, ...plannedCase.steps, ...plannedCase.afterEach].map(item => item.id));
          for (const attempt of executed.attempts) for (const step of [...attempt.beforeEach, ...attempt.steps, ...attempt.afterEach]) {
            if (step.planStepId) assert.ok(plannedSteps.has(step.planStepId), 'Execution step must resolve to its original plan');
          }
        }
      }
    }
    assert.deepEqual(readdirSync(join(root, '.midscene/test-results')), ['complete']);
    const summary = readFileSync(join(root, '.midscene/test-results/complete/result.md'), 'utf8');
    assert.equal(summary.match(/^# Midscene Test$/gm)?.length, 1);
    for (const id of ids) assert.match(summary, new RegExp(`\\| ${id} \\|`));
    assert.match(summary, /9\/11 passed/);
    const provenance = join(root, 'shard-results/midscene-apk-shard-1-1/midscene_run/ci/provenance.json');
    const data = JSON.parse(readFileSync(provenance, 'utf8'));
    writeFileSync(provenance, JSON.stringify({ ...data, apkSha256: 'b'.repeat(64) }));
    const mismatched = runMerge();
    assert.equal(mismatched.status, 1);
    assert.match(mismatched.stderr, /must install the exact shared APK/);
  } finally { rmSync(root, { recursive: true, force: true }); }
});

test('a native case deadline still produces failed report evidence', async () => {
  const root = mkdtempSync(join(tmpdir(), 'tapilot-deadline-report-'));
  const sdk = import.meta.resolve('@midscene/test');
  try {
    writeFileSync(join(root, 'case.yaml'), 'cases:\n  - name: stuck native shutdown\n    steps:\n      - wait: {}\n');
    writeFileSync(join(root, 'config.mjs'), `import {defineNode,z} from ${JSON.stringify(sdk)};
export default {test:{testTimeout:50},projects:[{name:'deadline',files:{include:['case.yaml']},retry:0}],nodes:[defineNode({name:'wait',inputSchema:z.strictObject({}),async execute({signal}){await new Promise((_,reject)=>{if(signal.aborted)reject(signal.reason);else signal.addEventListener('abort',()=>reject(signal.reason),{once:true});});}})]};`);
    await runTestProject({ cwd: root, projectRoot: root, configPath: join(root, 'config.mjs') });
    const results = join(root, '.midscene/test-results');
    const summary = JSON.parse(readFileSync(join(results, readdirSync(results)[0], 'summary.json'), 'utf8'));
    assert.equal(summary.exitCode, 1);
    assert.equal(summary.status, 'failed');
    assert.equal(summary.projects[0].cases[0].attempts.length, 1);
    assert.notEqual(summary.projects[0].cases[0].status, 'success');
    const html = readFileSync(join(results, readdirSync(results)[0], summary.report), 'utf8');
    assert.match(html, /midscene_test_run_dump/);
  } finally { rmSync(root, { recursive: true, force: true }); }
});
