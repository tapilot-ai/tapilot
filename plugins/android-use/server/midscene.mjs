import { t } from '../ui/i18n.mjs';
import { existsSync } from "node:fs";
import { fork, execFile } from "node:child_process";
import { mkdir, readFile, writeFile, rename } from "node:fs/promises";
import { homedir } from "node:os";
import { join } from "node:path";
import { randomUUID } from "node:crypto";
import { pathToFileURL } from "node:url";
import { getInstallationGuide } from "./installation.mjs";
import { ChatGPTAuth } from "./midscene/chatgpt-auth.mjs";
const text = (value) => ({
  content: [{ type: "text", text: JSON.stringify(value) }],
  structuredContent: value,
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
  "status",
  "pair",
  "pair_status",
  "pair_confirm",
  "discover",
  "setup",
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
    "Midscene over the existing Wi-Fi connection. Default steps mode: chat host decides explicit actions and gets native reports. AI act/assert/wait require mode=ai and separate ChatGPT consent. Use status for mode, authorization and running-task status without connecting a phone. Settings/auth need no phone. Reuse report_id per task. At task completion, present the returned report_link as a clickable Markdown link to the user, including when the task fails. Use frame_id from the latest Midscene screenshot for tap/swipe/input. Never replay uncertain actions.",
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
    this.pairRevision = 0;
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
    if (action === "setup") return text(getInstallationGuide());
    if (action === "status") {
      const mode = await this.mode();
      const status = await this.auth.status();
      return text({
        mode, active: !!this.active, authorized: status.authorized,
        onboarding_required: !existsSync(join(this.root, "midscene.json")),
        ...(mode === "ai" ? this.authGuidance(mode, status) : {
          needs_user_input: false,
          next_action: mode === "steps" ? "continue_steps" : "respect_saved_mode",
        }),
        ...(this.active ? { next_action: "wait_or_pause", message: t("任务正在执行。可继续查看实时画面；如需接管，先暂停任务，勿重复提交。") } : {}),
      });
    }
    if (action === "discover") {
      const { discoverPhones } = await import("./discovery.mjs");
      return text({ devices: await discoverPhones(), requires_pairing: true });
    }
    if (action === "pair_status" || action === "pair_confirm") {
      if (!this.pairWindow || !this.pairProcess?.connected || this.pairWindow.expires_at <= Date.now())
        throw Error("Pairing expired; open a new pairing window.");
      const url = new URL(action === "pair_confirm" ? "confirm" : "status", this.pairWindow.url);
      const response = await fetch(url, {method:action === "pair_confirm" ? "POST" : "GET",
        headers:{Origin:url.origin}, signal:AbortSignal.timeout(15000), redirect:"error"});
      const data = await response.json();
      if (!response.ok) throw Error(data.error ?? "Pairing failed");
      if (data.paired) this.pairWindow.completed = true;
      return text(data);
    }
    if (action === "pair") {
      if (this.pairWindow && !this.pairWindow.completed && this.pairProcess?.connected && this.pairWindow.expires_at > Date.now()) return {...text(this.pairWindow), _meta:{pairingQr:this.pairQr}};
      this.pairProcess?.kill();
      const child = fork(new URL("./pair-ui.mjs", import.meta.url), [], { execArgv: [], stdio: ["ignore", "ignore", "ignore", "ipc"] });
      this.pairProcess = child;
      return new Promise((resolve, reject) => {
        const timer = setTimeout(() => { child.kill(); reject(Error("Could not open pairing window; check Wi-Fi.")); }, 10000);
        child.on("error", error => { clearTimeout(timer); reject(error); });
        child.on("exit", () => { clearTimeout(timer); reject(Error("Pairing window closed before it was ready.")); });
        child.on("message", message => {
          if (message.paired) { if (this.pairProcess === child && this.pairWindow) this.pairWindow.completed = true; this.pairRevision++; this.invalidate(); return; }
          clearTimeout(timer);
          if (message.error) return reject(Error(message.error));
          if (!message.url) return;
          this.pairWindow = { url: message.url, expires_at: message.expires_at, needs_user_input: true,
            host_instruction: "Show the QR in the Android Use panel. On the phone enable wireless control and scan the computer QR, then confirm the phone in the same panel. Use the local window link only as an explicit fallback. Wait for completion before checking status. Never ask for pairing tokens in chat." };

          this.pairQr = message.qr_data_url;
          resolve({...text(this.pairWindow), _meta:{pairingQr:this.pairQr}});
        });
      });
    }
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
        onboarding_blocking: false,
        onboarding_required: !existsSync(join(this.root, "midscene.json")),
        host_instruction: "For a new user, briefly explain the default steps mode and continue their phone task without blocking on a mode question. Offer AI as an optional later choice. Respect a saved mode. Only after explicit AI consent set mode=ai and check auth_status; reuse pending or existing authorization. Never silently enable AI.",
      });
    }
    if (action === "auth_status") {
      const status = await this.auth.status();
      return text({ ...status, ...this.authGuidance(await this.mode(), status) });
    }
    if (action === "auth_cancel") return text(await this.auth.cancel());
    if (action === "auth_logout") return text(await this.auth.logout());
    if (action === "models") return text({ models: await this.auth.models() });
    if (action === "auth_login") {
      if ((await this.mode()) !== "ai")
        throw new Error(
          "Select ai mode only after user consent before signing in",
        );
      const status = await this.auth.status();
      if (status.authorized) return text({ ...status, ...this.authGuidance("ai", status) });
      if (status.login?.status === "pending") return text({ ...status.login, ...this.authGuidance("ai", status) });
      return new Promise((resolve, reject) => {
        this.login = this.auth
          .login((info) => {
            execFile(
              process.platform === "darwin" ? "open" : process.platform === "win32" ? "explorer" : "xdg-open",
              [info.url],
              () => {},
            );
            resolve(text({ ...info, ...this.authGuidance("ai", { authorized: false, login: info }) }));
          })
          .catch(reject)
          .finally(() => {
            this.login = undefined;
          });
      });
    }
    throw new Error("Not a settings or auth action");
  }
  authGuidance(mode, status) {
    if (status.authorized) return { next_action: mode === "ai" ? "continue_ai_task" : "respect_saved_mode", needs_user_input: false };
    const pending = status.login?.status === "pending";
    return {
      code: mode !== "ai" ? "ai_consent_required" : pending ? "auth_pending" : "auth_required",
      needs_user_input: true,
      next_action: mode !== "ai" ? "ask_ai_consent" : pending ? "auth_status" : "auth_login",
      ...(pending ? { authorization_url: status.login.url } : {}),
      retry_requires_user_request: ["cancelled", "failed", "expired", "signed_out"].includes(status.login?.status),
      message: mode !== "ai"
        ? t("Midscene AI 可以自动识别和规划操作，需要通过 OpenAI 页面登录授权；也可继续使用当前聊天模型决策的单步模式。请选择后再继续。")
        : pending
          ? t("请在已打开的 OpenAI 页面完成 Android Use 授权，完成后再检查授权状态。不要在聊天里提供密码、验证码或令牌。")
          : t("Midscene AI 尚未授权。通过 auth_login 打开 OpenAI 登录授权页；聊天窗口的登录不会自动授权给插件，无需填写 API Key。"),
      host_instruction: mode !== "ai"
        ? "Ask whether the user wants Midscene AI or steps. Explicit AI/login requests already count as consent: set mode=ai then check auth_status. Otherwise wait for their choice."
        : pending
          ? "Show the returned authorization_url as a clickable link. Wait for actual user completion, then call auth_status; continue only if authorized=true. Do not restart a pending login or replay a phone mutation."
          : "Explain sign-in briefly, call auth_login for a user who chose AI, and guide them through the returned link. If the previous login was cancelled/failed/expired or signed out, ask before restarting it. Never silently downgrade to steps.",
    };
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
    if (ai) {
      const status = await this.auth.status();
      if (mode !== "ai" || !status.authorized)
        return { ...text(this.authGuidance(mode, status)), isError: true };
    }
    if (args.frame_id && args.frame_id !== this.observation?.geometry.frameId)
      throw new Error("Stale frame; call Midscene screenshot again");
    const initialFrame = args.frame_id ? this.observation : undefined;
    this.invalidate();
    guard();
    if (initialFrame && !phone.connected)
      throw new Error("Disconnected; obtain a new screenshot before acting");
    const workerUrl = new URL("./midscene/worker.mjs", import.meta.url);
    if (!existsSync(workerUrl)) throw new Error("Android Use was updated while this session was running. Reload Android Use or restart ChatGPT to load the installed version; restarting Tapilot will not fix this computer-side error. No action was started.");
    await phone.open();
    await mkdir(this.root, { recursive: true, mode: 0o700 });
    const reportId = args.report_id ?? randomUUID();
    const env = Object.fromEntries(
      Object.entries(process.env).filter(
        ([k]) => !k.startsWith("MIDSCENE_") && !k.startsWith("OPENAI_"),
      ),
    );
    const child = fork(workerUrl, [], {
      cwd: this.root,
      env,
      execArgv: [],
      stdio: ["ignore", "ignore", "pipe", "ipc"],
    });
    let workerFailure = "";
    child.stderr.on("data", chunk => {
      const diagnostic = chunk.toString();
      if (/MODULE_NOT_FOUND|ERR_MODULE_NOT_FOUND/.test(diagnostic)) workerFailure = "Worker files or dependencies are missing. Reload Android Use to load the installed version.";
      else if (/ERR_INPUT_TYPE_NOT_ALLOWED/.test(diagnostic)) workerFailure = "Worker was started with incompatible Node options.";
      // Drain stderr, but never echo provider responses, screenshots, or secrets.
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
        child.on("exit", (code, signal) =>
          finish(
            new Error(
              `Midscene worker exited (code ${code}, signal ${signal ?? "none"}). ${workerFailure || "Computer-side worker failed; reload Android Use and inspect the phone before continuing."} Do not replay an uncertain action.`,
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
      const reportLink = result.report
        ? `[${t('查看 Midscene HTML 报告')}](<${result.report}>)`
        : undefined;
      return {
        structuredContent: {
          ...metadata,
          ...(frame ? { geometry: frame.geometry } : {}),
          ...(reportLink ? {
            report_link: reportLink,
            final_response_instruction: `Include this exact clickable link in the final reply: ${reportLink}`,
          } : {}),
        },
        isError: !result.ok,
        content: [
          {
            type: "text",
            text: JSON.stringify({
              ...metadata,
              ...(reportLink ? { report_link: reportLink } : {}),
              ...(frame ? { geometry: frame.geometry } : {}),
            }),
          },
          ...(reportLink ? [
            { type: "text", text: reportLink },
            {
              type: "resource_link",
              uri: pathToFileURL(result.report).href,
              name: `android-use-${reportId}.html`,
              title: t("Midscene HTML 报告"),
              description: "Local Midscene execution report with screenshots and action replay.",
              mimeType: "text/html",
            },
          ] : []),
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
