import { randomUUID } from 'node:crypto';
import { createInterface } from 'node:readline';

const DEFAULT_TIMEOUT_MS = 60_000;

export class StdioNativeBridge {
  #pending = new Map();
  #reader;
  #sequence = 0;
  #sessionId = randomUUID();

  constructor(timeoutMs = DEFAULT_TIMEOUT_MS) {
    this.timeoutMs = timeoutMs;
    this.#reader = createInterface({ input: process.stdin });
    this.#reader.on('line', (line) => this.#resolve(line));
    this.#reader.on('close', () =>
      this.#rejectAll(new Error('Native RPC input closed')),
    );
  }

  getCapabilities() {
    return this.#request('device.getCapabilities', {});
  }

  capture() {
    return this.#request('device.capture', {});
  }

  getSize() {
    return this.#request('device.getSize', {});
  }

  execute(action, frameId) {
    return this.#request('device.execute', { action, frameId });
  }

  cancelSession() {
    return this.#request('task.cancel', {});
  }

  completeSession() {
    return this.#request('task.complete', {});
  }

  reportTaskStatus(status, detail) {
    return this.#request('task.status', { status, detail });
  }

  close() {
    this.#reader.close();
    this.#rejectAll(new Error('Native RPC bridge closed'));
  }

  #request(method, params) {
    const requestId = `${this.#sessionId}:${++this.#sequence}`;
    const message = {
      type: 'rpc.request',
      timestamp: new Date().toISOString(),
      protocolVersion: 1,
      sessionId: this.#sessionId,
      requestId,
      method,
      params,
    };
    return new Promise((resolve, reject) => {
      const timeout = setTimeout(() => {
        this.#pending.delete(requestId);
        reject(new Error(`Native RPC timed out: ${method}`));
      }, this.timeoutMs);
      this.#pending.set(requestId, { resolve, reject, timeout });
      process.stdout.write(`MIDSCENE_EVENT ${JSON.stringify(message)}\n`);
    });
  }

  #resolve(line) {
    let response;
    try {
      response = JSON.parse(line);
    } catch {
      this.#rejectAll(new Error('Native RPC returned malformed JSON'));
      return;
    }
    const pending = this.#pending.get(response.requestId);
    if (!pending) return;
    clearTimeout(pending.timeout);
    this.#pending.delete(response.requestId);
    if (response.ok) {
      pending.resolve(response.result);
    } else {
      const error = new Error(response.error?.message ?? 'Native RPC failed');
      error.code = response.error?.code;
      pending.reject(error);
    }
  }

  #rejectAll(error) {
    for (const pending of this.#pending.values()) {
      clearTimeout(pending.timeout);
      pending.reject(error);
    }
    this.#pending.clear();
  }
}
