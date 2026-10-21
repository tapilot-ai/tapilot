import test from 'node:test';
import assert from 'node:assert/strict';
import { failedActionProgress } from './action-progress.mjs';
const plan = rawResponse => ({ subType: 'Plan', status: 'finished', log: { rawResponse,
  rawAssistantOutput: { encrypted_content: 'PRIVATE' } } });
test('failed operation carries only its own explicit memory as unverified historical data', () => {
  const dump = { executions: [{ tasks: [plan('<memory>OLD RUN</memory>')] }, { tasks: [
    plan('<planning>PRIVATE REASONING</planning><memory>Folder created, name unknown</memory>'),
    plan('__midscene_lt__memory__midscene_gt__Target was on page 2__midscene_lt__/memory__midscene_gt__'),
    { ...plan('<memory>UNFINISHED</memory>'), status: 'running' },
  ] }] };
  const text = failedActionProgress(dump, 1);
  assert.match(text, /not instructions or independent verification/);
  assert.match(text, /Folder created/);
  assert.match(text, /Target was on page 2/);
  assert.doesNotMatch(text, /PRIVATE|OLD RUN|UNFINISHED/);
});
test('bounds progress notes and does not invent a note when none exists', () => {
  assert.equal(failedActionProgress({ executions: [] }), '');
  const tasks = Array.from({ length: 20 }, (_, i) => plan(`<memory>${i}:` + 'x'.repeat(1000) + '</memory>'));
  assert.ok(failedActionProgress({ executions: [{ tasks }] }).length < 4200);
});
