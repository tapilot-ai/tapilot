import { describe, expect, it } from '@rstest/core';
import {
  MODEL_PRESETS,
  defaultModelConfiguration,
  getModelPresetConfiguration,
  resolveInitialModelPreset,
} from './model-presets';

describe('model presets', () => {
  it('uses the documented values for every built-in provider', () => {
    expect(MODEL_PRESETS).toEqual({
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
    });
  });

  it('migrates an existing saved configuration to Custom', () => {
    expect(resolveInitialModelPreset(null, true)).toBe('custom');
  });

  it('defaults a fresh installation to Doubao Seed', () => {
    expect(resolveInitialModelPreset(null, false)).toBe('doubao');
  });

  it('never supplies values that could overwrite an API key', () => {
    expect(getModelPresetConfiguration('custom')).toBeUndefined();
    expect(getModelPresetConfiguration('qwen')).not.toHaveProperty('apiKey');
  });

  it('starts each built-in preset with its own empty API key', () => {
    expect(defaultModelConfiguration('qwen')).toEqual({
      ...MODEL_PRESETS.qwen,
      apiKey: '',
    });
  });

  it('preserves the complete existing configuration for Custom', () => {
    const existing = {
      baseUrl: 'https://internal.example/v1',
      apiKey: 'keep-me',
      modelName: 'custom-model',
      modelFamily: 'custom-family',
    };
    expect(defaultModelConfiguration('custom', existing)).toEqual(existing);
  });
});
