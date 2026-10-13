import { fork, execFile } from "node:child_process";
import { mkdir, readFile, writeFile, rename } from "node:fs/promises";
import { homedir } from "node:os";
import { join } from "node:path";
import { randomUUID } from "node:crypto";
import { ChatGPTAuth } from "./midscene/chatgpt-auth.mjs";
const text = (value) => ({
  content: [{ type: "text", text: JSON.stringify(value) }],
});
const fields = {
  screenshot: [],
  tap: ["x", "y", "frame_id"],
  swipe: ["x", "y", "end_x", "end_y", "frame_id"],
  input: ["text", "frame_id"],
  home: [],
  back: [],
  recentApps: [],
  launch: ["text"],
  record: ["text", "passed"],
  act: ["text"],
  assert: ["text"],
  wait: ["text", "timeout_ms"],
};
const admin = new Set([
  "settings",
  "auth_status",
  "auth_login",
  "auth_cancel",
  "auth_logout",
  "models",
]);
export const midsceneTool = {
  name: "tapilot_midscene",
  description:
    "Midscene over the existing Wi-Fi connection. Default steps mode: chat host decides explicit actions and gets native reports. AI act/assert/wait require mode=ai and separate ChatGPT consent. Settings/auth need no phone. Reuse report_id per task. Use frame_id from the latest Midscene screenshot for tap/swipe/input. Never replay uncertain actions.",
  inputSchema: {
    type: "object",
    properties: {
      action: { type: "string", enum: [...admin, ...Object.keys(fields)] },
      mode: { type: "string", enum: ["off", "steps", "ai"] },
      report_id: { type: "string", pattern: "^[A-Za-z0-9_-]{1,64}$" },
      frame_id: { type: "string" },
      text: { type: "string", maxLength: 10000 },
      passed: { type: "boolean" },
      x: { type: "number", minimum: 0 },
      y: { type: "number", minimum: 0 },
      end_x: { type: "number", minimum: 0 },
      end_y: { type: "number", minimum: 0 },
      timeout_ms: { type: "integer", minimum: 1000, maximum: 60000 },
    },
    required: ["action"],
    additionalProperties: false,
  },
  annotations: {
    readOnlyHint: false,
    destructiveHint: true,
    idempotentHint: false,
    openWorldHint: true,
  },
};
export class MidsceneController {
  constructor({
    root = process.env.ANDROID_USE_STATE_DIR ||
      join(homedir(), ".local", "share", "android-use"),
  } = {}) {
    this.root = root;
    this.auth = new ChatGPTAuth(join(root, "chatgpt"));
  }
  isAdmin(action) {
    return admin.has(action);
  }
  async mode() {
    try {
      const v = JSON.parse(
        await readFile(join(this.root, "midscene.json"), "utf8"),
      );
      if (!["off", "steps", "ai"].includes(v.mode)) throw Error();
      return v.mode;
    } catch (e) {
      if (e.code === "ENOENT") return "steps";
      throw new Error("Invalid Midscene settings");
    }
  }
  validate(args) {
    if (!args || typeof args !== "object")
      throw new Error("Invalid Midscene arguments");
    const { action } = args;
    const required = admin.has(action) ? [] : fields[action];
    if (!required) throw new Error("Unknown Midscene action");
    const allowed = new Set([
      "action",
      ...(action === "settings"
        ? ["mode"]
        : admin.has(action)
          ? []
          : ["report_id", ...required]),
    ]);
    if (
      Object.keys(args).some((k) => !allowed.has(k)) ||
      required.some(
        (k) => !(k in args) && !(action === "wait" && k === "timeout_ms"),
      )
    )
      throw new Error("Supply exactly the fields for this Midscene action");
    if (
      args.report_id !== undefined &&
      !/^[A-Za-z0-9_-]{1,64}$/.test(args.report_id)
    )
      throw new Error("Invalid report_id");
    if (args.mode !== undefined && !["off", "steps", "ai"].includes(args.mode))
      throw new Error("Invalid mode");
    for (const k of ["x", "y", "end_x", "end_y"])
      if (k in args && (!Number.isFinite(args[k]) || args[k] < 0))
        throw new Error("Invalid coordinates");
    if (
      "text" in args &&
      (typeof args.text !== "string" || args.text.length > 10000)
    )
      throw new Error("Invalid text");
    if (["act", "assert", "wait"].includes(action) && !args.text.trim())
      throw new Error("AI task must not be empty");
    if ("passed" in args && typeof args.passed !== "boolean")
      throw new Error("Invalid verdict");
    if (
      "frame_id" in args &&
      (typeof args.frame_id !== "string" || !args.frame_id)
    )
      throw new Error("Invalid frame_id");
    if (
      "timeout_ms" in args &&
      (!Number.isInteger(args.timeout_ms) ||
        args.timeout_ms < 1000 ||
        args.timeout_ms > 60000)
    )
      throw new Error("Invalid wait timeout");
    if (action === "input" && /[\r\n\t]/.test(args.text))
      throw new Error("Single-line input only");
    if (
      action === "launch" &&
      !/^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$/.test(args.text)
    )
      throw new Error("Launch requires package name");
  }
  async configure(args) {
    this.validate(args);
    await mkdir(this.root, { recursive: true, mode: 0o700 });
    const { action } = args;
    if (action === "settings") {
      if (args.mode) {
        const temp = join(this.root, `${randomUUID()}.tmp`);
        await writeFile(temp, JSON.stringify({ mode: args.mode }), {
          mode: 0o600,
        });
        await rename(temp, join(this.root, "midscene.json"));
      }
      return text({
        mode: await this.mode(),
        default_mode: "steps",
        authorization_changes_mode: false,
      });
    }
    if (action === "auth_status") return text(await this.auth.status());
    if (action === "auth_cancel") return text(await this.auth.cancel());
    if (action === "auth_logout") return text(await this.auth.logout());
    if (action === "models") return text({ models: await this.auth.models() });
    if (action === "auth_login") {
      if ((await this.mode()) !== "ai")
        throw new Error(
          "Select ai mode only after user consent before signing in",
        );
      if (this.login)
        throw new Error("Sign-in already pending; use auth_status");
      return new Promise((resolve, reject) => {
        this.login = this.auth
          .login((info) => {
            execFile(
              process.platform === "darwin" ? "open" : "xdg-open",
              [info.url],
              () => {},
            );
            resolve(text(info));
          })
          .catch(reject)
          .finally(() => {
            this.login = undefined;
          });
      });
    }
    throw new Error("Not a settings or auth action");
  }
  cancel() {
    this.abort?.();
  }
  invalidate() {
    this.observation = undefined;
  }
  async run(phone, args, guard = () => {}) {
    this.validate(args);
    const mode = await this.mode();
    if (mode === "off")
      throw new Error("Midscene is off; use original tools or enable steps");
    const ai = ["act", "assert", "wait"].includes(args.action);
    if (ai && (mode !== "ai" || !(await this.auth.status()).authorized))
      throw new Error("AI mode and separate ChatGPT sign-in required");
    if (args.frame_id && args.frame_id !== this.observation?.geometry.frameId)
      throw new Error("Stale frame; call Midscene screenshot again");
    const initialFrame = args.frame_id ? this.observation : undefined;
    this.invalidate();
    guard();
    if (initialFrame && !phone.connected)
      throw new Error("Disconnected; obtain a new screenshot before acting");
    await phone.open();
    await mkdir(this.root, { recursive: true, mode: 0o700 });
    const reportId = args.report_id ?? randomUUID();
    const env = Object.fromEntries(
      Object.entries(process.env).filter(
        ([k]) => !k.startsWith("MIDSCENE_") && !k.startsWith("OPENAI_"),
      ),
    );
    const child = fork(new URL("./midscene/worker.mjs", import.meta.url), [], {
      cwd: this.root,
      env,
      stdio: ["ignore", "ignore", "ignore", "ipc"],
    });
    this.active = true;
    const inFlight = new Set();
    try {
      const result = await new Promise((resolve, reject) => {
        let settled = false;
        const finish = (err, value) => {
          if (settled) return;
          settled = true;
          clearTimeout(timer);
          this.abort = undefined;
          child.kill();
          err ? reject(err) : resolve(value);
        };
        const timer = setTimeout(
          () =>
            finish(
              new Error(
                `Midscene timed out; outcome unknown. Inspect screen before continuing. Report ID: ${reportId}`,
              ),
            ),
          ai
            ? args.action === "act"
              ? 330000
              : args.action === "wait"
                ? (args.timeout_ms ?? 15000) + 30000
                : 180000
            : 60000,
        );
        this.abort = () =>
          finish(
            new Error(
              "Midscene cancelled for user takeover; outcome may be unknown. Observe before continuing.",
            ),
          );
        child.on("error", (e) => finish(e));
        child.on("exit", () =>
          finish(
            new Error(
              "Midscene worker exited; inspect screen before continuing",
            ),
          ),
        );
        child.on("message", async (message) => {
          if (message.type === "result") return finish(null, message.result);
          if (message.type !== "request" || settled) return;
          try {
            guard();
            if (!["device.capture", "device.execute"].includes(message.method))
              throw new Error("Unsupported worker request");
            const operation = phone.request(message.method, message.params);
            inFlight.add(operation);
            let value;
            try {
              value = await operation;
            } finally {
              inFlight.delete(operation);
            }
            guard();
            if (!settled)
              child.send({ type: "response", id: message.id, value });
          } catch (error) {
            if (!settled && child.connected)
              child.send({
                type: "response",
                id: message.id,
                error: error.message,
              });
          }
        });
        child.send({
          type: "run",
          request: {
            action: args.action,
            args: { ...args, timeout_ms: args.timeout_ms ?? 15000 },
            reportId,
            initialFrame,
          },
        });
      });
      if (
        [
          "Unlock the phone first",
          "Phone is locked. Unlock it on the device.",
        ].includes(result.error)
      ) {
        throw new Error(result.error);
      }
      if (result.frame) this.observation = result.frame;
      const { screenshot, frame, ...metadata } = result;
      return {
        isError: !result.ok,
        content: [
          {
            type: "text",
            text: JSON.stringify({
              ...metadata,
              ...(frame ? { geometry: frame.geometry } : {}),
            }),
          },
          ...(screenshot
            ? [
                {
                  type: "image",
                  mimeType: "image/png",
                  data: screenshot.split(",").at(-1),
                },
              ]
            : []),
        ],
      };
    } finally {
      await Promise.allSettled([...inFlight]);
      this.active = false;
      this.abort = undefined;
      if (child.connected) child.kill();
    }
  }
}
