import { WirelessDevice } from "./device.mjs";
import { ActionReport } from "./action-report.mjs";
import { randomUUID } from "node:crypto";
import { format } from "node:util";
process.umask(0o077);
console.log = console.info = (...args) =>
  process.stderr.write(format(...args) + "\n");
const pending = new Map();
process.on("disconnect", () => process.exit(1));
function rpc(method, params = {}) {
  return new Promise((resolve, reject) => {
    const id = randomUUID();
    pending.set(id, { resolve, reject });
    process.send({ type: "request", id, method, params });
  });
}
process.on("message", (message) => {
  if (message.type === "response") {
    const p = pending.get(message.id);
    pending.delete(message.id);
    if (p)
      message.error
        ? p.reject(new Error(message.error))
        : p.resolve(message.value);
  }
  if (message.type === "run")
    run(message.request).then((result) =>
      process.send({ type: "result", result }, () =>
        process.exit(result.ok ? 0 : 1),
      ),
    );
});
async function run(request) {
  const device = new WirelessDevice(rpc);
  let report,
    agent,
    screenshot,
    viewport,
    result = { ok: false },
    timer;
  try {
    const { action, args, reportId } = request;
    if (request.initialFrame) {
      device.frame = request.initialFrame;
      screenshot = device.frame.dataUrl;
    } else screenshot = await device.screenshotBase64();
    viewport = await device.size();
    if (["act", "assert", "wait"].includes(action)) {
      const { Agent } = await import("@midscene/core/agent");
      const { ChatGPTAuth } = await import("./chatgpt-auth.mjs");
      const { createChatGPTClient } = await import("./chatgpt-client.mjs");
      const { TapProgressGuard } = await import("./ai-budget.mjs");
      const { compactPlanningContext } = await import("./compact-planning.mjs");
      const auth = new ChatGPTAuth(`${process.cwd()}/chatgpt`);
      const model = (await auth.models()).find((m) =>
        /^gpt-/.test(m.slug),
      )?.slug;
      if (!model) throw new Error("no_compatible_model");
      const controller = new AbortController();
      timer = setTimeout(
        () => controller.abort(new Error("midscene_budget_exhausted")),
        action === "act"
          ? 300000
          : action === "wait"
            ? args.timeout_ms
            : 150000,
      );
      const progress = new TapProgressGuard();
      const capture = device.screenshotBase64.bind(device);
      device.screenshotBase64 = async () => {
        controller.signal.throwIfAborted();
        const image = await capture();
        progress.observe(image);
        return image;
      };
      const actions = device.actionSpace.bind(device);
      device.actionSpace = () =>
        actions().map((a) => ({
          ...a,
          call: async (p, c) => {
            controller.signal.throwIfAborted();
            progress.beforeAction(a.name, p);
            return a.call(p, c);
          },
        }));
      agent = new Agent(device, {
        generateReport: true,
        autoPrintReportMsg: false,
        reportFileName: `android-use-${reportId}`,
        reportAttributes: { "data-group-id": `android-use-${reportId}` },
        cache: false,
        replanningCycleLimit: 24,
        waitAfterAction: 600,
        aiActContext:
          compactPlanningContext +
          " Only perform the requested task. Stop for passwords, PIN, OTP, biometrics or authentication and ask for user takeover. Never submit implicitly. Observe the final state before reporting completion.",
        modelConfig: {
          MIDSCENE_MODEL_NAME: model,
          MIDSCENE_MODEL_FAMILY: /^gpt-6/.test(model) ? "gpt-6" : "gpt-5",
          MIDSCENE_MODEL_API_KEY: "oauth-managed-by-android-use",
          MIDSCENE_MODEL_BASE_URL: "http://127.0.0.1:1",
          MIDSCENE_MODEL_TIMEOUT: 60000,
          MIDSCENE_MODEL_RETRY_COUNT: 0,
        },
        createOpenAIClient: createChatGPTClient(auth, {
          signal: controller.signal,
        }),
      });
      let summary;
      if (action === "act")
        summary = await agent.aiAct(args.text, {
          abortSignal: controller.signal,
          effort: "fast",
        });
      else if (action === "assert") await agent.aiAssert(args.text);
      else
        await agent.aiWaitFor(args.text, {
          timeoutMs: args.timeout_ms,
          checkIntervalMs: 1000,
        });
      controller.signal.throwIfAborted();
      result = {
        ok: true,
        action_complete: true,
        decision_source: "chatgpt_oauth",
        model,
        ...(action === "act"
          ? {
              completion: {
                source: "midscene_aiAct",
                summary: summary ?? null,
                independent_assertion: false,
              },
            }
          : {}),
      };
    } else {
      report = new ActionReport(reportId, action, args, screenshot, viewport);
      await report.flush();
      report.startAction();
      screenshot = undefined;
      const names = {
        tap: "Tap",
        swipe: "Swipe",
        input: "Input",
        home: "AndroidHomeButton",
        back: "AndroidBackButton",
        recentApps: "AndroidRecentAppsButton",
        launch: "Launch",
      };
      const sdkAction = device
        .actionSpace()
        .find((item) => item.name === names[action]);
      if (sdkAction) {
        const param =
          action === "tap"
            ? { locate: { center: [args.x, args.y] } }
            : action === "swipe"
              ? {
                  start: { center: [args.x, args.y] },
                  end: { center: [args.end_x, args.end_y] },
                  duration: 500,
                }
              : action === "input"
                ? { value: args.text, mode: "typeOnly" }
                : action === "launch"
                  ? { uri: args.text }
                  : undefined;
        await sdkAction.call(param);
      }
      report.endAction();
      screenshot = await device.screenshotBase64();
      viewport = await device.size();
      const error =
        action === "record" && !args.passed
          ? "Host reported the condition was not met"
          : undefined;
      await report.finish(screenshot, error);
      result = {
        ok: !error,
        action_complete: true,
        decision_source: "chat_host",
        ...(error ? { error } : {}),
      };
    }
  } catch (error) {
    result = {
      ok: false,
      error: error.code ?? error.message,
      action_complete: false,
    };
    // A new observation is safe; never replay the failed action.
    screenshot = await device.screenshotBase64().catch(() => undefined);
    if (report)
      await report
        .finish(screenshot, "Action failed; inspect before continuing")
        .catch(() => {});
  } finally {
    clearTimeout(timer);
    if (agent) {
      screenshot = await device.screenshotBase64().catch(() => undefined);
      try {
        await agent.destroy();
        result.report = agent.reportFile;
      } catch {
        result.ok = false;
        result.error = "report_finalize_failed";
      }
    }
    if (report)
      try {
        result.report = await report.finalize();
      } catch {
        result.ok = false;
        result.error = "report_finalize_failed";
      }
  }
  return {
    ...result,
    ...(screenshot ? { screenshot, viewport: await device.size() } : {}),
    frame: device.frame,
    report_id: request.reportId,
  };
}
