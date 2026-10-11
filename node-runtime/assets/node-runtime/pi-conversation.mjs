import { projectContext } from './pi-context.mjs';
import { Agent } from '@earendil-works/pi-agent-core';
import { createAssistantMessageEventStream, getCurrentSystemMessage } from '@earendil-works/pi-ai';
import { Type } from 'typebox';

const usage = () => ({ input: 0, output: 0, cacheRead: 0, cacheWrite: 0, totalTokens: 0,
  cost: { input: 0, output: 0, cacheRead: 0, cacheWrite: 0, total: 0 } });
const model = { id: 'tapilot-native', name: 'Tapilot configured API', api: 'openai-completions',
  provider: 'tapilot', baseUrl: '', reasoning: false, input: ['text', 'image'],
  cost: { input: 0, output: 0, cacheRead: 0, cacheWrite: 0 }, contextWindow: 128000, maxTokens: 2048 };
const assistant = (content = []) => ({ role: 'assistant', content, api: model.api,
  provider: model.provider, model: model.id, usage: usage(), stopReason: 'stop', timestamp: Date.now() });

function userContent(content) {
  if (typeof content === 'string') return [{ type: 'text', text: content }];
  return content.map(block => {
    if (block.type === 'text') return block;
    const match = /^data:([^;]+);base64,(.+)$/s.exec(block.image_url?.url ?? '');
    if (!match) throw new Error('Only archived data URL images are supported');
    return { type: 'image', mimeType: match[1], data: match[2] };
  });
}
export function fromOpenAI(messages) {
  return messages.filter(m => m.role !== 'system').map(m => {
    if (m.role === 'user') return { role: 'user', content: userContent(m.content), timestamp: Date.now() };
    if (m.role === 'tool') return { role: 'toolResult', toolCallId: m.tool_call_id,
      toolName: 'operate_phone', content: [{ type: 'text', text: m.content }], isError: false, timestamp: Date.now() };
    return { ...assistant(typeof m.content === 'string' && m.content ? [{ type: 'text', text: m.content }] : []),
      content: [...(m.content ? [{ type: 'text', text: m.content }] : []), ...(m.tool_calls ?? []).map(c => ({
        type: 'toolCall', id: c.id, name: c.function.name, arguments: JSON.parse(c.function.arguments) }))] };
  });
}
export function toOpenAI(context) {
  const system = getCurrentSystemMessage(context.messages);
  const messages = [{ role: 'system', content: system?.content ?? '' }];
  for (const m of context.messages) {
    if (m.role === 'system') continue;
    if (m.role === 'user') messages.push({ role: 'user', content: typeof m.content === 'string' ? m.content : m.content.map(b =>
      b.type === 'image' ? { type: 'image_url', image_url: { url: `data:${b.mimeType};base64,${b.data}` } } : { type: 'text', text: b.text }) });
    else if (m.role === 'toolResult') messages.push({ role: 'tool', tool_call_id: m.toolCallId,
      content: JSON.stringify({ success: !m.isError, result: m.content.filter(b => b.type === 'text').map(b => b.text).join('\n') }) });
    else if (m.role === 'assistant' && !['error', 'aborted'].includes(m.stopReason)) {
      const calls = m.content.filter(b => b.type === 'toolCall');
      messages.push({ role: 'assistant', content: m.content.filter(b => b.type === 'text').map(b => b.text).join('\n') || null,
        ...(calls.length ? { tool_calls: calls.map(c => ({ id: c.id, type: 'function', function: { name: c.name, arguments: JSON.stringify(c.arguments) } })) } : {}) });
    }
  }
  return { messages, tools: (system?.toolsAdded ?? []).map(t => ({ type: 'function', function: {
    name: t.name, description: t.description, parameters: t.parameters } })) };
}

/** Repair interrupted tool batches. A missing result means unknown effect, never success. */
export function resumableTranscript(messages) {
  const result = [];
  for (let i = 0; i < messages.length; i++) {
    const m = messages[i];
    if (m.role === 'system' || (m.role === 'assistant' && ['error', 'aborted'].includes(m.stopReason))) continue;
    result.push(structuredClone(m));
    if (m.role !== 'assistant') continue;
    for (const call of m.content.filter(b => b.type === 'toolCall')) {
      if (!messages.slice(i + 1).some(next => next.role === 'toolResult' && next.toolCallId === call.id))
        result.push({ role: 'toolResult', toolCallId: call.id, toolName: call.name,
          content: [{ type: 'text', text: '执行被打断，结果未知。先检查当前手机画面再决定下一步，不能盲目重做。' }],
          isError: true, timestamp: Date.now() });
    }
  }
  return result;
}
export function checkpointMessages(messages) {
  return messages.filter(m => m.role !== 'system').map(m => ({ ...m,
    content: Array.isArray(m.content) ? m.content.map(b => b.type === 'image'
      ? { type: 'text', text: '[此前引用的图片，请通过当前手机画面或重新提供的附件核验。]' } : b) : m.content }));
}

/** Pi alone owns the transcript and model/tool loop. Native RPC owns credentials and Android I/O. */
export function createConversation({ messages, goal = '', resume = [], pendingInputs = [], limits = {}, rpc, emit }) {
  let turns = 0, operations = 0, lastPhoneResult = null, final = null, planned = false;
  const checkLimit = (count, limit, title) => { if (limit > 0 && count >= limit)
    throw new Error(`本次任务已达到${title}上限（${limit}），可重试或在设置中调整上限`); };
  const taskPrompt = `用户的操作指令必须作为任务持续推进。复杂任务先调用 set_plan 保存简短步骤，执行中按结果更新计划。用户在执行中补充的信息属于当前任务：修改条件就调整后续步骤，追加要求就继续完成；明确换任务则遵循最新要求。手机操作必须串行执行，失败可以调整后重试。需要补充信息时调用 finish_task，outcome=waiting_user 并提出具体问题；任务无法完成时用 outcome=failed 如实说明原因。执行过手机操作的任务只有通过最终画面核验后才可调用 finish_task，outcome=completed。不能把一句承诺、计划或工具未返回当作完成。恢复被打断任务时先检查实时画面，避免重复已经发生的操作。普通知识问答可以直接回答。`;
  const tools = [
    { name: 'operate_phone', description: 'Execute a concrete phone instruction with Midscene and verify the final visible screen. Do not pass chat history.',
      executionMode: 'sequential', parameters: Type.Object({ instruction: Type.String({ minLength: 1 }), success_condition: Type.String({ minLength: 1 }) }),
      execute: async (_id, args, signal) => {
        lastPhoneResult = { success: false, result: '手机操作未完成' };
        checkLimit(operations, limits.phoneOperations, '手机操作'); operations++;
        emit({ type: 'operation', operations, instruction: args.instruction });
        lastPhoneResult = await rpc('phone.execute', args, signal);
        if (!lastPhoneResult.success) throw new Error(lastPhoneResult.result || '手机操作失败');
        return { content: [{ type: 'text', text: lastPhoneResult.result }], details: lastPhoneResult };
      } },
    { name: 'set_plan', description: 'Save or revise the task plan and progress. Step index is zero based.',
      parameters: Type.Object({ steps: Type.Array(Type.String({ minLength: 1 }), { minItems: 1, maxItems: 20 }), currentStep: Type.Integer({ minimum: 0 }) }),
      execute: async (_id, args) => {
        if (args.currentStep > args.steps.length) throw new Error('步骤索引超过计划长度');
        planned = true; emit({ type: 'plan', ...args });
        return { content: [{ type: 'text', text: '任务计划已保存，继续推进任务。' }] };
      } },
    { name: 'finish_task', description: 'Finish a verified task or ask a specific question before continuing it.',
      parameters: Type.Object({ outcome: Type.Union([Type.Literal('completed'), Type.Literal('waiting_user'), Type.Literal('failed')]), summary: Type.String({ minLength: 1 }) }),
      execute: async (_id, args) => {
        if (args.outcome === 'completed' && lastPhoneResult?.success === false) throw new Error('操作失败，不能标记任务完成');
        final = args;
        return { content: [{ type: 'text', text: args.summary }], terminate: true };
      } },
  ];
  const agent = new Agent({ initialState: { systemPrompt: `${messages.find(m => m.role === 'system')?.content ?? ''}\n${taskPrompt}${goal ? `\n当前任务目标（继续或重试时按此目标和已返回的操作记录推进；最新用户消息可修改目标）：${JSON.stringify(goal)}` : ''}`,
      model, tools, messages: resume.length ? resumableTranscript(resume) : fromOpenAI(messages.slice(0, -1)) },
    toolExecution: 'sequential', steeringMode: 'all', followUpMode: 'all',
    beforeToolCall: () => final ? { block: true, terminate: true, reason: '任务已结束，后续操作未执行。' } : undefined,
    streamFn: (_model, context, options) => {
      const stream = createAssistantMessageEventStream();
      void (async () => {
        try {
          options?.signal?.throwIfAborted();
          checkLimit(turns, limits.modelTurns, '对话轮次'); turns++;
          emit({ type: 'model', turns });
          const response = await rpc('model.complete', (() => { const request = toOpenAI(context); request.messages = projectContext(request.messages); return request; })(), options?.signal);
          options?.signal?.throwIfAborted();
          const message = response.choices?.[0]?.message;
          if (!message) throw new Error('模型未返回消息');
          const content = [];
          if (message.content) content.push({ type: 'text', text: message.content });
          for (const c of message.tool_calls ?? []) content.push({ type: 'toolCall', id: c.id, name: c.function.name, arguments: JSON.parse(c.function.arguments) });
          if (!content.length) throw new Error('模型返回了空回复');
          const result = { ...assistant(content), stopReason: response.choices[0].finish_reason === 'length' ? 'length' : content.some(b => b.type === 'toolCall') ? 'toolUse' : 'stop' };
          stream.push({ type: 'done', reason: result.stopReason, message: result }); stream.end(result);
        } catch (error) {
          const result = { ...assistant(), stopReason: options?.signal?.aborted ? 'aborted' : 'error', errorMessage: error.message };
          stream.push({ type: 'error', reason: result.stopReason, error: result }); stream.end(result);
        }
      })();
      return stream;
    },
    finishTurn: ({ message }) => {
      if (['error', 'aborted'].includes(message.stopReason)) return;
      if (final && !agent.hasQueuedMessages()) return { action: 'end' };
      if ((operations > 0 || planned) && !final && !message.content.some(b => b.type === 'toolCall')) return { action: 'continue' };
    },
  });
  agent.subscribe(event => {
    if (event.type === 'message_end' && event.message.role === 'user' && event.message.inputId) final = null;
    if (event.type === 'tool_execution_end' && event.toolName === 'operate_phone' && event.isError)
      lastPhoneResult = { success: false, result: event.result.content.filter(b => b.type === 'text').map(b => b.text).join('\n') };
    if (event.type === 'message_end' || event.type === 'turn_end') emit({ type: 'checkpoint', messages: checkpointMessages(agent.state.messages), turns, operations });
  });
  return {
    steer(input, followUp = false) {
      final = null;
      (followUp ? agent.followUp.bind(agent) : agent.steer.bind(agent))({ role: 'user', content: input.text, timestamp: Date.now(), inputId: input.id });
    },
    abort: () => agent.abort(),
    async run() {
      for (const input of pendingInputs) agent.steer({ role: 'user', content: input.text, inputId: input.id, timestamp: Date.now() });
      await agent.prompt(fromOpenAI([messages.at(-1)])[0]);
      // Messages arriving during the final turn are still part of this task.
      while (agent.hasQueuedMessages()) { final = null; await agent.continue(); }
      const last = agent.state.messages.filter(m => m.role === 'assistant').at(-1);
      if (last && ['error', 'aborted'].includes(last.stopReason)) throw new Error(last.errorMessage || '任务已停止');
      const reply = final?.summary ?? last?.content.filter(b => b.type === 'text').map(b => b.text).join('\n');
      if (!reply?.trim()) throw new Error('模型未返回有效结果');
      return { reply, lastPhoneResult, phase: final?.outcome ?? 'completed', turns, operations };
    },
  };
}
