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
import { StdioNativeBridge } from './stdio-native-bridge.mjs';
import { siwcFetch } from './siwc-fetch.mjs';

let activeAgentProvider;
const assistantLanguage =
  process.env.MIDSCENE_ANDROID_ASSISTANT_LANGUAGE === 'zh' ? 'zh' : 'en';
const defaultAIActContext = androidAssistantAIActContext(assistantLanguage);

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
    if (process.env.MIDSCENE_CHATGPT_ACCOUNT_ID) {
      globalThis.fetch = siwcFetch(originalFetch, force => bridge.accessToken(force));
    }
    onTeardown(() => { globalThis.fetch = originalFetch; });
    const capabilities = await bridge.getCapabilities();
    if (!capabilities.canCapture || !capabilities.canPerformGestures) {
      bridge.close();
      throw new Error('请先在系统设置中开启 Tapilot 辅助功能');
    }
    const device = new AndroidAccessibilityDevice(bridge, capabilities);
    const agents = new Map();
    activeAgentProvider = {
      getAgent(runId) {
        let agent = agents.get(runId);
        if (!agent) {
          agent = new AndroidAssistantAgent(device, {
            autoPrintReportMsg: false,
            generateReport: true,
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
    return { summary: `Launched ${input.uri}` };
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
    ...createMidsceneNodes({
      agentClass: AndroidAssistantAgent,
      agentProvider,
    }),
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
