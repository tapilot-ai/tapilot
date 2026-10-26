import { readFile, mkdir } from "node:fs/promises";
import { homedir } from "node:os";
import { join, resolve } from "node:path";
import { createHash } from "node:crypto";
import { fork } from "node:child_process";
import lockfile from "proper-lockfile";

export const runtimeVersion = "0.6.0";
export function runtimePaths() {
  const root = resolve(
    process.env.ANDROID_USE_STATE_DIR ||
      join(homedir(), ".local/share/android-use"),
  );
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
export async function ensureRuntime() {
  const existing = await readRuntime();
  if (existing) {
    if (existing.version !== runtimeVersion)
      throw new Error(
        "Android Use runtime version changed. Run runtime-stop, then retry an observation; never replay a mutation.",
      );
    return existing;
  }
  const { dir } = runtimePaths();
  await mkdir(dir, { recursive: true, mode: 0o700 });
  const release = await lockfile.lock(dir, {
    retries: { retries: 40, minTimeout: 100, maxTimeout: 250 },
    stale: 10000,
  });
  try {
    const current = await readRuntime();
    if (current) {
      if (current.version !== runtimeVersion)
        throw new Error("Stop the old Android Use runtime before upgrading");
      return current;
    }
    await new Promise((resolve, reject) => {
      const child = fork(new URL("./runtime-server.mjs", import.meta.url), [], {
        detached: true,
        stdio: ["ignore", "ignore", "ignore", "ipc"],
        execArgv: [],
      });
      const timer = setTimeout(() => {
        child.kill();
        reject(new Error("Android Use runtime startup timed out"));
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
        finish(new Error("Android Use runtime exited during startup")),
      );
      child.once("message", (m) =>
        finish(m.error ? new Error(m.error) : undefined),
      );
    });
    const started = await readRuntime();
    if (!started) throw new Error("Android Use runtime is unavailable");
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
    throw new Error(result.error || "Android Use request failed");
  return result;
}
