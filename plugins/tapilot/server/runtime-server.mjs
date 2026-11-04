import { createServer } from "node:http";
import { randomBytes } from "node:crypto";
import { readFile, writeFile, chmod, unlink } from "node:fs/promises";
import lockfile from "proper-lockfile";
import { join } from "node:path";
import { createRuntime } from "./runtime.mjs";
import { runtimePaths, runtimeVersion } from "./runtime-client.mjs";
import { panelRequest } from "./panel-tools.mjs";

const runtime = createRuntime();
const { metadata, dir } = runtimePaths();
let releaseOwner;
try {
  releaseOwner = await lockfile.lock(dir, {
    lockfilePath: join(dir, "owner.lock"),
    stale: 10000,
  });
} catch {
  process.send?.({
    error:
      "Another runtime owns this configuration. Stop it before starting a replacement.",
  });
  process.exit(1);
}
const token = randomBytes(32).toString("hex");
const panelToken = randomBytes(32).toString("hex");
let origin;
let stopping = false;
const pending = new Set();
async function callRuntime(params) {
  if (stopping) throw new Error("Tapilot background is upgrading; retry after it finishes");
  const call = runtime.call({params});
  pending.add(call);
  try { return await call; } finally { pending.delete(call); }
}
function reply(res, status, value) {
  res.writeHead(status, {
    "Content-Type": "application/json",
    "Cache-Control": "no-store",
  });
  res.end(JSON.stringify(value));
}
async function readBody(req) {
  let body = "";
  for await (const chunk of req) {
    body += chunk;
    if (Buffer.byteLength(body) > 65536) throw new Error("Request too large");
  }
  return JSON.parse(body || "{}");
}
const server = createServer(async (req, res) => {
  try {
    if (
      req.headers.host !== new URL(origin).host ||
      (req.headers.origin && req.headers.origin !== origin)
    )
      return reply(res, 403, { error: "Origin rejected" });
    if (req.method === "GET" && req.url === "/") {
      res.writeHead(200, {
        "Content-Type": "text/html; charset=utf-8",
        "Cache-Control": "no-store",
        "Referrer-Policy": "no-referrer",
        "X-Frame-Options": "DENY",
        "Content-Security-Policy":
          "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'",
      });
      return res.end(
        await readFile(new URL("../assets/browser.html", import.meta.url)),
      );
    }
    const admin = req.headers.authorization === `Bearer ${token}`;
    const panel = req.headers.authorization === `Bearer ${panelToken}`;
    if (!admin && !panel)
      return reply(res, 401, { error: "Authorization required" });
    if (req.method === "GET" && req.url === "/health" && admin)
      return reply(res, 200, { version: runtimeVersion });
    if (req.method !== "POST")
      return reply(res, 405, { error: "POST required" });
    const body = await readBody(req);
    if (req.url === "/panel")
      return reply(
        res,
        200,
        await callRuntime(panelRequest(body)),
      );
    if (!admin) return reply(res, 403, { error: "Panel capability only" });
    if (req.url === "/call")
      return reply(res, 200, await callRuntime(body));
    if (req.url === "/preview")
      return reply(res, 200, { url: `${origin}/#${panelToken}` });
    if (req.url === "/stop") {
      stopping = true;
      await Promise.allSettled([...pending]);
      // Flush the stop acknowledgement before shutdown destroys HTTP sockets.
      // Otherwise a fast restart races an unread response and reports UND_ERR_SOCKET.
      await new Promise(resolve => {
        res.once('finish', resolve);
        res.once('close', resolve);
        reply(res, 200, { stopped: true });
      });
      return shutdown();
    }
    reply(res, 404, { error: "Unknown endpoint" });
  } catch (error) {
    reply(res, 400, { error: error.message });
  }
});
async function shutdown() {
  runtime.close();
  await unlink(metadata).catch(() => {});
  await releaseOwner();
  server.close();
  server.closeAllConnections();
  process.exit(0);
}
process.on("SIGTERM", shutdown);
process.on("SIGINT", shutdown);
server.requestTimeout = 370000;
server.listen(0, "127.0.0.1", async () => {
  origin = `http://127.0.0.1:${server.address().port}`;
  await writeFile(
    metadata,
    JSON.stringify({
      port: server.address().port,
      token,
      pid: process.pid,
      version: runtimeVersion,
    }),
    { mode: 0o600 },
  );
  await chmod(metadata, 0o600);
  process.send?.({ ready: true });
});
