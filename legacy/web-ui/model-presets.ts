export const MODEL_PRESET_IDS = [
  'doubao',
  'qwen',
  'deepseek',
  'gemini',
  'openai',
  'kimi',
  'mimo',
  'glm',
  'custom',
] as const;

export type ModelPresetId = (typeof MODEL_PRESET_IDS)[number];

export interface ModelPresetConfiguration {
  baseUrl: string;
  modelName: string;
  modelFamily: string;
}

export interface ModelConfiguration extends ModelPresetConfiguration {
  apiKey: string;
}

export const MODEL_PRESETS: Record<
  Exclude<ModelPresetId, 'custom'>,
  ModelPresetConfiguration
> = {
  doubao: {
    baseUrl: 'https://ark.cn-beijing.volces.com/api/v3',
    modelName: 'doubao-seed-2-1-turbo-260628',
    modelFamily: 'doubao-seed',
  },
  qwen: {
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    modelName: 'qwen3.7-plus',
    modelFamily: 'qwen3',
  },
  deepseek: {
    baseUrl: 'https://api.deepseek.com',
    modelName: 'deepseek-flash',
    modelFamily: 'deepseek',
  },
  gemini: {
    baseUrl: 'https://generativelanguage.googleapis.com/v1beta/openai/',
    modelName: 'gemini-3.5-flash',
    modelFamily: 'gemini',
  },
  openai: {
    baseUrl: 'https://api.openai.com/v1',
    modelName: 'gpt-6-astra',
    modelFamily: 'gpt-6',
  },
  kimi: {
    baseUrl: 'https://api.moonshot.cn/v1',
    modelName: 'kimi-k3',
    modelFamily: 'kimi3',
  },
  mimo: {
    baseUrl: 'https://api.xiaomimimo.com/v1',
    modelName: 'mimo-v2.5',
    modelFamily: 'xiaomi-mimo',
  },
  glm: {
    baseUrl: 'https://open.bigmodel.cn/api/paas/v4',
    modelName: 'glm-5v-turbo',
    modelFamily: 'glm-v',
  },
};

export function isModelPresetId(value: string | null): value is ModelPresetId {
  return MODEL_PRESET_IDS.some((id) => id === value);
}

export function resolveInitialModelPreset(
  storedPreset: string | null,
  hasSavedModelConfiguration: boolean,
): ModelPresetId {
  if (isModelPresetId(storedPreset)) return storedPreset;
  return hasSavedModelConfiguration ? 'custom' : 'doubao';
}

export function getModelPresetConfiguration(
  preset: ModelPresetId,
): ModelPresetConfiguration | undefined {
  return preset === 'custom' ? undefined : MODEL_PRESETS[preset];
}

export function defaultModelConfiguration(
  preset: ModelPresetId,
  customConfiguration?: ModelConfiguration,
): ModelConfiguration {
  const configuration = getModelPresetConfiguration(preset);
  if (configuration) return { ...configuration, apiKey: '' };
  return (
    customConfiguration ?? {
      baseUrl: '',
      apiKey: '',
      modelName: '',
      modelFamily: '',
    }
  );
}
