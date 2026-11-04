import { createRequire } from 'node:module';
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

// Midscene 1.17.4 namespaces executions but leaves plans unchanged. Preserve
// every plan and align its IDs with the same source namespace as its execution.
export function patchReportPlanIds() {
  const require = createRequire(import.meta.url);
  const file = join(dirname(require.resolve('@midscene/core/report')), 'test-runner/reporting/merge-report.js');
  let source = readFileSync(file, 'utf8');
  if (source.includes('// tapilot-plan-id-remap-v3')) return;
  const anchors = ['projectId: id(project.projectId),', 'id: id(value.id),',
    'const documentScope = (value)=>({', 'documentId: id(value.documentId),', 'caseId: id(testCase.caseId),'];
  for (const anchor of anchors) if (source.split(anchor).length !== 2) {
    throw new Error('Review Midscene report ID compatibility before merging reports');
  }
  source = source.replace(anchors[3], `${anchors[3]}\n                ...(value.plan ? { plan: documentPlan(value.plan) } : {}),`)
    .replace(anchors[2], `// tapilot-plan-id-remap-v3
        const plannedSteps = values => values.map(value => ({ ...value, id: id(value.id) }));
        const casePlan = value => ({ ...value, caseId: id(value.caseId),
            beforeEach: plannedSteps(value.beforeEach), steps: plannedSteps(value.steps), afterEach: plannedSteps(value.afterEach) });
        const documentPlan = value => ({ ...value, documentId: id(value.documentId),
            beforeAll: plannedSteps(value.beforeAll), afterAll: plannedSteps(value.afterAll), cases: value.cases.map(casePlan) });
        ${anchors[2]}`)
    .replace(anchors[0], `${anchors[0]}\n                    ...(project.plans ? { plans: project.plans.map(documentPlan) } : {}),`)
    .replace(anchors[1], `${anchors[1]}\n                ...(value.planStepId ? { planStepId: id(value.planStepId) } : {}),`)
    .replace(anchors[4], `${anchors[4]}\n                                    ...(testCase.plan ? { plan: casePlan(testCase.plan) } : {}),`);
  writeFileSync(file, source);
}
