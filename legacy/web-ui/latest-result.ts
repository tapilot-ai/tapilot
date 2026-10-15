export type LatestTaskResultStatus = 'succeeded' | 'failed' | 'stopped';

export type LatestTaskResult = {
  message: string;
  status: LatestTaskResultStatus;
};

export type LatestTaskHistoryHint = {
  detail?: string;
  status: LatestTaskResultStatus | 'running';
};

export function parseLatestTaskResult(
  serialized: string | null,
  legacyMessage: string | null,
  latestHistory?: LatestTaskHistoryHint,
): LatestTaskResult | undefined {
  if (serialized) {
    try {
      const parsed = JSON.parse(serialized) as Partial<LatestTaskResult>;
      if (
        typeof parsed.message === 'string' &&
        (parsed.status === 'succeeded' ||
          parsed.status === 'failed' ||
          parsed.status === 'stopped')
      ) {
        return { message: parsed.message, status: parsed.status };
      }
    } catch {
      // Invalid local state is replaced by the legacy value when one exists.
    }
  }
  if (latestHistory) {
    const status =
      latestHistory.status === 'running' ? 'stopped' : latestHistory.status;
    return {
      message:
        latestHistory.detail?.trim() ||
        (status === 'succeeded' ? legacyMessage ?? '' : ''),
      status,
    };
  }
  return legacyMessage
    ? { message: legacyMessage, status: 'succeeded' }
    : undefined;
}

export function serializeLatestTaskResult(result: LatestTaskResult): string {
  return JSON.stringify(result);
}
