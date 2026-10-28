import { applyModelEnvironment } from './model-environment.js';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';
import { config as loadEnv } from 'dotenv';
import { AndroidAgent, AndroidDevice } from '@midscene/android';
import { defineNode, z, type NodeExecutionContext } from '@midscene/test';
import { defineProjectSetup, defineTestProject } from '@midscene/test/config';
import { createMidsceneNodes } from '@midscene/test/midscene';
import { DeviceFixtures } from './device-fixtures.js';
import { displayOptions } from './display-options.js';

const configured = loadEnv({ path: process.env.TAPILOT_E2E_ENV_FILE || fileURLToPath(new URL('.env', import.meta.url)) });
// An explicit missing file must not fall back to another account/provider.
if (process.env.TAPILOT_E2E_ENV_FILE && configured.error) throw configured.error;
applyModelEnvironment(configured.parsed);
const tags = (value = '') => value.split(',').map(tag => tag.trim()).filter(Boolean);
const packageName = process.env.TAPILOT_E2E_PACKAGE || 'lifeos.md.tapilot';
if (!/^lifeos\.md\.tapilot(?:\.[a-z][a-z0-9]*)?$/.test(packageName)) throw new Error('Invalid Tapilot test package');

interface Context { fixtures: DeviceFixtures; serial: string; device?: AndroidDevice; agent?: AndroidAgent }
function createDevice(serial: string) {
  return new AndroidDevice(serial, displayOptions(process.env.TAPILOT_E2E_DISPLAY_ID,
    process.env.TAPILOT_E2E_SCREEN_INFO));
}
const setup = defineProjectSetup<Context>({
  name: 'android',
  async setup({ onTeardown }) {
    const serial = process.env.TAPILOT_E2E_DEVICE_ID;
    if (!serial) throw new Error('Set TAPILOT_E2E_DEVICE_ID explicitly; the suite never selects the first device');
    const adb = (args: string[]) => execFileSync('adb', ['-s', serial, ...args], { encoding: 'utf8', timeout: 15_000 }).trim();
    if (adb(['get-state']) !== 'device') throw new Error('Selected Android device is not ready');
    if (!/mWakefulness=Awake\b/.test(adb(['shell', 'dumpsys', 'power']))) {
      throw new Error('Wake and unlock the selected phone, and enable staying awake while charging before E2E');
    }
    if (!adb(['shell', 'pm', 'path', packageName]).startsWith('package:')) throw new Error(`Install ${packageName} before running E2E`);
    const context: Context = { serial, fixtures: new DeviceFixtures(serial, packageName) };
    onTeardown(async () => {
      try { await context.fixtures.restore(); } finally {
        if (context.agent) await context.agent.destroy();
        else await context.device?.destroy();
      }
    });
    return context;
  },
});

const appOpen = defineNode({
  name: 'app.open',
  description: 'Launch the installed Tapilot app with a fresh observer agent. Preserves app data and authentication.',
  inputSchema: z.strictObject({}),
  async execute({ context }: NodeExecutionContext<unknown, Context>) {
    const services = execFileSync('adb', ['-s', context.serial, 'shell', 'dumpsys', 'activity', 'services', packageName], { encoding: 'utf8', timeout: 15_000 });
    if (/\* ServiceRecord\{[^\n]*TaskRuntimeService[\s}]/.test(services)) {
      throw new Error('The phone still has a native task running. Inspect and settle it before another case; never replace an uncertain task.');
    }
    if (context.agent) await context.agent.destroy();
    else await context.device?.destroy();
    context.agent = undefined;
    // AndroidAgent.destroy owns its device; never reuse it across cases.
    context.device = createDevice(context.serial);
    await context.device.connect();
    await context.device.launch(packageName);
    // am start returns before the Android activity transition finishes. An assertion
    // immediately after restart would otherwise inspect the previous app mid-animation.
    await delay(500);
    context.agent = new AndroidAgent(context.device, {
      aiContexts: { aiAct: `You test Tapilot on Android. Its exact installed package is ${packageName}. The phone app owns task execution. Return to its main chat using Launch with that exact package or Android recent apps. Do not use the floating assistant bubble to return: it starts a new input with a screen attachment and can contaminate the next task. For native tasks, device control means the AccessibilityService connection: the main chat shows 设备控制未连接 only when it is unavailable. Wireless control is a separate optional Wi-Fi service; its 未开启 status does not mean accessibility is unavailable. Never require wireless control for a local task. For a task that asks Tapilot to operate the phone, fail BEFORE submission if device control is unavailable. Draft-only UI checks and general questions do not require device control. To dismiss a visible keyboard use AndroidBackButton. In the XML action output write its exact type as <action-type>AndroidBackButton</action-type> with no extra punctuation inside the tag; do not output AndroidBackButton> as the action name. The keyboard Enter/Done/Send action key can submit the composer; never use it merely to dismiss the keyboard or finish typing an unsent draft. After submission, follow the current step's stopping condition: a task may intentionally remain running for later steps. Do not revisit settings to check readiness. Never perform its requested Calculator, Settings or browser task yourself. Observe and verify actual visible results. When Activity opens a record selector, its numbered text rows are clickable buttons titled with the original operation instructions. Tap the row itself to open Replay, then use Player screenshots. The instruction text is the navigation label, not evidence; do not dismiss this selector as an irrelevant raw-prompt dialog. Progress summary rows in the chat are not replay links. Choose the operation that supports the fact being checked. Each record contains only its own operation; return to the selector for earlier operations instead of searching the last record for every prior page. In execution reports use Player screenshots as visual evidence; raw prompts describe requirements and are never proof that they were satisfied. Once the required screenshot is confirmed, return to the chat instead of scrolling through raw prompt details. Execute only the current step. Do not anticipate later steps or invent extra controls, labels or outcomes. Once every explicit requirement of the current step is met, finish immediately; a separate assertion checks the outcome. Do not interact with private notifications, authentication, payments or message sending. If an unexpected installer or system permission dialog interrupts the case, stop and report the interruption; never approve, dismiss or navigate through it.` },
      replanningCycleLimit: 80,
    });
  },
});

const fixtureNode = (name: string, execute: (fixtures: DeviceFixtures) => unknown) => defineNode({
  name, description: `Explicit test fixture: ${name}`,
  inputSchema: z.strictObject({}),
  async execute({ context }: NodeExecutionContext<unknown, Context>) {
    await execute(context.fixtures);
    return { summary: `${name} completed` };
  },
});

export default defineTestProject<Context>({
  test: { maxConcurrency: 1, testTimeout: 30 * 60_000 },
  projects: [{
    name: 'android', setup, retry: 0,
    files: { include: ['cases/**/*.yaml'] },
    tags: {
      include: tags(process.env.MIDSCENE_INCLUDE_TAGS),
      exclude: [...tags(process.env.MIDSCENE_EXCLUDE_TAGS), ...(process.env.TAPILOT_E2E_LIVE === '1' ? [] : ['live']), ...(process.env.TAPILOT_E2E_FAULTS === '1' ? [] : ['resilience']), ...(process.env.TAPILOT_E2E_AUTH_FIXTURE === '1' ? [] : ['auth-fixture'])],
    },
  }],
  nodes: [
    ...createMidsceneNodes<Context>({
      agentClass: AndroidAgent,
      getAgent: ({ context }) => {
        if (!context.agent) throw new Error('Call app.open before AI steps');
        return context.agent;
      },
    }),
    appOpen,
    defineNode({
      name: 'task.waitForIdle',
      description: 'Wait for the native task service to settle without performing UI actions or inferring a pause from a still image.',
      inputSchema: z.strictObject({}),
      async execute({ context, signal }: NodeExecutionContext<unknown, Context>) {
        const data = await context.fixtures.waitForTaskIdle(signal);
        return { summary: 'Native task service settled; verify the actual UI outcome next', data };
      },
    }),
    defineNode({
      name: 'fixture.waitForNetwork',
      description: 'Wait for Android to validate the default network; does not claim model endpoint reachability.',
      inputSchema: z.strictObject({}),
      async execute({ context, signal }: NodeExecutionContext<unknown, Context>) {
        return { summary: 'Android default network validated', data: await context.fixtures.waitForNetwork(signal) };
      },
    }),
    fixtureNode('fault.offline', fixture => fixture.offline()),
    fixtureNode('fault.restart', fixture => fixture.restart()),
    fixtureNode('fault.lockAndWake', fixture => fixture.lockAndWake()),
    fixtureNode('fault.occupyPort', fixture => fixture.occupyPort()),
    fixtureNode('fixture.requireRejectedAuth', fixture => fixture.requireRejectedAuth()),
    fixtureNode('wireless.connect', fixture => fixture.connectController()),
    fixtureNode('wireless.disconnect', fixture => fixture.disconnectController()),
    fixtureNode('wireless.verifyReconnect', fixture => fixture.verifyReconnect()),
    fixtureNode('fixture.restore', fixture => fixture.restore()),
    fixtureNode('fixture.attachment', fixture => fixture.stageAttachment()),
    fixtureNode('fixture.shareText', fixture => fixture.shareText()),
  ],
});
