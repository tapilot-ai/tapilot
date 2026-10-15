import { describe, expect, it } from '@rstest/core';
import {
  addTaskHistoryEntry,
  normalizeTaskHistory,
  primaryTaskReport,
  taskHistoryRerunRequest,
  updateTaskHistoryEntry,
} from './task-history';

describe('task history', () => {
  it('keeps a failed task report available when only the runner report exists', () => {
    const report = { reportId: 'failed-run', kind: 'test-runner' as const, title: 'Failed run' };
    expect(primaryTaskReport([report])).toEqual(report);
  });
  it('opens the last attempt step report in preference to a summary', () => {
    expect(primaryTaskReport([
      { reportId: 'first', kind: 'agent', title: 'Attempt 1' },
      { reportId: 'last', kind: 'agent', title: 'Attempt 2 failed' },
      { reportId: 'summary', kind: 'test-runner', title: 'Summary' },
    ])?.reportId).toBe('last');
    expect(primaryTaskReport([])).toBeUndefined();
  });
  it('migrates old report entries and marks unfinished runs as stopped', () => {
    expect(
      normalizeTaskHistory([
        { id: 'old', task: 'open settings', succeeded: true, reports: [] },
        { id: 'run', source: 'open camera', status: 'running', reports: [] },
      ]),
    ).toMatchObject([
      { id: 'old', source: 'open settings', status: 'succeeded' },
      { id: 'run', source: 'open camera', status: 'stopped' },
    ]);
  });

  it('restores conversation ids and assistant replies', () => {
    expect(normalizeTaskHistory([{
      id: 'turn', conversationId: 'chat', source: '打开日历',
      reply: '已经打开', status: 'succeeded', reports: [],
    }])[0]).toMatchObject({ conversationId: 'chat', reply: '已经打开' });
  });

  it('adds, limits, and updates entries without duplicating ids', () => {
    const first = {
      id: '1',
      createdAt: 1,
      source: 'one',
      kind: 'task' as const,
      modelName: 'model',
      status: 'running' as const,
      reports: [],
    };
    const second = { ...first, id: '2', source: 'two' };
    const history = addTaskHistoryEntry([first], second, 2);
    expect(history.map((entry) => entry.id)).toEqual(['2', '1']);
    expect(
      updateTaskHistoryEntry(history, '2', {
        status: 'failed',
        detail: 'network error',
      })[0],
    ).toMatchObject({ status: 'failed', detail: 'network error' });
  });

  it('creates a rerun request from the original task without mutating it', () => {
    const entry = {
      id: '1',
      createdAt: 1,
      source: '  open settings  ',
      kind: 'task' as const,
      modelName: 'model',
      status: 'succeeded' as const,
      reports: [],
    };

    expect(taskHistoryRerunRequest(entry)).toEqual({
      source: 'open settings',
      kind: 'task',
    });
    expect(entry.source).toBe('  open settings  ');
    expect(taskHistoryRerunRequest({ ...entry, source: '   ' })).toBeUndefined();
  });
});
