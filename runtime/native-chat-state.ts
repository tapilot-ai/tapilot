import { conversationEntries } from './conversation';
import { primaryTaskReport, type TaskHistoryEntry } from './task-history';

export interface NativeChatCommand {
  action: 'refresh' | 'draft' | 'send' | 'stop' | 'new' | 'select' | 'settings'
    | 'attachment' | 'removeAttachment' | 'report' | 'confirm' | 'advanced';
  text?: string;
  id?: string;
  attachment?: import('./native-bridge').NativeAttachment;
  accepted?: boolean;
}

/** A view model containing only display data, never credentials or model context. */
export function conversationView(history: TaskHistoryEntry[], activeId: string) {
  const names = new Map<string, string>();
  for (const entry of [...history].reverse()) {
    const id = entry.conversationId ?? entry.id;
    if (!names.has(id)) names.set(id, entry.source.slice(0, 28) || 'Tapilot');
  }
  const ids = [...new Set(history.map(entry => entry.conversationId ?? entry.id))];
  if (!ids.includes(activeId)) ids.unshift(activeId);
  return {
    conversations: ids.map(id => ({ id, title: names.get(id) || 'Tapilot' })),
    messages: conversationEntries(history, activeId).flatMap(entry => [
      { id: `${entry.id}-user`, role: 'user', text: entry.source, status: entry.status, reportId: '' },
      { id: `${entry.id}-assistant`, role: 'assistant', text: entry.reply || entry.detail || '',
        status: entry.status, reportId: primaryTaskReport(entry.reports)?.reportId || '' },
    ]),
  };
}
