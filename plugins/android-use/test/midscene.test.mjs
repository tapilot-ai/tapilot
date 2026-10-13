import test from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { MidsceneController } from "../server/midscene.mjs";
import { WirelessDevice } from "../server/midscene/device.mjs";
const png =
  "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLbtAAAAABJRU5ErkJggg==";
async function fixture(t) {
  const root = await mkdtemp(join(tmpdir(), "android-midscene-"));
  t.after(() => rm(root, { recursive: true, force: true }));
  let n = 0;
  const calls = [];
  const phone = {
    connected: true,
    open: async () => {},
    request: async (method, params) => {
      calls.push({ method, params });
      if (method === "device.capture")
        return {
          dataUrl: png,
          geometry: { frameId: `frame-${++n}`, imageWidth: 1, imageHeight: 1 },
        };
      if (method === "device.execute") return null;
      throw Error("Unexpected request");
    },
  };
  return { controller: new MidsceneController({ root }), phone, calls };
}
const metadata = (r) => JSON.parse(r.content[0].text);
test("default steps and auth status require no device or credentials; mode persists", async (t) => {
  const { controller: c } = await fixture(t);
  assert.equal(
    metadata(await c.configure({ action: "settings" })).mode,
    "steps",
  );
  assert.equal(
    metadata(await c.configure({ action: "auth_status" })).authorized,
    false,
  );
  await assert.rejects(c.configure({ action: "auth_login" }), /Select ai mode/);
  await c.configure({ action: "settings", mode: "off" });
  assert.equal(await new MidsceneController({ root: c.root }).mode(), "off");
  await assert.rejects(c.run(null, { action: "home" }), /off/);
  await c.configure({ action: "settings", mode: "ai" });
  await assert.rejects(c.run(null, { action: "act", text: "home" }), /sign-in/);
});
test("real SDK worker writes native report and borrows one phone session", async (t) => {
  const { controller: c, phone, calls } = await fixture(t);
  const observation = metadata(
    await c.run(phone, { action: "screenshot", report_id: "real-sdk" }),
  );
  assert.equal(observation.ok, true);
  assert.equal(observation.geometry.frameId, "frame-2");
  const result = await c.run(phone, {
    action: "tap",
    x: 0,
    y: 0,
    frame_id: observation.geometry.frameId,
    report_id: "real-sdk",
  });
  const data = metadata(result);
  assert.equal(data.ok, true);
  assert.equal(data.decision_source, "chat_host");
  assert.equal(calls.filter((c) => c.method === "device.execute").length, 1);
  assert.equal(
    calls.find((c) => c.method === "device.execute").params.frameId,
    "frame-2",
  );
  const html = await readFile(data.report, "utf8");
  assert.match(html, /Tap/);
  assert.match(html, /Screenshot/);
  assert.equal(phone.connected, true);
  await assert.rejects(
    c.run(phone, {
      action: "tap",
      x: 0,
      y: 0,
      frame_id: observation.geometry.frameId,
    }),
    /Stale frame/,
  );
});
test("failed verdict is a failed report, not an independent AI assertion", async (t) => {
  const { controller: c, phone } = await fixture(t);
  const r = await c.run(phone, {
    action: "record",
    text: "Wrong page",
    passed: false,
  });
  assert.equal(r.isError, true);
  assert.equal(metadata(r).decision_source, "chat_host");
  assert.match(await readFile(metadata(r).report, "utf8"), /failed/);
});
test("cancellation kills worker without replay or connection release", async (t) => {
  const { controller: c, phone, calls } = await fixture(t);
  const original = phone.request;
  let release;
  let started;
  const ready = new Promise((r) => (started = r));
  phone.request = async (method, params) => {
    if (method === "device.execute") {
      started();
      return new Promise((resolve) => {
        release = resolve;
      });
    }
    return original(method, params);
  };
  const run = c.run(phone, { action: "home" });
  await ready;
  c.cancel();
  release(null);
  await assert.rejects(run, /cancelled/);
  assert.equal(phone.connected, true);
  assert.equal(c.active, false);
});
test("invalid arguments and bounds stop before mutation; input never submits", async (t) => {
  const { controller: c, phone, calls } = await fixture(t);
  await assert.rejects(
    c.run(phone, { action: "screenshot", extra: true }),
    /exactly/,
  );
  const device = new WirelessDevice(phone.request);
  await device.capture();
  await assert.rejects(device.tap({ x: 1, y: 0 }), /outside/);
  await assert.rejects(device.input("a\nb"), /single-line/);
  await device.input("中文");
  assert.deepEqual(calls.at(-1).params.action, {
    type: "input",
    text: "中文",
    replace: false,
    autoDismissKeyboard: false,
  });
});

test("real AI SDK uses Responses fixture and wireless adapter for act/assert/wait", async (t) => {
  const { fork } = await import("node:child_process");
  const { writeFile, mkdir } = await import("node:fs/promises");
  const { default: sharp } = await import("sharp");
  const { controller: c, phone, calls } = await fixture(t);
  const image =
    "data:image/png;base64," +
    (
      await sharp({
        create: { width: 100, height: 200, channels: 3, background: "#fff" },
      })
        .png()
        .toBuffer()
    ).toString("base64");
  let frame = 0;
  phone.request = async (method, params) => {
    calls.push({ method, params });
    return method === "device.capture"
      ? {
          dataUrl: image,
          geometry: {
            frameId: `ai-${++frame}`,
            imageWidth: 100,
            imageHeight: 200,
          },
        }
      : null;
  };
  await mkdir(join(c.root, "chatgpt"), { mode: 0o700 });
  await writeFile(
    join(c.root, "chatgpt", "account.json"),
    JSON.stringify({
      access_token: "fixture-only",
      refresh_token: "fixture-only",
      client_id: "oaiapp_fixture",
      scopes: ["chatgpt.tokens.use.direct", "resource.invoke"],
      expires_at: Date.now() + 3600000,
    }),
    { mode: 0o600 },
  );
  const preload = join(c.root, "fixture.mjs");
  await writeFile(
    preload,
    `let calls=0;globalThis.fetch=async(url)=>{if(String(url).endsWith('/models'))return Response.json({models:[{visibility:'list',slug:'gpt-fixture'}]});if(!String(url).endsWith('/responses'))throw Error('Unexpected network');const mode=process.env.FIXTURE_MODE;const content=mode==='act'?(++calls===1?'<action-type>Tap</action-type><action-param-json>{"locate":{"prompt":"fixture","bbox":[10,20,30,40]}}</action-param-json>':'<complete success="true">Fixture tapped</complete>'):'<data-json>'+JSON.stringify({StatementIsTruthy:mode!=='assert-false'})+'</data-json>';return new Response('data: '+JSON.stringify({type:'response.completed',response:{status:'completed',model:'gpt-fixture',output:[{type:'message',content:[{type:'output_text',text:content}]}]}})+'\\n\\n',{headers:{'Content-Type':'text/event-stream'}});};`,
  );
  for (const mode of ["act", "assert-true", "assert-false", "wait"]) {
    const result = await new Promise((resolve, reject) => {
      const child = fork(
        new URL("../server/midscene/worker.mjs", import.meta.url),
        [],
        {
          cwd: c.root,
          execArgv: ["--import", preload],
          env: { ...process.env, FIXTURE_MODE: mode },
          stdio: ["ignore", "ignore", "pipe", "ipc"],
        },
      );
      let stderr = "";
      child.stderr.on("data", (v) => (stderr += v));
      const timer = setTimeout(() => {
        child.kill();
        reject(Error("worker timeout " + stderr));
      }, 20000);
      child.on("message", async (m) => {
        if (m.type === "request")
          child.send({
            type: "response",
            id: m.id,
            value: await phone.request(m.method, m.params),
          });
        else if (m.type === "result") {
          clearTimeout(timer);
          resolve({ ...m.result, diagnostic: stderr });
        }
      });
      child.on("error", reject);
      child.on("exit", (code) => {
        clearTimeout(timer);
        if (code && !stderr.includes("Assertion")) reject(Error(stderr));
      });
      child.send({
        type: "run",
        request: {
          action: mode.startsWith("assert") ? "assert" : mode,
          args: { text: "Check fixture", timeout_ms: 1500 },
          reportId: `fixture-${mode}`,
        },
      });
    });
    assert.equal(
      result.ok,
      mode !== "assert-false",
      JSON.stringify({ ...result, screenshot: undefined, frame: undefined }),
    );
    assert.ok(result.report);
    if (mode === "act")
      assert.equal(result.completion.source, "midscene_aiAct");
  }
  assert.equal(calls.filter((x) => x.method === "device.execute").length, 1);
  assert.equal(
    calls.find((x) => x.method === "device.execute").params.action.type,
    "tap",
  );
});
