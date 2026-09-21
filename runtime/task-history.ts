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

export interface TaskHistoryEntry {
  id: string;
  createdAt: number;
  source: string;
  kind: 'task' | 'yaml';
  modelName: string;
  status: TaskHistoryStatus;
  detail?: string;
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
  kind?: unknown;
  modelName?: unknown;
  status?: unknown;
  succeeded?: unknown;
  detail?: unknown;
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
        kind: legacy.kind === 'yaml' ? 'yaml' : 'task',
        modelName:
          typeof legacy.modelName === 'string' ? legacy.modelName : '',
        status,
        detail: typeof legacy.detail === 'string' ? legacy.detail : undefined,
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
