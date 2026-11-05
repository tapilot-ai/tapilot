import { App } from '@modelcontextprotocol/ext-apps';
import { BrowserApp } from './browser-app.mjs';
import { DshApp } from './dsh-app.mjs';

export function createPanelBridge({ browser = false, dsh = false, host = {}, AppClass = App, BrowserClass = BrowserApp, DshClass = DshApp } = {}) {
  const app = browser ? new BrowserClass() : dsh ? new DshClass() : new AppClass({ name: 'Tapilot', version: '0.6.18' }, { availableDisplayModes: ['inline', 'fullscreen'] });
  const capabilities = Object.freeze({ interactive: browser || host.interactive !== false });
  const callTool = app.callServerTool.bind(app);
  app.capabilities = capabilities;
  if (!capabilities.interactive) {
    let sequence = 0;
    app.callServerTool = async request => {
      let uri;
      if (request.name === 'tapilot_preview_frame' && /^[A-Za-z0-9_-]{1,64}$/.test(request.arguments?.viewer_id ?? ''))
        uri = `ui://tapilot/frame/${request.arguments.viewer_id}/${++sequence}`;
      else if (request.name === 'tapilot_devices' && request.arguments?.action === 'list' && Object.keys(request.arguments).length === 1)
        uri = `ui://tapilot/devices/${++sequence}`;
      else if (request.name === 'tapilot_preview' && request.arguments?.action === 'suspend')
        return { content: [], structuredContent: { suspended: true } };
      else throw new Error('This client supports read-only preview. Use the Tapilot skill for phone actions.');
      const result = await app.readServerResource({ uri });
      const text = result.contents?.find(item => item.uri === uri && typeof item.text === 'string')?.text;
      if (!text) throw new Error('Missing Tapilot preview resource');
      return JSON.parse(text);
    };
  } else app.callServerTool = callTool;
  return app;
}
