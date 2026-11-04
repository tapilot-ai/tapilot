import test from 'node:test';
import assert from 'node:assert/strict';
import { AndroidAccessibilityDevice } from '@tapilot/android-accessibility';

test('the deployed adapter exposes action parameters to the planning SDK', () => {
  const device = new AndroidAccessibilityDevice({});
  const actions = device.actionSpace();
  for (const [name, field] of [['Launch', 'uri'], ['SearchApps', 'query'], ['PullGesture', 'direction']]) {
    const action = actions.find(item => item.name === name);
    assert.ok(action, `Missing action ${name}`);
    assert.equal(action.paramSchema._def.typeName, 'ZodObject', `${name} must expose the Zod 3 schema expected by the planner`);
    assert.ok(action.paramSchema.shape[field], `${name}.${field} disappeared from the model action schema`);
  }
});
