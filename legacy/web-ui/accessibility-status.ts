export type AccessibilityStatus = 'connected' | 'disconnected' | 'not-enabled';

export function resolveAccessibilityStatus(
  connected: boolean,
  enabled: boolean,
): AccessibilityStatus {
  if (connected) return 'connected';
  return enabled ? 'disconnected' : 'not-enabled';
}
