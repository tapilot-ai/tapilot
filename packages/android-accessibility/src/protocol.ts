export const ANDROID_ACCESSIBILITY_PROTOCOL_VERSION = 1 as const;

export type NativeErrorCode =
  | 'ACCESSIBILITY_DISABLED'
  | 'CAPTURE_FAILED'
  | 'INVALID_REQUEST'
  | 'SESSION_CANCELLED'
  | 'STALE_FRAME'
  | 'UNSUPPORTED_ACTION'
  | 'TIMEOUT'
  | 'INTERNAL_ERROR';

export interface FrameGeometry {
  frameId: string;
  capturedAt: number;
  displayId: number;
  rotation: number;
  screenWidth: number;
  screenHeight: number;
  imageWidth: number;
  imageHeight: number;
}

export interface CaptureResult {
  dataUrl: string;
  geometry: FrameGeometry;
  /** Optional same-frame hint for visual settling, not task verification. */
  fingerprint?: { algorithm: string; frameId: string; data: string };
}

export type NativeAction =
  | { type: 'system'; action: string }
  | { type: 'gesture'; fingers: Array<Array<{ x: number; y: number }>>; duration: number; holdMs: number }
  | { type: 'editText'; operation: string; start?: number; end?: number; text?: string }
  | { type: 'tap'; x: number; y: number; duration?: number }
  | { type: 'doubleTap'; x: number; y: number }
  | { type: 'longPress'; x: number; y: number; duration?: number }
  | {
      type: 'swipe';
      startX: number;
      startY: number;
      endX: number;
      endY: number;
      duration?: number;
    }
  | {
      type: 'pinch';
      centerX: number;
      centerY: number;
      startDistance: number;
      endDistance: number;
      duration: number;
    }
  | {
      type: 'input';
      text: string;
      x?: number;
      y?: number;
      replace: boolean;
      autoDismissKeyboard: boolean;
    }
  | { type: 'clearInput'; x?: number; y?: number }
  | { type: 'key'; keyName: string }
  | { type: 'cursorMove'; direction: 'left' | 'right'; times: number }
  | { type: 'back' }
  | { type: 'home' }
  | { type: 'recentApps' }
  | { type: 'appDetails'; packageOrName: string }
  | { type: 'launch'; uri: string };

export interface NativeCapabilities {
  systemActions?: string[];
  apps?: Array<{ label: string; packageName: string; component: string }>;
  protocolVersion: typeof ANDROID_ACCESSIBILITY_PROTOCOL_VERSION;
  canCapture: boolean;
  canPerformGestures: boolean;
  canInputText: boolean;
}

export interface NativeBridge {
  getCapabilities(): Promise<NativeCapabilities>;
  capture(): Promise<CaptureResult>;
  getSize(): Promise<{ width: number; height: number }>;
  execute(action: NativeAction, frameId?: string): Promise<void>;
  cancelSession(): Promise<void>;
  completeSession(): Promise<void>;
}

export class NativeBridgeError extends Error {
  constructor(
    readonly code: NativeErrorCode,
    message: string,
    readonly retryable = false,
  ) {
    super(message);
    this.name = 'NativeBridgeError';
  }
}
