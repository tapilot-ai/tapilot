import { describe, expect, it } from '@rstest/core';
import { conversationMessages, runChatAgent, type ChatMessage } from './chat-agent';
import type { TaskHistoryEntry } from './task-history';

function entry(id: string, conversationId: string, source: string, reply: string): TaskHistoryEntry {
  return {
    id, conversationId, source, reply, createdAt: Number(id), kind: 'task',
    modelName: 'model', status: 'succeeded', reports: [],
  };
}

describe('chat agent', () => {
  it('gives the conversation model every saved turn in this conversation', () => {
    const messages = conversationMessages([
      entry('3', 'a', '第三轮', '答三'),
      entry('2', 'b', '别的对话', '别的答复'),
      entry('1', 'a', '第一轮', '答一'),
    ], 'a', '继续', 'zh');
    expect(messages.slice(1)).toEqual([
      { role: 'user', content: '第一轮' },
      { role: 'assistant', content: '答一' },
      { role: 'user', content: '第三轮' },
      { role: 'assistant', content: '答三' },
      { role: 'user', content: '继续' },
    ]);
  });

  it('keeps chat context in the model and sends only an explicit instruction to the phone', async () => {
    const modelCalls: ChatMessage[][] = [];
    const phoneCalls: string[] = [];
    const reply = await runChatAgent({
      history: [entry('1', 'a', '打开相机', '相机已打开')],
      conversationId: 'a', source: '再返回桌面', language: 'zh',
      async complete(messages) {
        modelCalls.push(structuredClone(messages));
        return modelCalls.length === 1
          ? { choices: [{ message: { content: null, tool_calls: [{
            id: 'call-1', type: 'function', function: {
              name: 'operate_phone', arguments: JSON.stringify({ instruction: '返回 Android 桌面' }),
            },
          }] } }] }
          : { choices: [{ message: { content: '已经返回桌面。' } }] };
      },
      async operatePhone(instruction) {
        phoneCalls.push(instruction);
        return { success: true, result: 'Android 桌面已显示' };
      },
    });
    expect(reply).toBe('已经返回桌面。');
    expect(phoneCalls).toEqual(['返回 Android 桌面']);
    expect(modelCalls[0].some(message => message.content === '打开相机')).toBe(true);
    expect(modelCalls[1].at(-1)).toEqual({
      role: 'tool', tool_call_id: 'call-1', content: JSON.stringify({ success: true, result: 'Android 桌面已显示' }),
    });
  });

  it('answers directly without invoking the phone', async () => {
    let operated = false;
    const reply = await runChatAgent({
      history: [], conversationId: 'a', source: '你好', language: 'zh',
      async complete() { return { choices: [{ message: { content: '你好！' } }] }; },
      async operatePhone() { operated = true; return { success: true, result: '' }; },
    });
    expect(reply).toBe('你好！');
    expect(operated).toBe(false);
  });
});
