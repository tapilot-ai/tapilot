import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js';
import { EXTENSION_ID } from '@modelcontextprotocol/ext-apps/server';
import { SCREEN_URI } from '../preview.mjs';
import { readFile } from 'node:fs/promises';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';

export async function probeDshPlugin(directory) {
  const manifest = JSON.parse(await readFile(join(directory, 'package.json'), 'utf8'));
  const plugin = await import(pathToFileURL(join(directory, 'index.mjs')).href);
  if (!manifest.dsh?.client || typeof plugin.apply !== 'function') throw new Error('Invalid DSH panel package');
  return { protocol: 'native-plugin', package: manifest.name, entryPoint: 'passed', nativeRegistration: 'not_tested', hostRendering: 'not_tested', phone: 'not_tested' };
}

// This probe deliberately does not call the phone, start pairing, or claim that
// a real host rendered an iframe. It exercises the configured child process.
export async function probeConnector(connector) {
  const transport = new StdioClientTransport({ ...connector, env: { ...process.env, ...connector.env }, stderr: 'pipe' });
  transport.stderr?.on('data', () => {});
  const client = new Client({ name: 'tapilot-connector-probe', version: '1' }, { capabilities: { extensions: { [EXTENSION_ID]: { mimeTypes: ['text/html;profile=mcp-app'] } } } });
  try {
    const timeout = { timeout: 10000 };
    await client.connect(transport, timeout);
    const { tools } = await client.listTools({}, timeout);
    const openers = tools.filter(tool => tool._meta?.ui?.resourceUri);
    if (openers.length !== 1 || openers[0].name !== 'tapilot_preview_open') throw new Error('Expected one Tapilot panel entry point');
    const result = await client.callTool({ name: 'tapilot_preview_open', arguments: {} }, undefined, timeout);
    if (result.isError) throw new Error('Panel opener failed');
    const resource = await client.readResource({ uri: SCREEN_URI }, timeout);
    if (!resource.contents.some(item => item.mimeType === 'text/html;profile=mcp-app' && item.text?.includes('<title>Tapilot</title>')))
      throw new Error('Missing bundled panel HTML');
    return { protocol: 'passed', server: client.getServerVersion(), toolCount: tools.length, panelResource: 'passed', hostRendering: 'not_tested', phone: 'not_tested' };
  } finally {
    await client.close();
  }
}
