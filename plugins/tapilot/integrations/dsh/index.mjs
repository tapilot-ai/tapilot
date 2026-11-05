import { readFile } from 'node:fs/promises';
import { panelRequest } from '../../server/panel-tools.mjs';
import { runtimeRequest } from '../../server/runtime-client.mjs';

export const name = 'tapilot-panel';
export const inject = ['connection'];
const json = (value, status = 200) => Response.json(value, { status, headers: { 'Cache-Control': 'no-store' } });

export function registerDshPanel(ctx, request = runtimeRequest) {
  if (!ctx.connection?.fetch?.register) throw new Error('This DSH version has no authenticated Fetch route registry');
  // Connection owns admission, Host/Origin validation and owner-only access.
  // Do not register an unauthenticated webServer route as a fallback.
  ctx.connection.fetch.register({
    path: '/api/tapilot/panel', methods: ['GET', 'HEAD'], requestBody: 'buffered',
    fetch: async incoming => new Response(incoming.method === 'HEAD' ? null : await readFile(new URL('../../assets/dsh.html', import.meta.url), 'utf8'), {
      headers: {
        'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store', 'Referrer-Policy': 'no-referrer',
        'Content-Security-Policy': "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data:; connect-src 'self'; frame-ancestors 'self'; base-uri 'none'",
      },
    }),
  });
  ctx.connection.fetch.register({
    path: '/api/tapilot/call', methods: ['POST'], requestBody: 'buffered',
    fetch: async incoming => {
      if (incoming.headers.get('X-Tapilot-Panel') !== '1' || incoming.headers.get('Content-Type') !== 'application/json') return json({ error: 'Panel request required' }, 403);
      try {
        const text = await incoming.text();
        if (Buffer.byteLength(text) > 65536) return json({ error: 'Request too large' }, 413);
        const params = JSON.parse(text);
        panelRequest(params); // The runtime translates pairing exactly once.
        if (params.name === 'tapilot_preview_open') return json({ content: [], structuredContent: { state: 'connecting' } });
        return json(await request('/panel', params));
      } catch (error) { return json({ error: error.message }, 400); }
    },
  });
}
export function apply(ctx) { registerDshPanel(ctx); }
