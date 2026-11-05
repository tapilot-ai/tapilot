// Host policy belongs to the connector, never to the shared phone runtime.
import { SCREEN_META, PANEL_META } from './preview.mjs';
import { panelTools } from './panel-tools.mjs';

export const HOSTS = Object.freeze(['codex', 'standard', 'doubao', 'workbuddy', 'cursor', 'vscode']);
export function hostProfile(name = 'codex') {
  if (!HOSTS.includes(name)) throw new Error(`Unknown Tapilot host: ${name}`);
  return Object.freeze({
    name,
    transport: name === 'workbuddy' ? 'resources' : 'tools',
    interactive: name !== 'workbuddy',
  });
}
export function hostTools(host) {
  const tools = structuredClone(panelTools).filter(tool => host.interactive || tool.name === 'tapilot_preview_open');
  for (const tool of tools) {
    if (!['codex', 'doubao'].includes(host.name)) delete tool._meta?.['openai/ui'];
    if (tool.name === 'tapilot_preview_open' && host.name === 'workbuddy') {
      tool._meta.workbuddy = { ui: { launchSurface: 'panel' } };
      tool.description += ' This host provides read-only preview. Pair, switch devices, pause, or control the phone through the Tapilot CLI skill.';
    }
  }
  return tools;
}
export function hostResourceMeta(host) {
  const meta = structuredClone(SCREEN_META);
  // Keep the display preferences used by Doubao's reference integration.
  // Actual expansion also requires the App to declare its display capabilities.
  if (!['codex', 'doubao'].includes(host.name)) delete meta['openai/ui'];
  return meta;
}
export function hostResultMeta(host, sessionId) {
  return host.name === 'codex' ? { ...PANEL_META, 'openai/widgetSessionId': sessionId } : {};
}
export function hostHtml(html, host) {
  return html.replace('<head>', `<head><script>globalThis.__TAPILOT_PANEL__=${JSON.stringify(host)};</script>`);
}

// Resource reads are strictly read-only. No query string, arbitrary tool name,
// action, pairing confirmation, or runtime-admin route can pass through here.
export function resourceRequest(uri, host) {
  if (host.transport !== 'resources') throw new Error('Unknown UI resource');
  if (/^ui:\/\/tapilot\/devices(?:\/\d{1,12})?$/.test(uri)) return { name: 'tapilot_devices', arguments: { action: 'list' } };
  const match = /^ui:\/\/tapilot\/frame\/([A-Za-z0-9_-]{1,64})(?:\/\d{1,12})?$/.exec(uri);
  if (match) return { name: 'tapilot_preview_frame', arguments: { viewer_id: match[1] } };
  throw new Error('Unknown UI resource');
}
