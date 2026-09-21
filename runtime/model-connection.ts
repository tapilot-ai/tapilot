export type ModelConnectionFailure = 'apiKey' | 'model' | 'quota' | 'request';

export function modelConnectionEndpoint(baseUrl: string): string {
  const url = new URL(baseUrl);
  const path = url.pathname.replace(/\/$/, '');
  if (/\/chat$/.test(path)) {
    url.pathname = `${path}/completions`;
  } else if (!/(\/chat\/completions|\/responses)$/.test(path)) {
    url.pathname = `${path}/chat/completions`;
  }
  return url.toString();
}

export function classifyModelConnectionResponse(
  status: number,
  body: string,
): ModelConnectionFailure {
  const message = body.toLowerCase();
  if (
    status === 401 ||
    status === 403 ||
    /api.?key|unauthori|authenticat/.test(message)
  )
    return 'apiKey';
  if (
    status === 402 ||
    status === 429 ||
    /quota|rate.?limit|credit|balance/.test(message)
  )
    return 'quota';
  if (status === 404 || /model.*(not found|invalid|unavailable)/.test(message))
    return 'model';
  return 'request';
}
