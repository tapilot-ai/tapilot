import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import yaml from 'js-yaml';

interface TestCase { name: string; tags: string[]; steps: unknown[]; [key: string]: unknown }
interface CaseDocument { cases: TestCase[]; [key: string]: unknown }
interface CaseDefinition { id: string; name: string; file: string; definitionSha256: string; document?: CaseDocument }
interface CollectedCase extends CaseDefinition { document: CaseDocument }
interface PlanIdentity { sourceSha: string; runId: string; apkSha256: string }
interface ShardPlan extends PlanIdentity {
  schemaVersion: 1;
  selectedCaseIds?: string[];
  cases: (Omit<CaseDefinition, 'document'> & { estimatedMs: number })[];
  lanes: { id: number; estimatedMs: number; caseIds: string[] }[];
}

export const hostedTags = ['smoke', 'draft-restart', 'share-draft', 'ci-core'];
export const projectRoot = fileURLToPath(new URL('../', import.meta.url));
const repoRoot = resolve(projectRoot, '../..');
export const digest = (value: string | Buffer) => createHash('sha256').update(value).digest('hex');
export const planDigest = (plan: ShardPlan) => digest(JSON.stringify(plan));
export const caseId = (test: { name: string }) => test.name.split(' ')[0];
export const isHosted = (test: TestCase) => test.tags.some(tag => hostedTags.includes(tag));

export function collectCases(root = projectRoot): CollectedCase[] {
  return readdirSync(join(root, 'cases')).filter(file => file.endsWith('.yaml')).sort().flatMap(file => {
    const document = yaml.load(readFileSync(join(root, 'cases', file), 'utf8')) as CaseDocument;
    return document.cases.filter(isHosted).map(test => ({
      id: caseId(test), name: test.name, file,
      definitionSha256: digest(JSON.stringify({ ...document, cases: [test] })),
      document: { ...document, cases: [test] },
    }));
  });
}

// Longest-processing-time placement is deterministic; authored case order is
// retained within each YAML document when materializing a lane.
export function createPlan(cases: readonly CaseDefinition[], timings: Record<string, number>, identity: PlanIdentity, count = 4): ShardPlan {
  assert.ok(Number.isInteger(count) && count > 0 && count <= cases.length);
  assert.equal(new Set(cases.map(item => item.id)).size, cases.length, 'Duplicate case ID');
  const fallback = Math.max(90_000, ...Object.values(timings));
  const items = cases.map(({ document, ...item }) => ({ ...item, estimatedMs: timings[item.id] ?? fallback }));
  assert.ok(items.every(item => Number.isFinite(item.estimatedMs) && item.estimatedMs > 0));
  const lanes = Array.from({ length: count }, (_, index) => ({ id: index + 1, estimatedMs: 0, caseIds: [] as string[] }));
  for (const item of [...items].sort((a, b) => b.estimatedMs - a.estimatedMs || a.id.localeCompare(b.id))) {
    const lane = [...lanes].sort((a, b) => a.estimatedMs - b.estimatedMs || a.id - b.id)[0];
    lane.caseIds.push(item.id);
    lane.estimatedMs += item.estimatedMs;
  }
  return { schemaVersion: 1, ...identity, cases: items, lanes };
}

export function prepareShard(plan: ShardPlan, laneId: number, { apk, root = projectRoot, sourceSha, runId, evidenceDirectory }: {
  apk: string; root?: string; sourceSha: string; runId: string; evidenceDirectory: string;
}) {
  assert.equal(plan.schemaVersion, 1);
  assert.equal(plan.sourceSha, sourceSha, 'APK source revision does not match checkout');
  assert.equal(plan.runId, runId, 'APK must originate from this workflow run');
  assert.equal(digest(readFileSync(apk)), plan.apkSha256, 'Shared APK digest mismatch');
  const lane = plan.lanes.find(item => item.id === laneId);
  assert.ok(lane && lane.caseIds.length > 0, 'Unknown or empty lane');
  const allCases = collectCases(root);
  const selectedCaseIds = plan.selectedCaseIds;
  if (selectedCaseIds) assert.deepEqual([...selectedCaseIds].sort(), plan.cases.map(item => item.id).sort(), 'Focused selection must match every planned case');
  const selected = selectedCaseIds ? allCases.filter(item => selectedCaseIds.includes(item.id)) : allCases;
  const actual = selected.map(({ document, ...item }) => item);
  assert.deepEqual(actual, plan.cases.map(({ estimatedMs, ...item }) => item), 'Case definitions changed after planning');
  const directory = join(root, '.ci-cases', `lane-${laneId}`);
  rmSync(directory, { recursive: true, force: true });
  mkdirSync(directory, { recursive: true });
  const documents = new Map<string, CaseDocument>();
  for (const item of selected.filter(item => lane.caseIds.includes(item.id))) {
    const existing = documents.get(item.file);
    if (existing) existing.cases.push(...item.document.cases);
    else documents.set(item.file, item.document);
  }
  for (const [file, document] of documents) writeFileSync(join(directory, file), yaml.dump(document));
  const provenance = { sourceSha, runId, apkSha256: plan.apkSha256, planSha256: planDigest(plan), lane: laneId };
  mkdirSync(evidenceDirectory, { recursive: true });
  writeFileSync(join(evidenceDirectory, 'provenance.json'), JSON.stringify(provenance));
  return provenance;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const sourceSha = execFileSync('git', ['rev-parse', 'HEAD'], { cwd: repoRoot, encoding: 'utf8' }).trim();
  const runId = process.env.GITHUB_RUN_ID;
  assert.ok(runId, 'CI handoff requires GITHUB_RUN_ID');
  const apk = join(repoRoot, 'build/ci-apk/app-debug.apk');
  const planPath = join(dirname(apk), 'ci-plan.json');
  if (process.argv[2] === 'plan') {
    const timings = JSON.parse(readFileSync(join(projectRoot, 'ci-timings.json'), 'utf8')).durationMs;
    const requested = process.env.TAPILOT_CI_CASE_IDS?.split(',').map(id => id.trim()).filter(Boolean);
    const allCases = collectCases();
    if (requested?.length) {
      assert.equal(new Set(requested).size, requested.length, 'Duplicate focused case ID');
      for (const id of requested) assert.ok(allCases.some(item => item.id === id), `Unknown hosted case ID: ${id}`);
    }
    const selected = requested?.length ? allCases.filter(item => requested.includes(item.id)) : allCases;
    const plan = createPlan(selected, timings, { sourceSha, runId, apkSha256: digest(readFileSync(apk)) }, Math.min(4, selected.length));
    if (requested?.length) plan.selectedCaseIds = selected.map(item => item.id);
    writeFileSync(planPath, JSON.stringify(plan));
    for (const lane of plan.lanes) console.log(`Lane ${lane.id}: ${lane.caseIds.join(', ')}; estimated ${Math.ceil(lane.estimatedMs / 1000)}s`);
  } else if (process.argv[2] === 'prepare') {
    const provenance = prepareShard(JSON.parse(readFileSync(planPath, 'utf8')), Number(process.env.TAPILOT_E2E_CI_LANE), {
      apk, sourceSha, runId, evidenceDirectory: join(projectRoot, 'midscene_run/ci'),
    });
    console.log(`Verified shared APK ${provenance.apkSha256} for lane ${provenance.lane}`);
  } else throw new Error('Expected plan or prepare');
}
