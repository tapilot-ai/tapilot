import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
  ListResourcesRequestSchema,
  ReadResourceRequestSchema,
} from "@modelcontextprotocol/sdk/types.js";
import { randomUUID } from "node:crypto";
import { readFile } from "node:fs/promises";
import { SCREEN_URI, LEGACY_SCREEN_URIS, PANEL_META } from "./preview.mjs";
import { panelRequest } from "./panel-tools.mjs";
import { runtimeRequest } from "./runtime-client.mjs";
import { hostProfile, hostTools, hostResourceMeta, hostResultMeta, hostHtml, resourceRequest } from './hosts.mjs';

let panelSessionId = PANEL_META["openai/widgetSessionId"];
const host = hostProfile(process.env.TAPILOT_HOST || 'codex');
const tools = hostTools(host);
const resourceMeta = hostResourceMeta(host);

const server = new Server(
  { name: "tapilot", version: "0.6.18" },
  {
    capabilities: { tools: {}, resources: {} },
    instructions:
      "At the start of each user turn that invokes Tapilot or continues a phone task, call tapilot_preview_open with recover:true once, even if an old panel exists. Reuse that panel within the turn; do not reopen for every action. All agent phone operations use the Tapilot CLI described in the bundled skill. Other tools are for panel interaction only. CLI and panel share one local runtime and Wi-Fi connection.",
  },
);
server.setRequestHandler(ListToolsRequestSchema, async () => ({
  tools,
}));
server.setRequestHandler(ListResourcesRequestSchema, async () => ({
  resources: [
    {
      uri: SCREEN_URI,
      name: "Tapilot Screen",
      mimeType: "text/html;profile=mcp-app",
      _meta: resourceMeta,
    },
  ],
}));
server.setRequestHandler(ReadResourceRequestSchema, async (request) => {
  if (request.params.uri !== SCREEN_URI && !LEGACY_SCREEN_URIS.includes(request.params.uri)) {
    const params = resourceRequest(request.params.uri, host);
    const result = await runtimeRequest('/panel', params);
    return { contents: [{ uri: request.params.uri, mimeType: 'application/json', text: JSON.stringify(result) }] };
  }
  return {
    contents: [
      {
        uri: request.params.uri,
        mimeType: "text/html;profile=mcp-app",
        text: hostHtml(await readFile(
          new URL("../assets/screen.html", import.meta.url),
          "utf8",
        ), host),
        _meta: resourceMeta,
      },
    ],
  };
});
server.setRequestHandler(CallToolRequestSchema, async (request) => {
  try {
    panelRequest(request.params);
    if (!tools.some(tool => tool.name === request.params.name)) throw new Error('Unsupported panel request for this host');
    // Return the shell before starting the runtime or waiting for its phone queue.
    // The mounted panel fetches devices and frames through its normal polling.
    if (request.params.name === 'tapilot_preview_open') {
      // A new user turn can replace a dead host view without touching the runtime.
      // Plain opens keep the latest session; only recovery rotates its identity.
      if (request.params.arguments?.recover) panelSessionId = `tapilot-screen-${randomUUID()}`;
      return {
        content: [{type:'text',text:'Tapilot screen panel'}],
        structuredContent: {state:'connecting'},
        _meta: hostResultMeta(host, panelSessionId),
      };
    }
    return await runtimeRequest("/panel", request.params);
  } catch (error) {
    return { isError: true, content: [{ type: "text", text: error.message }] };
  }
});
await server.connect(new StdioServerTransport());
