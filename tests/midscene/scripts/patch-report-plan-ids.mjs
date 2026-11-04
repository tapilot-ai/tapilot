import { createRequire } from 'node:module';
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

// Midscene 1.17.4 namespaces execution IDs when merging, but leaves plan IDs
// unchanged. Its report UI consequently renders each plan as a second, unrun case.
// Preserve all planned nodes and evidence; namespace their IDs by the same source.
export function patchReportPlanIds() {
  const require = createRequire(import.meta.url);
  const root = dirname(require.resolve('@midscene/core/report'));
  const file = join(root, 'test-runner/reporting/merge-report.js');
  const source = readFileSync(file, 'utf8');
  if (source.includes('// tapilot-plan-id-remap')) return;
  const projectAnchor = 'projectId: id(project.projectId),';
  const stepAnchor = 'id: id(value.id),';
  if (source.split(projectAnchor).length !== 2 || source.split(stepAnchor).length !== 2) {
    throw new Error('Review Midscene report ID compatibility before merging reports');
  }
  const patched = source.replace(stepAnchor, `${stepAnchor}
                ...(value.planStepId ? { planStepId: id(value.planStepId) } : {}),`)
    .replace(projectAnchor, `${projectAnchor}
                    // tapilot-plan-id-remap
                    ...(project.plans ? { plans: project.plans.map(plan => ({
                        ...plan, documentId: id(plan.documentId),
                        beforeAll: plan.beforeAll.map(value => ({ ...value, id: id(value.id) })),
                        afterAll: plan.afterAll.map(value => ({ ...value, id: id(value.id) })),
                        cases: plan.cases.map(testCase => ({
                            ...testCase, caseId: id(testCase.caseId),
                            beforeEach: testCase.beforeEach.map(value => ({ ...value, id: id(value.id) })),
                            steps: testCase.steps.map(value => ({ ...value, id: id(value.id) })),
                            afterEach: testCase.afterEach.map(value => ({ ...value, id: id(value.id) }))
                        }))
                    })) } : {}),`);
  writeFileSync(file, patched);
}
