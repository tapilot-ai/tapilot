import type { CaptureResult } from './protocol';
/** Native thumbnail pixels are evidence of UI change, never evidence of task completion. */
export declare function frameFingerprint(frame: CaptureResult): Uint8Array | undefined;
export declare function sameScreen(a?: Uint8Array, b?: Uint8Array): boolean;
