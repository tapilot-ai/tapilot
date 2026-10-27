import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import yaml from 'js-yaml';
import { normalizeSteps } from '@midscene/test';
import project from '../midscene.config.ts';
const directory = fileURLToPath(new URL('../', import.meta.url));
const ids = new Set();
let fixtureCount = 0;
for (const name of ['cases', 'fixtures'].flatMap(folder => readdirSync(`${directory}/${folder}`).filter(name => name.endsWith('.yaml')).map(name => `${folder}/${name}`))) {
  const document = yaml.load(readFileSync(`${directory}/${name}`, 'utf8'));
  for (const phase of ['beforeAll', 'beforeEach', 'afterEach', 'afterAll']) {
    if (document[phase]) normalizeSteps(document[phase], name => project.nodes.find(node => node.name === name));
  }
  assert.ok(Array.isArray(document?.cases) && document.cases.length, `${name}: cases are required`);
  for (const test of document.cases) {
    normalizeSteps(test.steps, name => project.nodes.find(node => node.name === name));
    assert.equal(typeof test.name, 'string');
    const id = test.name.split(' ')[0];
    assert.ok(!ids.has(id), `${id}: duplicate ID`); ids.add(id);
    assert.ok(Array.isArray(test.tags) && test.tags.length, `${id}: tags required`);
    assert.deepEqual(test.steps[0], { 'app.open': {} }, `${id}: begin with isolated observer setup`);
    for (const step of test.steps.slice(1)) {
      assert.equal(Object.keys(step).length, 1, `${id}: exactly one node per step`);
      if ('aiAct' in step || 'aiAssert' in step) {
        const prompt = step.aiAct ?? step.aiAssert;
        assert.ok(typeof prompt === 'string' && prompt.trim(), `${id}: nonempty visual prompt required`);
      }
      else {
        const node = Object.keys(step)[0];
        assert.ok(node === 'app.open' || /^(fault\.|fixture\.|wireless\.|task\.)/.test(node), `${id}: UI work must use aiAct or independent aiAssert`);
        assert.deepEqual(step[node], {}, `${id}: fixtures have no hidden UI instructions`);
        if (node === 'fixture.requireRejectedAuth') assert.ok(test.tags.includes('auth-fixture'), `${id}: rejected credentials require the isolated auth profile`);
        if (node.startsWith('fault.') || node.startsWith('wireless.')) assert.ok(test.tags.includes('resilience'), `${id}: fault/control fixtures need resilience tag`);
      }
    }
    if (test.steps.some(step => Object.keys(step)[0].startsWith('fault.') || Object.keys(step)[0].startsWith('wireless.'))) {
      assert.deepEqual(document.afterEach, [{ 'fixture.restore': {} }], `${id}: fault resources must restore after each case`);
    }
    if (name.startsWith('fixtures/')) fixtureCount++;
    assert.equal(test.tags.includes('live'), name !== 'cases/smoke.yaml', `${id}: model-driven phone tasks must opt in`);
  }
}
console.log(`Validated ${ids.size - fixtureCount} Midscene E2E cases and ${fixtureCount} setup fixtures; live tasks are explicitly tagged.`);
