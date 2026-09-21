export function resolveAdvancedMode(value: string | null): boolean {
  return value === 'true';
}

export function resolveRunKindForAdvancedMode(
  runKind: 'task' | 'yaml',
  advancedMode: boolean,
): 'task' | 'yaml' {
  return advancedMode ? runKind : 'task';
}
