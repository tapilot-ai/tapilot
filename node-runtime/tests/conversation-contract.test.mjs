import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { parsePhoneOperation } from './conversation-tools.mjs';
import { RuntimeFailure, failureEvent, runtimeMessage } from './runtime-errors.mjs';
import { createConversation } from './pi-conversation.mjs';

const fixture = JSON.parse(await readFile(new URL('./conversation-fixtures.json', import.meta.url), 'utf8'));
test('phone operation contract agrees with native defaults and rejects invalid fields', () => {
  for (const value of fixture.valid) assert.deepEqual(parsePhoneOperation(value.input), value.expected);
  for (const value of fixture.invalid) assert.throws(() => parsePhoneOperation(value));
});
test('runtime errors preserve stable codes and match native translations', () => {
  for (const item of fixture.errors) {
    assert.equal(runtimeMessage(item.code), item.en);
    for (const language of ['en', 'zh']) {
      const failure = failureEvent(new RuntimeFailure(item.code, language));
      assert.equal(failure.errorCode, item.code);
      assert.equal(failure.error, item[language]);
    }
  }
  assert.equal(failureEvent(new Error('provider diagnostic')).error, 'provider diagnostic');
  assert.equal(failureEvent(new Error('provider diagnostic')).errorCode, undefined);
});
test('English is the default for model failures and non-progress terminal results', async () => {
  const create = rpc => createConversation({ messages: [{ role: 'user', content: 'Read the screen' }], rpc, emit() {} });
  await assert.rejects(create(async () => ({ choices: [] })).run(), error =>
    error.code === 'model_message_missing' && error.message === runtimeMessage(error.code));
  const result = await create(async () => ({ choices: [{ message: { content: 'I will do it' } }] })).run();
  assert.equal(result.phase, 'failed');
  assert.equal(result.errorCode, 'task_no_progress');
  assert.equal(result.reply, runtimeMessage('task_no_progress'));
});
