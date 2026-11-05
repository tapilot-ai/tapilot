import { readFile, mkdir } from "node:fs/promises";
import { existsSync } from 'node:fs';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { homedir } from "node:os";
import { join, resolve, sep } from "node:path";
import { createHash } from "node:crypto";
import { fork } from "node:child_process";
import lockfile from "proper-lockfile";
import { stateRoot } from "./state-paths.mjs";

export const runtimeVersion = "0.6.18";
export async function runtimeLaunch({ root = fileURLToPath(new URL('../', import.meta.url)), installations = process.env.TAPILOT_INSTALLATIONS_DIR || join(homedir(), '.local/share/tapilot/clients') } = {}) {
  const binary = process.platform === 'win32' ? 'node.exe' : 'node';
  const localNode = join(root, 'bin', binary);
  if (existsSync(localNode)) return { entry: pathToFileURL(join(root, 'server/runtime-server.mjs')), execPath: localNode, managed: true };
  // Native plugins have their own package lifecycle. Reuse an installed copy of
  // this exact runtime version when another client has already staged one.
  for (const client of ['codex', 'doubao', 'workbuddy', 'vscode', 'dsh', 'cherry', 'standard', 'skill', 'cursor']) {
    try {
      const record = JSON.parse(await readFile(join(installations, client, 'installation.json'), 'utf8'));
      if (!record || typeof record.root !== 'string') continue;
      const installedRoot = resolve(record.root);
      const node = join(installedRoot, 'bin', binary);
      const entry = join(installedRoot, 'server/runtime-server.mjs');
      if (record.runtime?.version === runtimeVersion && record.runtime.root === installedRoot && record.runtime.node === node &&
          installedRoot.startsWith(resolve(installations, client, 'releases') + sep) && existsSync(node) && existsSync(entry))
        return { entry: pathToFileURL(entry), execPath: node, managed: true };
    } catch { /* Optional installations must not break a native plugin fallback. */ }
  }
  return { entry: pathToFileURL(join(root, 'server/runtime-server.mjs')), execPath: process.execPath, managed: false };
}
export function runtimePaths() {
  const root = stateRoot();
  const config = resolve(
    process.env.TAPILOT_CONFIG ||
      join(homedir(), ".config/tapilot/wireless.json"),
  );
  const key = createHash("sha256").update(config).digest("hex").slice(0, 12);
  const dir = join(root, "runtime", key);
  return { root, dir, metadata: join(dir, "runtime.json") };
}
async function readRuntime() {
  try {
    const info = JSON.parse(await readFile(runtimePaths().metadata, "utf8"));
    if (
      !Number.isInteger(info.port) ||
      info.port < 1 ||
      info.port > 65535 ||
      !/^[a-f0-9]{64}$/.test(info.token)
    )
      return;
    const response = await fetch(`http://127.0.0.1:${info.port}/health`, {
      headers: { Authorization: `Bearer ${info.token}` },
      signal: AbortSignal.timeout(1500),
    });
    if (response.ok)
      return { ...info, version: (await response.json()).version };
  } catch {}
}
// Older panels can share a newer patch runtime; never downgrade it on their next poll.
export function compatibleRuntime(version) {
  const current = /^(\d+)\.(\d+)\.(\d+)$/.exec(runtimeVersion);
  const running = /^(\d+)\.(\d+)\.(\d+)$/.exec(version ?? '');
  if (!running) throw new Error('Unrecognized Tapilot runtime version');
  if (running[1] !== current[1] || running[2] !== current[2])
    throw new Error('Tapilot runtime requires a plugin reload before changing major or minor versions');
  return Number(running[3]) >= Number(current[3]);
}
export async function ensureRuntime() {
  const launch = await runtimeLaunch();
  // Any managed copy of this patch can serve all clients. Do not bounce between
  // their individual installation paths, or downgrade a newer compatible patch.
  const reusable = current => compatibleRuntime(current.version) &&
    (current.version !== runtimeVersion || !launch.managed || current.managed === true);
  const existing = await readRuntime();
  if (existing && reusable(existing)) return existing;
  const { dir } = runtimePaths();
  await mkdir(dir, { recursive: true, mode: 0o700 });
  const release = await lockfile.lock(dir, {
    retries: { retries: 40, minTimeout: 100, maxTimeout: 250 },
    stale: 10000,
  });
  let privacyPaused = false;
  try {
    const current = await readRuntime();
    if (current) {
      if (reusable(current)) return current;
      const response = await fetch(`http://127.0.0.1:${current.port}/stop`, {
        method: 'POST',
        headers: {Authorization: `Bearer ${current.token}`, 'Content-Type': 'application/json'},
        body: '{}', signal: AbortSignal.timeout(360000),
      });
      if (!response.ok) throw new Error('Tapilot background upgrade could not stop the previous runtime');
      // Legacy daemons cannot export their privacy state. Keep capture paused
      // until an explicit resume rather than silently clearing a private pause.
      privacyPaused = (await response.json()).privacyPaused !== false;
      // Shutdown removes metadata and releases ownership before the replacement starts.
      for (let attempt = 0; attempt < 100; attempt++) {
        if (!await readRuntime()) break;
        await new Promise(resolve => setTimeout(resolve, 100));
      }
      if (await readRuntime()) throw new Error('Tapilot background upgrade is still waiting for shutdown');
    }
    await new Promise((resolve, reject) => {
      const child = fork(launch.entry, [], {
        execPath: launch.execPath,
        env: { ...process.env, TAPILOT_PREVIEW_PAUSED: privacyPaused ? '1' : '0' },
        detached: true,
        stdio: ["ignore", "ignore", "ignore", "ipc"],
        execArgv: [],
      });
      const timer = setTimeout(() => {
        child.kill();
        reject(new Error("Tapilot runtime startup timed out"));
      }, 15000);
      const finish = (error) => {
        clearTimeout(timer);
        child.removeAllListeners();
        if (child.connected) child.disconnect();
        child.unref();
        error ? reject(error) : resolve();
      };
      child.once("error", finish);
      child.once("exit", () =>
        finish(new Error("Tapilot runtime exited during startup")),
      );
      child.once("message", (m) =>
        finish(m.error ? new Error(m.error) : undefined),
      );
    });
    const started = await readRuntime();
    if (!started) throw new Error("Tapilot runtime is unavailable");
    return started;
  } finally {
    await release();
  }
}
export async function runtimeRequest(path, body, { start = true } = {}) {
  const info = start ? await ensureRuntime() : await readRuntime();
  if (!info) return { stopped: true };
  // Dispatch once. A timeout may mean the phone already acted; never replay.
  const response = await fetch(`http://127.0.0.1:${info.port}${path}`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${info.token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body ?? {}),
    signal: AbortSignal.timeout(360000),
  });
  const result = await response.json();
  if (!response.ok)
    throw new Error(result.error || "Tapilot request failed");
  return result;
}
