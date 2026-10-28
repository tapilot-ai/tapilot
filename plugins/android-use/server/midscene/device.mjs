import {
  defineAction,
  defineActionTap,
  defineActionSwipe,
  defineActionInput,
  defineActionScroll,
} from "@midscene/core/device";
import { z } from "zod/v3";

// Borrow the MCP owner's TLS session through IPC. Never create an ADB controller.
export class WirelessDevice {
  interfaceType = "android";
  constructor(request) {
    this.request = request;
  }
  async capture() {
    const frame = await this.request("device.capture");
    if (
      !frame?.dataUrl?.startsWith("data:image/png;base64,") ||
      !frame.geometry?.frameId
    )
      throw new Error("Invalid phone frame");
    this.frame = frame;
    return frame;
  }
  async screenshotBase64() {
    return (await this.capture()).dataUrl;
  }
  async size() {
    const { geometry: g } = this.frame ?? (await this.capture());
    return { width: g.imageWidth, height: g.imageHeight };
  }
  async execute(action) {
    // Coordinates belong to the observation used by the planner; never refresh
    // that observation silently or reconnect a failed mutation.
    if (!this.frame) throw new Error("Observe before acting");
    const frameId = this.frame.geometry.frameId;
    this.frame = undefined;
    await this.request("device.execute", { action, frameId });
  }
  async point(x, y) {
    if (!this.frame) throw new Error("Observe before acting");
    const s = await this.size();
    if (
      ![x, y].every(Number.isFinite) ||
      x < 0 ||
      y < 0 ||
      x >= s.width ||
      y >= s.height
    )
      throw new Error("Point outside screenshot");
    return { x, y };
  }
  async tap(p) {
    await this.point(p.x, p.y);
    await this.execute({ type: "tap", ...p });
  }
  async swipe(a, b, { duration = 500, repeat = 1 } = {}) {
    if (repeat !== 1 || duration < 100 || duration > 2000)
      throw new Error("Unbounded swipe");
    await this.point(a.x, a.y);
    await this.point(b.x, b.y);
    await this.execute({
      type: "swipe",
      startX: a.x,
      startY: a.y,
      endX: b.x,
      endY: b.y,
      duration,
    });
  }
  async input(text, { replace = false } = {}) {
    if (
      typeof text !== "string" ||
      text.length > 10000 ||
      /[\r\n\t]/.test(text)
    )
      throw new Error("Input must be single-line; no implicit submit");
    await this.execute({
      type: "input",
      text,
      replace,
      autoDismissKeyboard: false,
    });
  }
  async launch(uri) {
    if (!/^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$/.test(uri))
      throw new Error("Launch requires a package name");
    await this.execute({ type: "launch", uri });
  }
  async focus(target) {
    if (!target) return;
    const [x, y] = target.center ?? [];
    await this.tap({ x, y });
    await this.capture();
  }
  async scroll({
    direction = "down",
    scrollType = "singleAction",
    distance,
    locate,
  } = {}) {
    if (scrollType !== "singleAction")
      throw new Error("Use one scroll then observe");
    const { width, height } = await this.size();
    const [x, y] = locate?.center ?? [width / 2, height / 2];
    const amount =
      distance ??
      (direction === "down" || direction === "up" ? height : width) * 0.35;
    if (!Number.isFinite(amount) || amount <= 0)
      throw new Error("Invalid scroll distance");
    const dx =
      direction === "right" ? -amount : direction === "left" ? amount : 0;
    const dy = direction === "down" ? -amount : direction === "up" ? amount : 0;
    await this.swipe(
      { x, y },
      {
        x: Math.max(1, Math.min(width - 1, x + dx)),
        y: Math.max(1, Math.min(height - 1, y + dy)),
      },
    );
  }
  actionSpace() {
    const keyboard = {
      typeText: async (value, opts) => {
        await this.focus(opts?.target);
        await this.input(value, opts);
      },
      clearInput: async (target) => {
        await this.focus(target);
        await this.input("", { replace: true });
      },
    };
    return [
      defineActionTap((p) => this.tap(p)),
      defineActionSwipe({
        swipe: (a, b, o) => this.swipe(a, b, o),
        size: () => this.size(),
        inputMode: "touch",
      }),
      defineActionInput(keyboard),
      {
        ...defineActionScroll((p) => this.scroll(p)),
        description:
          "Reveal off-screen content with one scroll. Only singleAction is supported; observe between scrolls.",
      },
      ...[
        ["AndroidHomeButton", "home"],
        ["AndroidBackButton", "back"],
        ["AndroidRecentAppsButton", "recentApps"],
      ].map(([name, type]) =>
        defineAction({
          name,
          description: `Android ${type}`,
          call: () => this.execute({ type }),
        }),
      ),
      defineAction({
        name: "Launch",
        description: "Launch an installed Android app by exact package name.",
        paramSchema: z.object({ uri: z.string() }),
        call: ({ uri }) => this.launch(uri),
      }),
    ].map((action) => ({
      ...action,
      call: async (param, context) => {
        if (
          action.name === "Swipe" &&
          param?.repeat !== undefined &&
          param.repeat !== 1
        )
          throw new Error("Use one swipe then observe");
        if (
          action.name === "Input" &&
          ((param.mode === "clear" && String(param.value) !== "") ||
            /[\r\n\t]/.test(String(param.value)) ||
            String(param.value).length > 10000)
        )
          throw new Error("Invalid single-line input");
        return action.call(param, context);
      },
    }));
  }
  async destroy() {} // Session belongs to Android Use and remains open.
}
