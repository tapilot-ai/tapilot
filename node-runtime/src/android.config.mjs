import { verifyPhone } from './phone-verification.mjs';
import { AndroidAccessibilityDevice } from '@tapilot/android-accessibility';
import { Agent } from '@midscene/core/agent';
import { defineNode, z } from '@midscene/test';
import { defineProjectSetup, defineTestProject } from '@midscene/test/config';
import { createMidsceneNodes } from '@midscene/test/midscene';
import {
  androidAssistantAIActContext,
  mergeAndroidAssistantAIActContext,
} from './assistant-context.mjs';
import { withAndroidAiActDefaults } from './execution-policy.mjs';
import { withSettledScreenshots, nativeFingerprint } from './settled-capture.mjs';
import { StdioNativeBridge } from './stdio-native-bridge.mjs';
import { siwcFetch, measuredFetch, observeModelNetwork } from './siwc-fetch.mjs';

let activeAgentProvider;
const assistantLanguage =
  process.env.MIDSCENE_ANDROID_ASSISTANT_LANGUAGE === 'zh' ? 'zh' : 'en';
const defaultAIActContext = androidAssistantAIActContext(assistantLanguage);
const screenshotShrinkFactor = Number(process.env.TAPILOT_SCREENSHOT_SHRINK_FACTOR ?? 1);
if (![1, 1.5].includes(screenshotShrinkFactor)) {
  throw new Error('TAPILOT_SCREENSHOT_SHRINK_FACTOR must be 1 or 1.5');
}

class AndroidAssistantAgent extends Agent {
  aiAct(task, options) {
    const resolvedOptions = withAndroidAiActDefaults(options);
    return super.aiAct(task, {
      ...resolvedOptions,
      context: mergeAndroidAssistantAIActContext(
        defaultAIActContext,
        resolvedOptions.context,
        assistantLanguage,
      ),
    });
  }
}

const setup = defineProjectSetup({
  name: 'android-accessibility',
  async setup({ onTeardown }) {
    const bridge = new StdioNativeBridge();
    const originalFetch = globalThis.fetch;
    const stopNetworkTiming = observeModelNetwork(timing => {
      process.stdout.write(`MIDSCENE_EVENT ${JSON.stringify({ type: 'model.network', timestamp: new Date().toISOString(), ...timing })}\n`);
    });
    onTeardown(stopNetworkTiming);
    const transportFetch = measuredFetch(originalFetch, timing => {
      process.stdout.write(`MIDSCENE_EVENT ${JSON.stringify({ type: 'model.timing', timestamp: new Date().toISOString(), ...timing })}\n`);
    });
    globalThis.fetch = process.env.MIDSCENE_CHATGPT_ACCOUNT_ID
      ? siwcFetch(transportFetch, force => bridge.accessToken(force)) : transportFetch;
    onTeardown(() => { globalThis.fetch = originalFetch; });
    const capabilities = await bridge.getCapabilities();
    if (!capabilities.canCapture || !capabilities.canPerformGestures) {
      bridge.close();
      throw new Error('请先在系统设置中开启 Tapilot 辅助功能');
    }
    const device = new AndroidAccessibilityDevice(withSettledScreenshots(bridge, {
      ...(capabilities.captureFingerprint === 'rgb-luma-64x96-v1' ? { fingerprint: nativeFingerprint } : {}),
      record: timing => {
      process.stdout.write(`MIDSCENE_EVENT ${JSON.stringify({ type: 'capture.settled', timestamp: new Date().toISOString(), ...timing })}\n`);
    } }), capabilities);
    const agents = new Map();
    activeAgentProvider = {
      getAgent(runId) {
        let agent = agents.get(runId);
        if (!agent) {
          agent = new AndroidAssistantAgent(device, {
            autoPrintReportMsg: false,
            generateReport: true,
            screenshotShrinkFactor,
            replanningCycleLimit: 100,
            aiContexts: { aiAct: defaultAIActContext },
          });
          agents.set(runId, agent);
        }
        return agent;
      },
      async releaseAgent(runId) {
        const agent = agents.get(runId);
        if (!agent) return undefined;
        agents.delete(runId);
        await agent.destroy();
        return agent.reportFile ? { reportPath: agent.reportFile } : undefined;
      },
    };
    onTeardown(async () => {
      try {
        await Promise.all(
          [...agents.keys()].map((runId) =>
            activeAgentProvider.releaseAgent(runId),
          ),
        );
      } finally {
        activeAgentProvider = undefined;
        bridge.close();
      }
    });
    return { bridge, device };
  },
});

const agentProvider = {
  getAgent(runId) {
    if (!activeAgentProvider)
      throw new Error('Android Agent provider is unavailable');
    return activeAgentProvider.getAgent(runId);
  },
  releaseAgent(runId) {
    if (!activeAgentProvider)
      throw new Error('Android Agent provider is unavailable');
    return activeAgentProvider.releaseAgent(runId);
  },
};

const systemNode = (name, actionName, summary) =>
  defineNode({
    name,
    stringInputKey: false,
    inputSchema: z.strictObject({}),
    async execute({ context }) {
      const action = context.device
        .actionSpace()
        .find((candidate) => candidate.name === actionName);
      if (!action)
        throw new Error(`Android action is unavailable: ${actionName}`);
      await action.call({});
      return { summary };
    },
  });

const launchNode = defineNode({
  name: 'launch',
  stringInputKey: 'uri',
  inputSchema: z.strictObject({ uri: z.string().min(1) }),
  async execute({ context, input }) {
    const action = context.device
      .actionSpace()
      .find((candidate) => candidate.name === 'Launch');
    if (!action) throw new Error('Android Launch action is unavailable');
    await action.call(input);
    // startActivity returns before the window transition. Direct nodes bypass
    // aiAct's post-action wait; allow the new window to appear before aiQuery.
    await new Promise(resolve => setTimeout(resolve, 500));
    return { summary: `Launched ${input.uri}` };
  },
});

const midsceneNodes = createMidsceneNodes({ agentClass: AndroidAssistantAgent, agentProvider });
const queryNode = midsceneNodes.find(node => node.name === 'aiQuery');
const verificationNode = defineNode({
  name: 'verifyPhone',
  inputSchema: z.strictObject({ instruction: z.string().min(1), condition: z.string().min(1) }),
  async execute(context) {
    return verifyPhone(ctx => queryNode.execute(ctx), context, context.input);
  },
});

export default defineTestProject({
  test: { maxConcurrency: 1, testTimeout: 0 },
  projects: [
    {
      name: 'android-accessibility',
      setup,
      files: { include: ['*.{yaml,yml}'] },
    },
  ],
  nodes: [
    ...midsceneNodes,
    verificationNode,
    launchNode,
    systemNode('back', 'AndroidBackButton', 'Triggered Android back'),
    systemNode('home', 'AndroidHomeButton', 'Triggered Android home'),
    systemNode(
      'recentApps',
      'AndroidRecentAppsButton',
      'Triggered Android recent apps',
    ),
  ],
});
