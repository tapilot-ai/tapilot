import { channel } from 'node:diagnostics_channel';

/** Refresh credentials at the model-call boundary, never restart phone actions. */
export function siwcFetch(fetch, accessToken) {
  return async (input, init) => {
    const request = new Request(input, init);
    const url = new URL(request.url);
    if (url.origin !== 'https://api.openai.com' || url.pathname !== '/v1/responses') {
      return fetch(input, init);
    }
    const attempt = async (forceRefresh) => {
      request.signal.throwIfAborted();
      const token = await accessToken(forceRefresh);
      request.signal.throwIfAborted();
      const headers = new Headers(request.headers);
      headers.set('Authorization', `Bearer ${token}`);
      // Request does not retain Undici's dispatcher; keep the SDK-selected proxy.
      return fetch(new Request(request.clone(), { headers }),
        init?.dispatcher ? { dispatcher: init.dispatcher } : undefined);
    };
    let response = await attempt(false);
    if (response.status === 401) {
      await response.body?.cancel();
      response = await attempt(true);
    }
    return response;
  };
}

/** Observe transport boundaries without buffering, decoding or logging model data. */
export function measuredFetch(fetch, record, now = () => performance.now()) {
  let sequence = 0;
  return async (input, init) => {
    const start = now();
    const timing = { phase: 'model.http', request: ++sequence };
    if (typeof init?.body === 'string') timing.requestBytes = new TextEncoder().encode(init.body).byteLength;
    let finished = false;
    const finish = outcome => {
      if (finished) return;
      finished = true;
      try { record({ ...timing, totalMs: now() - start, outcome }); } catch { /* telemetry cannot fail a request */ }
    };
    try {
      const response = await fetch(input, init);
      timing.headersMs = now() - start;
      timing.status = response.status;
      // Keep only the edge code, never request IDs, credentials or arbitrary headers.
      const edge = response.headers.get('cf-ray')?.match(/^[a-f0-9]+-([A-Z]{3})$/)?.[1];
      if (edge) timing.edge = edge;
      const processing = response.headers.get('openai-processing-ms');
      if (processing && /^\d+(?:\.\d+)?$/.test(processing) && Number.isFinite(Number(processing))) {
        timing.serverProcessingMs = Number(processing);
      }
      if (!response.body) { finish('complete'); return response; }
      const reader = response.body.getReader();
      timing.responseBytes = 0;
      const body = new ReadableStream({
        async pull(controller) {
          try {
            const { done, value } = await reader.read();
            if (done) { finish('complete'); controller.close(); return; }
            timing.firstBodyMs ??= now() - start;
            timing.responseBytes += value.byteLength;
            controller.enqueue(value);
          } catch (error) { finish('error'); controller.error(error); }
        },
        async cancel(reason) { finish('cancelled'); await reader.cancel(reason); },
      }, { highWaterMark: 0 });
      return new Response(body, { status: response.status, statusText: response.statusText, headers: response.headers });
    } catch (error) { finish('error'); throw error; }
  };
}

/** Dedicated model-worker diagnostics. Never inspect request bodies or headers. */
export function observeModelNetwork(record, now = () => performance.now()) {
  const requests = new WeakMap();
  let sequence = 0;
  const listeners = [];
  const listen = (name, handler) => {
    const topic = channel(`undici:request:${name}`);
    const safe = event => { try { handler(event); } catch { /* diagnostics must not affect I/O */ } };
    topic.subscribe(safe); listeners.push(() => topic.unsubscribe(safe));
  };
  listen('create', ({ request }) => requests.set(request, { request: ++sequence, start: now() }));
  listen('bodySent', ({ request }) => {
    const value = requests.get(request);
    if (value) value.bodySentMs = now() - value.start;
  });
  listen('headers', ({ request, response }) => {
    const value = requests.get(request);
    if (value) { value.headersMs = now() - value.start; value.status = response.statusCode; }
  });
  const finish = (request, outcome) => {
    const value = requests.get(request);
    if (!value) return;
    requests.delete(request);
    const { start, ...timing } = value;
    record({ ...timing, totalMs: now() - start, outcome });
  };
  listen('trailers', ({ request }) => finish(request, 'complete'));
  listen('error', ({ request }) => finish(request, 'error'));
  return () => listeners.forEach(unsubscribe => unsubscribe());
}
