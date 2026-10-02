import { describe, expect, it } from '@rstest/core';
import { conversationView } from './native-chat-state';
import type { TaskHistoryEntry } from './task-history';

const entry = (id: string, conversationId?: string): TaskHistoryEntry => ({
  id, conversationId, createdAt: Number(id), source: `task ${id}`, reply: `reply ${id}`,
  kind: 'task', modelName: 'model', status: 'succeeded', reports: [],
});

describe('native conversation migration', () => {
  it('preserves legacy conversations and orders only the selected conversation chronologically', () => {
    const result = conversationView([entry('3', 'a'), entry('2'), entry('1', 'a')], 'a');
    expect(result.conversations).toEqual([{ id: 'a', title: 'task 1' }, { id: '2', title: 'task 2' }]);
    expect(result.messages.map(message => message.text)).toEqual(['task 1', 'reply 1', 'task 3', 'reply 3']);
    expect(conversationView([entry('2')], '2').messages[0].id).toBe('2-user');
  });

  it('keeps an empty new conversation selectable without fabricating a message', () => {
    const result = conversationView([entry('1')], 'new');
    expect(result.conversations[0]).toEqual({ id: 'new', title: 'Tapilot' });
    expect(result.messages).toEqual([]);
  });

  it('includes failure details and runner-only reports without exposing internal model fields', () => {
    const failed = { ...entry('1'), reply: undefined, status: 'failed' as const, detail: 'step failed',
      reports: [{ reportId: 'runner-report', kind: 'test-runner' as const, title: 'Report' }] };
    const message = conversationView([failed], '1').messages[1];
    expect(message).toEqual({ id: '1-assistant', role: 'assistant', text: 'step failed', status: 'failed', reportId: 'runner-report' });
    expect(message).not.toHaveProperty('modelName');
    expect(message).not.toHaveProperty('reports');
  });
});
