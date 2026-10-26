import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
  ListResourcesRequestSchema,
  ReadResourceRequestSchema,
} from "@modelcontextprotocol/sdk/types.js";
import { readFile } from "node:fs/promises";
import { SCREEN_URI, SCREEN_META } from "./preview.mjs";
import { panelTools, panelRequest } from "./panel-tools.mjs";
import { runtimeRequest } from "./runtime-client.mjs";

const server = new Server(
  { name: "android-use", version: "0.6.1" },
  {
    capabilities: { tools: {}, resources: {} },
    instructions:
      "Open tapilot_preview_open once for the embedded phone panel. All agent phone operations use the Android Use CLI described in the bundled skill. Other tools are for panel interaction only. CLI and panel share one local runtime and Wi-Fi connection.",
  },
);
server.setRequestHandler(ListToolsRequestSchema, async () => ({
  tools: panelTools,
}));
server.setRequestHandler(ListResourcesRequestSchema, async () => ({
  resources: [
    {
      uri: SCREEN_URI,
      name: "Android Use Screen",
      mimeType: "text/html;profile=mcp-app",
      _meta: SCREEN_META,
    },
  ],
}));
server.setRequestHandler(ReadResourceRequestSchema, async (request) => {
  if (request.params.uri !== SCREEN_URI) throw new Error("Unknown UI resource");
  return {
    contents: [
      {
        uri: SCREEN_URI,
        mimeType: "text/html;profile=mcp-app",
        text: await readFile(
          new URL("../assets/screen.html", import.meta.url),
          "utf8",
        ),
        _meta: SCREEN_META,
      },
    ],
  };
});
server.setRequestHandler(CallToolRequestSchema, async (request) => {
  try {
    panelRequest(request.params);
    return await runtimeRequest("/panel", request.params);
  } catch (error) {
    return { isError: true, content: [{ type: "text", text: error.message }] };
  }
});
await server.connect(new StdioServerTransport());
