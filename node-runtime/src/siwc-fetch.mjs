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
      return fetch(new Request(request.clone(), { headers }));
    };
    let response = await attempt(false);
    if (response.status === 401) {
      await response.body?.cancel();
      response = await attempt(true);
    }
    return response;
  };
}
