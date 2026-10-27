import test from 'node:test';
import assert from 'node:assert/strict';
import { parseArithmetic, checkArithmetic } from './lib/arithmetic-verification.mjs';
import { normalizationMessages, validResolution, verifyResolved } from './lib/verification-prototype.mjs';
import { resolutionMatches, locatingMatches } from './lib/replay-scoring.mjs';

const condition = { expression: '(128*37+456)/4', requireExpandedExpression: false };
const facts = { isCalculator: true, expression: '5,192÷4', result: '1,298', evaluated: true };
const contract = { scope: 'intermediate_step', userRequirements: ['Compute then divide by 8; check each stage', 'Divide by 4 instead; only check the final result'], proposedStepCondition: 'Expanded expression remains visible' };
const resolution = { kind: 'calculation', visibleRequirement: 'Evaluated final expression and value', executionConstraints: [], calculation: condition };

test('committed operands are allowed without accepting a different final operator or divisor', () => {
  assert.equal(checkArithmetic(condition, facts).success, true);
  for (const expression of ['10384/8', '5192*0.25', '1298', '4736+456/4']) {
    assert.equal(checkArithmetic(condition, { ...facts, expression }).success, false, expression);
  }
  assert.equal(checkArithmetic({ ...condition, requireExpandedExpression: true }, facts).success, false);
  assert.equal(checkArithmetic({ ...condition, requireExpandedExpression: true }, { ...facts, expression: '(128×37+456)÷4' }).success, true);
});

test('a wrong or preview result never establishes calculation success', () => {
  for (const change of [{ result: '649' }, { result: '5192/4' }, { evaluated: false }, { evaluated: null }, { isCalculator: false }, { expression: null }]) {
    assert.equal(checkArithmetic(condition, { ...facts, ...change }).success, false);
  }
});

test('restricted arithmetic handles precedence and decimal fractions without float rounding', () => {
  for (const [expression, result] of [['0.1+0.2', '0.3'], ['-2*(-3+4)', '-2'], ['12/3/2', '2'], ['128*37+(456/4)', '4850']]) {
    assert.equal(checkArithmetic({ expression, requireExpandedExpression: true }, { ...facts, expression, result }).success, true);
  }
  for (const input of ['', '1,23', '1/0', '1/(2-2)', 'process.exit()', '2**3', 'sqrt(4)', '1;2', '(1', '1)+2', '1e3', '('.repeat(30) + '1' + ')'.repeat(30)]) assert.throws(() => parseArithmetic(input), input);
});

test('text normalization receives requirement provenance but never a screenshot or verdict', async () => {
  const messages = normalizationMessages(contract);
  const data = JSON.parse(messages.at(-1).content);
  assert.deepEqual(data.userRequirements, contract.userRequirements);
  assert.equal(data.modelStepProposal, contract.proposedStepCondition);
  assert.equal(Object.hasOwn(JSON.parse(normalizationMessages({ ...contract, scope: 'entire_user_goal' }).at(-1).content), 'modelStepProposal'), false);
  let calls = 0;
  const result = await verifyResolved({ signal: new AbortController().signal,
    normalize: async input => { calls++; assert.deepEqual(input, messages); return resolution; },
    query: async prompt => { calls++; assert.doesNotMatch(JSON.stringify(prompt), /128|5192|1298|Divide by 4|Expanded expression remains visible/); return facts; },
  }, contract);
  assert.equal(calls, 2); assert.equal(result.success, true);
});

test('invalid resolution, cancellation and missing facts fail without a success retry', async () => {
  assert.equal(validResolution({ ...resolution, calculation: { expression: '1+1' } }), false);
  let queries = 0;
  const invalid = await verifyResolved({ signal: new AbortController().signal, normalize: async () => ({}), query: async () => { queries++; } }, contract);
  assert.equal(invalid.success, false); assert.equal(queries, 0);
  const controller = new AbortController();
  await assert.rejects(verifyResolved({ signal: controller.signal, normalize: async () => { controller.abort(); return resolution; }, query: async () => { queries++; } }, contract), { name: 'AbortError' });
  assert.equal(queries, 0);
  const negative = await verifyResolved({ signal: new AbortController().signal, normalize: async () => resolution,
    query: async () => { queries++; return { ...facts, result: '649' }; } }, contract);
  assert.equal(negative.success, false); assert.equal(queries, 1);
});

test('visual verification uses only the resolved predicate and preserves negative or invalid results', async () => {
  for (const answer of [{ success: false, observation: 'Requested label absent' }, { success: true, observation: '' }, { success: 'true', observation: 'Visible' }]) {
    let calls = 0;
    const result = await verifyResolved({ signal: new AbortController().signal,
      normalize: async () => ({ kind: 'visual', visibleRequirement: 'Android version 14 is visible', executionConstraints: ['Do not modify settings'], calculation: null }),
      query: async prompt => { calls++; assert.match(prompt.success, /Android version 14/); assert.doesNotMatch(prompt.success, /Expanded expression|divide by 8/); return answer; },
    }, contract);
    assert.equal(result.success, false); assert.equal(calls, 1);
  }
});

test('an API-successful location on digit 5 is not a successful location of digit 2', () => {
  const target = { center: [375, 1544], radius: 85 };
  assert.equal(locatingMatches({ center: [375, 1544] }, target), true);
  for (const center of [[375, 1328], [250, 1029], [NaN, 1544], ['375', 1544], [375], null]) {
    assert.equal(locatingMatches({ center }, target), false);
  }
  assert.equal(locatingMatches({ center: [250, 1030] }, target, 1.5), true);
  assert.equal(locatingMatches({ center: [250, 885] }, target, 1.5), false);
  assert.equal(locatingMatches({ center: [250, 1030] }, target, NaN), false);
});

test('a correct final verdict cannot hide an incorrect calculation resolution', () => {
  const expected = { kind: 'calculation', expression: '(128*37+456)/4', requireExpandedExpression: false };
  assert.equal(resolutionMatches(resolution, expected), true);
  for (const calculation of [{ ...condition, expression: '128*37+456/4' }, { ...condition, expression: '1298' }, { ...condition, requireExpandedExpression: true }]) {
    assert.equal(resolutionMatches({ ...resolution, calculation }, expected), false);
  }
  assert.equal(resolutionMatches({ kind: 'visual', calculation: null }, expected), false);
});
