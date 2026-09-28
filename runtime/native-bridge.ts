import {
  ANDROID_ACCESSIBILITY_PROTOCOL_VERSION,
  type NativeBridge,
  NativeBridgeError,
  type NativeErrorCode,
} from '@tapilot/android-accessibility/protocol';
import {
  type NativeAppLanguage,
  type NativeAppTheme,
  type NativeHttpRequest,
  type NativeHttpResponse,
  type NativeNodeRuntimeDoctorResult,
  type NativeNodeTestRunRequest,
  type NativeNodeTestRunResult,
  type NativeTaskStatus,
} from './native-protocol';
export type { NativeTaskStatus } from './native-protocol';

type PendingRequest = {
  resolve(value: unknown): void;
  reject(reason: unknown): void;
  cleanup(): void;
};

type BridgeResponse =
  | { requestId: string; ok: true; result: unknown }
  | {
      requestId: string;
      ok: false;
      error: { code: NativeErrorCode; message: string; retryable?: boolean };
    };

export interface AndroidJavascriptHost {
  postMessage(payload: string): void;
}

export type EdgeEffectGeometryMode = 'auto' | 'square' | 'custom';

export interface EdgeEffectSettings {
  mode: EdgeEffectGeometryMode;
  cornerRadiusDp: number;
  insetDp: number;
  detected: boolean;
  detectedTopLeftPx: number;
  detectedTopRightPx: number;
  detectedBottomRightPx: number;
  detectedBottomLeftPx: number;
}

declare global {
  var MidsceneNative: AndroidJavascriptHost | undefined;
  var __midsceneNativeResolve: ((payload: string) => void) | undefined;
}

type BridgeGlobal = typeof globalThis & {
  MidsceneNative?: AndroidJavascriptHost;
  __midsceneNativeResolve?: (payload: string) => void;
};

const bridgeGlobal = globalThis as BridgeGlobal;

const pendingRequests = new Map<string, PendingRequest>();
const DEFAULT_REQUEST_TIMEOUT_MS = 30_000;
const DEFAULT_MODEL_REQUEST_TIMEOUT_MS = 165_000;
const NODE_TEST_TIMEOUT_MS = 30 * 60_000;

const nativeResponseDispatcher = (payload: string): void => {
  let response: BridgeResponse;
  try {
    response = JSON.parse(payload) as BridgeResponse;
  } catch {
    return;
  }
  const request = pendingRequests.get(response.requestId);
  if (!request) return;
  request.cleanup();
  pendingRequests.delete(response.requestId);
  if (response.ok) {
    request.resolve(response.result);
  } else {
    request.reject(
      new NativeBridgeError(
        response.error.code,
        response.error.message,
        response.error.retryable,
      ),
    );
  }
};

function installNativeResponseDispatcher(): void {
  if (bridgeGlobal.__midsceneNativeResolve !== nativeResponseDispatcher) {
    bridgeGlobal.__midsceneNativeResolve = nativeResponseDispatcher;
  }
}

export class WebViewNativeBridge implements NativeBridge {
  private sequence = 0;
  private pendingRequestIds = new Set<string>();
  private readonly sessionId =
    globalThis.crypto?.randomUUID?.() ??
    `${Date.now()}-${Math.random().toString(36).slice(2)}`;

  constructor(
    private host: AndroidJavascriptHost = bridgeGlobal.MidsceneNative as AndroidJavascriptHost,
    private timeoutMs = DEFAULT_REQUEST_TIMEOUT_MS,
    private modelTimeoutMs = DEFAULT_MODEL_REQUEST_TIMEOUT_MS,
  ) {
    if (!host?.postMessage) {
      throw new NativeBridgeError(
        'ACCESSIBILITY_DISABLED',
        'Midscene native bridge is unavailable',
      );
    }
    installNativeResponseDispatcher();
  }

  getCapabilities(): ReturnType<NativeBridge['getCapabilities']> {
    return this.request('device.getCapabilities', {});
  }

  capture(): ReturnType<NativeBridge['capture']> {
    return this.request('device.capture', {});
  }

  getSize(): ReturnType<NativeBridge['getSize']> {
    return this.request('device.getSize', {});
  }

  execute(
    action: Parameters<NativeBridge['execute']>[0],
    frameId?: string,
  ): Promise<void> {
    return this.request('device.execute', { action, frameId });
  }

  cancelSession(): Promise<void> {
    return this.request('task.cancel', {});
  }

  completeSession(): Promise<void> {
    return this.request('task.complete', {});
  }

  reportTaskStatus(status: NativeTaskStatus, detail?: string): Promise<void> {
    return this.request('task.status', { status, detail });
  }

  httpRequest(
    request: NativeHttpRequest,
    signal?: AbortSignal,
  ): Promise<NativeHttpResponse> {
    return this.request('model.request', request, {
      timeoutMs: this.modelTimeoutMs,
      signal,
      cancelMethod: 'model.cancel',
    });
  }

  async saveReport(html: string): Promise<string> {
    const { reportId } = await this.request<{ reportId: string }>(
      'report.begin',
      {},
    );
    try {
      for (let offset = 0; offset < html.length; ) {
        let end = Math.min(html.length, offset + REPORT_CHUNK_SIZE);
        if (end < html.length && isHighSurrogate(html.charCodeAt(end - 1))) {
          end -= 1;
        }
        await this.request('report.append', {
          reportId,
          chunk: html.slice(offset, end),
        });
        offset = end;
      }
      await this.request('report.finish', { reportId });
      return reportId;
    } catch (error) {
      await this.request('report.abort', { reportId }).catch(() => undefined);
      throw error;
    }
  }

  openReport(reportId: string): Promise<void> {
    return this.request('report.open', { reportId });
  }

  openReportExternally(reportId: string): Promise<void> {
    return this.request('report.openExternal', { reportId });
  }

  shareReport(reportId: string): Promise<void> {
    return this.request('report.share', { reportId });
  }

  exportReport(reportId: string): Promise<{ fileName: string }> {
    return this.request('report.export', { reportId });
  }

  deleteReport(reportId: string): Promise<void> {
    return this.request('report.delete', { reportId });
  }

  doctorNodeRuntime(): Promise<NativeNodeRuntimeDoctorResult> {
    return this.request('runtime.doctor', {}, { timeoutMs: 30_000 });
  }

  runNodeTest(
    request: NativeNodeTestRunRequest,
  ): Promise<NativeNodeTestRunResult> {
    return this.request('runtime.runTest', request, {
      timeoutMs: NODE_TEST_TIMEOUT_MS,
    });
  }

  stopNodeTest(): Promise<{ stopped: boolean }> {
    return this.request('runtime.stop', {});
  }

  setAppLanguage(language: NativeAppLanguage): Promise<void> {
    return this.request('app.setLanguage', { language });
  }

  consumeLaunchContext(): Promise<{ kind: string; label: string; text?: string; dataUrl?: string; autoSend?: boolean; fromPanel?: boolean } | null> {
    return this.request('app.consumeLaunchContext', {});
  }

  getAssistantRole(): Promise<{ available: boolean; held: boolean }> {
    return this.request('app.getAssistantRole', {});
  }

  requestAssistantRole(): Promise<void> {
    return this.request('app.requestAssistantRole', {});
  }

  setAppTheme(theme: NativeAppTheme): Promise<void> {
    return this.request('app.setTheme', { theme });
  }

  getFloatingBallState(): Promise<{ enabled: boolean; available: boolean }> {
    return this.request('app.getFloatingBallState', {});
  }

  setFloatingBallEnabled(
    enabled: boolean,
  ): Promise<{ enabled: boolean; available: boolean }> {
    return this.request('app.setFloatingBallEnabled', { enabled });
  }

  getEdgeEffectSettings(): Promise<EdgeEffectSettings> {
    return this.request('app.getEdgeEffectSettings', {});
  }

  setEdgeEffectSettings(
    mode: EdgeEffectGeometryMode,
    cornerRadiusDp: number,
    insetDp: number,
  ): Promise<EdgeEffectSettings> {
    return this.request('app.setEdgeEffectSettings', {
      mode,
      cornerRadiusDp,
      insetDp,
    });
  }

  goHome(): Promise<void> {
    return this.request('app.goHome', {});
  }

  returnToPreviousApp(): Promise<void> {
    return this.request('app.returnToPreviousApp', {});
  }

  showGlobalResult(message: string): Promise<void> {
    return this.request('app.showGlobalResult', { message });
  }

  bringToFront(): Promise<void> {
    return this.request('app.bringToFront', {});
  }

  openAccessibilitySettings(): Promise<void> {
    return this.request('app.openAccessibilitySettings', {});
  }

  openBatterySettings(): Promise<void> {
    return this.request('app.openBatterySettings', {});
  }

  getAppVersion(): Promise<{ versionName: string; buildNumber: number }> {
    return this.request('app.getVersion', {});
  }

  getModelCredentials(): Promise<Record<string, string>> {
    return this.request('app.getModelCredentials', {});
  }

  setModelCredential(preset: string, apiKey: string): Promise<void> {
    return this.request('app.setModelCredential', { preset, apiKey });
  }

  deleteModelCredential(preset: string): Promise<void> {
    return this.request('app.deleteModelCredential', { preset });
  }

  hasSpeechCredential(provider: 'volcengine' | 'deepgram'): Promise<{ configured: boolean }> {
    return this.request('speech.hasCredential', { provider });
  }

  setSpeechCredential(provider: 'volcengine' | 'deepgram', apiKey: string): Promise<void> {
    return this.request('speech.setCredential', { provider, apiKey });
  }

  setSpeechProvider(provider: 'volcengine' | 'deepgram'): Promise<void> {
    return this.request('speech.setProvider', { provider });
  }

  startSpeech(provider: 'volcengine' | 'deepgram', language: 'zh-CN' | 'en-US'): Promise<void> {
    return this.request('speech.start', { provider, language }, { timeoutMs: 20_000 });
  }

  finishSpeech(): Promise<{ text: string }> {
    return this.request('speech.finish', {}, { timeoutMs: 35_000 });
  }

  cancelSpeech(): Promise<void> {
    return this.request('speech.cancel', {});
  }

  destroy(reason = 'Native bridge destroyed'): void {
    for (const requestId of this.pendingRequestIds) {
      const request = pendingRequests.get(requestId);
      if (!request) continue;
      request.cleanup();
      request.reject(new NativeBridgeError('SESSION_CANCELLED', reason));
      pendingRequests.delete(requestId);
    }
    this.pendingRequestIds.clear();
  }

  private request<T>(
    method: string,
    params: unknown,
    options: {
      timeoutMs?: number;
      signal?: AbortSignal;
      cancelMethod?: string;
    } = {},
  ): Promise<T> {
    installNativeResponseDispatcher();
    const requestId = `${this.sessionId}:${++this.sequence}`;
    const message = JSON.stringify({
      protocolVersion: ANDROID_ACCESSIBILITY_PROTOCOL_VERSION,
      sessionId: this.sessionId,
      requestId,
      method,
      params,
    });

    if (options.signal?.aborted) {
      return Promise.reject(abortReason(options.signal));
    }

    return new Promise<T>((resolve, reject) => {
      const bridge = this;
      function cleanup() {
        clearTimeout(timeout);
        options.signal?.removeEventListener('abort', onAbort);
      }
      const cancelNativeRequest = () => {
        if (options.cancelMethod) {
          this.sendNotification(options.cancelMethod, {
            targetRequestId: requestId,
          });
        }
      };
      function onAbort() {
        cleanup();
        pendingRequests.delete(requestId);
        bridge.pendingRequestIds.delete(requestId);
        cancelNativeRequest();
        reject(abortReason(options.signal));
      }
      const timeout = setTimeout(() => {
        cleanup();
        pendingRequests.delete(requestId);
        this.pendingRequestIds.delete(requestId);
        cancelNativeRequest();
        reject(
          new NativeBridgeError(
            'TIMEOUT',
            `Native request timed out: ${method}`,
            true,
          ),
        );
      }, options.timeoutMs ?? this.timeoutMs);
      pendingRequests.set(requestId, {
        resolve: (value) => {
          cleanup();
          this.pendingRequestIds.delete(requestId);
          resolve(value as T);
        },
        reject: (reason) => {
          cleanup();
          this.pendingRequestIds.delete(requestId);
          reject(reason);
        },
        cleanup,
      });
      this.pendingRequestIds.add(requestId);
      options.signal?.addEventListener('abort', onAbort, { once: true });
      try {
        this.host.postMessage(message);
      } catch (error) {
        cleanup();
        pendingRequests.delete(requestId);
        this.pendingRequestIds.delete(requestId);
        reject(error);
      }
    });
  }

  private sendNotification(method: string, params: unknown): void {
    try {
      this.host.postMessage(
        JSON.stringify({
          protocolVersion: ANDROID_ACCESSIBILITY_PROTOCOL_VERSION,
          sessionId: this.sessionId,
          requestId: `${this.sessionId}:${++this.sequence}`,
          method,
          params,
        }),
      );
    } catch {
      // The originating request is already being cancelled or timed out.
    }
  }
}

const REPORT_CHUNK_SIZE = 256 * 1024;

function isHighSurrogate(codeUnit: number): boolean {
  return codeUnit >= 0xd800 && codeUnit <= 0xdbff;
}

function abortReason(signal?: AbortSignal): unknown {
  return (
    signal?.reason ??
    new NativeBridgeError('SESSION_CANCELLED', 'Native request was cancelled')
  );
}
