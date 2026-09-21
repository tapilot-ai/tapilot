export function resolveRunResultMessage(
  finalResponse: string | undefined,
  output: readonly string[],
  fallback: string,
): string {
  const response = finalResponse?.trim();
  if (response) return response;
  const logTail = output.slice(-30).join('\n').trim();
  return logTail || fallback;
}
