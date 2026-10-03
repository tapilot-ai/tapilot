import { conversationEntries } from './conversation';
import type { NativeAttachment } from './native-bridge';
import type { TaskHistoryEntry, AttachmentReference } from './task-history';

export function attachmentReference(attachment: NativeAttachment): AttachmentReference {
  if (!attachment.id) throw new Error('附件尚未保存');
  return { id: attachment.id, name: attachment.name, kind: attachment.kind,
    ...(attachment.pageCount ? { pageCount: attachment.pageCount } : {}) };
}

/** Limit image history, and never load another conversation's private attachment. */
export async function loadHistoryAttachments(
  history: TaskHistoryEntry[], conversationId: string,
  read: (id: string) => Promise<NativeAttachment>,
): Promise<Map<string, NativeAttachment>> {
  const selected = conversationEntries(history, conversationId)
    .filter(entry => entry.status === 'succeeded' && entry.reply && entry.attachment).slice(-2);
  const pairs = await Promise.all(selected.map(async entry => {
    let attachment: NativeAttachment;
    try { attachment = await read(entry.attachment!.id); }
    catch { attachment = { name: entry.attachment!.name, kind: 'text', images: [],
      text: '之前的附件已不可用。不能猜测其内容；若问题依赖附件，请用户重新提供。' }; }
    return [entry.id, attachment] as const;
  }));
  return new Map(pairs);
}
