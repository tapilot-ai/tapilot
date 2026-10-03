import { describe, expect, it } from '@rstest/core';
import { attachmentReference, loadHistoryAttachments } from './chat-attachments';
import { conversationMessages, runChatAgent } from './chat-agent';
import type { TaskHistoryEntry } from './task-history';

const entry = (id: string, conversationId = 'a'): TaskHistoryEntry => ({
  id, conversationId, source: '看看附件', reply: '已经读取', status: 'succeeded',
  createdAt: Number(id), kind: 'task', modelName: 'model', reports: [],
  attachment: { id: `00000000-0000-0000-0000-${id.padStart(12, '0')}`, name: 'note.txt', kind: 'text' },
});

describe('durable chat inputs', () => {
  it('stores only references in history, excluding document content and image bytes', () => {
    const ref = attachmentReference({ id: entry('1').attachment!.id, name: 'photo.png', kind: 'image', images: ['private pixels'], text: 'private document' });
    expect(ref).not.toHaveProperty('images');
    expect(ref).not.toHaveProperty('text');
  });
  it('loads only the latest two completed inputs from the selected conversation', async () => {
    const reads: string[] = [];
    const map = await loadHistoryAttachments([entry('4', 'other'), entry('3'), entry('2'), entry('1')], 'a', async id => {
      reads.push(id); return { name: 'note.txt', kind: 'text', images: [], text: '文件内容' };
    });
    expect([...map.keys()]).toEqual(['2', '3']);
    expect(reads).not.toContain(entry('4').attachment!.id);
    expect(JSON.stringify(conversationMessages([entry('2')], 'a', '继续解释', 'zh', map))).toContain('文件内容');
  });
  it('tells the agent an unavailable historical file cannot be guessed', async () => {
    const map = await loadHistoryAttachments([entry('1')], 'a', async () => { throw new Error('missing'); });
    expect(map.get('1')?.text).toContain('不能猜测');
  });
  it('does not execute a tool returned after stopping the model request', async () => {
    let stopped = false; let calls = 0;
    await expect(runChatAgent({ history: [], conversationId: 'a', source: '打开计算器', language: 'zh',
      isStopped: () => stopped,
      complete: async () => { stopped = true; return { choices: [{ message: { tool_calls: [{ id: '1', type: 'function',
        function: { name: 'operate_phone', arguments: JSON.stringify({ instruction: 'open calculator', success_condition: 'calculator visible' }) } }] } }] }; },
      operatePhone: async () => { calls++; return { success: true, result: 'done' }; },
    })).rejects.toThrow('Task stopped');
    expect(calls).toBe(0);
  });
});
