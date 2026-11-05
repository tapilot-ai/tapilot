import { protectPrivatePath } from '../server/private-path.mjs';
import test from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { fileURLToPath, pathToFileURL } from "node:url";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { MidsceneController } from "../server/midscene.mjs";
import { WirelessDevice } from "../server/midscene/device.mjs";
import sharp from "sharp";
const png = "data:image/png;base64," + (await sharp({ create: {
  width: 1, height: 1, channels: 3, background: "white",
} }).png().toBuffer()).toString("base64");
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
  const blocked = await c.run(null, { action: "act", text: "home" });
  assert.equal(blocked.isError, true);
  assert.equal(blocked.structuredContent.code, "auth_required");
  assert.equal(blocked.structuredContent.next_action, "auth_login");
});
test("real SDK worker writes native report and borrows one phone session", async (t) => {
  const { controller: c, phone, calls } = await fixture(t);
  const observation = metadata(
    await c.run(phone, { action: "screenshot", report_id: "real-sdk" }),
  );
  assert.equal(observation.ok, true);
  assert.equal(observation.geometry.frameId, "frame-1");
  const result = await c.run(phone, {
    action: "tap",
    x: 0,
    y: 0,
    frame_id: observation.geometry.frameId,
    report_id: "real-sdk",
  });
  const data = metadata(result);
  assert.equal(data.ok, true, JSON.stringify(data));
  assert.equal(result.structuredContent.report_link, data.report_link);
  assert.match(result.structuredContent.final_response_instruction, /final reply/);
  assert.equal(data.report_link, `[View Midscene HTML report](<${data.report}>)`);
  assert.ok(result.content.some(item => item.type === "text" && item.text === data.report_link));
  const resource = result.content.find(item => item.type === "resource_link");
  assert.equal(resource.mimeType, "text/html");
  assert.equal(fileURLToPath(resource.uri), data.report);
  assert.equal(data.decision_source, "chat_host");
  assert.equal(calls.filter((c) => c.method === "device.execute").length, 1);
  assert.equal(
    calls.find((c) => c.method === "device.execute").params.frameId,
    "frame-1",
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
  assert.ok(metadata(r).report_link);
  assert.ok(r.content.some(item => item.type === "resource_link"));
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
  await protectPrivatePath(join(c.root, "chatgpt"), true);
  await protectPrivatePath(join(c.root, "chatgpt", "account.json"));
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
          execArgv: ["--import", pathToFileURL(preload).href],
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

test("removed plugin worker tells the host to reload before touching the phone", async (t) => {
  const { controller: original, phone, calls } = await fixture(t);
  const source = (await readFile(new URL('../server/midscene.mjs', import.meta.url), 'utf8'))
    .replace("'../ui/i18n.mjs'", JSON.stringify(new URL('../ui/i18n.mjs', import.meta.url).href))
    .replace('"./midscene/chatgpt-auth.mjs"', JSON.stringify(new URL('../server/midscene/chatgpt-auth.mjs', import.meta.url).href))
    .replace('"./midscene/openai-config.mjs"', JSON.stringify(new URL('../server/midscene/openai-config.mjs', import.meta.url).href))
    .replace('"./state-paths.mjs"', JSON.stringify(new URL('../server/state-paths.mjs', import.meta.url).href))
    .replace('"./installation.mjs"', JSON.stringify(new URL('../server/installation.mjs', import.meta.url).href));
  const modulePath = join(original.root, 'removed-version.mjs');
  await writeFile(modulePath, source);
  const { MidsceneController: RemovedController } = await import(pathToFileURL(modulePath).href);
  let opened = false;
  phone.open = async () => { opened = true; };
  await assert.rejects(new RemovedController({ root: original.root }).run(phone, { action: 'screenshot' }), /Reload Tapilot or restart ChatGPT/);
  assert.equal(opened, false);
  assert.equal(calls.length, 0);
});

test("new users get a mode choice and AI authorization guidance without phone access", async (t) => {
  const { controller: c, calls } = await fixture(t);
  assert.equal(metadata(await c.configure({ action: 'settings' })).onboarding_required, true);
  const result = await c.run(null, { action: 'act', text: 'Read the screen' });
  assert.equal(result.structuredContent.code, 'ai_consent_required');
  assert.equal(result.structuredContent.next_action, 'ask_ai_consent');
  await c.configure({ action: 'settings', mode: 'ai' });
  assert.equal(metadata(await c.configure({ action: 'settings' })).onboarding_required, false);
  const status = metadata(await c.configure({ action: 'auth_status' }));
  assert.equal(status.authorized, false);
  assert.equal(status.next_action, 'auth_login');
  assert.equal(calls.length, 0);
});
test("authorization flow reuses pending login and respects cancellation and saved modes", async (t) => {
  const { controller: c } = await fixture(t);
  await c.configure({ action: 'settings', mode: 'ai' });
  const pending = { authorized: false, login: { status: 'pending', url: 'http://127.0.0.1:1234/start/fixture' } };
  c.auth.status = async () => pending;
  c.auth.login = () => { throw Error('Must not restart pending login'); };
  const login = metadata(await c.configure({ action: 'auth_login' }));
  assert.equal(login.authorization_url, pending.login.url);
  assert.equal(login.next_action, 'auth_status');
  assert.equal(c.authGuidance('ai', { authorized: false, login: {status:'cancelled'} }).retry_requires_user_request, true);
  c.auth.status = async () => ({authorized:true});
  assert.equal(metadata(await c.configure({action:'auth_login'})).next_action, 'continue_ai_task');
  assert.equal(c.authGuidance('steps', {authorized:true}).next_action, 'respect_saved_mode');
});

test("installation guidance includes both APK sources and needs no phone", async (t) => {
  const { controller: c, calls } = await fixture(t);
  const result = await c.configure({action:'setup'});
  assert.equal(result.structuredContent.state, 'setup_required');
  assert.equal(result.structuredContent.downloads.feishu, 'https://www.feishu.cn/file/Ig0KbUKlJoADozxRbOocm5iqnXf');
  assert.equal(result.structuredContent.downloads.github, 'https://github.com/tapilot-ai/tapilot/releases');
  assert.match(result.structuredContent.download_links, /\[Download Tapilot APK from Feishu/);
  assert.match(result.structuredContent.compatibility, /wireless control/);
  assert.equal(result.structuredContent.pairing.command, 'node');
  assert.match(result.structuredContent.pairing.script, /server[\\/]cli\.mjs$/);
  assert.deepEqual(result.structuredContent.pairing.args, ["pair"]);
  assert.equal(calls.length, 0);
});

test('unified status is nonblocking and preserves saved mode and pending authorization', async t => {
  const {controller:c,calls}=await fixture(t);
  const initial=metadata(await c.configure({action:'status'}));
  assert.equal(initial.mode,'steps');
  assert.equal(initial.needs_user_input,false);
  assert.equal(initial.next_action,'continue_steps');
  assert.equal(metadata(await c.configure({action:'settings'})).onboarding_blocking,false);
  await c.configure({action:'settings',mode:'ai'});
  c.auth.status=async()=>({authorized:false,login:{status:'pending',url:'http://127.0.0.1:1234/start/fixture'}});
  const pending=metadata(await c.configure({action:'status'}));
  assert.equal(pending.authorization_url,'http://127.0.0.1:1234/start/fixture');
  c.active=true;
  assert.equal(metadata(await c.configure({action:'status'})).next_action,'wait_or_pause');
  assert.equal(calls.length,0);
});

test('explicit API key model works without OAuth and never exposes the key', async (t) => {
  const { controller: c } = await fixture(t);
  const apiKeyFile = join(c.root, 'key.txt');
  await writeFile(apiKeyFile, 'sk-test-secret\n', { mode: 0o600 });
  await writeFile(join(c.root, 'openai.json'), JSON.stringify({ model: 'gpt-6.1-terra', apiKeyFile }));
  await c.configure({ action: 'settings', mode: 'ai' });
  const status = metadata(await c.configure({ action: 'auth_status' }));
  assert.equal(status.authorized, true);
  assert.equal(status.provider, 'openai_api_key');
  assert.equal(status.model, 'gpt-6.1-terra');
  assert.equal(JSON.stringify(status).includes('sk-test-secret'), false);
  assert.deepEqual(metadata(await c.configure({ action: 'models' })).models, [{ slug: 'gpt-6.1-terra' }]);
  await assert.rejects(c.configure({ action: 'auth_logout' }), /openai.json/);
  await rm(apiKeyFile);
  await assert.rejects(c.configure({ action: 'auth_status' }), /Cannot read/);
  await writeFile(join(c.root, 'openai.json'), '{');
  await assert.rejects(c.configure({ action: 'auth_status' }), /Invalid local/);
});
