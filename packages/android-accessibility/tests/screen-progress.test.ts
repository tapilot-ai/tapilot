import { describe, expect, it } from '@rstest/core';
import { frameFingerprint, sameScreen } from '../src/screen-progress';
import type { CaptureResult } from '../src/protocol';

const frame = (frameId = 'current'): CaptureResult => ({
  dataUrl: 'data:image/png;base64,AAAA',
  geometry: { frameId: 'current', capturedAt: 1, displayId: 0, rotation: 0,
    screenWidth: 400, screenHeight: 800, imageWidth: 400, imageHeight: 800 },
  fingerprint: { algorithm: 'rgb-luma-64x96-v1', frameId,
    data: Buffer.alloc(64 * 96, 80).toString('base64') },
});

describe('navigation progress evidence', () => {
  it('rejects stale, malformed and unknown fingerprint formats', () => {
    expect(frameFingerprint(frame('old'))).toBeUndefined();
    const malformed = frame();
    malformed.fingerprint!.data = 'short';
    expect(frameFingerprint(malformed)).toBeUndefined();
    const unknown = frame();
    unknown.fingerprint!.algorithm = 'other';
    expect(frameFingerprint(unknown)).toBeUndefined();
    expect(sameScreen(undefined, frameFingerprint(frame()))).toBe(true);
  });
  it('tolerates compression noise but detects visible content changes', () => {
    const original = frameFingerprint(frame())!;
    const noise = original.map(value => value + 5);
    expect(sameScreen(original, noise)).toBe(true);
    const changed = original.slice();
    changed.fill(180, 64 * 30, 64 * 33);
    expect(sameScreen(original, changed)).toBe(false);
  });
});
