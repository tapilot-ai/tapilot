import test from 'node:test';
import assert from 'node:assert/strict';
import { createConversation, checkpointMessages, resumableTranscript } from './pi-conversation.mjs';
const input = text => [{ role: 'system', content: 'Tapilot' }, { role: 'user', content: text }];
const answer = content => ({ choices: [{ message: { content } }] });
const tool = (name, args) => ({ choices: [{ message: { tool_calls: [{ id: `call-${name}`, function: { name, arguments: JSON.stringify(args) } }] } }] });

test('Pi pushes a planned phone task to verified completion through native RPC', async () => {
  let count = 0;
  const events = [], methods = [];
  const conversation = createConversation({ messages: input('打开计算器'), rpc: async (method, params) => {
    methods.push(method);
    if (method === 'phone.execute') return { success: true, result: '计算器已显示' };
    assert.deepEqual(params.tools.map(t => t.function.name), ['operate_phone', 'set_plan', 'finish_task']);
    return [tool('set_plan', { steps: ['打开计算器'], currentStep: 0 }),
      tool('operate_phone', { instruction: '打开计算器', success_condition: '计算器显示' }),
      tool('finish_task', { outcome: 'completed', summary: '已打开计算器' })][count++];
  }, emit: e => events.push(e) });
  const result = await conversation.run();
  assert.equal(result.reply, '已打开计算器'); assert.equal(result.operations, 1);
  assert.equal(result.phase, 'completed'); assert.ok(events.some(e => e.type === 'plan'));
  assert.equal(methods.filter(m => m === 'phone.execute').length, 1);
});

test('steering amends an active task after its phone operation', async () => {
  let conversation, seen = false, models = 0;
  conversation = createConversation({ messages: input('打开计算器'), rpc: async (method, params) => {
    if (method === 'phone.execute') {
      conversation.steer({ id: 'amend-1', text: '改为只回复 PINE94' });
      return { success: true, result: '已打开' };
    }
    if (models++ === 0) return tool('operate_phone', { instruction: '打开计算器', success_condition: '计算器显示' });
    seen = params.messages.some(m => JSON.stringify(m.content).includes('PINE94'));
    return tool('finish_task', { outcome: 'completed', summary: 'PINE94' });
  }, emit: () => {} });
  assert.equal((await conversation.run()).reply, 'PINE94'); assert.ok(seen);
});

test('failed phone verification cannot finish successfully; adjusted retry can', async () => {
  let models = 0, phones = 0;
  const results = [];
  const conversation = createConversation({ messages: input('任务'), rpc: async (method, params) => {
    if (method === 'phone.execute') return { success: ++phones > 1, result: phones === 1 ? '未验证成功' : '已验证' };
    results.push(params.messages);
    return [tool('operate_phone', { instruction: '操作', success_condition: '成功' }),
      tool('finish_task', { outcome: 'completed', summary: '错误地完成' }),
      tool('operate_phone', { instruction: '调整后操作', success_condition: '成功' }),
      tool('finish_task', { outcome: 'completed', summary: '重试完成' })][models++];
  }, emit: () => {} });
  assert.equal((await conversation.run()).reply, '重试完成'); assert.equal(phones, 2);
  assert.ok(JSON.stringify(results).includes('不能标记任务完成'));
});

test('waiting for clarification returns a durable waiting phase', async () => {
  const conversation = createConversation({ messages: input('打开那个应用'), rpc: async () =>
    tool('finish_task', { outcome: 'waiting_user', summary: '要打开哪个应用？' }), emit: () => {} });
  const result = await conversation.run(); assert.equal(result.phase, 'waiting_user'); assert.equal(result.operations, 0);
});

test('per-attempt model limit stops a runaway agent', async () => {
  let calls = 0;
  const conversation = createConversation({ messages: input('任务'), limits: { modelTurns: 1 }, rpc: async () => {
    calls++; return tool('set_plan', { steps: ['检查'], currentStep: 0 });
  }, emit: () => {} });
  await assert.rejects(conversation.run(), /对话轮次.*上限/); assert.equal(calls, 1);
});

test('abort rejects a blocked model request and stops the task', async () => {
  let ready;
  const started = new Promise(resolve => { ready = resolve; });
  const conversation = createConversation({ messages: input('任务'), rpc: async (_m, _p, signal) =>
    new Promise((_resolve, reject) => { signal.addEventListener('abort', () => reject(new Error('stopped')), { once: true }); ready(); }), emit: () => {} });
  const result = conversation.run(); await started; conversation.abort();
  await assert.rejects(result, /stopped/);
});

test('resume repairs a missing tool result instead of inventing success', () => {
  const context = resumableTranscript([{ role: 'assistant', content: [{ type: 'toolCall', id: 'uncertain', name: 'operate_phone', arguments: {} }] }]);
  assert.equal(context[1].isError, true); assert.match(context[1].content[0].text, /先检查/);
});

test('checkpoint excludes archived image payload but preserves steering input identity', () => {
  const saved = checkpointMessages([{ role: 'user', inputId: 'input-1', content: [{ type: 'image', data: 'SECRET_IMAGE', mimeType: 'image/png' }] }]);
  assert.equal(saved[0].inputId, 'input-1'); assert.ok(!JSON.stringify(saved).includes('SECRET_IMAGE'));
});

test('disabled limits permit more than 100 operations and preserve fresh counters per attempt', async () => {
  for (let attempt = 0; attempt < 2; attempt++) {
    let operations = 0;
    const conversation = createConversation({ messages: input('长任务'), limits: { modelTurns: 0, phoneOperations: 0 }, rpc: async method => {
      if (method === 'phone.execute') return { success: true, result: `步骤 ${++operations}` };
      return operations < 120 ? tool('operate_phone', { instruction: '检查屏幕', success_condition: '可见' })
        : tool('finish_task', { outcome: 'completed', summary: '完成' });
    }, emit: () => {} });
    assert.equal((await conversation.run()).operations, 120);
  }
});

test('phone ceiling blocks subsequent side effects and cannot be mistaken for success', async () => {
  let operations = 0, models = 0;
  const conversation = createConversation({ messages: input('任务'), limits: { phoneOperations: 2, modelTurns: 6 }, rpc: async method => {
    if (method === 'phone.execute') { operations++; return { success: true, result: '成功' }; }
    models++;
    if (models === 4) return tool('finish_task', { outcome: 'completed', summary: '不能完成' });
    return tool('operate_phone', { instruction: '操作', success_condition: '成功' });
  }, emit: () => {} });
  await assert.rejects(conversation.run(), /上限/); assert.equal(operations, 2);
});

test('abort after model response prevents any queued phone operation', async () => {
  let conversation, operations = 0;
  conversation = createConversation({ messages: input('任务'), rpc: async method => {
    if (method === 'phone.execute') { operations++; return { success: true, result: '' }; }
    conversation.abort(); return tool('operate_phone', { instruction: '操作', success_condition: '成功' });
  }, emit: () => {} });
  await assert.rejects(conversation.run()); assert.equal(operations, 0);
});

test('a completed task blocks later operations in the same model batch', async () => {
  let operations = 0;
  const first = tool('finish_task', { outcome: 'completed', summary: '完成' });
  first.choices[0].message.tool_calls.push(tool('operate_phone', { instruction: '不应执行', success_condition: '成功' }).choices[0].message.tool_calls[0]);
  const conversation = createConversation({ messages: input('任务'), rpc: async method => {
    if (method === 'phone.execute') operations++;
    return first;
  }, emit: () => {} });
  assert.equal((await conversation.run()).reply, '完成'); assert.equal(operations, 0);
});

test('checkpoint stores consumed steering identities for crash recovery', async () => {
  const checkpoints = []; let conversation;
  conversation = createConversation({ messages: input('任务'), rpc: async (_method, params) => {
    if (!params.messages.some(m => JSON.stringify(m.content).includes('NEW_INPUT'))) {
      conversation.steer({ id: 'durable-input', text: 'NEW_INPUT' }); return answer('旧回复');
    }
    return answer('新回复');
  }, emit: event => { if (event.type === 'checkpoint') checkpoints.push(event.messages); } });
  assert.equal((await conversation.run()).reply, '新回复');
  assert.ok(checkpoints.at(-1).some(m => m.inputId === 'durable-input'));
});

test('resume retains an amendment accepted before an interrupted operation finished', async () => {
  let seen = false;
  const conversation = createConversation({ messages: input('继续'),
    resume: [{ role: 'user', content: '打开计算器', timestamp: 1 }], pendingInputs: [{ id: 'old-input', text: '改成返回桌面' }],
    rpc: async (_method, params) => {
      seen = JSON.stringify(params.messages).includes('改成返回桌面'); return answer('已收到补充');
    }, emit: () => {} });
  await conversation.run(); assert.ok(seen);
});

test('steering received during a finish response can still execute the added requirement', async () => {
  let conversation, models = 0, phones = 0;
  conversation = createConversation({ messages: input('原任务'), rpc: async method => {
    if (method === 'phone.execute') { phones++; return { success: true, result: '新增操作成功' }; }
    if (models++ === 0) {
      conversation.steer({ id: 'late-amendment', text: '还需要打开计算器' });
      return tool('finish_task', { outcome: 'completed', summary: '旧任务已完成' });
    }
    if (models === 2) return tool('operate_phone', { instruction: '打开计算器', success_condition: '显示计算器' });
    return tool('finish_task', { outcome: 'completed', summary: '追加任务已完成' });
  }, emit: () => {} });
  assert.equal((await conversation.run()).reply, '追加任务已完成'); assert.equal(phones, 1);
});

test('restored task goal is explicit in the model context and truncated calls never operate', async () => {
  let seen = false, phones = 0, turns = 0;
  const conversation = createConversation({ messages: input('继续'), goal: '打开计算器并计算 3+4', limits: { modelTurns: 2 },
    rpc: async (method, params) => {
      if (method === 'phone.execute') { phones++; return { success: true, result: '不应发生' }; }
      seen = params.messages[0].content.includes('打开计算器并计算 3+4');
      if (turns++ === 0) {
        const response = tool('operate_phone', { instruction: '截断的操作', success_condition: '成功' });
        response.choices[0].finish_reason = 'length'; return response;
      }
      return answer('参数被截断，请重试');
    }, emit: () => {} });
  const result = await conversation.run(); assert.ok(seen); assert.equal(phones, 0); assert.equal(result.lastPhoneResult.success, false);
});

test('agent can report an unrecoverable failure without claiming completion', async () => {
  const conversation = createConversation({ messages: input('任务'), rpc: async () =>
    tool('finish_task', { outcome: 'failed', summary: '服务不可用，请稍后重试' }), emit: () => {} });
  const result = await conversation.run(); assert.equal(result.phase, 'failed'); assert.match(result.reply, /服务不可用/);
});

import { projectContext } from './pi-context.mjs';
test('context projection preserves constraints, failures, and complete recent tool batches', () => {
  const messages = [{role:'system',content:'system'}, {role:'user',content:'Never send messages without my approval'}];
  for (let i=0;i<30;i++) messages.push({role:'assistant',content:null,tool_calls:[{id:`c${i}`,function:{name:'operate_phone',arguments:'{"instruction":"check"}'}}]}, {role:'tool',tool_call_id:`c${i}`,content:JSON.stringify({success:i!==0,result:'start'+'.'.repeat(4000)+'end'})});
  const projected=projectContext(messages);
  assert.ok(JSON.stringify(projected).length<JSON.stringify(messages).length);
  assert.ok(projected.some(m=>m.content==='Never send messages without my approval'));
  assert.ok(JSON.stringify(projected).includes('false'));
  for(let i=0;i<projected.length;i++) if(projected[i].role==='tool') assert.ok(projected.slice(0,i).some(m=>m.tool_calls?.some(c=>c.id===projected[i].tool_call_id)));
  assert.equal(messages[3].content.length>4000,true); // Durable source is untouched.
});

test('Responses replay state survives tool round trips and checkpoints', async () => {
  const replay = [{ type: 'reasoning', encrypted_content: 'opaque-test-state' },
    { type: 'function_call', call_id: 'call-1', name: 'set_plan', arguments: '{}' }];
  let turns = 0;
  const checkpoints = [];
  const conversation = createConversation({ messages: input('检查任务'), limits: { modelTurns: 3 }, rpc: async (_method, params) => {
    if (turns++ === 0) return { ...tool('set_plan', { steps: ['检查'], currentStep: 0 }), tapilotResponsesOutput: replay };
    assert.deepEqual(params.messages.find(m => m.role === 'assistant').tapilotResponsesOutput, replay);
    return tool('finish_task', { outcome: 'completed', summary: '已检查' });
  }, emit: event => { if (event.type === 'checkpoint') checkpoints.push(event.messages); } });
  await conversation.run();
  assert.ok(checkpoints.some(messages => messages.some(m => m.tapilotResponsesOutput?.[0].encrypted_content === 'opaque-test-state')));
});
