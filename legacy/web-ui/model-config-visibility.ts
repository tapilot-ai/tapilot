export function resolveInitialModelConfigOpen(
  savedPreference: string | null,
  configurationComplete: boolean,
): boolean {
  if (savedPreference !== null) return savedPreference === 'true';
  return !configurationComplete;
}
