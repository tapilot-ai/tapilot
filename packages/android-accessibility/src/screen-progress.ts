import type { CaptureResult } from './protocol';

/** Native thumbnail pixels are evidence of UI change, never evidence of task completion. */
export function frameFingerprint(frame: CaptureResult): Uint8Array | undefined {
  const hint = frame.fingerprint;
  if (hint?.algorithm !== 'rgb-luma-64x96-v1' ||
      !frame.geometry?.frameId || hint.frameId !== frame.geometry.frameId ||
      !/^[A-Za-z0-9+/]{8192}$/.test(hint.data)) return undefined;
  return Uint8Array.from(Buffer.from(hint.data, 'base64'));
}

export function sameScreen(a?: Uint8Array, b?: Uint8Array): boolean {
  // Without reliable image evidence keep the conservative action-count fallback.
  if (!a || !b) return true;
  let changed = 0;
  // Ignore clock/status and navigation bars, and tolerate compression noise.
  for (let i = 64 * 6; i < 64 * 91; i++) {
    if (Math.abs(a[i] - b[i]) > 12 && ++changed >= 48) return false;
  }
  return true;
}
