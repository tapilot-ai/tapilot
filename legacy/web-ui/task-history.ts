export type TaskHistoryStatus =
  | 'running'
  | 'succeeded'
  | 'failed'
  | 'stopped';

export interface TaskHistoryReport {
  reportId: string;
  kind: 'agent' | 'test-runner';
  title: string;
  projectName?: string;
  caseName?: string;
  attempt?: number;
}

export interface AttachmentReference { id: string; name: string; kind: 'image' | 'pdf' | 'text'; pageCount?: number }

export interface TaskHistoryEntry {
  id: string;
  conversationId?: string;
  createdAt: number;
  source: string;
  inputText?: string;
  attachment?: AttachmentReference;
  kind: 'task' | 'yaml';
  modelName: string;
  status: TaskHistoryStatus;
  detail?: string;
  reply?: string;
  reports: TaskHistoryReport[];
}

export interface TaskHistoryRerunRequest {
  source: string;
  kind: TaskHistoryEntry['kind'];
}

/** Returns an executable copy without mutating the saved history entry. */
export function taskHistoryRerunRequest(
  entry: TaskHistoryEntry,
): TaskHistoryRerunRequest | undefined {
  const source = entry.source.trim();
  return source ? { source, kind: entry.kind } : undefined;
}

/** A failure may have only a runner report; it must remain accessible in simple mode. */
export function primaryTaskReport(reports: TaskHistoryReport[]): TaskHistoryReport | undefined {
  return reports.filter(report => report.kind === 'agent').at(-1) ?? reports.at(-1);
}

interface LegacyReportRun {
  id?: unknown;
  createdAt?: unknown;
  task?: unknown;
  source?: unknown;
  inputText?: unknown;
  attachment?: unknown;
  kind?: unknown;
  modelName?: unknown;
  status?: unknown;
  succeeded?: unknown;
  detail?: unknown;
  reply?: unknown;
  conversationId?: unknown;
  reports?: unknown;
}

const statuses = new Set<TaskHistoryStatus>([
  'running',
  'succeeded',
  'failed',
  'stopped',
]);

export function normalizeTaskHistory(value: unknown): TaskHistoryEntry[] {
  if (!Array.isArray(value)) return [];
  return value.flatMap((candidate, index) => {
    if (!candidate || typeof candidate !== 'object') return [];
    const legacy = candidate as LegacyReportRun;
    const source =
      typeof legacy.source === 'string'
        ? legacy.source
        : typeof legacy.task === 'string'
          ? legacy.task
          : '';
    const rawStatus = legacy.status;
    const status: TaskHistoryStatus =
      typeof rawStatus === 'string' && statuses.has(rawStatus as TaskHistoryStatus)
        ? rawStatus === 'running'
          ? 'stopped'
          : (rawStatus as TaskHistoryStatus)
        : legacy.succeeded === false
          ? 'failed'
          : 'succeeded';
    return [
      {
        id:
          typeof legacy.id === 'string'
            ? legacy.id
            : `legacy-${String(index)}`,
        createdAt:
          typeof legacy.createdAt === 'number'
            ? legacy.createdAt
            : Date.now(),
        source,
        inputText: typeof legacy.inputText === 'string' ? legacy.inputText : undefined,
        attachment: normalizeAttachmentReference(legacy.attachment),
        conversationId: typeof legacy.conversationId === 'string' ? legacy.conversationId : undefined,
        kind: legacy.kind === 'yaml' ? 'yaml' : 'task',
        modelName:
          typeof legacy.modelName === 'string' ? legacy.modelName : '',
        status,
        detail: typeof legacy.detail === 'string' ? legacy.detail : undefined,
        reply: typeof legacy.reply === 'string' ? legacy.reply : undefined,
        reports: Array.isArray(legacy.reports)
          ? (legacy.reports as TaskHistoryReport[])
          : [],
      },
    ];
  });
}

export function addTaskHistoryEntry(
  history: TaskHistoryEntry[],
  entry: TaskHistoryEntry,
  limit = 20,
): TaskHistoryEntry[] {
  return [entry, ...history.filter((item) => item.id !== entry.id)].slice(
    0,
    limit,
  );
}

export function updateTaskHistoryEntry(
  history: TaskHistoryEntry[],
  id: string,
  patch: Partial<Omit<TaskHistoryEntry, 'id'>>,
): TaskHistoryEntry[] {
  return history.map((entry) =>
    entry.id === id ? { ...entry, ...patch } : entry,
  );
}

export function normalizeAttachmentReference(value: unknown): AttachmentReference | undefined {
  if (!value || typeof value !== 'object') return;
  const ref = value as Partial<AttachmentReference>;
  if (typeof ref.id !== 'string' || !/^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/.test(ref.id) ||
      typeof ref.name !== 'string' || !['image', 'pdf', 'text'].includes(ref.kind ?? '')) return;
  return { id: ref.id, name: ref.name.slice(0, 120), kind: ref.kind!,
    ...(typeof ref.pageCount === 'number' && ref.pageCount > 0 ? { pageCount: ref.pageCount } : {}) };
}
