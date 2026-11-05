import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import yaml from 'js-yaml';
import { collectCases, createPlan, digest, prepareShard } from '../scripts/ci-shards.ts';

test('duration-based placement balances a known workload and admits new cases without lane tags', () => {
  const cases = Array.from({ length: 8 }, (_, index) => ({ id: `C${index}`, name: `C${index}`, file: 'cases.yaml', definitionSha256: 'fixture' }));
  const timings = Object.fromEntries(cases.map((item, index) => [item.id, (8 - index) * 100_000]));
  const identity = { sourceSha: 'a'.repeat(40), runId: '42', apkSha256: 'b'.repeat(64) };
  const plan = createPlan(cases, timings, identity);
  assert.deepEqual(plan.lanes.map(lane => lane.estimatedMs), [900_000, 900_000, 900_000, 900_000]);
  const expanded = createPlan([...cases, { ...cases[0], id: 'NEW', name: 'NEW' }], timings, identity);
  assert.equal(expanded.lanes.flatMap(lane => lane.caseIds).filter(id => id === 'NEW').length, 1);
  assert.equal(new Set(expanded.lanes.flatMap(lane => lane.caseIds)).size, 9);
});

test('APK handoff rejects changed bytes, checkout, run and definitions; generated YAML retains steps and cleanup', () => {
  const root = mkdtempSync(join(tmpdir(), 'tapilot-ci-plan-'));
  try {
    mkdirSync(join(root, 'cases'));
    const document = { afterEach: [{ 'fixture.restore': {} }], cases: Array.from({ length: 4 }, (_, index) => ({
      name: `CASE-${index} fixture`, tags: ['smoke'], steps: [{ probe: { value: index } }],
    })) };
    const file = join(root, 'cases/cases.yaml');
    writeFileSync(file, yaml.dump(document));
    const apk = join(root, 'fixture.apk');
    writeFileSync(apk, 'fixture APK bytes');
    const identity = { sourceSha: 'a'.repeat(40), runId: '42', apkSha256: digest(readFileSync(apk)) };
    const plan = createPlan(collectCases(root), {}, identity);
    const options = { apk, root, sourceSha: identity.sourceSha, runId: identity.runId, evidenceDirectory: join(root, 'evidence') };
    prepareShard(plan, 1, options);
    const generated = yaml.load(readFileSync(join(root, '.ci-cases/lane-1/cases.yaml'), 'utf8')) as typeof document;
    assert.deepEqual(generated.afterEach, document.afterEach);
    assert.deepEqual(generated.cases, document.cases.filter(item => plan.lanes[0].caseIds.includes(item.name.split(' ')[0])));
    const focused = { ...createPlan(collectCases(root).slice(0, 2), {}, identity, 2), selectedCaseIds: ['CASE-0', 'CASE-1'] };
    prepareShard(focused, 1, options);
    const focusedYaml = yaml.load(readFileSync(join(root, '.ci-cases/lane-1/cases.yaml'), 'utf8')) as typeof document;
    assert.deepEqual(focusedYaml.cases, [document.cases[0]], 'Focused lanes retain original case assertions without selecting unrelated cases');
    assert.deepEqual(focusedYaml.afterEach, document.afterEach);
    assert.throws(() => prepareShard({ ...focused, selectedCaseIds: ['CASE-0'] }, 1, options), /selection must match/);
    assert.throws(() => prepareShard(plan, 1, { ...options, sourceSha: 'b'.repeat(40) }), /source revision/);
    assert.throws(() => prepareShard(plan, 1, { ...options, runId: '43' }), /workflow run/);
    writeFileSync(apk, 'tampered bytes');
    assert.throws(() => prepareShard(plan, 1, options), /digest mismatch/);
    writeFileSync(apk, 'fixture APK bytes');
    document.cases[0].steps[0].probe.value = 99;
    writeFileSync(file, yaml.dump(document));
    assert.throws(() => prepareShard(plan, 1, options), /definitions changed/);
  } finally { rmSync(root, { recursive: true, force: true }); }
});
