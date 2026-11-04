import assert from 'node:assert/strict';
import { globSync, mkdirSync, readFileSync, writeFileSync, renameSync } from 'node:fs';
import { basename, dirname, resolve } from 'node:path';
import { caseId, planDigest } from './ci-shards.ts';
import { patchReportPlanIds } from './patch-report-plan-ids.mjs';
import { createRequire } from 'node:module';
patchReportPlanIds();
const { ReportMergingTool } = createRequire(import.meta.url)('@midscene/core/report');

// Use Midscene's own merger: it preserves case status, steps, agent executions,
// screenshot assets and evidence references. Never manufacture a report landing page.
const plan = JSON.parse(process.env.TAPILOT_CI_PLAN || 'null');
assert.equal(plan?.schemaVersion, 1, 'Expected the shared build job plan');
if (process.env.GITHUB_SHA) assert.equal(plan.sourceSha, process.env.GITHUB_SHA);
if (process.env.GITHUB_RUN_ID) assert.equal(plan.runId, process.env.GITHUB_RUN_ID);
const merger = new ReportMergingTool();
const cases = [];
let failed = false;
for (const plannedLane of plan.lanes) {
  const lane = plannedLane.id;
  // Re-running one failed job may leave successful lanes from an earlier attempt.
  const artifacts = [...globSync(`shard-results/midscene-apk-shard-${lane}-*`)]
    .sort((a, b) => Number(basename(a).split('-').at(-1)) - Number(basename(b).split('-').at(-1)));
  const root = artifacts[0];
  assert.ok(root, `Missing lane ${lane} artifact`);
  const provenance = JSON.parse(readFileSync(`${root}/midscene_run/ci/provenance.json`, 'utf8'));
  assert.deepEqual(provenance, {
    sourceSha: plan.sourceSha, runId: plan.runId, apkSha256: plan.apkSha256,
    planSha256: planDigest(plan), lane,
  }, `Lane ${lane} must install the exact shared APK and plan`);
  const summaries = [...globSync(`${root}/.midscene/test-results/*/summary.json`)];
  assert.equal(summaries.length, 1, `Lane ${lane} must contain exactly one native run`);
  const summaryPath = summaries[0];
  const summary = JSON.parse(readFileSync(summaryPath, 'utf8'));
  assert.equal(summary.schemaVersion, 3, 'Review merger when upgrading Midscene');
  assert.equal(summary.projects.length, 1);
  assert.equal(summary.projects[0].name, `android-${lane}`);
  const report = resolve(dirname(summaryPath), summary.report);
  assert.ok(report.startsWith(`${resolve(root)}/`), 'Report must belong to its lane artifact');
  merger.append({ reportFilePath: report, reportAttributes: {
    testTitle: `Android lane ${lane}`, testId: `lane-${lane}`,
    testDescription: 'Isolated runner and Android 35 emulator; zero retries',
    testDuration: summary.durationMs,
    testStatus: summary.status === 'success' ? 'passed' : 'failed',
  } });
  assert.deepEqual(summary.projects[0].cases.map(caseId).sort(), [...plannedLane.caseIds].sort(), `Lane ${lane} case selection mismatch`);
  cases.push(...summary.projects[0].cases);
  failed ||= summary.exitCode !== 0 || summary.status !== 'success';
}
let report = merger.mergeReports('midscene-e2e-complete', { outputDir: 'midscene_run/report' });
assert.ok(report, 'Native report was not generated');
// Newer SDKs can emit a single HTML when no lane captured screenshots. Publish
// those exact SDK bytes at the same Pages entry, without generating a wrapper.
if (basename(report) !== 'index.html') {
  const entry = resolve('midscene_run/report/midscene-e2e-complete/index.html');
  mkdirSync(dirname(entry), { recursive: true });
  renameSync(report, entry);
  report = entry;
}
const expected = plan.cases.map(item => item.id);
assert.deepEqual(cases.map(caseId).sort(), expected.sort(), 'Every planned case must run exactly once');
assert.ok(cases.every(item => item.attempts.length === 1), 'Retries or missing attempts are not accepted');
const passed = cases.filter(item => item.status === 'success').length;
const failedOrNotRun = cases.length - passed;
const resultDirectory = '.midscene/test-results/complete';
mkdirSync(resultDirectory, { recursive: true });
const escapeCell = value => String(value).replaceAll('|', '\\|').replaceAll('\n', ' ');
writeFileSync(`${resultDirectory}/result.md`, [
  '# Midscene Test', '',
  `${passed}/${cases.length} passed · ${failedOrNotRun} failed or not run · zero retries`, '',
  '| Case | Result |', '| --- | --- |',
  ...cases.map(item => `| ${escapeCell(item.name)} | ${escapeCell(item.status)} |`), '',
].join('\n'));
writeFileSync('midscene_run/ci-badge.md',
  `![Midscene Test: ${passed} passed, ${failedOrNotRun} failed or not run](https://badge.midscenejs.com/e2e?pass=${passed}&fail=${failedOrNotRun}&style=large)\n`);
console.log(`Complete native report: ${report}; ${passed}/${cases.length} passed; shared APK ${plan.apkSha256}`);
if (failed || cases.some(item => item.status !== 'success')) process.exitCode = 1;
