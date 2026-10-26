#!/usr/bin/env node
import { mkdir, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { randomUUID } from "node:crypto";
import { execFile } from "node:child_process";
import { runtimeRequest, runtimePaths } from "./runtime-client.mjs";

const commands = new Set([
  "setup",
  "pair",
  "pair-status",
  "discover",
  "settings",
  "status",
  "auth-status",
  "auth-login",
  "auth-cancel",
  "auth-logout",
  "models",
  "screenshot",
  "tap",
  "swipe",
  "input",
  "home",
  "back",
  "recentApps",
  "launch",
  "record",
  "act",
  "assert",
  "wait",
]);
const [command, ...argv] = process.argv.slice(2);
const help = `Tapilot CLI (Android over Wi-Fi; no ADB)
Usage: tapilot <command> [--field value] [--json '{...}']
Commands: ${[...commands].join(", ")}
  capabilities             Connect and list installed launchable apps
  preview [--open]          Return/open the local live preview URL
  pause | resume           Pause private capture/control; explicitly resume
  disconnect               Release the phone
  runtime-stop             Stop this local runtime (not other hosts)
Fields: --report-id, --frame-id, --x, --y, --end-x, --end-y,
        --text, --passed true|false, --mode steps|ai|off, --timeout-ms
Screenshot responses contain images[].path; read the image before acting.
Reuse report_id for one task and geometry.frameId for coordinate actions.
Failed/uncertain actions are never replayed automatically.
`;
function parseArgs(values) {
  const args = {};
  for (let i = 0; i < values.length; i++) {
    const flag = values[i];
    if (flag === "--json") {
      if (Object.keys(args).length || i + 2 !== values.length)
        throw new Error("--json must be the only option");
      const value = JSON.parse(values[++i]);
      if (!value || Array.isArray(value) || typeof value !== "object")
        throw new Error("Expected JSON object");
      return value;
    }
    if (!/^--[a-z][a-z-]*$/.test(flag) || i + 1 >= values.length)
      throw new Error("Expected --field value");
    const key = flag.slice(2).replaceAll("-", "_");
    if (Object.hasOwn(args, key)) throw new Error("Duplicate option");
    const raw = values[++i];
    if (["x", "y", "end_x", "end_y", "timeout_ms"].includes(key)) {
      if (!raw.trim() || !Number.isFinite(Number(raw)))
        throw new Error("Expected number");
      args[key] = Number(raw);
    } else if (key === "passed") {
      if (!["true", "false"].includes(raw))
        throw new Error("Expected true or false");
      args[key] = raw === "true";
    } else args[key] = raw;
  }
  return args;
}
function openBrowser(url) {
  const [cmd, args] =
    process.platform === "darwin"
      ? ["open", [url]]
      : process.platform === "win32"
        ? ["rundll32", ["url.dll,FileProtocolHandler", url]]
        : ["xdg-open", [url]];
  return new Promise((resolve, reject) =>
    execFile(cmd, args, (error) => (error ? reject(error) : resolve())),
  );
}
async function serialize(result) {
  const images = [];
  const content = [];
  for (const item of result.content ?? []) {
    if (item.type !== "image") {
      content.push(item);
      continue;
    }
    if (!["image/png", "image/jpeg"].includes(item.mimeType))
      throw new Error("Unsupported screenshot type");
    const dir = join(runtimePaths().root, "cli-screenshots");
    await mkdir(dir, { recursive: true, mode: 0o700 });
    const path = join(
      dir,
      `${randomUUID()}.${item.mimeType === "image/png" ? "png" : "jpg"}`,
    );
    await writeFile(path, Buffer.from(item.data, "base64"), {
      mode: 0o600,
      flag: "wx",
    });
    images.push({ path, mimeType: item.mimeType });
  }
  let data = result.structuredContent;
  if (!data && content.length === 1 && content[0].type === "text") {
    try {
      data = JSON.parse(content[0].text);
    } catch {}
  }
  const messages = content
    .filter(
      (item) =>
        item.type === "text" &&
        item.text !== data?.report_link &&
        !item.text.startsWith("{"),
    )
    .map((item) => item.text);
  return {
    ...(data ?? {}),
    isError: !!result.isError,
    ...(messages.length ? { messages } : {}),
    ...(images.length ? { images } : {}),
  };
}
try {
  if (!command || ["help", "--help", "-h"].includes(command)) {
    process.stdout.write(help);
  } else {
    let result;
    if (command === "runtime-stop") {
      if (argv.length) throw new Error("No options expected");
      result = await runtimeRequest("/stop", {}, { start: false });
    } else if (command === "preview") {
      if (argv.length && (argv.length !== 1 || argv[0] !== "--open"))
        throw new Error("Use preview [--open]");
      result = await runtimeRequest("/preview");
      if (argv[0] === "--open") await openBrowser(result.url);
    } else {
      const args = parseArgs(argv);
      let request;
      const special = {
        capabilities: "tapilot_status",
        disconnect: "tapilot_disconnect",
        pause: "pause",
        resume: "resume",
      };
      if (Object.hasOwn(special, command)) {
        if (Object.keys(args).length) throw new Error("No options expected");
        request = ["pause", "resume"].includes(command)
          ? { name: "tapilot_preview", arguments: { action: command } }
          : { name: special[command], arguments: {} };
      } else {
        if (!commands.has(command))
          throw new Error("Unknown command; use --help");
        if (Object.hasOwn(args, "action"))
          throw new Error("Action is selected by command");
        request = {
          name: "tapilot_midscene",
          arguments: { ...args, action: command.replaceAll("-", "_") },
        };
      }
      result = await serialize(await runtimeRequest("/call", request));
      if (command === "pair" && result.url)
        result.host_instruction =
          "Open this local URL. Scan the computer QR using Tapilot, then confirm the phone in that page. Never paste pairing tokens into chat.";
    }
    process.stdout.write(JSON.stringify(result) + "\n");
    if (result.isError) process.exitCode = 1;
  }
} catch (error) {
  process.stdout.write(
    JSON.stringify({
      isError: true,
      error: error.message,
      advice:
        "If a phone action was dispatched, inspect a fresh screenshot before another action. Do not replay it automatically.",
    }) + "\n",
  );
  process.exitCode = 1;
}
