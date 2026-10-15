import type { TaskHistoryEntry } from './task-history';

export function conversationEntries(history: TaskHistoryEntry[], id: string): TaskHistoryEntry[] {
  return history.filter(entry => (entry.conversationId ?? entry.id) === id)
    .sort((a, b) => a.createdAt - b.createdAt);
}
