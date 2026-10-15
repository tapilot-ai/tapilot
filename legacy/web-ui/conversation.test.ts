import { describe, expect, it } from '@rstest/core';
import { conversationEntries } from './conversation';
import type { TaskHistoryEntry } from './task-history';

function entry(id: string, conversationId: string, source: string, reply: string): TaskHistoryEntry {
  return {
    id, conversationId, source, reply, createdAt: Number(id), kind: 'task',
    modelName: 'vision', status: 'succeeded', reports: [],
  };
}

describe('conversation context', () => {
  it('keeps messages in chronological order and isolates other conversations', () => {
    const history = [
      entry('3', 'a', '第三条', '已完成'),
      entry('2', 'b', '其他对话', '已完成'),
      entry('1', 'a', '第一条', '已完成'),
    ];
    expect(conversationEntries(history, 'a').map(item => item.source)).toEqual(['第一条', '第三条']);
  });
});
