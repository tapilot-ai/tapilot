import assert from 'node:assert/strict';
import test from 'node:test';
import { applyModelEnvironment } from '../model-environment.js';

test('selected provider cannot inherit protocol, role credentials or extra body from another profile', () => {
  const env: Record<string, string | undefined> = {
    MIDSCENE_MODEL_PROTOCOL:'openai-responses', MIDSCENE_MODEL_API_KEY:'old-test-token',
    MIDSCENE_PLANNING_MODEL_API_KEY:'old-role-test-token', MIDSCENE_INSIGHT_MODEL_EXTRA_BODY_JSON:'{}',
    TAPILOT_E2E_DEVICE_ID:'explicit-device',
  };
  applyModelEnvironment({MIDSCENE_MODEL_FAMILY:'doubao-seed', MIDSCENE_MODEL_API_KEY:'selected-test-token'},env);
  assert.deepEqual(env,{TAPILOT_E2E_DEVICE_ID:'explicit-device',MIDSCENE_MODEL_FAMILY:'doubao-seed',MIDSCENE_MODEL_API_KEY:'selected-test-token'});
});

test('explicit protocol stays selected; absent local profiles leave a shell configuration usable', () => {
  const env={MIDSCENE_MODEL_PROTOCOL:'openai-responses'};
  applyModelEnvironment(undefined,env); assert.equal(env.MIDSCENE_MODEL_PROTOCOL,'openai-responses');
  applyModelEnvironment({MIDSCENE_MODEL_PROTOCOL:'openai-chat'},env); assert.equal(env.MIDSCENE_MODEL_PROTOCOL,'openai-chat');
});
