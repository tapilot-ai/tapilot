import {
  WebViewNativeBridge,
  type EdgeEffectGeometryMode,
  type EdgeEffectSettings,
} from './native-bridge';
import { shouldDismissBottomSheet } from './bottom-sheet';
import { resolveAccessibilityStatus } from './accessibility-status';
import {
  resolveAdvancedMode,
  resolveRunKindForAdvancedMode,
} from './advanced-mode';
import { classifyExecutionFailure } from './execution-failure';
import { editorHeightForContent } from './editor-height';
import {
  addTaskHistoryEntry,
  normalizeTaskHistory,
  primaryTaskReport,
  taskHistoryRerunRequest,
  type TaskHistoryEntry,
  type TaskHistoryStatus,
  updateTaskHistoryEntry,
} from './task-history';
import { resolveRunResultMessage } from './final-response';
import { conversationEntries } from './conversation';
import { PHONE_TOOL, runChatAgent, type ChatMessage, type ChatCompletion } from './chat-agent';
import {
  classifyModelConnectionResponse,
  modelConnectionEndpoint,
} from './model-connection';
import { resolveInitialModelConfigOpen } from './model-config-visibility';
import {
  MODEL_PRESET_IDS,
  type ModelConfiguration,
  type ModelPresetId,
  getModelPresetConfiguration,
  resolveInitialModelPreset,
} from './model-presets';
import { ModelProfileStore } from './model-profile-store';
import {
  type LatestTaskResultStatus,
  parseLatestTaskResult,
  serializeLatestTaskResult,
} from './latest-result';
import {
  type ThemePreference,
  resolveTheme,
  resolveThemePreference,
} from './theme';
import './style.css';
import chatGlmIcon from '@lobehub/icons-static-svg/icons/zhipu-color.svg';
import deepSeekIcon from '@lobehub/icons-static-svg/icons/deepseek-color.svg';
import doubaoIcon from '@lobehub/icons-static-svg/icons/doubao-color.svg';
import geminiIcon from '@lobehub/icons-static-svg/icons/gemini-color.svg';
import kimiIcon from '@lobehub/icons-static-svg/icons/kimi-color.svg';
import mimoIcon from '@lobehub/icons-static-svg/icons/xiaomimimo.svg';
import openAiIcon from '@lobehub/icons-static-svg/icons/openai.svg';
import qwenIcon from '@lobehub/icons-static-svg/icons/qwen-color.svg';

const element = <T extends HTMLElement>(selector: string): T => {
  const value = document.querySelector<T>(selector);
  if (!value) throw new Error(`Missing runtime element: ${selector}`);
  return value;
};

const translations = {
  zh: {
    appTitle: 'Tapilot',
    appSubtitle: '用视觉模型理解屏幕，并在设备上完成操作',
    checkingDevice: '正在检查设备控制',
    pleaseWait: '请稍候…',
    accessibilityConnected: '辅助功能已连接',
    accessibilityNotEnabled: '开启设备控制',
    accessibilityNotEnabledDetail: '需要开启辅助功能，才能读取屏幕并执行操作。',
    accessibilityDisconnected: '设备控制已断开',
    accessibilityDisconnectedDetail:
      '系统仍显示已授权，但辅助服务已停止。请重新开启，并关闭 Tapilot 的电量优化。',
    openAccessibilitySettings: '重新开启',
    batteryOptimizationHelp: '关闭电量优化',
    modelConfig: '模型配置',
    settingsTitle: '设置',
    backToChat: '返回聊天',
    speechConfig: '语音转写 API',
    speechConfigHint: '选择语音服务，填写 API Key。按住麦克风说话，松开后转写并发送。',
    speechProvider: '语音服务',
    volcengineProvider: '火山引擎 Seed ASR 2.0',
    deepgramProvider: 'Deepgram Nova-3（英语）',
    volcengineHint: '使用新版豆包语音控制台的 API Key；资源 ID 固定为 volc.seedasr.sauc.duration。',
    deepgramHint: '使用 Deepgram API Key，Nova-3 美式英语模型。',
    speechSave: '保存语音配置',
    speechKeyPlaceholder: '输入后保存到 Android Keystore',
    holdToTalk: '按住说话',
    switchToVoiceInput: '切换到语音输入',
    switchToTextInput: '切换到文字输入',
    releaseToSend: '松开发送',
    releaseToCancel: '松开取消',
    slideUpToCancel: '上滑取消',
    transcribing: '正在转写…',
    newConversation: '新对话',
    modelConfigHint: '配置兼容 OpenAI 的视觉模型服务',
    modelConfigRequired: '还没有配置模型，点这里完成配置后就可以开始使用',
    configured: '已配置',
    notConfigured: '未配置',
    modelPreset: '模型预设',
    chooseModelPreset: '选择模型预设',
    modelPresetHint: '预设会填写服务信息，但不会修改 API Key',
    customPresetHint: '自定义全部模型变量',
    baseUrl: '服务地址',
    apiKeyPlaceholder: '输入 API Key',
    privateStorage: '按模型预设分别保存在当前设备的应用私有空间',
    secureStorage: 'API Key 已使用 Android Keystore 加密，并按预设分别保存',
    testConnection: '测试连接',
    testingConnection: '正在测试…',
    connectionSucceeded: '连接成功，API Key 和模型均可用',
    connectionNetworkFailed: '网络连接失败，请检查网络、VPN 和服务地址',
    connectionApiKeyFailed: 'API Key 无效或无权访问该服务',
    connectionModelFailed: '模型不存在、不可用，或服务地址不正确',
    connectionQuotaFailed: '额度不足或请求频率受限，请检查账户额度',
    connectionRequestFailed: '模型服务拒绝了测试请求，请检查服务地址和模型参数',
    resetPreset: '恢复默认',
    resetPresetDescription:
      '恢复“{preset}”的默认配置并清除它的 API Key，其他预设不受影响。',
    cancel: '取消',
    confirmReset: '恢复默认',
    modelName: '模型名称',
    modelNamePlaceholder: '例如 doubao-vision',
    modelFamily: '模型系列',
    modelFamilyPlaceholder: '例如 qwen-vl',
    nodeEngine: 'Node 执行引擎',
    runtimeUnchecked: '尚未检测',
    runtimeChecking: '正在启动设备内 Node.js…',
    runtimeReady: '可用',
    runnerLoaded: 'YAML Runner 已加载',
    runnerUnavailable: 'YAML Runner 不可用',
    runtimeUnavailable: '不可用',
    execution: '想让我做什么？',
    executionHint: '输入想完成的事情，或切换到 YAML',
    executionHintSimple: '告诉 Tapilot，你想在手机上完成什么',
    taskMode: '自然语言',
    taskInstruction: '想做的事',
    expandEditor: '全屏编辑',
    collapseEditor: '退出全屏',
    taskPlaceholder: '发消息给 Tapilot',
    yamlHint: '支持 aiAct、aiTap、aiAssert、aiAsk、launch、home、back 等节点',
    start: '开始执行',
    runYaml: '执行 YAML',
    running: '正在执行',
    stop: '停止',
    result: '完成情况',
    viewReport: '查看报告',
    recentReport: '最近执行报告',
    reportCenter: '报告中心',
    reportCenterHint: '查看步骤回放和 Test Runner 汇总',
    reportCenterHintSimple: '查看任务的步骤回放',
    noReports: '还没有执行报告',
    history: '历史',
    taskHistory: '任务历史',
    taskHistoryHint: '查看最近完成的事情和报告',
    examples: '示例',
    examplesTitle: '试试这样说',
    examplesHint: '点击示例即可填入，你还可以继续修改',
    noTaskHistory: '还没有执行记录',
    deleteHistory: '删除记录',
    deleteHistoryTitle: '删除这条记录？',
    deleteHistoryDescription: '删除后，关联的执行报告也将无法再从历史中打开。',
    clearHistoryTitle: '清空全部记录？',
    clearHistoryDescription: '清空后，全部执行报告也将从设备中删除，无法恢复。',
    confirmDelete: '删除',
    confirmClear: '清空',
    deleteHistoryFailed: '删除报告失败，请稍后重试。',
    clearHistory: '清空',
    historyRunning: '执行中',
    historySucceeded: '已完成',
    historyFailed: '失败',
    historyStopped: '已停止',
    rerunTask: '再次执行',
    agentReport: '步骤回放',
    testRunnerReport: '用例汇总',
    latestReportAvailable: '可查看上次执行报告',
    language: '界面语言',
    followSystem: '自动',
    chooseLanguage: '选择界面语言',
    languageHint: '可随时切换，配置与任务内容不受影响',
    followSystemHint: '跟随手机系统语言',
    theme: '主题模式',
    chooseTheme: '选择主题模式',
    themeHint: '自动模式会跟随手机系统外观',
    themeAuto: '自动',
    themeAutoHint: '跟随系统',
    themeLight: '浅色',
    themeDark: '深色',
    edgeEffectGeometry: '边缘特效',
    edgeEffectGeometryHint: '适配屏幕边角',
    chooseEdgeEffectGeometry: '边缘特效',
    edgeEffectDialogHint: '选择边角，调整时预览',
    edgeEffectAuto: '自动',
    edgeEffectAutoHint: '使用系统边角',
    edgeEffectSquare: '直角',
    edgeEffectSquareHint: '适合直角屏',
    edgeEffectCustom: '自定义',
    edgeEffectCustomHint: '调整弧度与内缩',
    edgeEffectRadius: '圆角弧度',
    edgeEffectInset: '边缘内缩',
    edgeEffectDetected: '系统检测',
    edgeEffectDetectedSquare: '系统未报告圆角',
    floatingBall: '全局悬浮球',
    floatingBallHint: '离开 Tapilot 后显示，点击即可回来输入任务',
    floatingBallUnavailableHint: '开启设备控制后即可显示悬浮球',
    advancedMode: '高级模式',
    advancedModeHint: '显示 YAML 和 Test Runner',
    backgroundHelp: '后台常驻设置说明',
    backgroundHelpTitle: '保持设备控制稳定',
    backgroundHelpHint: '部分手机会在锁屏后停止辅助服务',
    backgroundHelpReason:
      '为避免锁屏或长时间未使用后需要重新开启辅助功能，请完成以下设置：',
    backgroundHelpStepOne: '在电池设置中将 Tapilot 设为“不受限制”。',
    backgroundHelpBatterySettings: '打开 Tapilot 应用设置',
    backgroundHelpStepTwo:
      '三星手机：电池与设备维护 → 电池 → 后台使用限制 → 永不休眠的应用，添加 Tapilot。',
    backgroundHelpStepThree: '不要在系统管家或省电工具中清理 Tapilot。',
    gotIt: '知道了',
    close: '关闭',
    configPrivacy: '配置仅保存在此设备中',
    showApiKey: '显示 API Key',
    hideApiKey: '隐藏 API Key',
    missingBaseUrl: '请填写模型服务地址',
    missingApiKey: '请填写 API Key',
    missingModelName: '请填写模型名称',
    missingModelFamily: '请填写模型系列',
    missingTask: '请填写任务指令',
    missingYaml: '请填写 Test Runner YAML',
    configIncomplete: '配置尚未完成',
    taskAlreadyRunning: '任务正在执行',
    stopCurrentTask: '请先停止当前任务',
    preparing: '正在准备任务',
    preparingDetail: '正在启动设备内执行引擎…',
    executingTask: '正在执行任务',
    executingDetail: '你可以切换到目标应用，Tapilot 会继续操作',
    taskSucceeded: '已经做好了',
    taskSucceededDetail: '这件事已经完成',
    taskFailed: '这次没能完成',
    modelTimeout: '模型服务响应超时',
    modelTimeoutDetail:
      '模型服务长时间没有返回，请检查网络后重试，或切换模型服务。',
    stepTimeout: '任务步骤超时',
    stepTimeoutDetail: '当前步骤超过 10 分钟仍未完成，请缩小任务范围后重试。',
    taskStopped: '已停止',
    taskStoppedDetail: '可以修改内容后再试一次',
    reportOpenFailed: '报告打开失败',
    enableAccessibility:
      'Tapilot 辅助服务未连接。请在系统辅助功能设置中关闭再重新开启“Tapilot 设备控制”。',
    deviceError: '设备连接异常',
    modelNetworkError: '无法连接模型服务',
    modelNetworkErrorDetail:
      '请检查手机网络；如果使用内部模型地址，还需要连接公司网络或 VPN。',
    technicalDetails: '技术信息',
    dnsLookupFailed: 'DNS 无法解析',
  },
  en: {
    appTitle: 'Tapilot',
    appSubtitle: 'Understand the screen visually and automate this device',
    checkingDevice: 'Checking device control',
    pleaseWait: 'Please wait…',
    accessibilityConnected: 'Accessibility connected',
    accessibilityNotEnabled: 'Enable device control',
    accessibilityNotEnabledDetail:
      'Enable the accessibility service to read the screen and perform actions.',
    accessibilityDisconnected: 'Device control disconnected',
    accessibilityDisconnectedDetail:
      'Permission is still on, but the service stopped. Turn it off and on again, then disable battery optimization for Tapilot.',
    openAccessibilitySettings: 'Reconnect',
    batteryOptimizationHelp: 'Battery settings',
    modelConfig: 'Model configuration',
    settingsTitle: 'Settings',
    backToChat: 'Back to chat',
    speechConfig: 'Speech transcription API',
    speechConfigHint: 'Choose a speech service and enter its API Key. Hold the microphone to speak; release to transcribe and send.',
    speechProvider: 'Speech service',
    volcengineProvider: 'Volcengine Seed ASR 2.0',
    deepgramProvider: 'Deepgram Nova-3 (English)',
    volcengineHint: 'Use an API Key from the new Doubao Speech console. Resource ID: volc.seedasr.sauc.duration.',
    deepgramHint: 'Use a Deepgram API Key with Nova-3 US English.',
    speechSave: 'Save speech settings',
    speechKeyPlaceholder: 'Stored with Android Keystore',
    holdToTalk: 'Hold to talk',
    switchToVoiceInput: 'Switch to voice input',
    switchToTextInput: 'Switch to text input',
    releaseToSend: 'Release to send',
    releaseToCancel: 'Release to cancel',
    slideUpToCancel: 'Slide up to cancel',
    transcribing: 'Transcribing…',
    newConversation: 'New chat',
    modelConfigHint: 'Configure an OpenAI-compatible vision model',
    modelConfigRequired:
      'No model is configured yet. Tap here to finish setup before you begin',
    configured: 'Configured',
    notConfigured: 'Not configured',
    modelPreset: 'Model preset',
    chooseModelPreset: 'Choose a model preset',
    modelPresetHint:
      'Presets fill service details without changing your API Key',
    customPresetHint: 'Customize every model variable',
    baseUrl: 'Base URL',
    apiKeyPlaceholder: 'Enter API Key',
    privateStorage: 'Stored separately for each preset on this device',
    secureStorage:
      'API Keys are encrypted by Android Keystore and stored per preset',
    testConnection: 'Test connection',
    testingConnection: 'Testing…',
    connectionSucceeded: 'Connected. The API Key and model are available',
    connectionNetworkFailed:
      'Network connection failed. Check the network, VPN, and service URL',
    connectionApiKeyFailed:
      'The API Key is invalid or lacks access to this service',
    connectionModelFailed:
      'The model is unavailable, missing, or the service URL is incorrect',
    connectionQuotaFailed:
      'Quota is exhausted or rate limited. Check the account quota',
    connectionRequestFailed:
      'The service rejected the test request. Check the URL and model settings',
    resetPreset: 'Restore defaults',
    resetPresetDescription:
      'Restore the default “{preset}” settings and clear its API Key. Other presets are unchanged.',
    cancel: 'Cancel',
    confirmReset: 'Restore',
    modelName: 'Model name',
    modelNamePlaceholder: 'For example, doubao-vision',
    modelFamily: 'Model family',
    modelFamilyPlaceholder: 'For example, qwen-vl',
    nodeEngine: 'Node runtime',
    runtimeUnchecked: 'Not checked',
    runtimeChecking: 'Starting on-device Node.js…',
    runtimeReady: 'Ready',
    runnerLoaded: 'YAML Runner loaded',
    runnerUnavailable: 'YAML Runner unavailable',
    runtimeUnavailable: 'Unavailable',
    execution: 'What can I do for you?',
    executionHint: 'Describe what you want done, or switch to YAML',
    executionHintSimple:
      'Tell Tapilot what you want to accomplish on this phone',
    taskMode: 'Natural language',
    taskInstruction: 'What you want done',
    expandEditor: 'Fullscreen',
    collapseEditor: 'Exit fullscreen',
    taskPlaceholder: 'Message Tapilot',
    yamlHint:
      'Supports aiAct, aiTap, aiAssert, aiAsk, launch, home, back, and more',
    start: 'Start',
    runYaml: 'Run YAML',
    running: 'Running',
    stop: 'Stop',
    result: 'How it went',
    viewReport: 'View report',
    recentReport: 'Recent report',
    reportCenter: 'Reports',
    reportCenterHint: 'Open step replays and Test Runner summaries',
    reportCenterHintSimple: 'Open task step replays',
    noReports: 'No reports yet',
    history: 'History',
    taskHistory: 'Task history',
    taskHistoryHint: 'Review recent tasks and their reports',
    examples: 'Examples',
    examplesTitle: 'Try saying this',
    examplesHint: 'Tap an example to fill it in, then edit it if needed',
    noTaskHistory: 'No task history yet',
    deleteHistory: 'Delete entry',
    deleteHistoryTitle: 'Delete this entry?',
    deleteHistoryDescription:
      'After deletion, its execution report will no longer be available from history.',
    clearHistoryTitle: 'Clear all history?',
    clearHistoryDescription:
      'This also deletes every execution report from this device and cannot be undone.',
    confirmDelete: 'Delete',
    confirmClear: 'Clear all',
    deleteHistoryFailed: 'Could not delete the report. Please try again.',
    clearHistory: 'Clear',
    historyRunning: 'Running',
    historySucceeded: 'Completed',
    historyFailed: 'Failed',
    historyStopped: 'Stopped',
    rerunTask: 'Run again',
    agentReport: 'Step replay',
    testRunnerReport: 'Case summary',
    latestReportAvailable: 'The latest execution report is available',
    language: 'Language',
    followSystem: 'Auto',
    chooseLanguage: 'Choose language',
    languageHint: 'Switch anytime without changing your settings or tasks',
    followSystemHint: 'Follow the device system language',
    theme: 'Theme',
    chooseTheme: 'Choose theme',
    themeHint: 'Auto follows the device system appearance',
    themeAuto: 'Auto',
    themeAutoHint: 'Follow system',
    themeLight: 'Light',
    themeDark: 'Dark',
    edgeEffectGeometry: 'Edge effect',
    edgeEffectGeometryHint: 'Fit the screen corners',
    chooseEdgeEffectGeometry: 'Edge effect',
    edgeEffectDialogHint: 'Choose corners; adjustments preview live',
    edgeEffectAuto: 'Auto',
    edgeEffectAutoHint: 'Use system corners',
    edgeEffectSquare: 'Square',
    edgeEffectSquareHint: 'For square displays',
    edgeEffectCustom: 'Custom',
    edgeEffectCustomHint: 'Tune radius and inset',
    edgeEffectRadius: 'Corner radius',
    edgeEffectInset: 'Edge inset',
    edgeEffectDetected: 'System detected',
    edgeEffectDetectedSquare: 'No rounded corners reported by the system',
    floatingBall: 'Floating ball',
    floatingBallHint: 'Appears outside Tapilot; tap it to enter a task',
    floatingBallUnavailableHint:
      'Enable device control to show the floating ball',
    advancedMode: 'Advanced mode',
    advancedModeHint: 'Show YAML and Test Runner',
    backgroundHelp: 'Background operation guidance',
    backgroundHelpTitle: 'Keep device control available',
    backgroundHelpHint:
      'Some phones stop accessibility services while the screen is locked',
    backgroundHelpReason:
      'To avoid enabling device control again after the phone is idle, complete these settings:',
    backgroundHelpStepOne:
      'Set Tapilot battery usage to Unrestricted in system settings.',
    backgroundHelpBatterySettings: 'Open Tapilot app settings',
    backgroundHelpStepTwo:
      'On Samsung: Battery and device care → Battery → Background usage limits → Never sleeping apps, then add Tapilot.',
    backgroundHelpStepThree:
      'Exclude Tapilot from system cleaners and power-saving tools.',
    gotIt: 'Got it',
    close: 'Close',
    configPrivacy: 'Configuration stays on this device',
    showApiKey: 'Show API Key',
    hideApiKey: 'Hide API Key',
    missingBaseUrl: 'Enter the model service URL',
    missingApiKey: 'Enter the API Key',
    missingModelName: 'Enter the model name',
    missingModelFamily: 'Enter the model family',
    missingTask: 'Enter a task instruction',
    missingYaml: 'Enter Test Runner YAML',
    configIncomplete: 'Configuration incomplete',
    taskAlreadyRunning: 'A task is already running',
    stopCurrentTask: 'Stop the current task first',
    preparing: 'Preparing task',
    preparingDetail: 'Starting the on-device execution engine…',
    executingTask: 'Running task',
    executingDetail:
      'You may switch to the target app while Tapilot continues',
    taskSucceeded: 'All done',
    taskSucceededDetail: 'This is done',
    taskFailed: 'Could not finish this time',
    modelTimeout: 'Model response timed out',
    modelTimeoutDetail:
      'The model service did not respond in time. Check the network, retry, or switch providers.',
    stepTimeout: 'Task step timed out',
    stepTimeoutDetail:
      'This step did not finish within 10 minutes. Try a smaller task.',
    taskStopped: 'Stopped',
    taskStoppedDetail: 'You can edit it and try again',
    reportOpenFailed: 'Could not open report',
    enableAccessibility:
      'Tapilot accessibility service is disconnected. Turn Tapilot device control off and on again in Accessibility settings.',
    deviceError: 'Device connection error',
    modelNetworkError: 'Could not connect to the model service',
    modelNetworkErrorDetail:
      'Check this phone’s network connection. Internal model endpoints also require the company network or VPN.',
    technicalDetails: 'Technical details',
    dnsLookupFailed: 'DNS lookup failed',
  },
} as const;

type Language = keyof typeof translations;
type LanguagePreference = Language | 'system';
type TranslationKey = keyof (typeof translations)['zh'];
type RunKind = 'task' | 'yaml';
type UiState =
  | 'checking'
  | 'ready'
  | 'running'
  | 'success'
  | 'warning'
  | 'error';

const statusCard = element<HTMLElement>('#status-card');
const accessibilityActions = element<HTMLElement>('#accessibility-actions');
const openAccessibilitySettingsButton = element<HTMLButtonElement>(
  '#open-accessibility-settings',
);
const openBatteryHelpButton = element<HTMLButtonElement>('#open-battery-help');
const backgroundHelpBatterySettings = element<HTMLButtonElement>(
  '#background-help-battery-settings',
);
let accessibilityState: { connected: boolean; enabled: boolean } | undefined;
const accessibilityConnectedDot = element<HTMLElement>(
  '#accessibility-connected-dot',
);
const backgroundHelpTrigger = element<HTMLButtonElement>(
  '#background-help-trigger',
);
const backgroundHelpDialog = element<HTMLElement>('#background-help-dialog');
const backgroundHelpClose = element<HTMLButtonElement>(
  '#background-help-close',
);
const backgroundHelpDone = element<HTMLButtonElement>('#background-help-done');
const statusTitle = element<HTMLElement>('#status-title');
const status = element<HTMLParagraphElement>('#status');
const resultCard = element<HTMLElement>('#result-card');
const resultTitle = element<HTMLElement>('#result-title');
const result = element<HTMLPreElement>('#result');
const openReportButton = element<HTMLButtonElement>('#open-report');
const taskCard = element<HTMLElement>('.task-card');
const chatThread = element<HTMLElement>('#chat-thread');
const conversationList = element<HTMLSelectElement>('#conversation-list');
const newConversation = element<HTMLButtonElement>('#new-conversation');
const settingsOpen = element<HTMLButtonElement>('#settings-open');
const settingsPage = element<HTMLElement>('#settings-page');
const settingsBack = element<HTMLButtonElement>('#settings-back');
const settingsScroll = element<HTMLElement>('#settings-scroll');
const voiceModeToggle = element<HTMLButtonElement>('#voice-mode-toggle');
const voiceRecord = element<HTMLButtonElement>('#voice-record');
const voiceRecordLabel = element<HTMLElement>('#voice-record-label');
const voiceState = element<HTMLElement>('#voice-state');
const speechProvider = element<HTMLSelectElement>('#speech-provider');
const speechProviderHint = element<HTMLElement>('#speech-provider-hint');
const speechApiKey = element<HTMLInputElement>('#speech-api-key');
const speechSave = element<HTMLButtonElement>('#speech-save');
const speechConfigStatus = element<HTMLElement>('#speech-config-status');
const runButton = element<HTMLButtonElement>('#run');
const runButtonLabel = element<HTMLElement>('#run .button-label');
const runningTaskBar = element<HTMLElement>('#running-task-bar');
const runningTaskStop = element<HTMLButtonElement>('#running-task-stop');
const taskDecoration = document.createElement('span');
taskDecoration.className = 'section-icon task-decoration';
taskDecoration.setAttribute('aria-hidden', 'true');
taskDecoration.innerHTML = '<svg viewBox="0 0 24 24"><path d="M5 4h14v12H9l-4 4V4Z"/><path d="M9 8h6m-6 4h4"/></svg>';
runButton.before(taskDecoration);
const apiKeyToggle = element<HTMLButtonElement>('#toggle-api-key');
const modelConfig = element<HTMLDetailsElement>('#model-config');
const preferenceCard = element<HTMLElement>('.preference-card');
settingsScroll.append(modelConfig, preferenceCard);
const modelConfigRequired = document.createElement('button');
modelConfigRequired.type = 'button';
modelConfigRequired.className = 'model-config-required';
modelConfigRequired.dataset.i18n = 'modelConfigRequired';
modelConfigRequired.hidden = true;
taskCard.querySelector('.section-heading')?.after(modelConfigRequired);
const modelConfigStatus = element<HTMLElement>('#model-config-status');
const modelPresetTrigger = element<HTMLButtonElement>('#model-preset-trigger');
const modelPresetBadge = element<HTMLElement>('#model-preset-badge');
const modelPresetValue = element<HTMLElement>('#model-preset-value');
const modelPresetSummary = element<HTMLElement>('#model-preset-summary');
const modelPresetDialog = element<HTMLElement>('#model-preset-dialog');
const modelPresetClose = element<HTMLButtonElement>('#model-preset-close');
const modelPresetOptions = element<HTMLElement>('#model-preset-options');
const testModelButton = element<HTMLButtonElement>('#test-model');
const testModelButtonLabel = element<HTMLElement>(
  '#test-model span:last-child',
);
const resetModelButton = element<HTMLButtonElement>('#reset-model');
const modelTestStatus = element<HTMLElement>('#model-test-status');
const modelResetDialog = element<HTMLElement>('#model-reset-dialog');
const modelResetDescription = element<HTMLElement>('#model-reset-description');
const modelResetCancel = element<HTMLButtonElement>('#model-reset-cancel');
const modelResetConfirm = element<HTMLButtonElement>('#model-reset-confirm');
const historyDeleteDialog = element<HTMLElement>('#history-delete-dialog');
const historyDeleteCancel = element<HTMLButtonElement>('#history-delete-cancel');
const historyDeleteConfirm = element<HTMLButtonElement>(
  '#history-delete-confirm',
);
const historyDeleteTitle = element<HTMLElement>(
  '#history-delete-dialog-title',
);
const historyDeleteDescription = element<HTMLElement>(
  '#history-delete-dialog-description',
);
const historyDeleteError = element<HTMLElement>('#history-delete-error');
const nodeRuntimeStatus = element<HTMLElement>('#node-runtime-status');
const appVersion = element<HTMLElement>('#app-version');
const themeColor = element<HTMLMetaElement>('#theme-color');
const languageTrigger = element<HTMLButtonElement>('#language-trigger');
const languageValue = element<HTMLElement>('#language-value');
const languageDialog = element<HTMLElement>('#language-dialog');
const languageClose = element<HTMLButtonElement>('#language-close');
const languageOptions = Array.from(
  document.querySelectorAll<HTMLButtonElement>('.language-option'),
);
const themeTrigger = element<HTMLButtonElement>('#theme-trigger');
const themeValue = element<HTMLElement>('#theme-value');
const themeDialog = element<HTMLElement>('#theme-dialog');
const themeClose = element<HTMLButtonElement>('#theme-close');
const themeOptions = Array.from(
  document.querySelectorAll<HTMLButtonElement>('.theme-option'),
);
const edgeEffectTrigger = element<HTMLButtonElement>('#edge-effect-trigger');
const edgeEffectValue = element<HTMLElement>('#edge-effect-value');
const edgeEffectDialog = element<HTMLElement>('#edge-effect-dialog');
const edgeEffectClose = element<HTMLButtonElement>('#edge-effect-close');
const edgeEffectDetected = element<HTMLElement>('#edge-effect-detected');
const edgeEffectOptions = Array.from(
  document.querySelectorAll<HTMLButtonElement>('.edge-effect-option'),
);
const edgeEffectCustomControls = element<HTMLElement>(
  '#edge-effect-custom-controls',
);
const edgeEffectRadius = element<HTMLInputElement>('#edge-effect-radius');
const edgeEffectRadiusValue = element<HTMLOutputElement>(
  '#edge-effect-radius-value',
);
const edgeEffectInset = element<HTMLInputElement>('#edge-effect-inset');
const edgeEffectInsetValue = element<HTMLOutputElement>(
  '#edge-effect-inset-value',
);
const reportDialog = element<HTMLElement>('#report-dialog');
const reportClose = element<HTMLButtonElement>('#report-close');
const taskHistoryTrigger = element<HTMLButtonElement>('#task-history-trigger');
const taskHistoryClear = element<HTMLButtonElement>('#task-history-clear');
const taskExamplesTrigger = element<HTMLButtonElement>('#task-examples-trigger');
const taskExamplesDialog = element<HTMLElement>('#task-examples-dialog');
const taskExampleOptions = element<HTMLElement>('#task-example-options');
const reportHistoryElement = element<HTMLElement>('#report-history');
const reportHistoryEmpty = element<HTMLElement>('#report-history-empty');
const taskModeButton = element<HTMLButtonElement>('#mode-task');
const yamlModeButton = element<HTMLButtonElement>('#mode-yaml');
const taskPanel = element<HTMLElement>('#task-panel');
if (localStorage.getItem('tapilot.inputMode') === 'voice') taskPanel.classList.add('is-voice-mode');
const yamlPanel = element<HTMLElement>('#yaml-panel');
const modeTabs = element<HTMLElement>('.mode-tabs');
const executionHint = element<HTMLElement>('#execution-hint');
const advancedModeToggle = element<HTMLButtonElement>('#advanced-mode-toggle');
const floatingBallToggle = element<HTMLButtonElement>('#floating-ball-toggle');
const floatingBallHint = element<HTMLElement>('#floating-ball-hint');
const fieldHelpButtons = Array.from(
  document.querySelectorAll<HTMLButtonElement>('.field-help-button'),
);
const fieldHelpDialog = element<HTMLElement>('#field-help-dialog');
const fieldHelpTitle = element<HTMLElement>('#field-help-title');
const fieldHelpDescription = element<HTMLElement>('#field-help-description');
const fieldHelpExample = element<HTMLElement>('#field-help-example');
const fieldHelpDone = element<HTMLButtonElement>('#field-help-done');
const editorExpandButtons = Array.from(
  document.querySelectorAll<HTMLButtonElement>('.editor-expand'),
);
const fields = {
  baseUrl: element<HTMLInputElement>('#base-url'),
  apiKey: element<HTMLInputElement>('#api-key'),
  modelName: element<HTMLInputElement>('#model-name'),
  modelFamily: element<HTMLInputElement>('#model-family'),
  task: element<HTMLTextAreaElement>('#task'),
  yaml: element<HTMLTextAreaElement>('#yaml-source'),
};

const MODEL_CONFIG_OPEN_KEY = 'midscene.modelConfigOpen';
const savedModelConfigOpen = localStorage.getItem(MODEL_CONFIG_OPEN_KEY);
const MODEL_PRESET_KEY = 'midscene.modelPreset';
const LANGUAGE_KEY = 'midscene.language';
const THEME_KEY = 'midscene.theme';
const RUN_KIND_KEY = 'midscene.runKind';
const ADVANCED_MODE_KEY = 'midscene.advancedMode';
const REPORT_HISTORY_KEY = 'midscene.reportHistory';
const LATEST_TASK_RESPONSE_KEY = 'midscene.latestTaskResponse';
const LATEST_TASK_RESULT_KEY = 'tapilot.latestTaskResult';
const MODEL_FIELD_KEYS = [
  'baseUrl',
  'apiKey',
  'modelName',
  'modelFamily',
] as const;
const DEFAULT_TASK: Record<Language, string> = {
  zh: '打开系统设置，不要使用搜索。直接进入“关于手机”，再进入“软件信息”，停留在显示当前 Android 版本的页面。',
  en: 'Open system settings without using search. Go directly to About phone, then Software information, and leave the current Android version visible on screen.',
};
const DEFAULT_YAML: Record<Language, string> = {
  zh: `cases:
  - name: 查看 Android 系统版本
    steps:
      - launch:
          uri: com.android.settings
      - aiAct:
          prompt: 在系统设置主列表中滚动到最底部，只点击“关于手机”。不要使用搜索，也不要进入“应用”。
      - aiAct:
          prompt: 在“关于手机”页面点击“软件信息”，它位于“预安装的应用程序”上方。确保进入显示 Android 版本的软件信息页面；如果误入其他页面，返回后重试。
      - aiAssert:
          prompt: 当前位于“软件信息”页面，并且屏幕上清晰显示了 Android 版本
`,
  en: `cases:
  - name: Check the Android system version
    steps:
      - launch:
          uri: com.android.settings
      - aiAct:
          prompt: On the main system Settings list, scroll to the very bottom and tap only About phone (关于手机). Do not use search or open Apps (应用).
      - aiAct:
          prompt: On the About phone page, tap Software information (软件信息), directly above Preinstalled applications. Ensure the Software information page showing the Android version opens; if another page opens, go back and retry.
      - aiAssert:
          prompt: The Software information page is open and the current Android version is clearly visible on screen
`,
};

const TASK_EXAMPLES: Record<Language, string[]> = {
  zh: [
    '打开微信，给【好友名字】发送消息：【消息内容】。',
    '打开美团，找到离我最近的霸王茶姬，选择一杯销量最高的奶茶并设置为到店自取，提交订单前让我确认。',
    '打开相册，查看最近拍摄的照片，并告诉我最近拍了什么。',
  ],
  en: [
    'Open WeChat and send this message to [friend name]: [message].',
    'Open Meituan, find the nearest CHAGEE, choose its best-selling milk tea for store pickup, and ask me to confirm before placing the order.',
    'Open Photos, review my most recent photos, and tell me what I photographed lately.',
  ],
};

const FIELD_HELP: Record<
  Language,
  Record<
    (typeof MODEL_FIELD_KEYS)[number],
    { title: string; description: string; example: string }
  >
> = {
  zh: {
    baseUrl: {
      title: '服务地址怎么填？',
      description:
        '填写模型服务提供的 OpenAI 兼容接口根地址。通常以 /v1 或版本路径结尾，不是服务商的普通网页地址。',
      example: 'https://api.openai.com/v1',
    },
    apiKey: {
      title: 'API Key 怎么填？',
      description:
        '填写你在模型服务控制台创建的访问密钥。它只会加密保存在当前手机中。',
      example: 'sk-••••••••••••',
    },
    modelName: {
      title: '模型名称怎么填？',
      description:
        '填写接口实际接受的模型 ID 或部署名称，必须和服务商控制台显示的值完全一致。',
      example: 'gpt-4o / qwen-vl-max / your-deployment-id',
    },
    modelFamily: {
      title: '模型系列怎么填？',
      description:
        '它告诉 Tapilot 该使用哪套视觉模型协议。应填写模型所属系列，而不是随意取名。',
      example: 'gpt-4o / qwen-vl / doubao-seed / gemini / deepseek',
    },
  },
  en: {
    baseUrl: {
      title: 'What is the service URL?',
      description:
        'Enter the root URL of an OpenAI-compatible model API. It usually ends in /v1 or another version path, not the provider website.',
      example: 'https://api.openai.com/v1',
    },
    apiKey: {
      title: 'What is the API Key?',
      description:
        'Enter the access key created in your model provider console. It is encrypted and stored only on this phone.',
      example: 'sk-••••••••••••',
    },
    modelName: {
      title: 'What is the model name?',
      description:
        'Enter the exact model ID or deployment name accepted by the API.',
      example: 'gpt-4o / qwen-vl-max / your-deployment-id',
    },
    modelFamily: {
      title: 'What is the model family?',
      description:
        'This selects the visual-model protocol. Use the actual model family rather than an arbitrary label.',
      example: 'gpt-4o / qwen-vl / doubao-seed / gemini / deepseek',
    },
  },
};

const MODEL_PRESET_PRESENTATION: Record<
  ModelPresetId,
  { badge: string; zh: string; en: string }
> = {
  doubao: { badge: 'DB', zh: '豆包 Seed', en: 'Doubao Seed' },
  qwen: { badge: 'QW', zh: '千问 Qwen', en: 'Qwen' },
  deepseek: { badge: 'DS', zh: '深度求索 DeepSeek', en: 'DeepSeek' },
  gemini: { badge: 'G', zh: 'Google Gemini', en: 'Google Gemini' },
  openai: { badge: 'AI', zh: 'OpenAI GPT', en: 'OpenAI GPT' },
  kimi: { badge: 'K', zh: '月之暗面 Kimi', en: 'Moonshot Kimi' },
  mimo: { badge: 'MI', zh: '小米 MiMo', en: 'Xiaomi MiMo' },
  glm: { badge: 'GLM', zh: '智谱 GLM-V', en: 'Zhipu GLM-V' },
  custom: { badge: 'C', zh: '自定义 Custom', en: 'Custom' },
};

const MODEL_PRESET_ICONS: Partial<
  Record<ModelPresetId, { src: string; monochrome?: boolean }>
> = {
  doubao: { src: doubaoIcon },
  qwen: { src: qwenIcon },
  deepseek: { src: deepSeekIcon },
  gemini: { src: geminiIcon },
  openai: { src: openAiIcon, monochrome: true },
  kimi: { src: kimiIcon },
  mimo: { src: mimoIcon, monochrome: true },
  glm: { src: chatGlmIcon },
};

function renderModelPresetIcon(
  container: HTMLElement,
  preset: ModelPresetId,
): void {
  container.replaceChildren();
  const icon = MODEL_PRESET_ICONS[preset];
  if (icon) {
    const image = document.createElement('img');
    image.src = icon.src;
    image.alt = '';
    image.className = icon.monochrome
      ? 'model-brand-icon is-monochrome'
      : 'model-brand-icon';
    container.append(image);
    return;
  }
  container.innerHTML =
    '<svg class="custom-model-icon" viewBox="0 0 24 24"><path d="M7 7h10M7 12h10M7 17h10M9 5v4m6 1v4m-6 1v4"/><circle cx="9" cy="7" r="1.5"/><circle cx="15" cy="12" r="1.5"/><circle cx="9" cy="17" r="1.5"/></svg>';
}

let currentLanguage: Language = 'zh';
const hasSavedModelConfiguration = ['baseUrl', 'modelName', 'modelFamily'].some(
  (key) => localStorage.getItem(`midscene.${key}`) !== null,
);
let currentModelPreset = resolveInitialModelPreset(
  localStorage.getItem(MODEL_PRESET_KEY),
  hasSavedModelConfiguration,
);
const credentialBridge = new WebViewNativeBridge();
const modelProfiles = new ModelProfileStore(localStorage, {
  getAll: () => credentialBridge.getModelCredentials(),
  set: (preset, apiKey) => credentialBridge.setModelCredential(preset, apiKey),
  delete: (preset) => credentialBridge.deleteModelCredential(preset),
});
let modelProfileWrite = Promise.resolve();
let modelProfileSaveTimer: ReturnType<typeof setTimeout> | undefined;
let pendingModelProfileSave:
  | { preset: ModelPresetId; configuration: ModelConfiguration }
  | undefined;
let runKind: RunKind =
  localStorage.getItem(RUN_KIND_KEY) === 'yaml' ? 'yaml' : 'task';
let advancedMode = resolveAdvancedMode(localStorage.getItem(ADVANCED_MODE_KEY));
let floatingBallEnabled = false;
let floatingBallAvailable = false;
let edgeEffectSettings: EdgeEffectSettings = {
  mode: 'auto',
  cornerRadiusDp: 20,
  insetDp: 0,
  detected: false,
  detectedTopLeftPx: 0,
  detectedTopRightPx: 0,
  detectedBottomRightPx: 0,
  detectedBottomLeftPx: 0,
};
let running = false;
let preparingExecution = false;
let stoppedByUser = false;
let activeBridge: WebViewNativeBridge | undefined;
let latestReportId = localStorage.getItem('midscene.latestReportId') ?? '';
const savedLanguage = localStorage.getItem(LANGUAGE_KEY);
let languagePreference: LanguagePreference =
  savedLanguage === 'zh' || savedLanguage === 'en' ? savedLanguage : 'system';
let themePreference: ThemePreference = resolveThemePreference(
  localStorage.getItem(THEME_KEY),
);
let nodeRuntimeInfo:
  | { node: string; arch: string; testRunnerLoaded: boolean }
  | undefined;
let nodeRuntimeError = '';
const systemTheme = window.matchMedia('(prefers-color-scheme: dark)');
let fullscreenEditor: HTMLElement | undefined;

function readReportHistory(): TaskHistoryEntry[] {
  try {
    const parsed = JSON.parse(localStorage.getItem(REPORT_HISTORY_KEY) ?? '[]');
    return normalizeTaskHistory(parsed);
  } catch {
    return [];
  }
}

let reportHistory = readReportHistory();
let activeConversationId = localStorage.getItem('tapilot.activeConversation') ??
  reportHistory[0]?.conversationId ?? reportHistory[0]?.id ?? `chat-${Date.now()}`;
if (reportHistory.length === 0 && latestReportId) {
  reportHistory = [
    {
      id: 'legacy',
      createdAt: Date.now(),
      source: '',
      kind: 'task',
      modelName: fields.modelName.value,
      status: 'succeeded',
      reports: [
        { reportId: latestReportId, kind: 'agent', title: 'Agent report' },
      ],
    },
  ];
}
const serializedLatestTaskResult = localStorage.getItem(LATEST_TASK_RESULT_KEY);
const latestHistoryEntry = reportHistory[0];
if (!serializedLatestTaskResult && latestHistoryEntry) {
  latestReportId = primaryTaskReport(latestHistoryEntry.reports)?.reportId ?? '';
  if (latestReportId) {
    localStorage.setItem('midscene.latestReportId', latestReportId);
  } else {
    localStorage.removeItem('midscene.latestReportId');
  }
}
const storedLatestTaskResult = parseLatestTaskResult(
  serializedLatestTaskResult,
  localStorage.getItem(LATEST_TASK_RESPONSE_KEY),
  latestHistoryEntry,
);
let latestTaskResponse = storedLatestTaskResult?.message ?? '';
let latestTaskResultStatus = storedLatestTaskResult?.status;
let showingPersistedReport = Boolean(
  latestReportId || latestTaskResponse || latestTaskResultStatus,
);

declare global {
  interface Window {
    __midsceneRefreshCapabilities?: () => void;
    __midsceneSetAccessibilityState?: (
      connected: boolean,
      enabled: boolean,
    ) => void;
    __tapilotFocusTaskInput?: () => void;
    __tapilotHandleBack?: () => boolean;
    __tapilotReportsDeleted?: (reportIds: string[]) => void;
    __tapilotSetThemePreference?: (preference: ThemePreference) => void;
  }
}

function resolveLanguage(value: string): Language {
  if (value === 'zh' || value === 'en') return value;
  return navigator.language.toLowerCase().startsWith('zh') ? 'zh' : 'en';
}

function t(key: TranslationKey): string {
  return translations[currentLanguage][key];
}

function latestResultTitle(status: LatestTaskResultStatus): string {
  if (status === 'succeeded') return t('taskSucceeded');
  if (status === 'stopped') return t('taskStopped');
  return t('taskFailed');
}

function latestResultDefaultMessage(status: LatestTaskResultStatus): string {
  if (status === 'failed') return t('taskFailed');
  if (status === 'stopped') return t('taskStoppedDetail');
  return t('latestReportAvailable');
}

function applyLanguage(): void {
  currentLanguage = resolveLanguage(languagePreference);
  document.documentElement.lang = currentLanguage === 'zh' ? 'zh-CN' : 'en';
  for (const item of Array.from(
    document.querySelectorAll<HTMLElement>('[data-i18n]'),
  )) {
    item.textContent = t(item.dataset.i18n as TranslationKey);
  }
  for (const item of Array.from(
    document.querySelectorAll<HTMLInputElement | HTMLTextAreaElement>(
      '[data-i18n-placeholder]',
    ),
  )) {
    item.placeholder = t(item.dataset.i18nPlaceholder as TranslationKey);
  }
  for (const item of Array.from(
    document.querySelectorAll<HTMLElement>('[data-i18n-aria-label]'),
  )) {
    item.setAttribute(
      'aria-label',
      t(item.dataset.i18nAriaLabel as TranslationKey),
    );
  }
  apiKeyToggle.setAttribute(
    'aria-label',
    fields.apiKey.type === 'password' ? t('showApiKey') : t('hideApiKey'),
  );
  updateModelConfigStatus();
  updateModelPresetUi();
  setRunKind(runKind);
  voiceModeToggle.setAttribute('aria-label', t(taskPanel.classList.contains('is-voice-mode')
    ? 'switchToTextInput' : 'switchToVoiceInput'));
  if (!voicePointerActive) voiceRecordLabel.textContent = t('holdToTalk');
  languageValue.textContent =
    languagePreference === 'system'
      ? t('followSystem')
      : languagePreference === 'zh'
        ? '简体中文'
        : 'English';
  updateThemePreferenceUi();
  updateEdgeEffectUi();
  updateAdvancedModeUi();
  updateFloatingBallUi();
  updateRunButtonPresentation();
  for (const option of languageOptions) {
    const selected = option.dataset.language === languagePreference;
    option.classList.toggle('is-selected', selected);
    option.setAttribute('aria-checked', String(selected));
  }
  updateEditorExpandButtons();
  if (showingPersistedReport) {
    const persistedStatus = latestTaskResultStatus ?? 'succeeded';
    resultTitle.textContent = latestResultTitle(persistedStatus);
    showResult(
      latestTaskResponse || latestResultDefaultMessage(persistedStatus),
      persistedStatus,
    );
  }
  renderReportHistory();
  renderConversation();
  speechProviderHint.textContent = t(selectedSpeechProvider() === 'volcengine' ? 'volcengineHint' : 'deepgramHint');
  if (accessibilityState && !running) renderAccessibilityStatus();
}

function themePreferenceName(preference: ThemePreference): string {
  if (preference === 'light') return t('themeLight');
  if (preference === 'dark') return t('themeDark');
  return t('themeAuto');
}

function updateThemePreferenceUi(): void {
  themeValue.textContent = themePreferenceName(themePreference);
  for (const option of themeOptions) {
    const selected = option.dataset.themePreference === themePreference;
    option.classList.toggle('is-selected', selected);
    option.setAttribute('aria-checked', String(selected));
  }
}

function applyTheme(syncNative = false): void {
  const theme = resolveTheme(themePreference, systemTheme.matches);
  document.documentElement.dataset.theme = theme;
  document.documentElement.style.colorScheme = theme;
  themeColor.content = theme === 'dark' ? '#111522' : '#f7f8fc';
  updateThemePreferenceUi();
  if (syncNative) {
    const bridge = new WebViewNativeBridge();
    void bridge.setAppTheme(themePreference).finally(() => bridge.destroy());
  }
}

function openThemeDialog(): void {
  themeDialog.hidden = false;
  document.body.classList.add('dialog-open');
  themeTrigger.setAttribute('aria-expanded', 'true');
  themeOptions
    .find((option) => option.dataset.themePreference === themePreference)
    ?.focus();
}

function closeThemeDialog(restoreFocus = true): void {
  themeDialog.hidden = true;
  document.body.classList.remove('dialog-open');
  themeTrigger.setAttribute('aria-expanded', 'false');
  if (restoreFocus) themeTrigger.focus();
}

function edgeEffectModeName(mode: EdgeEffectGeometryMode): string {
  if (mode === 'square') return t('edgeEffectSquare');
  if (mode === 'custom') return t('edgeEffectCustom');
  return t('edgeEffectAuto');
}

function updateEdgeEffectUi(): void {
  edgeEffectValue.textContent = edgeEffectModeName(edgeEffectSettings.mode);
  edgeEffectRadius.value = String(edgeEffectSettings.cornerRadiusDp);
  edgeEffectInset.value = String(edgeEffectSettings.insetDp);
  edgeEffectRadiusValue.textContent = `${Math.round(edgeEffectSettings.cornerRadiusDp)}dp`;
  edgeEffectInsetValue.textContent = `${Math.round(edgeEffectSettings.insetDp)}dp`;
  edgeEffectCustomControls.hidden = edgeEffectSettings.mode !== 'custom';
  const radii = [
    edgeEffectSettings.detectedTopLeftPx,
    edgeEffectSettings.detectedTopRightPx,
    edgeEffectSettings.detectedBottomRightPx,
    edgeEffectSettings.detectedBottomLeftPx,
  ];
  edgeEffectDetected.textContent = edgeEffectSettings.detected
    ? `${t('edgeEffectDetected')}: ${radii.join(' / ')}px`
    : t('edgeEffectDetectedSquare');
  for (const option of edgeEffectOptions) {
    const selected = option.dataset.edgeEffectMode === edgeEffectSettings.mode;
    option.classList.toggle('is-selected', selected);
    option.setAttribute('aria-checked', String(selected));
  }
}

function openEdgeEffectDialog(): void {
  updateEdgeEffectUi();
  edgeEffectDialog.hidden = false;
  document.body.classList.add('dialog-open');
  edgeEffectTrigger.setAttribute('aria-expanded', 'true');
  edgeEffectOptions
    .find((option) => option.dataset.edgeEffectMode === edgeEffectSettings.mode)
    ?.focus();
  queueEdgeEffectSave();
}

function closeEdgeEffectDialog(restoreFocus = true): void {
  edgeEffectDialog.hidden = true;
  document.body.classList.remove('dialog-open');
  edgeEffectTrigger.setAttribute('aria-expanded', 'false');
  if (restoreFocus) edgeEffectTrigger.focus();
}

let edgeEffectSaveTimer: number | undefined;

function queueEdgeEffectSave(): void {
  updateEdgeEffectUi();
  if (edgeEffectSaveTimer !== undefined) window.clearTimeout(edgeEffectSaveTimer);
  edgeEffectSaveTimer = window.setTimeout(async () => {
    edgeEffectSaveTimer = undefined;
    const bridge = new WebViewNativeBridge();
    try {
      edgeEffectSettings = await bridge.setEdgeEffectSettings(
        edgeEffectSettings.mode,
        edgeEffectSettings.cornerRadiusDp,
        edgeEffectSettings.insetDp,
      );
      updateEdgeEffectUi();
    } finally {
      bridge.destroy();
    }
  }, 80);
}

async function loadEdgeEffectSettings(): Promise<void> {
  const bridge = new WebViewNativeBridge();
  try {
    edgeEffectSettings = await bridge.getEdgeEffectSettings();
    updateEdgeEffectUi();
  } finally {
    bridge.destroy();
  }
}

function openBackgroundHelpDialog(): void {
  backgroundHelpDialog.hidden = false;
  document.body.classList.add('dialog-open');
  backgroundHelpTrigger.setAttribute('aria-expanded', 'true');
  backgroundHelpClose.focus();
}

function closeBackgroundHelpDialog(restoreFocus = true): void {
  backgroundHelpDialog.hidden = true;
  document.body.classList.remove('dialog-open');
  backgroundHelpTrigger.setAttribute('aria-expanded', 'false');
  if (restoreFocus) backgroundHelpTrigger.focus();
}

function renderNodeRuntimeStatus(): void {
  if (nodeRuntimeError) {
    nodeRuntimeStatus.textContent = `${t('runtimeUnavailable')} · ${nodeRuntimeError}`;
    return;
  }
  if (!nodeRuntimeInfo) {
    nodeRuntimeStatus.textContent = t('runtimeUnchecked');
    return;
  }
  const runnerStatus = advancedMode
    ? nodeRuntimeInfo.testRunnerLoaded
      ? ` · ${t('runnerLoaded')}`
      : ` · ${t('runnerUnavailable')}`
    : '';
  nodeRuntimeStatus.textContent = `${t('runtimeReady')} · ${nodeRuntimeInfo.node} · ${nodeRuntimeInfo.arch}${runnerStatus}`;
}

function updateAdvancedModeUi(): void {
  if (!advancedMode && runKind === 'yaml') setRunKind('task');
  modeTabs.hidden = !advancedMode;
  advancedModeToggle.setAttribute('aria-checked', String(advancedMode));
  advancedModeToggle.classList.toggle('is-enabled', advancedMode);
  executionHint.textContent = t(
    advancedMode ? 'executionHint' : 'executionHintSimple',
  );
  renderNodeRuntimeStatus();
}

function updateFloatingBallUi(): void {
  floatingBallToggle.setAttribute('aria-checked', String(floatingBallEnabled));
  floatingBallToggle.classList.toggle('is-enabled', floatingBallEnabled);
  floatingBallHint.textContent = t(
    floatingBallAvailable ? 'floatingBallHint' : 'floatingBallUnavailableHint',
  );
}

const pointerDownViewportTops = new WeakMap<HTMLElement, number>();

function rememberViewportPositionOnPointerDown(element: HTMLElement): void {
  element.addEventListener('pointerdown', () => {
    pointerDownViewportTops.set(element, element.getBoundingClientRect().top);
  });
}

function keepElementAtViewportPosition(
  anchor: HTMLElement,
  update: () => void,
): void {
  const top =
    pointerDownViewportTops.get(anchor) ?? anchor.getBoundingClientRect().top;
  pointerDownViewportTops.delete(anchor);
  update();
  requestAnimationFrame(() => {
    const offset = anchor.getBoundingClientRect().top - top;
    if (Math.abs(offset) > 0.5) window.scrollBy(0, offset);
  });
}

function modelPresetName(preset: ModelPresetId): string {
  return MODEL_PRESET_PRESENTATION[preset][currentLanguage];
}

function renderModelPresetOptions(): void {
  modelPresetOptions.replaceChildren();
  for (const preset of MODEL_PRESET_IDS) {
    const presentation = MODEL_PRESET_PRESENTATION[preset];
    const configuration = getModelPresetConfiguration(preset);
    const option = document.createElement('button');
    option.type = 'button';
    option.className = 'preset-option';
    option.setAttribute('role', 'radio');
    option.dataset.preset = preset;

    const badge = document.createElement('span');
    badge.className = 'preset-option-badge';
    badge.setAttribute('aria-hidden', 'true');
    renderModelPresetIcon(badge, preset);
    const copy = document.createElement('span');
    copy.className = 'preset-option-copy';
    const name = document.createElement('strong');
    name.textContent = modelPresetName(preset);
    const detail = document.createElement('small');
    const configured = modelProfiles.isConfigured(preset);
    detail.classList.toggle('is-configured', configured);
    detail.textContent = `${configuration?.modelName ?? t('customPresetHint')} · ${t(configured ? 'configured' : 'notConfigured')}`;
    copy.append(name, detail);
    const check = document.createElement('span');
    check.className = 'language-check';
    check.setAttribute('aria-hidden', 'true');
    check.innerHTML =
      '<svg viewBox="0 0 24 24"><path d="m6 12 4 4 8-9" /></svg>';
    option.append(badge, copy, check);
    option.addEventListener('click', () => void selectModelPreset(preset));
    modelPresetOptions.append(option);
  }
  updateModelPresetUi();
}

function updateModelPresetUi(): void {
  const presentation = MODEL_PRESET_PRESENTATION[currentModelPreset];
  const configuration = getModelPresetConfiguration(currentModelPreset);
  renderModelPresetIcon(modelPresetBadge, currentModelPreset);
  modelPresetValue.textContent = modelPresetName(currentModelPreset);
  modelPresetSummary.textContent = `${configuration?.modelName ?? t('customPresetHint')} · ${t(modelProfiles.isConfigured(currentModelPreset) ? 'configured' : 'notConfigured')}`;
  const custom = currentModelPreset === 'custom';
  fields.baseUrl.readOnly = !custom;
  fields.modelName.readOnly = !custom;
  fields.modelFamily.readOnly = !custom;
  for (const button of fieldHelpButtons) button.hidden = !custom;
  for (const option of Array.from(
    modelPresetOptions.querySelectorAll<HTMLButtonElement>('.preset-option'),
  )) {
    const selected = option.dataset.preset === currentModelPreset;
    option.classList.toggle('is-selected', selected);
    option.setAttribute('aria-checked', String(selected));
  }
}

function openFieldHelp(field: (typeof MODEL_FIELD_KEYS)[number]): void {
  const help = FIELD_HELP[currentLanguage][field];
  fieldHelpTitle.textContent = help.title;
  fieldHelpDescription.textContent = help.description;
  fieldHelpExample.textContent = help.example;
  fieldHelpDialog.hidden = false;
  document.body.classList.add('dialog-open');
  fieldHelpDone.focus();
}

function closeFieldHelp(restoreFocus = true): void {
  fieldHelpDialog.hidden = true;
  document.body.classList.remove('dialog-open');
  if (!restoreFocus) return;
  fieldHelpButtons.find((button) => !button.hidden)?.focus();
}

function renderTaskExamples(): void {
  taskExampleOptions.replaceChildren();
  for (const example of TASK_EXAMPLES[currentLanguage]) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'task-example-option';
    button.textContent = example;
    button.addEventListener('click', () => {
      fields.task.value = example;
      localStorage.setItem('midscene.task', example);
      closeTaskExamples(false);
      resizeEditorsToContent(fields.task);
      fields.task.focus();
      fields.task.setSelectionRange(example.length, example.length);
    });
    taskExampleOptions.append(button);
  }
}

function openTaskExamples(): void {
  renderTaskExamples();
  taskExamplesDialog.hidden = false;
  document.body.classList.add('dialog-open');
  taskExampleOptions.querySelector<HTMLButtonElement>('button')?.focus();
}

function closeTaskExamples(restoreFocus = true): void {
  taskExamplesDialog.hidden = true;
  document.body.classList.remove('dialog-open');
  if (restoreFocus) taskExamplesTrigger.focus();
}

function closeBottomSheet(dialog: HTMLElement): void {
  switch (dialog.id) {
    case 'background-help-dialog':
      closeBackgroundHelpDialog();
      break;
    case 'report-dialog':
      closeReportDialog();
      break;
    case 'model-preset-dialog':
      closeModelPresetDialog();
      break;
    case 'model-reset-dialog':
      closeModelResetDialog();
      break;
    case 'history-delete-dialog':
      closeHistoryDeleteDialog();
      break;
    case 'language-dialog':
      closeLanguageDialog();
      break;
    case 'theme-dialog':
      closeThemeDialog();
      break;
    case 'edge-effect-dialog':
      closeEdgeEffectDialog();
      break;
    case 'field-help-dialog':
      closeFieldHelp();
      break;
    case 'task-examples-dialog':
      closeTaskExamples();
      break;
  }
}

function enableBottomSheetGestures(): void {
  for (const dialog of Array.from(
    document.querySelectorAll<HTMLElement>('.dialog-backdrop'),
  )) {
    const sheet = dialog.querySelector<HTMLElement>(':scope > section');
    const handle = sheet?.querySelector<HTMLButtonElement>(
      '[data-sheet-dismiss]',
    );
    if (!sheet || !handle) continue;
    let startY = 0;
    let distance = 0;
    let dragged = false;
    let gestureStartedAt = 0;
    let canDragSheet = false;

    sheet.addEventListener(
      'touchstart',
      (event) => {
        if (event.touches.length !== 1) return;
        startY = event.touches[0].clientY;
        gestureStartedAt = performance.now();
        canDragSheet = sheet.scrollTop <= 0;
        distance = 0;
        dragged = false;
      },
      { passive: true },
    );
    sheet.addEventListener(
      'touchmove',
      (event) => {
        if (!canDragSheet || event.touches.length !== 1) return;
        const deltaY = event.touches[0].clientY - startY;
        if (deltaY <= 0) return;
        distance = deltaY;
        if (distance <= 6) return;
        dragged = true;
        event.preventDefault();
        sheet.classList.add('sheet-is-dragging');
        sheet.style.transform = `translateY(${distance}px)`;
      },
      { passive: false },
    );
    const finishTouch = () => {
      if (!canDragSheet) return;
      canDragSheet = false;
      const elapsed = Math.max(1, performance.now() - gestureStartedAt);
      const shouldClose =
        dragged &&
        shouldDismissBottomSheet(distance, elapsed, sheet.clientHeight);
      sheet.classList.remove('sheet-is-dragging');
      if (shouldClose) {
        sheet.style.removeProperty('transform');
        closeBottomSheet(dialog);
        return;
      }
      if (!dragged) return;
      sheet.style.transition = 'transform 180ms ease-out';
      sheet.style.transform = 'translateY(0)';
      window.setTimeout(() => {
        sheet.style.removeProperty('transition');
        sheet.style.removeProperty('transform');
      }, 190);
    };
    sheet.addEventListener('touchend', finishTouch, { passive: true });
    sheet.addEventListener('touchcancel', finishTouch, { passive: true });

    handle.addEventListener('click', (event) => {
      if (dragged) {
        event.preventDefault();
        dragged = false;
        return;
      }
      closeBottomSheet(dialog);
    });
  }
}

function activeModelConfiguration(): ModelConfiguration {
  return {
    baseUrl: fields.baseUrl.value,
    apiKey: fields.apiKey.value,
    modelName: fields.modelName.value,
    modelFamily: fields.modelFamily.value,
  };
}

function flushModelProfileSave(): Promise<void> {
  if (modelProfileSaveTimer) clearTimeout(modelProfileSaveTimer);
  modelProfileSaveTimer = undefined;
  const pending = pendingModelProfileSave;
  pendingModelProfileSave = undefined;
  if (!pending) return modelProfileWrite;
  modelProfileWrite = modelProfileWrite
    .then(() => modelProfilesReady)
    .then(() => modelProfiles.save(pending.preset, pending.configuration))
    .catch((error) => {
      setUiStatus(
        'error',
        t('deviceError'),
        error instanceof Error ? error.message : String(error),
      );
    });
  return modelProfileWrite;
}

function queueModelProfileSave(): void {
  pendingModelProfileSave = {
    preset: currentModelPreset,
    configuration: activeModelConfiguration(),
  };
  if (modelProfileSaveTimer) clearTimeout(modelProfileSaveTimer);
  modelProfileSaveTimer = setTimeout(() => void flushModelProfileSave(), 350);
}

function applyModelConfiguration(configuration: ModelConfiguration): void {
  for (const key of MODEL_FIELD_KEYS) {
    fields[key].value = configuration[key];
    if (key !== 'apiKey') {
      localStorage.setItem(`midscene.${key}`, configuration[key]);
    }
  }
}

async function selectModelPreset(preset: ModelPresetId): Promise<void> {
  await flushModelProfileSave();
  await modelProfiles.save(currentModelPreset, activeModelConfiguration());
  currentModelPreset = preset;
  localStorage.setItem(MODEL_PRESET_KEY, preset);
  const configuration = modelProfiles.load(preset);
  applyModelConfiguration(configuration);
  updateModelPresetUi();
  updateModelConfigStatus();
  modelTestStatus.hidden = true;
  closeModelPresetDialog(false);
}

function openModelPresetDialog(): void {
  renderModelPresetOptions();
  modelPresetDialog.hidden = false;
  document.body.classList.add('dialog-open');
  modelPresetTrigger.setAttribute('aria-expanded', 'true');
  modelPresetOptions
    .querySelector<HTMLButtonElement>('.preset-option.is-selected')
    ?.focus();
}

function closeModelPresetDialog(restoreFocus = true): void {
  modelPresetDialog.hidden = true;
  document.body.classList.remove('dialog-open');
  modelPresetTrigger.setAttribute('aria-expanded', 'false');
  if (restoreFocus) modelPresetTrigger.focus();
}

function openModelResetDialog(): void {
  modelResetDescription.textContent = t('resetPresetDescription').replace(
    '{preset}',
    modelPresetName(currentModelPreset),
  );
  modelResetDialog.hidden = false;
  document.body.classList.add('dialog-open');
  modelResetCancel.focus();
}

function closeModelResetDialog(restoreFocus = true): void {
  modelResetDialog.hidden = true;
  document.body.classList.remove('dialog-open');
  if (restoreFocus) resetModelButton.focus();
}

async function resetCurrentModelPreset(): Promise<void> {
  modelResetConfirm.disabled = true;
  try {
    await flushModelProfileSave();
    const configuration = await modelProfiles.reset(currentModelPreset);
    applyModelConfiguration(configuration);
    localStorage.removeItem('midscene.apiKey');
    updateModelConfigStatus();
    updateModelPresetUi();
    modelTestStatus.hidden = true;
    closeModelResetDialog(false);
  } finally {
    modelResetConfirm.disabled = false;
  }
}

function encodeBase64(value: string): string {
  const bytes = new TextEncoder().encode(value);
  let binary = '';
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}

function decodeBase64(value: string): string {
  const binary = atob(value);
  return new TextDecoder().decode(
    Uint8Array.from(binary, (character) => character.charCodeAt(0)),
  );
}

async function testModelConnection(): Promise<void> {
  await flushModelProfileSave();
  try {
    requireModelConfiguration();
  } catch (error) {
    modelConfig.open = true;
    modelTestStatus.dataset.state = 'error';
    modelTestStatus.textContent =
      error instanceof Error ? error.message : String(error);
    modelTestStatus.hidden = false;
    return;
  }

  testModelButton.disabled = true;
  testModelButton.classList.add('is-loading');
  testModelButtonLabel.textContent = t('testingConnection');
  modelTestStatus.hidden = true;
  const bridge = new WebViewNativeBridge();
  try {
    await modelProfiles.save(currentModelPreset, activeModelConfiguration());
    const response = await bridge.httpRequest({
      url: modelConnectionEndpoint(fields.baseUrl.value.trim()),
      method: 'POST',
      headers: {
        Authorization: `Bearer ${fields.apiKey.value.trim()}`,
        'Content-Type': 'application/json',
      },
      bodyBase64: encodeBase64(
        JSON.stringify({
          model: fields.modelName.value.trim(),
          messages: [{ role: 'user', content: 'Reply with OK.' }],
          max_tokens: 1,
          stream: false,
        }),
      ),
    });
    if (response.status < 200 || response.status >= 300) {
      const failure = classifyModelConnectionResponse(
        response.status,
        decodeBase64(response.bodyBase64),
      );
      const key: Record<typeof failure, TranslationKey> = {
        apiKey: 'connectionApiKeyFailed',
        model: 'connectionModelFailed',
        quota: 'connectionQuotaFailed',
        request: 'connectionRequestFailed',
      };
      throw new Error(t(key[failure]));
    }
    modelTestStatus.dataset.state = 'success';
    modelTestStatus.textContent = t('connectionSucceeded');
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    const isNetworkFailure =
      error instanceof TypeError ||
      /unable to resolve host|failed to connect|timeout|timed out|network is unreachable|connection reset/i.test(
        message,
      );
    modelTestStatus.dataset.state = 'error';
    modelTestStatus.textContent = isNetworkFailure
      ? t('connectionNetworkFailed')
      : message;
  } finally {
    bridge.destroy();
    modelTestStatus.hidden = false;
    testModelButton.disabled = false;
    testModelButton.classList.remove('is-loading');
    testModelButtonLabel.textContent = t('testConnection');
    updateModelPresetUi();
  }
}

function updateEditorExpandButtons(): void {
  for (const button of editorExpandButtons) {
    const expanded = fullscreenEditor?.id === button.dataset.editor;
    button.setAttribute('aria-pressed', String(expanded));
    const label = button.querySelector<HTMLElement>('span');
    if (label)
      label.textContent = t(expanded ? 'collapseEditor' : 'expandEditor');
  }
}

function resizeEditorsToContent(editor: HTMLTextAreaElement): void {
  if (editor === fields.task) {
    editor.style.height = '44px';
    editor.style.height = `${Math.min(120, Math.max(44, editor.scrollHeight))}px`;
    return;
  }
  // Grow only when content needs more room. Temporarily collapsing the focused
  // textarea for every input event cancels some IMEs' long-press backspace repeat
  // and also makes the page jump as the keyboard's visual viewport settles.
  const currentHeight = editor.getBoundingClientRect().height;
  const height = editorHeightForContent(editor.scrollHeight + 2);
  if (height <= currentHeight + 1) return;
  document.documentElement.style.setProperty(
    '--task-editor-height',
    `${height}px`,
  );
}

let editorFocusExitTimer: number | undefined;

function enterEditorFocusMode(): void {
  if (editorFocusExitTimer !== undefined) {
    window.clearTimeout(editorFocusExitTimer);
    editorFocusExitTimer = undefined;
  }
  if (fullscreenEditor) return;
  document.body.classList.add('task-input-focused');
  pinFocusedEditorViewport();
}

function scheduleEditorFocusModeExit(): void {
  editorFocusExitTimer = window.setTimeout(() => {
    editorFocusExitTimer = undefined;
    const activeElement = document.activeElement;
    if (activeElement === fields.task || activeElement === fields.yaml) return;
    document.body.classList.remove('task-input-focused');
  }, 180);
}

function leaveEditorFocusMode(): void {
  if (editorFocusExitTimer !== undefined) {
    window.clearTimeout(editorFocusExitTimer);
    editorFocusExitTimer = undefined;
  }
  const activeElement = document.activeElement;
  if (activeElement === fields.task || activeElement === fields.yaml) {
    (activeElement as HTMLTextAreaElement).blur();
  }
  document.body.classList.remove('task-input-focused');
}

function closeFullscreenEditor(): void {
  fullscreenEditor?.classList.remove('is-fullscreen');
  fullscreenEditor = undefined;
  document.body.classList.remove('editor-fullscreen-open');
  updateEditorExpandButtons();
}

function toggleFullscreenEditor(panel: HTMLElement): void {
  if (fullscreenEditor === panel) {
    closeFullscreenEditor();
    return;
  }
  closeFullscreenEditor();
  fullscreenEditor = panel;
  panel.classList.add('is-fullscreen');
  document.body.classList.add('editor-fullscreen-open');
  updateEditorExpandButtons();
  panel.querySelector<HTMLTextAreaElement>('textarea')?.focus();
}

function openLanguageDialog(): void {
  languageDialog.hidden = false;
  document.body.classList.add('dialog-open');
  languageTrigger.setAttribute('aria-expanded', 'true');
  languageOptions
    .find((option) => option.dataset.language === languagePreference)
    ?.focus();
}

function closeLanguageDialog(restoreFocus = true): void {
  languageDialog.hidden = true;
  document.body.classList.remove('dialog-open');
  languageTrigger.setAttribute('aria-expanded', 'false');
  if (restoreFocus) languageTrigger.focus();
}

let reportDialogTrigger: HTMLElement = taskHistoryTrigger;

function closeReportDialog(restoreFocus = true): void {
  reportDialog.hidden = true;
  document.body.classList.remove('dialog-open');
  taskHistoryTrigger.setAttribute('aria-expanded', 'false');
  if (restoreFocus) reportDialogTrigger.focus();
}

let pendingHistoryDeleteIds: string[] = [];
let historyDeleteTrigger: HTMLButtonElement | undefined;

function openHistoryDeleteDialog(
  historyIds: string[],
  trigger: HTMLButtonElement,
  clearingAll = false,
): void {
  pendingHistoryDeleteIds = historyIds;
  historyDeleteTrigger = trigger;
  historyDeleteTitle.textContent = t(
    clearingAll ? 'clearHistoryTitle' : 'deleteHistoryTitle',
  );
  historyDeleteDescription.textContent = t(
    clearingAll ? 'clearHistoryDescription' : 'deleteHistoryDescription',
  );
  historyDeleteConfirm.textContent = t(
    clearingAll ? 'confirmClear' : 'confirmDelete',
  );
  historyDeleteDialog.hidden = false;
  historyDeleteError.hidden = true;
  historyDeleteError.textContent = '';
  document.body.classList.add('dialog-open');
  historyDeleteConfirm.focus();
}

function closeHistoryDeleteDialog(restoreFocus = true): void {
  historyDeleteDialog.hidden = true;
  document.body.classList.add('dialog-open');
  pendingHistoryDeleteIds = [];
  if (restoreFocus && historyDeleteTrigger?.isConnected) {
    historyDeleteTrigger.focus();
  }
  historyDeleteTrigger = undefined;
}

async function openReport(reportId: string): Promise<void> {
  const bridge = new WebViewNativeBridge();
  try {
    await bridge.openReport(reportId);
  } catch (error) {
    setUiStatus(
      'error',
      t('reportOpenFailed'),
      error instanceof Error ? error.message : String(error),
    );
  } finally {
    bridge.destroy();
  }
}

function reportButton(reportId: string): HTMLButtonElement {
  const button = document.createElement('button');
  button.type = 'button';
  button.className = 'report-action is-primary';
  button.textContent = t('viewReport');
  button.addEventListener('click', (event) => {
    event.stopPropagation();
    void openReport(reportId);
  });
  return button;
}

function historyStatusIcon(status: TaskHistoryStatus): HTMLElement {
  const statusKey: Record<TaskHistoryStatus, TranslationKey> = {
    running: 'historyRunning',
    succeeded: 'historySucceeded',
    failed: 'historyFailed',
    stopped: 'historyStopped',
  };
  const paths: Record<TaskHistoryStatus, string> = {
    running: '<circle cx="12" cy="12" r="3" />',
    succeeded: '<path d="m6.5 12.5 3.5 3.5 7.5-8" />',
    failed: '<path d="m8 8 8 8m0-8-8 8" />',
    stopped: '<path d="M8 12h8" />',
  };
  const icon = document.createElement('span');
  icon.className = 'history-status-icon';
  icon.dataset.status = status;
  icon.setAttribute('role', 'img');
  icon.setAttribute('aria-label', t(statusKey[status]));
  icon.innerHTML = `<svg viewBox="0 0 24 24" aria-hidden="true">${paths[status]}</svg>`;
  return icon;
}

function renderReportHistory(): void {
  reportHistoryElement.replaceChildren();
  reportHistoryEmpty.hidden = reportHistory.length > 0;
  taskHistoryClear.hidden = reportHistory.length === 0;
  for (const run of reportHistory) {
    const article = document.createElement('article');
    article.className = 'report-run';
    const primaryReport = primaryTaskReport(run.reports);
    const canExpand = Boolean(run.detail);
    const detailId = `history-detail-${run.id.replace(/[^a-zA-Z0-9_-]/g, '-')}`;
    const setExpanded = (expanded: boolean): void => {
      article.classList.toggle('is-expanded', expanded);
      article.setAttribute('aria-expanded', String(expanded));
      const detail = document.getElementById(detailId);
      if (detail) detail.hidden = !expanded;
    };
    if (canExpand) {
      article.classList.add('is-expandable');
      article.tabIndex = 0;
      article.setAttribute('aria-expanded', 'false');
      article.setAttribute('aria-controls', detailId);
      article.addEventListener('click', (event) => {
        if ((event.target as HTMLElement).closest('button')) return;
        setExpanded(!article.classList.contains('is-expanded'));
      });
      article.addEventListener('keydown', (event) => {
        if (event.key !== 'Enter' && event.key !== ' ') return;
        event.preventDefault();
        setExpanded(!article.classList.contains('is-expanded'));
      });
    }
    const header = document.createElement('header');
    const heading = document.createElement('div');
    heading.className = 'report-run-heading';
    const title = document.createElement('strong');
    title.textContent =
      !advancedMode && run.kind === 'yaml'
        ? t('recentReport')
        : run.source || t('recentReport');
    heading.append(historyStatusIcon(run.status), title);
    const actions = document.createElement('div');
    actions.className = 'report-run-actions';
    if (primaryReport) actions.append(reportButton(primaryReport.reportId));
    const remove = document.createElement('button');
    remove.type = 'button';
    remove.className = 'report-action history-delete';
    remove.setAttribute('aria-label', t('deleteHistory'));
    remove.innerHTML =
      '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7h16M9 7V4h6v3m-8 0 1 13h8l1-13M10 11v5m4-5v5" /></svg>';
    remove.addEventListener('click', (event) => {
      event.stopPropagation();
      openHistoryDeleteDialog([run.id], remove);
    });
    actions.append(remove);
    header.append(heading, actions);
    article.append(header);
    const metaRow = document.createElement('div');
    metaRow.className = 'history-meta-row';
    const meta = document.createElement('small');
    meta.textContent = [
      new Date(run.createdAt).toLocaleString(),
      advancedMode ? run.modelName : '',
    ]
      .filter(Boolean)
      .join(' · ');
    metaRow.append(meta);
    const rerunRequest = taskHistoryRerunRequest(run);
    if (rerunRequest) {
      const rerun = document.createElement('button');
      rerun.type = 'button';
      rerun.className = 'report-action history-rerun';
      rerun.disabled = running || preparingExecution;
      rerun.setAttribute('aria-label', t('rerunTask'));
      rerun.innerHTML =
        '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 12a8 8 0 1 0 2.35-5.65L4 8.7M4 4v4.7h4.7" /></svg>' +
        `<span>${t('rerunTask')}</span>`;
      rerun.addEventListener('click', (event) => {
        event.stopPropagation();
        closeReportDialog(false);
        void execute(rerunRequest);
      });
      metaRow.append(rerun);
    }
    article.append(metaRow);
    if (run.detail) {
      const detail = document.createElement('p');
      detail.className = 'history-detail';
      detail.id = detailId;
      detail.textContent = run.detail;
      detail.hidden = true;
      article.append(detail);
    }
    reportHistoryElement.append(article);
  }
}

function persistReportHistory(): void {
  localStorage.setItem(REPORT_HISTORY_KEY, JSON.stringify(reportHistory));
  renderReportHistory();
  renderConversation();
}

function renderConversation(): void {
  const language = resolveLanguage(languagePreference);
  const names = new Map<string, string>();
  for (const entry of [...reportHistory].reverse()) {
    const id = entry.conversationId ?? entry.id;
    if (!names.has(id)) names.set(id, entry.source.slice(0, 28) || (language === 'zh' ? '旧对话' : 'Previous chat'));
  }
  conversationList.replaceChildren();
  if (activeConversationId && !names.has(activeConversationId)) names.set(activeConversationId, 'Tapilot');
  const orderedIds = [...new Set(reportHistory.map(entry => entry.conversationId ?? entry.id))];
  if (activeConversationId && !orderedIds.includes(activeConversationId)) orderedIds.unshift(activeConversationId);
  for (const id of orderedIds) {
    const option = document.createElement('option');
    option.value = id;
    option.textContent = names.get(id) ?? id;
    conversationList.append(option);
  }
  conversationList.value = activeConversationId;
  chatThread.replaceChildren();
  const entries = conversationEntries(reportHistory, activeConversationId);
  if (!entries.length) {
    const empty = document.createElement('div');
    empty.className = 'chat-empty';
    empty.textContent = language === 'zh' ? '你好，我是 Tapilot。说说你想在手机上完成什么。' : 'Hi, I’m Tapilot. What would you like me to do on your phone?';
    chatThread.append(empty);
  }
  for (const entry of entries) {
    const user = document.createElement('div');
    user.className = 'chat-message chat-user';
    user.textContent = entry.source;
    chatThread.append(user);
    const assistant = document.createElement('div');
    assistant.className = 'chat-message chat-assistant';
    assistant.textContent = entry.status === 'running'
      ? (language === 'zh' ? '正在执行…' : 'Working…')
      : entry.reply || entry.detail || (language === 'zh' ? '任务已完成' : 'Task complete');
    const report = primaryTaskReport(entry.reports);
    if (report) assistant.append(reportButton(report.reportId));
    chatThread.append(assistant);
  }
  chatThread.scrollTop = chatThread.scrollHeight;
}

function openReportDialog(trigger: HTMLElement = taskHistoryTrigger): void {
  renderReportHistory();
  reportDialogTrigger = trigger;
  reportDialog.hidden = false;
  document.body.classList.add('dialog-open');
  taskHistoryTrigger.setAttribute('aria-expanded', 'true');
  reportClose.focus();
}

function setUiStatus(state: UiState, title: string, message: string): void {
  statusCard.hidden = false;
  statusCard.dataset.state = state;
  statusTitle.textContent = title;
  status.textContent = message;
  accessibilityActions.hidden = true;
}

function renderAccessibilityStatus(): void {
  if (!accessibilityState || running) return;
  const { connected, enabled } = accessibilityState;
  const state = resolveAccessibilityStatus(connected, enabled);
  accessibilityConnectedDot.hidden = !connected;
  if (state === 'connected') {
    accessibilityActions.hidden = true;
    statusCard.hidden = true;
    return;
  }
  setUiStatus(
    'warning',
    t(
      state === 'disconnected'
        ? 'accessibilityDisconnected'
        : 'accessibilityNotEnabled',
    ),
    t(
      state === 'disconnected'
        ? 'accessibilityDisconnectedDetail'
        : 'accessibilityNotEnabledDetail',
    ),
  );
  openBatteryHelpButton.hidden = state !== 'disconnected';
  accessibilityActions.hidden = false;
}

function showResult(
  message?: string,
  status?: LatestTaskResultStatus,
): void {
  result.textContent = message ?? '';
  resultCard.hidden = !message;
  if (status) resultCard.dataset.status = status;
  else delete resultCard.dataset.status;
  openReportButton.hidden = !message || !latestReportId;
  resultCard.classList.toggle('has-report', Boolean(message && latestReportId));
  resultCard.classList.remove('is-expanded');
  resultCard.setAttribute('aria-expanded', 'false');
  resultCard.tabIndex = message ? 0 : -1;
}

function revealResult(
  message: string,
  title = t('result'),
  status: LatestTaskResultStatus,
): void {
  latestTaskResponse = message;
  latestTaskResultStatus = status;
  showingPersistedReport = true;
  localStorage.setItem(
    LATEST_TASK_RESULT_KEY,
    serializeLatestTaskResult({ message, status }),
  );
  localStorage.removeItem(LATEST_TASK_RESPONSE_KEY);
  resultTitle.textContent = title;
  showResult(message, status);
  requestAnimationFrame(() => {
    resultCard.scrollIntoView({ behavior: 'smooth', block: 'center' });
  });
}

function updateRunButtonPresentation(): void {
  runButtonLabel.textContent = running
    ? t('running')
    : t(runKind === 'yaml' ? 'runYaml' : 'start');
  const actionLabel = running
    ? t('stop')
    : t(runKind === 'yaml' ? 'runYaml' : 'start');
  runButton.setAttribute('aria-label', actionLabel);
  runButton.title = actionLabel;
}

function setRunning(value: boolean): void {
  running = value;
  voiceRecord.disabled = value;
  voiceModeToggle.disabled = value;
  conversationList.disabled = value;
  newConversation.disabled = value;
  runningTaskBar.hidden = !value;
  runningTaskStop.disabled = false;
  runButton.disabled = false;
  runButton.classList.toggle('is-running', value);
  updateRunButtonPresentation();
  taskModeButton.disabled = value;
  yamlModeButton.disabled = value;
  advancedModeToggle.disabled = value;
}

function setRunKind(value: RunKind): void {
  if (fullscreenEditor) closeFullscreenEditor();
  runKind = resolveRunKindForAdvancedMode(value, advancedMode);
  localStorage.setItem(RUN_KIND_KEY, runKind);
  const yaml = runKind === 'yaml';
  taskPanel.hidden = yaml;
  yamlPanel.hidden = !yaml;
  const editor = yaml ? fields.yaml : fields.task;
  let composer = editor.parentElement;
  if (!composer?.classList.contains('composer')) {
    composer = document.createElement('div');
    composer.className = 'composer';
    editor.before(composer);
    composer.append(editor);
  }
  if (yaml) composer.append(runButton);
  else taskPanel.append(runButton);
  voiceModeToggle.hidden = yaml;
  voiceRecord.hidden = yaml || !taskPanel.classList.contains('is-voice-mode');
  taskModeButton.classList.toggle('is-active', !yaml);
  yamlModeButton.classList.toggle('is-active', yaml);
  taskModeButton.setAttribute('aria-selected', String(!yaml));
  yamlModeButton.setAttribute('aria-selected', String(yaml));
  if (!running) updateRunButtonPresentation();
}

function isModelConfigurationComplete(): boolean {
  return [
    fields.baseUrl,
    fields.apiKey,
    fields.modelName,
    fields.modelFamily,
  ].every((input) => input.value.trim());
}

function updateModelConfigStatus(): void {
  const complete = isModelConfigurationComplete();
  modelConfigStatus.textContent = complete
    ? `${t('configured')} · ${fields.modelName.value.trim()}`
    : t('modelConfigHint');
  modelConfigRequired.hidden = complete;
}

function requireConfiguration(kind: RunKind, sourceOverride?: string): void {
  const required: Array<[HTMLInputElement, TranslationKey]> = [
    [fields.baseUrl, 'missingBaseUrl'],
    [fields.apiKey, 'missingApiKey'],
    [fields.modelName, 'missingModelName'],
    [fields.modelFamily, 'missingModelFamily'],
  ];
  const missing = required.find(([input]) => !input.value.trim());
  if (missing) {
    openSettings();
    missing[0].focus();
    throw new Error(t(missing[1]));
  }
  const sourceField = kind === 'task' ? fields.task : fields.yaml;
  if (!(sourceOverride ?? sourceField.value).trim()) {
    sourceField.focus();
    throw new Error(t(kind === 'task' ? 'missingTask' : 'missingYaml'));
  }
}

function requireModelConfiguration(): void {
  const required: Array<[HTMLInputElement, TranslationKey]> = [
    [fields.baseUrl, 'missingBaseUrl'],
    [fields.apiKey, 'missingApiKey'],
    [fields.modelName, 'missingModelName'],
    [fields.modelFamily, 'missingModelFamily'],
  ];
  const missing = required.find(([input]) => !input.value.trim());
  if (!missing) return;
  openSettings();
  missing[0].focus();
  throw new Error(t(missing[1]));
}

function modelEnvironment(): Record<string, string> {
  return {
    MIDSCENE_MODEL_BASE_URL: fields.baseUrl.value.trim(),
    MIDSCENE_MODEL_API_KEY: fields.apiKey.value.trim(),
    MIDSCENE_MODEL_NAME: fields.modelName.value.trim(),
    MIDSCENE_MODEL_FAMILY: fields.modelFamily.value.trim(),
  };
}

async function execute(
  request?: { source: string; kind: RunKind },
): Promise<void> {
  if (running || preparingExecution) {
    setUiStatus('running', t('taskAlreadyRunning'), t('stopCurrentTask'));
    return;
  }
  const executionKind = request?.kind ?? runKind;
  const source = (
    request?.source ??
    (executionKind === 'task' ? fields.task : fields.yaml).value
  ).trim();
  preparingExecution = true;
  try {
    await modelProfilesReady;
    await flushModelProfileSave();
    await modelProfiles.save(currentModelPreset, activeModelConfiguration());
    requireConfiguration(executionKind, request?.source);
  } catch (error) {
    preparingExecution = false;
    setUiStatus(
      'error',
      t('configIncomplete'),
      error instanceof Error ? error.message : String(error),
    );
    return;
  }
  setRunning(true);
  preparingExecution = false;
  stoppedByUser = false;
  if (!activeConversationId) activeConversationId = `chat-${Date.now()}`;
  localStorage.setItem('tapilot.activeConversation', activeConversationId);
  const historyEntryId = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  reportHistory = addTaskHistoryEntry(reportHistory, {
    id: historyEntryId,
    conversationId: activeConversationId,
    createdAt: Date.now(),
    source,
    kind: executionKind,
    modelName: fields.modelName.value.trim(),
    status: 'running',
    reports: [],
  });
  persistReportHistory();
  if (!request && executionKind === 'task') {
    fields.task.value = '';
    localStorage.setItem('midscene.task', '');
    document.documentElement.style.removeProperty('--task-editor-height');
  }
  showingPersistedReport = false;
  latestTaskResponse = '';
  latestTaskResultStatus = undefined;
  latestReportId = '';
  localStorage.removeItem(LATEST_TASK_RESPONSE_KEY);
  localStorage.removeItem(LATEST_TASK_RESULT_KEY);
  localStorage.removeItem('midscene.latestReportId');
  showResult();
  const bridge = new WebViewNativeBridge();
  activeBridge = bridge;
  try {
    setUiStatus('running', t('preparing'), t('preparingDetail'));
    let runResult: Awaited<ReturnType<WebViewNativeBridge['runNodeTest']>>;
    if (executionKind === 'task') {
      const toolReports: Awaited<ReturnType<WebViewNativeBridge['runNodeTest']>>['reports'] = [];
      let lastReportId = '';
      let startedPhoneWork = false;
      const reply = await runChatAgent({
        history: reportHistory.filter(entry => entry.id !== historyEntryId),
        conversationId: activeConversationId,
        source,
        language: resolveLanguage(languagePreference),
        isStopped: () => stoppedByUser,
        complete: async (messages: ChatMessage[]): Promise<ChatCompletion> => {
          setUiStatus('running', t('executingTask'), t('executingDetail'));
          const response = await bridge.httpRequest({
            url: modelConnectionEndpoint(fields.baseUrl.value.trim()),
            method: 'POST',
            headers: {
              Authorization: `Bearer ${fields.apiKey.value.trim()}`,
              'Content-Type': 'application/json',
            },
            bodyBase64: encodeBase64(JSON.stringify({
              model: fields.modelName.value.trim(),
              messages,
              tools: [PHONE_TOOL],
              tool_choice: 'auto',
              stream: false,
              max_tokens: 2048,
            })),
          });
          const body = decodeBase64(response.bodyBase64);
          if (response.status < 200 || response.status >= 300)
            throw new Error(`Conversation model HTTP ${response.status}: ${body.slice(0, 500)}`);
          return JSON.parse(body) as ChatCompletion;
        },
        operatePhone: async instruction => {
          const capabilities = await bridge.getCapabilities();
          if (!capabilities.canCapture || !capabilities.canPerformGestures)
            throw new Error(t('enableAccessibility'));
          const pending = bridge.runNodeTest({
            source: instruction,
            kind: 'task',
            environment: modelEnvironment(),
          });
          if (!startedPhoneWork) {
            startedPhoneWork = true;
            await bridge.goHome();
          }
          const result = await pending;
          lastReportId = result.reportId;
          toolReports.push(...result.reports);
          return {
            success: result.exitCode === 0,
            result: result.exitCode === 0
              ? result.finalResponse || result.output.slice(-10).join('\n') || 'Phone operation completed'
              : result.failure || result.output.slice(-10).join('\n') || 'Phone operation failed',
          };
        },
      });
      runResult = {
        exitCode: 0,
        reportId: lastReportId,
        reports: toolReports,
        output: [],
        finalResponse: reply,
      };
    } else {
      const capabilities = await bridge.getCapabilities();
      if (!capabilities.canCapture || !capabilities.canPerformGestures)
        throw new Error(t('enableAccessibility'));
      setUiStatus('running', t('executingTask'), t('executingDetail'));
      const pending = bridge.runNodeTest({
        source,
        kind: executionKind,
        environment: modelEnvironment(),
      });
      await bridge.goHome();
      runResult = await pending;
    }
    latestReportId = runResult.reportId;
    showingPersistedReport = false;
    if (latestReportId) localStorage.setItem('midscene.latestReportId', latestReportId);
    const reports = runResult.reports?.length
      ? runResult.reports
      : runResult.reportId ? [
          {
            reportId: runResult.reportId,
            kind: 'agent' as const,
            title: 'Agent report',
          },
        ] : [];
    const output = runResult.output.slice(-30).join('\n');
    if (runResult.exitCode === 0) {
      reportHistory = updateTaskHistoryEntry(reportHistory, historyEntryId, {
        status: 'succeeded',
        detail: runResult.finalResponse,
        reports,
      });
      setUiStatus('success', t('taskSucceeded'), t('taskSucceededDetail'));
      latestTaskResponse = resolveRunResultMessage(
        runResult.finalResponse,
        runResult.output,
        t('taskSucceededDetail'),
      );
      reportHistory = updateTaskHistoryEntry(reportHistory, historyEntryId, {
        reply: latestTaskResponse,
      });
      revealResult(latestTaskResponse, t('taskSucceeded'), 'succeeded');
    } else {
      const failure = runResult.failure || `Exit code ${runResult.exitCode}`;
      reportHistory = updateTaskHistoryEntry(reportHistory, historyEntryId, {
        status: stoppedByUser ? 'stopped' : 'failed',
        detail: stoppedByUser ? t('taskStoppedDetail') : failure,
        reports,
      });
      const classified = classifyExecutionFailure(failure, runResult.output);
      if (stoppedByUser) {
        setUiStatus('ready', t('taskStopped'), t('taskStoppedDetail'));
        revealResult(
          t('taskStoppedDetail'),
          t('taskStopped'),
          'stopped',
        );
      } else if (classified.category === 'network') {
        setUiStatus(
          'error',
          t('modelNetworkError'),
          t('modelNetworkErrorDetail'),
        );
        const technicalDetail = classified.hostname
          ? `${t('dnsLookupFailed')}: ${classified.hostname}`
          : t('modelNetworkError');
        revealResult(
          `${t('modelNetworkErrorDetail')}\n\n${t('technicalDetails')}: ${technicalDetail}`,
          t('modelNetworkError'),
          'failed',
        );
      } else if (
        classified.category === 'modelTimeout' ||
        classified.category === 'stepTimeout'
      ) {
        const isModelTimeout = classified.category === 'modelTimeout';
        const title = t(isModelTimeout ? 'modelTimeout' : 'stepTimeout');
        const detail = t(
          isModelTimeout ? 'modelTimeoutDetail' : 'stepTimeoutDetail',
        );
        setUiStatus('error', title, detail);
        revealResult(
          `${detail}\n\n${t('technicalDetails')}: ${output || failure}`,
          title,
          'failed',
        );
      } else {
        setUiStatus('error', t('taskFailed'), failure);
        revealResult(
          `${failure}${output ? `\n\n${output}` : ''}`,
          t('taskFailed'),
          'failed',
        );
      }
    }
    persistReportHistory();
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    reportHistory = updateTaskHistoryEntry(reportHistory, historyEntryId, {
      status: stoppedByUser ? 'stopped' : 'failed',
      detail: stoppedByUser ? t('taskStoppedDetail') : message,
    });
    persistReportHistory();
    setUiStatus(
      stoppedByUser ? 'ready' : 'error',
      stoppedByUser ? t('taskStopped') : t('taskFailed'),
      stoppedByUser ? t('taskStoppedDetail') : message,
    );
    revealResult(
      message,
      t(stoppedByUser ? 'taskStopped' : 'taskFailed'),
      stoppedByUser ? 'stopped' : 'failed',
    );
  } finally {
    setRunning(false);
    leaveEditorFocusMode();
    await bridge.bringToFront().catch((error) => {
      console.error('Unable to return Tapilot to the foreground', error);
    });
    bridge.destroy();
    activeBridge = undefined;
  }
}

for (const [key, input] of Object.entries(fields)) {
  const storageKey = `midscene.${key}`;
  const defaultLanguage = resolveLanguage(languagePreference);
  const defaultValue =
    key === 'task'
      ? ''
      : key === 'yaml'
        ? DEFAULT_YAML[defaultLanguage]
        : '';
  const storedValue = localStorage.getItem(storageKey);
  input.value = key === 'task' &&
    (storedValue === DEFAULT_TASK.zh || storedValue === DEFAULT_TASK.en)
    ? ''
    : storedValue ?? defaultValue;
  input.addEventListener('input', () => {
    if (key !== 'apiKey') localStorage.setItem(storageKey, input.value);
    if ((MODEL_FIELD_KEYS as readonly string[]).includes(key)) {
      queueModelProfileSave();
    }
    updateModelConfigStatus();
    if (input instanceof HTMLTextAreaElement) resizeEditorsToContent(input);
  });
}
for (const editor of [fields.task, fields.yaml]) {
  editor.addEventListener('focus', enterEditorFocusMode);
  editor.addEventListener('blur', scheduleEditorFocusModeExit);
}
const visualViewport = window.visualViewport;
function pinFocusedEditorViewport(): void {
  if (!document.body.classList.contains('task-input-focused')) return;
  window.scrollTo({ top: 0, left: 0, behavior: 'instant' });
  document.scrollingElement?.scrollTo({ top: 0, left: 0, behavior: 'instant' });
}
function updateKeyboardViewport(): void {
  document.documentElement.style.setProperty(
    '--keyboard-viewport-height', `${visualViewport?.height ?? window.innerHeight}px`,
  );
  pinFocusedEditorViewport();
}
updateKeyboardViewport();
visualViewport?.addEventListener('resize', updateKeyboardViewport);
let expandedViewportHeight = visualViewport?.height ?? window.innerHeight;
visualViewport?.addEventListener('resize', () => {
  expandedViewportHeight = Math.max(expandedViewportHeight, visualViewport.height);
  const keyboardIsHidden = visualViewport.height >= expandedViewportHeight * 0.82;
  if (!keyboardIsHidden || !document.body.classList.contains('task-input-focused')) {
    return;
  }
  const activeElement = document.activeElement;
  if (activeElement === fields.task || activeElement === fields.yaml) {
    (activeElement as HTMLTextAreaElement).blur();
  }
});
requestAnimationFrame(() =>
  resizeEditorsToContent(runKind === 'task' ? fields.task : fields.yaml),
);
document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'hidden') void flushModelProfileSave();
});

localStorage.setItem(MODEL_PRESET_KEY, currentModelPreset);
const legacyModelConfiguration = activeModelConfiguration();
if (hasSavedModelConfiguration) {
  for (const key of ['baseUrl', 'modelName', 'modelFamily'] as const) {
    const profileKey = `midscene.modelPresetConfig.custom.${key}`;
    if (localStorage.getItem(profileKey) === null) {
      localStorage.setItem(profileKey, legacyModelConfiguration[key]);
    }
  }
}
runButton.disabled = true;
testModelButton.disabled = true;
modelPresetTrigger.disabled = true;
const modelProfilesReady = modelProfiles
  .initialize()
  .then(() => {
    applyModelConfiguration(modelProfiles.load(currentModelPreset));
    updateModelPresetUi();
    updateModelConfigStatus();
    modelConfig.open = resolveInitialModelConfigOpen(
      savedModelConfigOpen,
      isModelConfigurationComplete(),
    );
  })
  .catch((error) => {
    setUiStatus(
      'error',
      t('deviceError'),
      error instanceof Error ? error.message : String(error),
    );
    throw error;
  })
  .finally(() => {
    runButton.disabled = false;
    testModelButton.disabled = false;
    modelPresetTrigger.disabled = false;
  });
void modelProfilesReady.catch(() => undefined);

modelPresetTrigger.addEventListener('click', openModelPresetDialog);
modelPresetDialog.addEventListener('click', (event) => {
  if (event.target === modelPresetDialog) closeModelPresetDialog();
});

languageTrigger.addEventListener('click', openLanguageDialog);
languageDialog.addEventListener('click', (event) => {
  if (event.target === languageDialog) closeLanguageDialog();
});
document.addEventListener('keydown', (event) => {
  if (event.key !== 'Escape') return;
  if (fullscreenEditor) closeFullscreenEditor();
  else if (!fieldHelpDialog.hidden) closeFieldHelp();
  else if (!taskExamplesDialog.hidden) closeTaskExamples();
  else if (!backgroundHelpDialog.hidden) closeBackgroundHelpDialog();
  else if (!modelPresetDialog.hidden) closeModelPresetDialog();
  else if (!modelResetDialog.hidden) closeModelResetDialog();
  else if (!historyDeleteDialog.hidden) closeHistoryDeleteDialog();
  else if (!languageDialog.hidden) closeLanguageDialog();
  else if (!themeDialog.hidden) closeThemeDialog();
  else if (!edgeEffectDialog.hidden) closeEdgeEffectDialog();
  else if (!reportDialog.hidden) closeReportDialog();
  else if (!settingsPage.hidden) closeSettings();
});
for (const button of fieldHelpButtons) {
  button.addEventListener('click', () => {
    const field = button.dataset.fieldHelp;
    if (!(MODEL_FIELD_KEYS as readonly string[]).includes(field ?? '')) return;
    openFieldHelp(field as (typeof MODEL_FIELD_KEYS)[number]);
  });
}
fieldHelpDone.addEventListener('click', () => closeFieldHelp());
fieldHelpDialog.addEventListener('click', (event) => {
  if (event.target === fieldHelpDialog) closeFieldHelp();
});
taskExamplesTrigger.addEventListener('click', openTaskExamples);
taskExamplesDialog.addEventListener('click', (event) => {
  if (event.target === taskExamplesDialog) closeTaskExamples();
});
for (const option of languageOptions) {
  option.addEventListener('click', () => {
    const selected = option.dataset.language;
    if (selected !== 'system' && selected !== 'zh' && selected !== 'en') return;
    languagePreference = selected;
    localStorage.setItem(LANGUAGE_KEY, languagePreference);
    closeLanguageDialog(false);
    applyLanguage();
    const bridge = new WebViewNativeBridge();
    void bridge
      .setAppLanguage(languagePreference)
      .finally(() => bridge.destroy());
  });
}
themeTrigger.addEventListener('click', openThemeDialog);
themeDialog.addEventListener('click', (event) => {
  if (event.target === themeDialog) closeThemeDialog();
});
for (const option of themeOptions) {
  option.addEventListener('click', () => {
    themePreference = resolveThemePreference(
      option.dataset.themePreference ?? null,
    );
    localStorage.setItem(THEME_KEY, themePreference);
    closeThemeDialog(false);
    applyTheme(true);
  });
}
systemTheme.addEventListener('change', () => {
  if (themePreference === 'system') applyTheme();
});
edgeEffectTrigger.addEventListener('click', openEdgeEffectDialog);
edgeEffectDialog.addEventListener('click', (event) => {
  if (event.target === edgeEffectDialog) closeEdgeEffectDialog();
});
edgeEffectClose.addEventListener('click', () => closeEdgeEffectDialog());
for (const option of edgeEffectOptions) {
  option.addEventListener('click', () => {
    const mode = option.dataset.edgeEffectMode;
    if (mode !== 'auto' && mode !== 'square' && mode !== 'custom') return;
    edgeEffectSettings.mode = mode;
    queueEdgeEffectSave();
  });
}
edgeEffectRadius.addEventListener('input', () => {
  edgeEffectSettings.cornerRadiusDp = Number(edgeEffectRadius.value);
  queueEdgeEffectSave();
});
edgeEffectInset.addEventListener('input', () => {
  edgeEffectSettings.insetDp = Number(edgeEffectInset.value);
  queueEdgeEffectSave();
});
advancedModeToggle.addEventListener('click', () => {
  keepElementAtViewportPosition(advancedModeToggle, () => {
    advancedMode = !advancedMode;
    localStorage.setItem(ADVANCED_MODE_KEY, String(advancedMode));
    updateAdvancedModeUi();
    renderReportHistory();
  });
});
rememberViewportPositionOnPointerDown(advancedModeToggle);
let floatingBallUpdatePending = false;
floatingBallToggle.addEventListener('click', async () => {
  if (floatingBallUpdatePending) return;
  floatingBallUpdatePending = true;
  floatingBallToggle.setAttribute('aria-busy', 'true');
  const bridge = new WebViewNativeBridge();
  try {
    const state = await bridge.setFloatingBallEnabled(!floatingBallEnabled);
    keepElementAtViewportPosition(floatingBallToggle, () => {
      floatingBallEnabled = state.enabled;
      floatingBallAvailable = state.available;
      updateFloatingBallUi();
    });
  } catch (error) {
    console.error('Unable to change floating ball state', error);
  } finally {
    floatingBallUpdatePending = false;
    floatingBallToggle.removeAttribute('aria-busy');
    bridge.destroy();
  }
});
rememberViewportPositionOnPointerDown(floatingBallToggle);
backgroundHelpTrigger.addEventListener('click', openBackgroundHelpDialog);
openAccessibilitySettingsButton.addEventListener('click', () => {
  const bridge = new WebViewNativeBridge();
  void bridge.openAccessibilitySettings().finally(() => bridge.destroy());
});
openBatteryHelpButton.addEventListener('click', openBackgroundHelpDialog);
backgroundHelpBatterySettings.addEventListener('click', () => {
  const bridge = new WebViewNativeBridge();
  void bridge.openBatterySettings().finally(() => bridge.destroy());
  closeBackgroundHelpDialog(false);
});
backgroundHelpDone.addEventListener('click', () => closeBackgroundHelpDialog());
backgroundHelpDialog.addEventListener('click', (event) => {
  if (event.target === backgroundHelpDialog) closeBackgroundHelpDialog();
});
for (const button of editorExpandButtons) {
  button.addEventListener('click', () => {
    const panelId = button.dataset.editor;
    const panel = panelId ? document.getElementById(panelId) : null;
    if (panel) toggleFullscreenEditor(panel);
  });
}

// Configuration profiles, including the encrypted API Key, load asynchronously.
// Keep the panel collapsed until that load completes so the temporary empty state
// cannot overwrite a user's saved preference and reopen the panel on app resume.
modelConfig.open = savedModelConfigOpen === 'true';
modelConfig.addEventListener('toggle', () =>
  localStorage.setItem(MODEL_CONFIG_OPEN_KEY, String(modelConfig.open)),
);
function openSettings(): void {
  if (!settingsPage.hidden) return;
  fields.task.blur();
  settingsPage.hidden = false;
  document.body.classList.add('show-settings');
  modelConfig.open = true;
  settingsScroll.scrollTop = 0;
  history.pushState({ tapilotPage: 'settings' }, '');
}
function hideSettings(): void {
  settingsPage.hidden = true;
  document.body.classList.remove('show-settings');
  settingsOpen.focus();
}
function closeSettings(): void {
  if (history.state?.tapilotPage === 'settings') history.back();
  else hideSettings();
}
window.__tapilotHandleBack = (): boolean => {
  if (settingsPage.hidden) return false;
  closeSettings();
  return true;
};
window.addEventListener('popstate', () => {
  if (!settingsPage.hidden) hideSettings();
});
modelConfigRequired.addEventListener('click', () => {
  openSettings();
});
settingsOpen.addEventListener('click', openSettings);
settingsBack.addEventListener('click', closeSettings);

apiKeyToggle.addEventListener('click', () => {
  const reveal = fields.apiKey.type === 'password';
  fields.apiKey.type = reveal ? 'text' : 'password';
  apiKeyToggle.setAttribute(
    'aria-label',
    reveal ? t('hideApiKey') : t('showApiKey'),
  );
});
testModelButton.addEventListener('click', () => void testModelConnection());
resetModelButton.addEventListener('click', openModelResetDialog);
modelResetCancel.addEventListener('click', () => closeModelResetDialog());
modelResetConfirm.addEventListener(
  'click',
  () => void resetCurrentModelPreset(),
);
modelResetDialog.addEventListener('click', (event) => {
  if (event.target === modelResetDialog) closeModelResetDialog();
});
historyDeleteCancel.addEventListener('click', () =>
  closeHistoryDeleteDialog(),
);
historyDeleteConfirm.addEventListener('click', async () => {
  const historyIds = new Set(pendingHistoryDeleteIds);
  const entries = reportHistory.filter((item) => historyIds.has(item.id));
  if (entries.length === 0) return;
  historyDeleteConfirm.disabled = true;
  historyDeleteError.hidden = true;
  const bridge = new WebViewNativeBridge();
  try {
    const reportIds = [
      ...new Set(
        entries.flatMap((entry) =>
          entry.reports.map((report) => report.reportId),
        ),
      ),
    ];
    await Promise.all(reportIds.map((reportId) => bridge.deleteReport(reportId)));
    if (reportIds.includes(latestReportId)) {
      latestReportId = '';
      localStorage.removeItem('midscene.latestReportId');
      showResult(latestTaskResponse, latestTaskResultStatus);
    }
    reportHistory = reportHistory.filter((item) => !historyIds.has(item.id));
    persistReportHistory();
    closeHistoryDeleteDialog(false);
    renderReportHistory();
  } catch (error) {
    console.error('Unable to delete task history report', error);
    historyDeleteError.textContent = t('deleteHistoryFailed');
    historyDeleteError.hidden = false;
  } finally {
    historyDeleteConfirm.disabled = false;
    bridge.destroy();
  }
});
historyDeleteDialog.addEventListener('click', (event) => {
  if (event.target === historyDeleteDialog) closeHistoryDeleteDialog();
});
taskModeButton.addEventListener('click', () => setRunKind('task'));
yamlModeButton.addEventListener('click', () => setRunKind('yaml'));
async function stopActiveTask(): Promise<void> {
  if (!activeBridge) return;
  stoppedByUser = true;
  runButton.disabled = true;
  runningTaskStop.disabled = true;
  try {
    await Promise.allSettled([
      activeBridge.cancelSession(),
      activeBridge.stopNodeTest(),
    ]);
  } catch (error) {
    console.error('Unable to stop Node Test Runner', error);
    runButton.disabled = false;
    runningTaskStop.disabled = false;
  }
}

runButton.addEventListener('click', async () => {
  if (!running) {
    await execute();
    return;
  }
  await stopActiveTask();
});
runButton.addEventListener('pointerdown', (event) => event.preventDefault());
runningTaskStop.addEventListener('click', () => void stopActiveTask());

conversationList.addEventListener('change', () => {
  if (running) return;
  activeConversationId = conversationList.value;
  localStorage.setItem('tapilot.activeConversation', activeConversationId);
  renderConversation();
});
newConversation.addEventListener('click', () => {
  if (running) return;
  activeConversationId = `chat-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`;
  localStorage.setItem('tapilot.activeConversation', activeConversationId);
  renderConversation();
  fields.task.focus();
});

type SpeechProvider = 'volcengine' | 'deepgram';
const storedSpeechProvider = localStorage.getItem('tapilot.speech.provider');
speechProvider.value = storedSpeechProvider === 'deepgram' ? 'deepgram' : 'volcengine';
function selectedSpeechProvider(): SpeechProvider {
  return speechProvider.value === 'deepgram' ? 'deepgram' : 'volcengine';
}
async function refreshSpeechProvider(): Promise<void> {
  const provider = selectedSpeechProvider();
  speechProviderHint.textContent = t(provider === 'volcengine' ? 'volcengineHint' : 'deepgramHint');
  const bridge = new WebViewNativeBridge();
  try {
    const { configured } = await bridge.hasSpeechCredential(provider);
    if (selectedSpeechProvider() !== provider) return;
    speechConfigStatus.textContent = configured
      ? (resolveLanguage(languagePreference) === 'zh' ? 'API Key 已保存' : 'API Key saved')
      : (resolveLanguage(languagePreference) === 'zh' ? '尚未配置' : 'Not configured');
  } catch {
    speechConfigStatus.textContent = '';
  } finally {
    bridge.destroy();
  }
}
speechProvider.addEventListener('change', () => {
  localStorage.setItem('tapilot.speech.provider', selectedSpeechProvider());
  speechApiKey.value = '';
  void refreshSpeechProvider();
});
speechSave.addEventListener('click', async () => {
  try {
    const provider = selectedSpeechProvider();
    const bridge = new WebViewNativeBridge();
    try {
      if (speechApiKey.value.trim()) await bridge.setSpeechCredential(provider, speechApiKey.value.trim());
      const { configured } = await bridge.hasSpeechCredential(provider);
      if (!configured) throw new Error('API Key required');
      speechApiKey.value = '';
      speechConfigStatus.textContent = resolveLanguage(languagePreference) === 'zh' ? '已保存' : 'Saved';
    } finally { bridge.destroy(); }
  } catch (error) {
    speechConfigStatus.textContent = error instanceof Error ? error.message : String(error);
  }
});
void refreshSpeechProvider();

let voicePointerActive = false;
let voicePointerStartY = 0;
let voiceCancelOnRelease = false;
let voiceBridge: WebViewNativeBridge | undefined;
let voiceStart: Promise<void> | undefined;
function setVoiceMode(enabled: boolean): void {
  if (voicePointerActive || running) return;
  taskPanel.classList.toggle('is-voice-mode', enabled);
  localStorage.setItem('tapilot.inputMode', enabled ? 'voice' : 'text');
  voiceModeToggle.setAttribute('aria-pressed', String(enabled));
  voiceModeToggle.setAttribute('aria-label', t(enabled ? 'switchToTextInput' : 'switchToVoiceInput'));
  voiceRecord.hidden = !enabled;
  if (enabled) fields.task.blur();
  else fields.task.focus();
}
voiceModeToggle.addEventListener('click', () =>
  setVoiceMode(!taskPanel.classList.contains('is-voice-mode')),
);
function voiceFeedback(message: string): void {
  voiceState.textContent = message;
  voiceState.hidden = !message;
}
voiceRecord.addEventListener('pointerdown', (event) => {
  if (running || voicePointerActive || voiceBridge) return;
  event.preventDefault();
  voicePointerActive = true;
  voicePointerStartY = event.clientY;
  voiceCancelOnRelease = false;
  voiceRecord.setPointerCapture(event.pointerId);
  voiceRecord.classList.add('is-recording');
  voiceRecordLabel.textContent = t('releaseToSend');
  voiceFeedback(t('slideUpToCancel'));
  const bridge = new WebViewNativeBridge();
  voiceBridge = bridge;
  voiceStart = (async () => {
    const provider = selectedSpeechProvider();
    const { configured } = await bridge.hasSpeechCredential(provider);
    if (!configured) {
      openSettings();
      requestAnimationFrame(() => speechProvider.scrollIntoView({ block: 'center' }));
      throw new Error(resolveLanguage(languagePreference) === 'zh' ? '请先保存语音转写 API 配置' : 'Save speech API settings first');
    }
    await bridge.startSpeech(provider, resolveLanguage(languagePreference) === 'zh' ? 'zh-CN' : 'en-US');
  })().catch(error => {
    voicePointerActive = false;
    voiceRecord.classList.remove('is-recording');
    voiceRecordLabel.textContent = t('holdToTalk');
    voiceFeedback(error instanceof Error ? error.message : String(error));
    bridge.destroy();
    voiceBridge = undefined;
    throw error;
  });
  void voiceStart.catch(() => undefined);
});
voiceRecord.addEventListener('pointermove', (event) => {
  if (!voicePointerActive) return;
  const cancel = voicePointerStartY - event.clientY > 72;
  if (cancel === voiceCancelOnRelease) return;
  voiceCancelOnRelease = cancel;
  voiceRecord.classList.toggle('will-cancel', cancel);
  voiceRecordLabel.textContent = t(cancel ? 'releaseToCancel' : 'releaseToSend');
});
voiceRecord.addEventListener('pointerup', async () => {
  if (!voicePointerActive || !voiceBridge) return;
  voicePointerActive = false;
  voiceRecord.classList.remove('is-recording');
  voiceRecord.classList.remove('will-cancel');
  voiceRecordLabel.textContent = t('holdToTalk');
  voiceRecord.disabled = true;
  const bridge = voiceBridge;
  try {
    await voiceStart;
    if (voiceCancelOnRelease) {
      await bridge.cancelSpeech();
      voiceFeedback('');
      return;
    }
    voiceFeedback(t('transcribing'));
    const { text } = await bridge.finishSpeech();
    voiceFeedback('');
    fields.task.value = text;
    setRunKind('task');
    await execute();
  } catch (error) {
    voiceFeedback(error instanceof Error ? error.message : String(error));
  } finally {
    bridge.destroy();
    voiceBridge = undefined;
    voiceRecord.disabled = running;
  }
});
voiceRecord.addEventListener('pointercancel', async () => {
  voicePointerActive = false;
  voiceRecord.classList.remove('is-recording');
  voiceRecord.classList.remove('will-cancel');
  voiceRecordLabel.textContent = t('holdToTalk');
  const bridge = voiceBridge;
  if (!bridge) return;
  await voiceStart?.catch(() => undefined);
  await bridge.cancelSpeech().catch(() => undefined);
  bridge.destroy();
  if (voiceBridge === bridge) voiceBridge = undefined;
  voiceFeedback('');
});

openReportButton.addEventListener('click', (event) => {
  event.stopPropagation();
  if (latestReportId) void openReport(latestReportId);
});
resultCard.addEventListener('click', (event) => {
  if ((event.target as HTMLElement).closest('button')) return;
  const expanded = !resultCard.classList.contains('is-expanded');
  resultCard.classList.toggle('is-expanded', expanded);
  resultCard.setAttribute('aria-expanded', String(expanded));
});
resultCard.addEventListener('keydown', (event) => {
  if (event.key !== 'Enter' && event.key !== ' ') return;
  event.preventDefault();
  const expanded = !resultCard.classList.contains('is-expanded');
  resultCard.classList.toggle('is-expanded', expanded);
  resultCard.setAttribute('aria-expanded', String(expanded));
});
taskHistoryTrigger.addEventListener('click', () =>
  openReportDialog(taskHistoryTrigger),
);
taskHistoryClear.addEventListener('click', () => {
  if (reportHistory.length > 0) {
    openHistoryDeleteDialog(
      reportHistory.map((entry) => entry.id),
      taskHistoryClear,
      true,
    );
  }
});
reportDialog.addEventListener('click', (event) => {
  if (event.target === reportDialog) closeReportDialog();
});

async function checkNodeRuntimeOnce(): Promise<void> {
  nodeRuntimeStatus.textContent = t('runtimeChecking');
  const bridge = new WebViewNativeBridge();
  try {
    const runtime = await bridge.doctorNodeRuntime();
    nodeRuntimeInfo = runtime;
    nodeRuntimeError = '';
  } catch (error) {
    nodeRuntimeInfo = undefined;
    nodeRuntimeError = error instanceof Error ? error.message : String(error);
  } finally {
    renderNodeRuntimeStatus();
    bridge.destroy();
  }
}

async function refreshCapabilities(): Promise<void> {
  if (running) return;
  const bridge = new WebViewNativeBridge();
  try {
    const capabilities = await bridge.getCapabilities();
    const connected =
      capabilities.canCapture && capabilities.canPerformGestures;
    if (accessibilityState) {
      accessibilityState.connected = connected;
      renderAccessibilityStatus();
    } else {
      accessibilityConnectedDot.hidden = !connected;
      if (connected) statusCard.hidden = true;
    }
  } catch (error) {
    setUiStatus(
      'error',
      t('deviceError'),
      error instanceof Error ? error.message : String(error),
    );
  } finally {
    bridge.destroy();
  }
}

async function displayAppVersion(): Promise<void> {
  const bridge = new WebViewNativeBridge();
  try {
    const version = await bridge.getAppVersion();
    appVersion.textContent = `Tapilot v${version.versionName} (${version.buildNumber})`;
    appVersion.hidden = false;
  } finally {
    bridge.destroy();
  }
}

async function loadFloatingBallState(): Promise<void> {
  const bridge = new WebViewNativeBridge();
  try {
    const state = await bridge.getFloatingBallState();
    floatingBallEnabled = state.enabled;
    floatingBallAvailable = state.available;
    updateFloatingBallUi();
  } finally {
    bridge.destroy();
  }
}

applyTheme(true);
applyLanguage();
renderReportHistory();
renderConversation();
enableBottomSheetGestures();
window.__midsceneRefreshCapabilities = () => void refreshCapabilities();
window.__midsceneSetAccessibilityState = (connected, enabled) => {
  accessibilityState = { connected, enabled };
  floatingBallAvailable = connected;
  updateFloatingBallUi();
  renderAccessibilityStatus();
};
window.__tapilotFocusTaskInput = () => {
  setRunKind('task');
  // The field may still own focus while the Activity is in the background. In that case
  // focus() does not dispatch another focus event, so enter the focused layout explicitly.
  enterEditorFocusMode();
  fields.task.focus({ preventScroll: true });
  fields.task.setSelectionRange(
    fields.task.value.length,
    fields.task.value.length,
  );
};
window.__tapilotSetThemePreference = (preference) => {
  themePreference = resolveThemePreference(preference);
  localStorage.setItem(THEME_KEY, themePreference);
  applyTheme(false);
};
window.__tapilotReportsDeleted = (reportIds) => {
  const deleted = new Set(reportIds);
  if (deleted.has(latestReportId)) {
    latestReportId = '';
    localStorage.removeItem('midscene.latestReportId');
    showResult(latestTaskResponse, latestTaskResultStatus);
  }
  reportHistory = reportHistory.map((entry) => ({
    ...entry,
    reports: entry.reports.filter((report) => !deleted.has(report.reportId)),
  }));
  persistReportHistory();
  renderReportHistory();
};
void refreshCapabilities();
void loadFloatingBallState().catch((error) => {
  console.error('Unable to read floating ball state', error);
});
void loadEdgeEffectSettings().catch((error) => {
  console.error('Unable to read edge effect settings', error);
});
void displayAppVersion().catch((error) => {
  console.error('Unable to read app version', error);
});
void checkNodeRuntimeOnce();
